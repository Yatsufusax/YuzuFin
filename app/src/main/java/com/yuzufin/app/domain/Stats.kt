package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** Ingresos y gastos reales de un mes (sin movimientos internos de préstamos). */
data class MonthPoint(val month: YearMonth, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

enum class BudgetLevel { OK, WARNING, OVER }

data class BudgetProgress(val categoryId: Long, val limitMinor: Long, val spentMinor: Long) {
    val percent: Int get() = StatsCalculator.percentOf(spentMinor, limitMinor)
    val fraction: Float get() = if (limitMinor <= 0L) 0f else (spentMinor.toDouble() / limitMinor).toFloat().coerceIn(0f, 1f)
    val remainingMinor: Long get() = limitMinor - spentMinor
    val level: BudgetLevel
        get() = when {
            limitMinor > 0L && spentMinor >= limitMinor -> BudgetLevel.OVER
            percent >= StatsCalculator.WARN_PERCENT -> BudgetLevel.WARNING
            else -> BudgetLevel.OK
        }
}

/** Gasto de una categoría este mes frente al mes anterior. */
data class CategoryChange(val categoryId: Long, val currentMinor: Long, val previousMinor: Long) {
    val deltaMinor: Long get() = currentMinor - previousMinor
    /** null si el mes anterior no hubo gasto (no se puede calcular un porcentaje). */
    val deltaPercent: Int? get() = if (previousMinor > 0L) (deltaMinor * 100 / previousMinor).toInt() else null
}

/** Cada recomendación sale de una regla fija; la pantalla explica la cuenta con estos mismos datos. */
sealed interface Recommendation {
    data class BudgetOver(val categoryId: Long, val spentMinor: Long, val limitMinor: Long) : Recommendation
    data class BudgetNear(val categoryId: Long, val percent: Int, val remainingMinor: Long) : Recommendation
    data class Deficit(val incomeMinor: Long, val expenseMinor: Long) : Recommendation
    data class ProjectionAboveIncome(val projectedMinor: Long, val incomeMinor: Long) : Recommendation
    data class CategoryRise(
        val categoryId: Long,
        val currentMinor: Long,
        val previousMinor: Long,
        val percent: Int,
    ) : Recommendation
    data class LowSavingsRate(val percent: Int) : Recommendation
    /** "Reducir X% este gasto podría ahorrarte Y por mes". [monthsUsed] = 0 si se usó solo el mes actual. */
    data class Scenario(
        val categoryId: Long,
        val averageMinor: Long,
        val savingMinor: Long,
        val percent: Int,
        val monthsUsed: Int,
    ) : Recommendation
}

/**
 * Cálculos de estadísticas, en funciones puras con enteros.
 *
 * Qué cuenta como ingreso o gasto: solo movimientos confirmados, desde la fecha inicial, que no sean
 * internos. Quedan fuera los préstamos (dados, recibidos y devueltos) porque solo mueven plata de un
 * lado a otro. Los aportes a ahorro no son movimientos, así que tampoco entran como gasto.
 */
object StatsCalculator {
    const val WARN_PERCENT = 80
    const val SCENARIO_PERCENT = 10
    const val RISE_MIN_PERCENT = 20
    const val MIN_DAYS_FOR_PROJECTION = 7
    const val LOW_SAVINGS_PERCENT = 10
    private const val MAX_RECOMMENDATIONS = 7

    fun counts(initial: InitialBalance?, transaction: FinanceTransaction): Boolean =
        !transaction.internalTransfer && (initial == null || !transaction.date.isBefore(initial.startDate))

    fun monthPoint(initial: InitialBalance?, transactions: List<FinanceTransaction>, month: YearMonth): MonthPoint {
        var income = 0L
        var expense = 0L
        for (t in transactions) {
            if (!counts(initial, t) || YearMonth.from(t.date) != month) continue
            if (t.type == TransactionType.INCOME) income += t.amountMinor else expense += t.amountMinor
        }
        return MonthPoint(month, income, expense)
    }

    /** Últimos [months] meses hasta [endMonth] inclusive, del más antiguo al más nuevo. */
    fun series(
        initial: InitialBalance?,
        transactions: List<FinanceTransaction>,
        endMonth: YearMonth,
        months: Int = 6,
    ): List<MonthPoint> = (months - 1 downTo 0).map { back -> monthPoint(initial, transactions, endMonth.minusMonths(back.toLong())) }

    fun expenseByCategory(
        initial: InitialBalance?,
        transactions: List<FinanceTransaction>,
        month: YearMonth,
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        for (t in transactions) {
            if (t.type != TransactionType.EXPENSE || !counts(initial, t) || YearMonth.from(t.date) != month) continue
            result[t.categoryId] = (result[t.categoryId] ?: 0L) + t.amountMinor
        }
        return result
    }

    fun budgetProgress(budgets: List<Budget>, spentByCategory: Map<Long, Long>): List<BudgetProgress> =
        budgets.filter { it.limitMinor > 0L }
            .map { BudgetProgress(it.categoryId, it.limitMinor, spentByCategory[it.categoryId] ?: 0L) }
            .sortedByDescending { it.spentMinor.toDouble() / it.limitMinor }

    fun changes(current: Map<Long, Long>, previous: Map<Long, Long>): List<CategoryChange> =
        (current.keys + previous.keys)
            .map { CategoryChange(it, current[it] ?: 0L, previous[it] ?: 0L) }
            .sortedByDescending { it.currentMinor }

    /** Estimación lineal: gasto hasta hoy ÷ días transcurridos × días del mes. Solo para el mes en curso. */
    fun projectedExpense(expenseSoFar: Long, today: LocalDate, month: YearMonth): Long? {
        if (month != YearMonth.from(today)) return null
        val day = today.dayOfMonth
        if (day < MIN_DAYS_FOR_PROJECTION) return null
        return expenseSoFar * month.lengthOfMonth() / day
    }

    fun percentOf(part: Long, whole: Long): Int =
        if (whole <= 0L) 0 else (part * 100 / whole).coerceAtMost(9_999L).toInt()

    /** (ingresos − gastos) ÷ ingresos, en %. null si no hubo ingresos. Puede ser negativo. */
    fun savingsRatePercent(income: Long, expense: Long): Int? =
        if (income <= 0L) null else ((income - expense) * 100 / income).toInt()

    fun reduction(amount: Long, percent: Int): Long = amount / 100 * percent + (amount % 100) * percent / 100

    /**
     * Gasto mensual promedio de una categoría en hasta [window] meses anteriores a [month], contando solo
     * los meses en que hubo algún gasto. Devuelve (promedio, meses usados) o null si no hay historia.
     */
    fun averageMonthlySpend(
        initial: InitialBalance?,
        transactions: List<FinanceTransaction>,
        categoryId: Long,
        month: YearMonth,
        window: Int = 3,
    ): Pair<Long, Int>? {
        var total = 0L
        var used = 0
        for (back in 1..window) {
            val m = month.minusMonths(back.toLong())
            if (monthPoint(initial, transactions, m).expense <= 0L) continue
            total += expenseByCategory(initial, transactions, m)[categoryId] ?: 0L
            used++
        }
        return if (used == 0) null else total / used to used
    }

    fun recommendations(
        initial: InitialBalance?,
        transactions: List<FinanceTransaction>,
        budgets: List<Budget>,
        month: YearMonth,
        today: LocalDate,
    ): List<Recommendation> {
        if (month.isAfter(YearMonth.from(today))) return emptyList()
        val point = monthPoint(initial, transactions, month)
        val spent = expenseByCategory(initial, transactions, month)
        val previous = expenseByCategory(initial, transactions, month.minusMonths(1))
        val result = mutableListOf<Recommendation>()

        for (p in budgetProgress(budgets, spent)) {
            when (p.level) {
                BudgetLevel.OVER -> result += Recommendation.BudgetOver(p.categoryId, p.spentMinor, p.limitMinor)
                BudgetLevel.WARNING -> result += Recommendation.BudgetNear(p.categoryId, p.percent, p.remainingMinor)
                BudgetLevel.OK -> Unit
            }
        }
        if (point.expense > point.income && point.expense > 0L) {
            result += Recommendation.Deficit(point.income, point.expense)
        } else {
            projectedExpense(point.expense, today, month)?.let { projected ->
                if (point.income > 0L && projected > point.income) {
                    result += Recommendation.ProjectionAboveIncome(projected, point.income)
                }
            }
            savingsRatePercent(point.income, point.expense)?.let { rate ->
                if (rate in 0 until LOW_SAVINGS_PERCENT && point.expense > 0L) result += Recommendation.LowSavingsRate(rate)
            }
        }
        val minDelta = (point.expense / 20).coerceAtLeast(1L)
        changes(spent, previous)
            .filter { it.currentMinor > 0L && (it.deltaPercent ?: 0) >= RISE_MIN_PERCENT && it.deltaMinor >= minDelta }
            .sortedByDescending { it.deltaMinor }
            .take(2)
            .forEach { result += Recommendation.CategoryRise(it.categoryId, it.currentMinor, it.previousMinor, it.deltaPercent ?: 0) }

        spent.maxByOrNull { it.value }?.let { (categoryId, amount) ->
            val history = averageMonthlySpend(initial, transactions, categoryId, month)
            val base = history?.first ?: amount
            val saving = reduction(base, SCENARIO_PERCENT)
            if (saving > 0L) result += Recommendation.Scenario(categoryId, base, saving, SCENARIO_PERCENT, history?.second ?: 0)
        }
        return result.take(MAX_RECOMMENDATIONS)
    }
}
