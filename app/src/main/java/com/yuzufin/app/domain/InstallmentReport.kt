package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Estado de una cuota en el comprobante. */
enum class InstallmentState {
    /** Ya estaba pagada cuando se anotó el gasto (no hay fecha ni movimiento). */
    PAID_BEFORE,

    /** Pagada a tiempo o tarde. */
    PAID,

    /** Pagada en un mes anterior al de su vencimiento. */
    ADVANCED,

    /** Ya tocó pagarla y sigue sin confirmar. */
    DUE,

    /** Todavía no llega su fecha. */
    UPCOMING,

    /** La persona la omitió. */
    SKIPPED,
}

data class InstallmentLine(
    val number: Int,
    val dueDate: LocalDate?,
    val paidDate: LocalDate?,
    val state: InstallmentState,
    val amountMinor: Long,
) {
    val isPaid: Boolean get() = state == InstallmentState.PAID || state == InstallmentState.ADVANCED || state == InstallmentState.PAID_BEFORE
    val isOpen: Boolean get() = state == InstallmentState.DUE || state == InstallmentState.UPCOMING
}

/** Un plan en cuotas listo para mostrar en el comprobante. */
data class InstallmentPlan(
    val name: String,
    val categoryName: String,
    val installmentMinor: Long,
    val dayOfMonth: Int,
    val total: Int,
    val lines: List<InstallmentLine>,
    val finished: Boolean,
) {
    val paidCount: Int get() = lines.count { it.isPaid }
    val paidMinor: Long get() = lines.filter { it.isPaid }.sumOf { it.amountMinor }
    val openCount: Int get() = lines.count { it.isOpen }
    val openMinor: Long get() = lines.filter { it.isOpen }.sumOf { it.amountMinor }
    val totalMinor: Long get() = paidMinor + openMinor
    val nextDue: LocalDate? get() = lines.filter { it.isOpen }.mapNotNull { it.dueDate }.minOrNull()

    /** Lo que vence dentro del mes de [today] y todavía no se pagó. */
    fun dueInMonth(today: LocalDate): Long {
        val month = YearMonth.from(today)
        return lines.filter { it.isOpen && it.dueDate != null && YearMonth.from(it.dueDate) == month }.sumOf { it.amountMinor }
    }

    /** Fecha del último pago, para decir cuándo se terminó. */
    val lastPaidDate: LocalDate? get() = lines.mapNotNull { it.paidDate }.maxOrNull()
}

/** Arma el [InstallmentPlan] de un gasto en cuotas. Función pura: no toca la base de datos ni el reloj. */
object InstallmentReportBuilder {

    /**
     * @param transactionDates fecha del gasto real de cada pago confirmado (id del movimiento → fecha).
     */
    fun build(
        expense: RecurringExpense,
        categoryName: String,
        occurrences: List<RecurringOccurrence>,
        transactionDates: Map<Long, LocalDate>,
    ): InstallmentPlan {
        val lines = ArrayList<InstallmentLine>()
        var number = 0

        repeat(expense.paidBefore) {
            number++
            lines += InstallmentLine(number, null, null, InstallmentState.PAID_BEFORE, expense.amountMinor)
        }

        val mine = occurrences.filter { it.expenseId == expense.id }.sortedBy { it.dueDate }
        for (occurrence in mine) {
            number++
            lines += when (occurrence.status) {
                OccurrenceStatus.CONFIRMED -> {
                    val paid = occurrence.transactionId?.let { transactionDates[it] } ?: occurrence.dueDate
                    // Adelantada = pagada en un mes anterior al de su vencimiento. Pagar un par de días antes es pagar a tiempo.
                    val advanced = YearMonth.from(paid).isBefore(YearMonth.from(occurrence.dueDate))
                    InstallmentLine(
                        number, occurrence.dueDate, paid,
                        if (advanced) InstallmentState.ADVANCED else InstallmentState.PAID,
                        occurrence.amountMinor,
                    )
                }
                OccurrenceStatus.PENDING ->
                    InstallmentLine(number, occurrence.dueDate, null, InstallmentState.DUE, occurrence.amountMinor)
                OccurrenceStatus.SKIPPED ->
                    InstallmentLine(number, occurrence.dueDate, null, InstallmentState.SKIPPED, 0L)
            }
        }

        // Cuotas que todavía no tienen pago creado: se proyectan en sus fechas.
        val existing = mine.map { it.dueDate }.toSet()
        val left = RecurringScheduler.remaining(expense, mine.size)
        val after = mine.maxOfOrNull { it.dueDate }?.plusDays(1) ?: expense.startDate
        for (date in RecurringScheduler.upcomingFree(expense, existing, mine.size, after, left)) {
            number++
            lines += InstallmentLine(number, date, null, InstallmentState.UPCOMING, expense.amountMinor)
        }

        val pending = mine.count { it.status == OccurrenceStatus.PENDING }
        return InstallmentPlan(
            name = expense.name,
            categoryName = categoryName,
            installmentMinor = expense.amountMinor,
            dayOfMonth = expense.dayOfMonth,
            total = expense.totalInstallments,
            lines = lines,
            finished = RecurringScheduler.isFinished(expense, mine.size, pending),
        )
    }
}
