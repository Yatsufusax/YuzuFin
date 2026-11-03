package com.yuzufin.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Resumen de los últimos 7 días: lo gastado y la categoría que más pesó. */
data class WeeklySummary(
    val totalExpenseMinor: Long,
    val expenseCount: Int,
    val topCategoryId: Long?,
    val topCategoryMinor: Long,
)

/** Reglas puras del aviso semanal (se prueban sin Android). */
object WeeklySummaryCalculator {

    val DAY: DayOfWeek = DayOfWeek.SUNDAY
    const val HOUR = 20

    /**
     * Los 7 días que terminan en [endDay] (inclusive). Solo gastos propios: los ingresos y los
     * movimientos internos de préstamos no cuentan.
     */
    fun compute(transactions: List<FinanceTransaction>, endDay: LocalDate): WeeklySummary {
        val start = endDay.minusDays(6)
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE && !it.internalTransfer && it.date >= start && it.date <= endDay
        }
        val byCategory = expenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        // Si dos categorías empatan, gana la de id más bajo (resultado estable).
        val top = byCategory.entries.sortedWith(compareByDescending<Map.Entry<Long, Long>> { it.value }.thenBy { it.key }).firstOrNull()
        return WeeklySummary(
            totalExpenseMinor = expenses.sumOf { it.amountMinor },
            expenseCount = expenses.size,
            topCategoryId = top?.key,
            topCategoryMinor = top?.value ?: 0L,
        )
    }

    /** Si Android demora el trabajo, el resumen de un domingo todavía se entrega hasta 2 días después (martes 20:00). */
    const val CATCH_UP_DAYS = 2L

    /**
     * El domingo cuyo resumen toca entregar ahora: el último domingo a las 20:00 que ya pasó, siempre
     * que no hayan pasado más de [CATCH_UP_DAYS] días. Devuelve null si no toca (o si está desactivado).
     * El resumen se calcula con los 7 días que terminan en ese domingo, aunque el aviso llegue tarde.
     */
    fun dueSunday(enabled: Boolean, now: LocalDateTime): LocalDate? {
        if (!enabled) return null
        var sunday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DAY))
        if (sunday.atTime(HOUR, 0).isAfter(now)) sunday = sunday.minusWeeks(1)
        val deadline = sunday.atTime(HOUR, 0).plusDays(CATCH_UP_DAYS)
        return sunday.takeIf { !now.isAfter(deadline) }
    }

    /** Clave para no repetir el aviso de una misma semana (la misma que usaba la versión anterior). */
    fun dedupeKey(sunday: LocalDate): String = "weekly:${sunday.toEpochDay()}"

    /** Solo se avisa el domingo, desde las 20:00 (si Android demora el trabajo, hasta medianoche). */
    fun shouldSend(enabled: Boolean, now: LocalDateTime): Boolean =
        enabled && now.dayOfWeek == DAY && now.hour >= HOUR

    /** Tiempo hasta el próximo domingo a las 20:00 (hoy mismo si todavía no llegó la hora). */
    fun delayUntilNext(now: LocalDateTime): Duration {
        var next = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DAY)).atTime(HOUR, 0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return Duration.between(now, next)
    }
}
