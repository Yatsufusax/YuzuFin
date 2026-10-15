package com.yuzufin.app.domain

import java.time.LocalDate

enum class StatementKind { INITIAL, INCOME, EXPENSE }

/**
 * Una línea del extracto. [signedAmountMinor] es positivo si entra dinero y negativo si sale;
 * [balanceAfter] es el saldo disponible justo después de esa línea.
 */
data class StatementRow(
    val date: LocalDate,
    val description: String,
    val categoryName: String,
    val kind: StatementKind,
    val signedAmountMinor: Long,
    val balanceAfter: Long,
    /** Movimiento interno de un préstamo: mueve el saldo pero no es ingreso ni gasto. */
    val internal: Boolean = false,
)

data class Statement(
    val from: LocalDate,
    val to: LocalDate,
    val openingBalance: Long,
    val incomeTotal: Long,
    val expenseTotal: Long,
    val closingBalance: Long,
    val rows: List<StatementRow>,
    /** Efecto neto de los préstamos en el período (entra +, sale −). No está en ingresos ni gastos. */
    val internalNet: Long = 0L,
)

/**
 * Arma el extracto de un período con las mismas reglas del saldo: solo cuentan los movimientos
 * desde la fecha inicial. Es una función pura: no toca la base de datos ni el reloj.
 *
 * Siempre se cumple: saldo final = saldo inicial del período + ingresos − gastos + préstamos (neto).
 * Los movimientos internos de préstamos mueven el saldo pero no cuentan como ingreso ni gasto.
 */
object StatementBuilder {

    fun build(
        initial: InitialBalance,
        transactions: List<FinanceTransaction>,
        categoryNames: Map<Long, String>,
        from: LocalDate,
        to: LocalDate,
    ): Statement {
        val counted = transactions
            .filter { !it.date.isBefore(initial.startDate) }
            .sortedWith(compareBy({ it.date }, { it.id }))

        // Período que termina antes de empezar a llevar las cuentas: no hay nada que mostrar.
        if (to.isBefore(initial.startDate) || to.isBefore(from)) {
            return Statement(from, to, initial.amountMinor, 0L, 0L, initial.amountMinor, emptyList())
        }

        val includesInitial = !from.isAfter(initial.startDate)
        val periodStart = if (includesInitial) initial.startDate else from

        val opening = if (includesInitial) {
            initial.amountMinor
        } else {
            initial.amountMinor + counted.filter { it.date.isBefore(from) }.sumOf(::signed)
        }

        val rows = ArrayList<StatementRow>()
        if (includesInitial) {
            rows += StatementRow(
                date = initial.startDate,
                description = "",
                categoryName = "",
                kind = StatementKind.INITIAL,
                signedAmountMinor = initial.amountMinor,
                balanceAfter = initial.amountMinor,
            )
        }

        var running = opening
        var income = 0L
        var expense = 0L
        var internalNet = 0L
        for (transaction in counted) {
            if (transaction.date.isBefore(periodStart) || transaction.date.isAfter(to)) continue
            running += signed(transaction)
            val categoryName = categoryNames[transaction.categoryId].orEmpty()
            if (transaction.internalTransfer) {
                internalNet += signed(transaction)
            } else {
                when (transaction.type) {
                    TransactionType.INCOME -> income += transaction.amountMinor
                    TransactionType.EXPENSE -> expense += transaction.amountMinor
                }
            }
            rows += StatementRow(
                date = transaction.date,
                description = transaction.description.ifBlank { categoryName },
                categoryName = categoryName,
                kind = if (transaction.type == TransactionType.INCOME) StatementKind.INCOME else StatementKind.EXPENSE,
                signedAmountMinor = signed(transaction),
                balanceAfter = running,
                internal = transaction.internalTransfer,
            )
        }

        return Statement(
            from = periodStart,
            to = to,
            openingBalance = opening,
            incomeTotal = income,
            expenseTotal = expense,
            closingBalance = running,
            rows = rows,
            internalNet = internalNet,
        )
    }

    private fun signed(transaction: FinanceTransaction): Long = when (transaction.type) {
        TransactionType.INCOME -> transaction.amountMinor
        TransactionType.EXPENSE -> -transaction.amountMinor
    }
}
