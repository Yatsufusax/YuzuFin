package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Clave de icono de la categoría "Préstamos", que usan los movimientos internos de los préstamos. */
const val LOAN_ICON_KEY = "loan"

/**
 * Factura o vencimiento (Fase 5). Mientras esté pendiente NO descuenta el saldo: solo baja el
 * "libre estimado". Al pagarla se crea el gasto real.
 */
data class Bill(
    val id: Long,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long,
    val dueDate: LocalDate,
    /** Día del mes en que se repite (1 a 31, 31 = último día); 0 si no se repite. */
    val repeatDay: Int,
    val remind: Boolean,
    val paid: Boolean,
    val paidDate: LocalDate?,
)

enum class BillStatus { PAID, OVERDUE, DUE_TODAY, UPCOMING }

enum class LoanDirection {
    /** Yo presté: me deben. */
    LENT,

    /** Me prestaron: debo. */
    BORROWED,
}

data class Loan(
    val id: Long,
    val person: String,
    val direction: LoanDirection,
    val principalMinor: Long,
    val startDate: LocalDate,
    val note: String,
    val remind: Boolean,
)

/** Devolución (parcial o total) de un préstamo. */
data class LoanPayment(
    val id: Long,
    val loanId: Long,
    val amountMinor: Long,
    val date: LocalDate,
    val note: String,
)

enum class LoanResult { OK, INVALID, TOO_MUCH, NOT_FOUND }

/** Cuentas de facturas y préstamos: funciones puras con enteros. */
object ObligationMath {

    /** Total de facturas pendientes: baja el libre estimado, no el saldo. */
    fun committed(bills: List<Bill>): Long = bills.filter { !it.paid }.sumOf { it.amountMinor }

    /** Libre estimado = disponible - facturas pendientes. Puede ser negativo. */
    fun freeEstimate(spendable: Long, committed: Long): Long = spendable - committed

    fun status(bill: Bill, today: LocalDate): BillStatus = when {
        bill.paid -> BillStatus.PAID
        bill.dueDate.isBefore(today) -> BillStatus.OVERDUE
        bill.dueDate == today -> BillStatus.DUE_TODAY
        else -> BillStatus.UPCOMING
    }

    /**
     * Próximo vencimiento de una factura mensual: el mes siguiente, en el día [repeatDay]
     * (31 = último día; en meses cortos se usa el último día del mes).
     */
    fun nextDueDate(due: LocalDate, repeatDay: Int): LocalDate {
        val month = YearMonth.from(due).plusMonths(1)
        return month.atDay(repeatDay.coerceIn(1, month.lengthOfMonth()))
    }

    /** Aviso de factura: pendiente, con recordatorio, sin avisar todavía y vence mañana, hoy o ya venció. */
    fun billNeedsReminder(bill: Bill, notified: Boolean, today: LocalDate): Boolean =
        !bill.paid && bill.remind && !notified && !bill.dueDate.isAfter(today.plusDays(1))

    /** Lo que falta devolver de un préstamo. Nunca negativo. */
    fun outstanding(principalMinor: Long, payments: List<LoanPayment>): Long =
        (principalMinor - payments.sumOf { it.amountMinor }).coerceAtLeast(0L)

    /** Una devolución válida es mayor que cero y no pasa de lo que falta. */
    fun canRepay(amountMinor: Long, outstandingMinor: Long): Boolean =
        amountMinor in 1..outstandingMinor

    /** Total que te deben (préstamos que hiciste) o que debés, según [direction]. */
    fun totalOutstanding(
        direction: LoanDirection,
        loans: List<Loan>,
        payments: List<LoanPayment>,
    ): Long {
        val byLoan = payments.groupBy { it.loanId }
        return loans.filter { it.direction == direction }
            .sumOf { outstanding(it.principalMinor, byLoan[it.id].orEmpty()) }
    }

    /** Los últimos días del mes (el último y el penúltimo): momento del recordatorio de saldos. */
    fun isMonthEndWindow(today: LocalDate): Boolean = today.dayOfMonth >= today.lengthOfMonth() - 1
}
