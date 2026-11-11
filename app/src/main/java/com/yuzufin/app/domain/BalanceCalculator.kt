package com.yuzufin.app.domain

import java.time.YearMonth

/**
 * Ingresos y gastos reales de un mes. [opening] es el saldo con el que la persona empezó el mes
 * (lo que le quedó del anterior, p. ej. un sueldo cobrado el 30).
 */
data class MonthTotals(val income: Long, val expense: Long, val opening: Long = 0L) {
    val net: Long get() = income - expense

    /**
     * Con lo que se contaba para gastar en el mes: lo que se traía del mes anterior (si era
     * positivo) más lo que ingresó. Un mes que arranca en 0 de ingresos pero con saldo no está en rojo.
     */
    val available: Long get() = opening.coerceAtLeast(0L) + income
}

/**
 * Reglas contables de la app, en funciones puras y con enteros.
 *
 *  - Saldo total = saldo inicial + ingresos - gastos (solo desde la fecha inicial). Incluye lo apartado.
 *  - Disponible = saldo total - ahorros apartados (ver [SavingsMath.spendable]).
 *  - Dinero libre estimado = disponible - obligaciones pendientes.
 *  - Patrimonio = disponible + ahorros + por cobrar - deudas (el ahorro no se cuenta dos veces).
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
        val monthStart = month.atDay(1)
        // Saldo al empezar el mes: el inicial más todo lo anterior (los préstamos también mueven el saldo).
        // Si el mes es anterior al saldo inicial no hay nada que traer.
        val beforeInitial = month.atEndOfMonth().isBefore(initial.startDate)
        var opening = if (beforeInitial) 0L else initial.amountMinor
        for (transaction in transactions) {
            if (!beforeInitial && counts(initial, transaction) && transaction.date.isBefore(monthStart)) {
                opening += signedAmount(transaction)
            }
            // Los movimientos internos (préstamos) mueven el saldo pero no son ingresos ni gastos del mes.
            if (transaction.internalTransfer || !counts(initial, transaction) || YearMonth.from(transaction.date) != month) continue
            when (transaction.type) {
                TransactionType.INCOME -> income += transaction.amountMinor
                TransactionType.EXPENSE -> expense += transaction.amountMinor
            }
        }
        return MonthTotals(income = income, expense = expense, opening = opening)
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
