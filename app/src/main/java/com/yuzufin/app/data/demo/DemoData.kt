package com.yuzufin.app.data.demo

import com.yuzufin.app.core.money.CurrencyInfo

/**
 * ============================  DATOS DE DEMOSTRACIÓN  ============================
 * Nada de lo que hay aquí es real ni se guarda en el dispositivo. Existe solo para
 * que la Fase 1 muestre cómo se verá la pantalla de Inicio. La pantalla lo avisa con
 * un banner visible. Se elimina cuando la Fase 2 conecte la base de datos (Room).
 *
 * Los cálculos derivados siguen las reglas del proyecto:
 *  - Dinero libre estimado = saldo disponible - obligaciones pendientes.
 *  - Patrimonio = saldo disponible + ahorros + por cobrar - deudas.
 *  - Una transferencia a ahorros NO es un gasto.
 * ================================================================================
 */
object DemoData {

    val currency: CurrencyInfo = CurrencyInfo.PYG

    enum class Category { Food, Salary, Savings, Transport, Internet, Rent, Electricity }

    enum class MovementKind { Income, Expense, InternalTransfer }

    data class PlannedIncome(val name: String, val amount: Long, val whenLabel: String)

    data class Bill(val name: String, val amount: Long, val dueLabel: String, val category: Category)

    data class Movement(
        val title: String,
        val detail: String,
        val amount: Long,
        val kind: MovementKind,
        val category: Category,
    )

    val availableBalance = 4_250_000L
    val savedTotal = 1_500_000L

    /** Dinero prestado a otras personas que aún no volvió. */
    val receivable = 200_000L

    /** Dinero recibido en préstamo que todavía se debe. */
    val debts = 0L

    val nextIncome = PlannedIncome(
        name = "Salario quincenal",
        amount = 2_700_000L,
        whenLabel = "En 3 días",
    )

    val upcomingBills = listOf(
        Bill("Internet", 150_000L, "En 2 días", Category.Internet),
        Bill("Alquiler", 800_000L, "En 5 días", Category.Rent),
        Bill("Electricidad", 230_000L, "En 8 días", Category.Electricity),
    )

    val committed: Long = upcomingBills.sumOf { it.amount }
    val freeEstimate: Long = availableBalance - committed
    val netWorth: Long = availableBalance + savedTotal + receivable - debts

    val incomeThisMonth = 5_400_000L
    val expenseThisMonth = 2_150_000L

    val recentMovements = listOf(
        Movement("Supermercado", "Alimentación · Hoy", 185_000L, MovementKind.Expense, Category.Food),
        Movement("Salario quincenal", "Ingreso · Ayer", 2_700_000L, MovementKind.Income, Category.Salary),
        Movement("Aporte a ahorros", "Hace 2 días", 300_000L, MovementKind.InternalTransfer, Category.Savings),
        Movement("Transporte", "Hace 3 días", 45_000L, MovementKind.Expense, Category.Transport),
    )
}
