package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.MonkiProactiveMode
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.StatsCalculator
import com.yuzufin.app.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields

/** Preferencias de los mensajes proactivos (sugerencia: guardarlas en DataStore y en la copia de seguridad). */
data class ProactivePrefs(
    /** FULL: todos los mensajes. IMPORTANT: solo los que importan. OFF: ninguno. */
    val mode: MonkiProactiveMode = MonkiProactiveMode.IMPORTANT,
    /** Respeta el ajuste global: los avisos no muestran montos en la pantalla bloqueada. */
    val showAmounts: Boolean = false,
    /** Último día que se abrió la app (SessionInfo.markOpened); alimenta el «¿todo bien?». */
    val lastOpenEpochDay: Long = 0L,
)

/**
 * Decide qué mensajes proactivos tocan hoy. Función pura: recibe una foto inmutable de los datos
 * ([MonkiSnapshot]) y devuelve como máximo [MAX_NOTES_PER_DAY] notas, ordenadas por importancia.
 * El cooldown lo cumple la clave de deduplicación: quien llama guarda cada nota en
 * `app_notifications` con su `dedupeKey` y el índice único hace que no se repita hasta que cambie.
 */
object ProactiveMonki {

    /**
     * [limit] recorta la lista; quien guarda los avisos pasa `Int.MAX_VALUE` y se queda con los primeros
     * [MAX_NOTES_PER_DAY] que todavía no estaban guardados (así un aviso ya publicado no ocupa lugar).
     */
    fun notes(
        snapshot: MonkiSnapshot,
        today: LocalDate,
        prefs: ProactivePrefs,
        limit: Int = MAX_NOTES_PER_DAY,
    ): List<ProactiveNote> {
        if (prefs.mode == MonkiProactiveMode.OFF) return emptyList()
        val month = YearMonth.from(today)
        val monthKey = today.year * 100 + today.monthValue
        val weekKey = today.get(WeekFields.ISO.weekOfWeekBasedYear())
        // La semilla cambia cada día: las frases rotan y las pruebas quedan deterministas.
        val voice = MonkiProactive(
            MonkiRegionResolver.resolve(null, snapshot.currency.code),
            seed = (today.toEpochDay() % 1000).toInt(),
        )
        val money = { minor: Long ->
            if (prefs.showAmounts) MoneyFormatter.format(minor, snapshot.currency) else null
        }

        val out = ArrayList<ProactiveNote>()

        // 1) Hito de ahorro: una meta que cruzó el 50% o el 100%. A lo sumo una nota por meta.
        snapshot.goals.filter { it.targetMinor > 0L }.forEach { goal ->
            val saved = snapshot.savingsEntries.filter { it.goalId == goal.id }.sumOf { it.amountMinor }
            val milestone = when {
                SavingsMath.isComplete(goal.targetMinor, saved) -> 100
                SavingsMath.percent(goal.targetMinor, saved) >= 50 -> 50
                else -> null
            }
            if (milestone != null) {
                out += ProactiveNote(
                    ProactiveKind.MILESTONE,
                    "monki:milestone:${goal.id}:$milestone",
                    voice.milestone(goal.name, milestone),
                )
            }
        }

        // 2) Buen ingreso: el mayor del mes, al menos el doble del promedio de los meses completos.
        val monthIncomes = counted(snapshot)
            .filter { it.type == TransactionType.INCOME && YearMonth.from(it.date) == month }
        val avgIncome = averageMonthly(snapshot, today, TransactionType.INCOME)
        if (monthIncomes.isNotEmpty() && avgIncome != null) {
            val biggest = monthIncomes.maxOf { it.amountMinor }
            if (biggest >= avgIncome * 2) {
                out += ProactiveNote(
                    ProactiveKind.INCOME,
                    "monki:income:$monthKey",
                    voice.income(money(biggest)),
                )
            }
        }

        // 3) Categoría que se dispara: 150% de su promedio mensual. Recién desde el día 7. Las categorías
        // con presupuesto ya tienen sus propios avisos (80% y 100%): acá no se repiten.
        if (today.dayOfMonth >= MIN_DAY_FOR_OVERSPEND) {
            val spent = StatsCalculator.expenseByCategory(snapshot.initial, snapshot.transactions, month)
            val averages = averageByCategory(snapshot, today)
            spent.forEach { (categoryId, spentMinor) ->
                if (snapshot.budgets.any { it.categoryId == categoryId && it.limitMinor > 0L }) return@forEach
                val overAverage = averages[categoryId]?.let { spentMinor >= it * 3 / 2 } == true
                if (overAverage) {
                    val name = snapshot.categories.firstOrNull { it.id == categoryId }?.name ?: "una categoría"
                    out += ProactiveNote(
                        ProactiveKind.OVERSPEND,
                        "monki:overspend:$categoryId:$weekKey",
                        voice.overspend(name, money(spentMinor), "este mes"),
                    )
                }
            }
        }

        // 4) Libre estimado bajo: queda menos del 20% de lo que ingresó este mes.
        val point = StatsCalculator.monthPoint(snapshot.initial, snapshot.transactions, month)
        // Solo cuentan las cuentas sin pagar que vencen este mes.
        val committed = snapshot.bills
            .filter { !it.paid && YearMonth.from(it.dueDate) == month }
            .sumOf { it.amountMinor }
        val free = point.income - point.expense - committed
        if (point.income > 0L && free * 5 < point.income) {
            out += ProactiveNote(
                ProactiveKind.LOW_FUNDS,
                "monki:lowfunds:$monthKey",
                voice.lowFunds(money(free)),
            )
        }

        // 5) Racha de registro: 7+ días seguidos con al menos un movimiento propio. Cada 7 días.
        val streak = streakDays(snapshot, today)
        if (streak >= MIN_STREAK && streak % 7 == 0) {
            out += ProactiveNote(
                ProactiveKind.STREAK,
                "monki:streak:$streak",
                voice.streak(streak),
            )
        }

        // 6) Silencio: hace 7+ días que no se abre la app. Sin dato (0) no se avisa.
        val daysSinceOpen = (today.toEpochDay() - prefs.lastOpenEpochDay).toInt()
        if (prefs.lastOpenEpochDay > 0L && daysSinceOpen >= MIN_SILENCE_DAYS) {
            out += ProactiveNote(
                ProactiveKind.SILENCE,
                "monki:silence:$weekKey",
                voice.silence(daysSinceOpen),
            )
        }

        return out
            .filter { prefs.mode == MonkiProactiveMode.FULL || it.kind in IMPORTANT_KINDS }
            .sortedBy { PRIORITY.indexOf(it.kind) }
            .take(limit)
    }

    /** Los movimientos que cuentan como propios (mismo criterio que Estadísticas). */
    private fun counted(snapshot: MonkiSnapshot): List<com.yuzufin.app.domain.FinanceTransaction> =
        snapshot.transactions.filter { StatsCalculator.counts(snapshot.initial, it) }

    /** Promedio mensual de un tipo en los meses completos anteriores al actual. */
    private fun averageMonthly(snapshot: MonkiSnapshot, today: LocalDate, type: TransactionType): Long? {
        val month = YearMonth.from(today)
        val totals = HashMap<YearMonth, Long>()
        counted(snapshot)
            .filter { it.type == type && YearMonth.from(it.date) < month }
            .forEach { totals.merge(YearMonth.from(it.date), it.amountMinor, Long::plus) }
        val values = totals.values.filter { it > 0L }
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    /** Gasto mensual promedio por categoría en los meses completos anteriores al actual. */
    private fun averageByCategory(snapshot: MonkiSnapshot, today: LocalDate): Map<Long, Long> {
        val month = YearMonth.from(today)
        val byCategoryMonth = HashMap<Long, HashMap<YearMonth, Long>>()
        counted(snapshot)
            .filter { it.type == TransactionType.EXPENSE && YearMonth.from(it.date) < month }
            .forEach { t ->
                byCategoryMonth.getOrPut(t.categoryId) { HashMap() }
                    .merge(YearMonth.from(t.date), t.amountMinor, Long::plus)
            }
        return byCategoryMonth.mapValues { (_, byMonth) ->
            val values = byMonth.values.filter { it > 0L }
            if (values.isEmpty()) 0L else values.sum() / values.size
        }.filterValues { it > 0L }
    }

    /** Días seguidos con al menos un movimiento propio, contando desde hoy o desde ayer. */
    private fun streakDays(snapshot: MonkiSnapshot, today: LocalDate): Int {
        fun hasMovement(day: LocalDate) = counted(snapshot).any { it.date == day }
        var day = if (hasMovement(today)) today else today.minusDays(1)
        if (!hasMovement(day)) return 0
        var streak = 0
        while (hasMovement(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Los que llegan también en el modo «solo lo importante». */
    private val IMPORTANT_KINDS = setOf(
        ProactiveKind.MILESTONE, ProactiveKind.INCOME,
        ProactiveKind.LOW_FUNDS, ProactiveKind.OVERSPEND,
    )
    private val PRIORITY = listOf(
        ProactiveKind.MILESTONE, ProactiveKind.INCOME, ProactiveKind.LOW_FUNDS,
        ProactiveKind.OVERSPEND, ProactiveKind.SILENCE, ProactiveKind.STREAK,
    )
    const val MAX_NOTES_PER_DAY = 2
    private const val MIN_DAY_FOR_OVERSPEND = 7
    private const val MIN_STREAK = 7
    private const val MIN_SILENCE_DAYS = 7
}
