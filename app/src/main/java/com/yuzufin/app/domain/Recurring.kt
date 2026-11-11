package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Gasto fijo mensual. Un día 29, 30 o 31 en un mes más corto cae en el último día de ese mes.
 * [startDate] es el primer día desde el que se generan pagos, para no inventar pagos pasados.
 */
data class RecurringExpense(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long,
    val dayOfMonth: Int,
    val startDate: LocalDate,
    val active: Boolean,
    val remind: Boolean,
    /** Cantidad total de cuotas si es un gasto en cuotas; 0 = sin fin (gasto fijo de siempre). */
    val totalInstallments: Int = 0,
    /** Cuotas que ya estaban pagadas al anotarlo. No generan pagos ni movimientos. */
    val paidBefore: Int = 0,
) {
    val isInstallments: Boolean get() = totalInstallments > 0
}

/** Un pago concreto de un gasto fijo. */
data class RecurringOccurrence(
    val id: Long,
    val expenseId: Long,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val status: OccurrenceStatus,
    val transactionId: Long?,
)

/** Reglas de fechas de los gastos fijos. Funciones puras, sin reloj ni base de datos. */
object RecurringScheduler {

    /** Fechas de pago entre [from] y [to] (ambas inclusive), ordenadas. */
    fun dueDates(dayOfMonth: Int, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val result = ArrayList<LocalDate>()
        var month = YearMonth.from(from)
        val last = YearMonth.from(to)
        while (!month.isAfter(last)) {
            val date = month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))
            if (!date.isBefore(from) && !date.isAfter(to)) result.add(date)
            month = month.plusMonths(1)
        }
        return result
    }

    /**
     * Cuotas que todavía no tienen pago creado: total - ya pagadas antes - pagos ya creados
     * ([created], incluye confirmados, pendientes y omitidos). Sin fin = [Int.MAX_VALUE].
     */
    fun remaining(expense: RecurringExpense, created: Int): Int =
        if (!expense.isInstallments) Int.MAX_VALUE
        else (expense.totalInstallments - expense.paidBefore - created).coerceAtLeast(0)

    /** Cuotas pagadas o ya generadas hasta ahora, para mostrar "cuota 3 de 6". */
    fun used(expense: RecurringExpense, created: Int): Int =
        (expense.paidBefore + created).coerceAtMost(if (expense.isInstallments) expense.totalInstallments else Int.MAX_VALUE)

    /** Un gasto en cuotas termina solo cuando no queda ninguna por crear ni pendiente de confirmar. */
    fun isFinished(expense: RecurringExpense, created: Int, pending: Int): Boolean =
        expense.isInstallments && remaining(expense, created) == 0 && pending == 0

    /**
     * Próximas [count] fechas de cuota que todavía no existen, a partir de [from] (inclusive).
     * Respeta las cuotas que faltan. Sirve para mostrar la próxima y para adelantar pagos.
     */
    fun upcomingFree(expense: RecurringExpense, existing: Set<LocalDate>, created: Int, from: LocalDate, count: Int): List<LocalDate> {
        val limit = minOf(count, remaining(expense, created))
        if (limit <= 0) return emptyList()
        val result = ArrayList<LocalDate>()
        var month = YearMonth.from(maxOf(from, expense.startDate))
        val start = maxOf(from, expense.startDate)
        var guard = 0
        while (result.size < limit && guard++ < 600) {
            val date = month.atDay(expense.dayOfMonth.coerceIn(1, month.lengthOfMonth()))
            if (!date.isBefore(start) && date !in existing) result.add(date)
            month = month.plusMonths(1)
        }
        return result
    }

    /** El próximo pago previsto después de [after]. */
    fun nextDueDate(dayOfMonth: Int, after: LocalDate): LocalDate? =
        dueDates(dayOfMonth, after.plusDays(1), after.plusMonths(2)).firstOrNull()

    /**
     * Pagos vencidos (hasta [today]) que todavía no existen. Antes de [floor] no se genera nada.
     * En un gasto en cuotas nunca pasa del total: cuando se completa, no se generan más.
     */
    fun missing(expense: RecurringExpense, existing: Set<LocalDate>, today: LocalDate, floor: LocalDate): List<LocalDate> {
        if (!expense.active) return emptyList()
        val from = if (expense.startDate.isAfter(floor)) expense.startDate else floor
        val due = dueDates(expense.dayOfMonth, from, today).filter { it !in existing }
        val left = remaining(expense, existing.size)
        return if (left >= due.size) due else due.take(left)
    }
}
