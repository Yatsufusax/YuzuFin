package com.yuzufin.app.domain

import java.time.YearMonth

data class MonthTotals(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

/**
 * Reglas contables de la app, en funciones puras y con enteros.
 *
 *  - Saldo disponible = saldo inicial + ingresos - gastos (solo desde la fecha inicial).
 *  - Dinero libre estimado = saldo disponible - obligaciones pendientes.
 *  - Patrimonio = saldo disponible + ahorros + por cobrar - deudas.
 *
 * Como el saldo se recalcula siempre desde los movimientos guardados, editar o eliminar
 * un movimiento lo corrige de forma consistente.
 */
object BalanceCalculator {

    fun availableBalance(initial: InitialBalance, transactions: List<FinanceTransaction>): Long {
        var balance = initial.amountMinor
        for (transaction in transactions) {
            if (counts(initial, transaction)) balance += signedAmount(transaction)
        }
        return balance
    }

    fun monthTotals(
        initial: InitialBalance,
        transactions: List<FinanceTransaction>,
        month: YearMonth,
    ): MonthTotals {
        var income = 0L
        var expense = 0L
        for (transaction in transactions) {
            if (!counts(initial, transaction) || YearMonth.from(transaction.date) != month) continue
            when (transaction.type) {
                TransactionType.INCOME -> income += transaction.amountMinor
                TransactionType.EXPENSE -> expense += transaction.amountMinor
            }
        }
        return MonthTotals(income = income, expense = expense)
    }

    fun freeEstimate(balance: Long, pendingObligations: Long): Long = balance - pendingObligations

    fun netWorth(balance: Long, saved: Long, receivable: Long, debts: Long): Long =
        balance + saved + receivable - debts

    private fun counts(initial: InitialBalance, transaction: FinanceTransaction): Boolean =
        !transaction.date.isBefore(initial.startDate)

    private fun signedAmount(transaction: FinanceTransaction): Long = when (transaction.type) {
        TransactionType.INCOME -> transaction.amountMinor
        TransactionType.EXPENSE -> -transaction.amountMinor
    }
}
