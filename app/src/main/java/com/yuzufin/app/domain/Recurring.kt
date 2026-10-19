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
)

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

    /** El próximo pago previsto después de [after]. */
    fun nextDueDate(dayOfMonth: Int, after: LocalDate): LocalDate? =
        dueDates(dayOfMonth, after.plusDays(1), after.plusMonths(2)).firstOrNull()

    /** Pagos vencidos (hasta [today]) que todavía no existen. Antes de [floor] no se genera nada. */
    fun missing(expense: RecurringExpense, existing: Set<LocalDate>, today: LocalDate, floor: LocalDate): List<LocalDate> {
        if (!expense.active) return emptyList()
        val from = if (expense.startDate.isAfter(floor)) expense.startDate else floor
        return dueDates(expense.dayOfMonth, from, today).filter { it !in existing }
    }
}
