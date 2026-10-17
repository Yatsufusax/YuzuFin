package com.yuzufin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsCalculatorTest {
    private val initial = InitialBalance(0L, LocalDate.of(2026, 1, 1))
    private val oct = YearMonth.of(2026, 10)

    private fun tx(type: TransactionType, amount: Long, category: Long, date: LocalDate, internal: Boolean = false) =
        FinanceTransaction(0L, type, amount, category, "", date, internal)

    @Test
    fun internalTransfersAndOldMovementsAreExcluded() {
        val list = listOf(
            tx(TransactionType.INCOME, 1_000_000, 1, LocalDate.of(2026, 10, 1)),
            tx(TransactionType.INCOME, 500_000, 9, LocalDate.of(2026, 10, 2), internal = true), // préstamo recibido
            tx(TransactionType.EXPENSE, 200_000, 2, LocalDate.of(2026, 10, 3)),
            tx(TransactionType.EXPENSE, 300_000, 9, LocalDate.of(2026, 10, 4), internal = true), // devolución
            tx(TransactionType.EXPENSE, 100_000, 2, LocalDate.of(2025, 12, 31)), // antes del inicio
        )
        val p = StatsCalculator.monthPoint(initial, list, oct)
        assertEquals(1_000_000L, p.income)
        assertEquals(200_000L, p.expense)
        assertEquals(800_000L, p.net)
    }

    @Test
    fun seriesIsOrderedOldestFirst() {
        val s = StatsCalculator.series(initial, emptyList(), oct, 6)
        assertEquals(6, s.size)
        assertEquals(YearMonth.of(2026, 5), s.first().month)
        assertEquals(oct, s.last().month)
    }

    @Test
    fun budgetLevels() {
        assertEquals(BudgetLevel.OK, BudgetProgress(1, 100, 79).level)
        assertEquals(BudgetLevel.WARNING, BudgetProgress(1, 100, 80).level)
        assertEquals(BudgetLevel.OVER, BudgetProgress(1, 100, 100).level)
        assertEquals(120, BudgetProgress(1, 100, 120).percent)
        assertEquals(1f, BudgetProgress(1, 100, 120).fraction, 0.0001f)
    }

    @Test
    fun projectionOnlyForCurrentMonthAfterSevenDays() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(3_100_000L, StatsCalculator.projectedExpense(1_000_000, today, oct))
        assertNull(StatsCalculator.projectedExpense(1_000_000, LocalDate.of(2026, 10, 6), oct))
        assertNull(StatsCalculator.projectedExpense(1_000_000, today, YearMonth.of(2026, 9)))
    }

    @Test
    fun reductionAndRate() {
        assertEquals(50_000L, StatsCalculator.reduction(500_000, 10))
        assertEquals(15L, StatsCalculator.reduction(155, 10))
        assertEquals(20, StatsCalculator.savingsRatePercent(1000, 800))
        assertNull(StatsCalculator.savingsRatePercent(0, 800))
    }

    @Test
    fun riseAndScenarioRecommendations() {
        val list = listOf(
            tx(TransactionType.INCOME, 5_000_000, 1, LocalDate.of(2026, 10, 1)),
            tx(TransactionType.EXPENSE, 600_000, 2, LocalDate.of(2026, 10, 5)),
            tx(TransactionType.EXPENSE, 400_000, 2, LocalDate.of(2026, 9, 5)),
            tx(TransactionType.EXPENSE, 200_000, 3, LocalDate.of(2026, 9, 6)),
            tx(TransactionType.EXPENSE, 200_000, 3, LocalDate.of(2026, 10, 6)),
        )
        val recs = StatsCalculator.recommendations(initial, list, emptyList(), oct, LocalDate.of(2026, 10, 20))
        val rise = recs.filterIsInstance<Recommendation.CategoryRise>().single()
        assertEquals(2L, rise.categoryId)
        assertEquals(50, rise.percent)
        val scenario = recs.filterIsInstance<Recommendation.Scenario>().single()
        assertEquals(2L, scenario.categoryId)
        assertEquals(400_000L, scenario.averageMinor) // promedio de meses anteriores con gasto
        assertEquals(40_000L, scenario.savingMinor)
        assertEquals(1, scenario.monthsUsed)
    }

    @Test
    fun budgetOverAndDeficit() {
        val list = listOf(
            tx(TransactionType.INCOME, 100_000, 1, LocalDate.of(2026, 10, 1)),
            tx(TransactionType.EXPENSE, 300_000, 2, LocalDate.of(2026, 10, 2)),
        )
        val recs = StatsCalculator.recommendations(initial, list, listOf(Budget(1, 2, 250_000)), oct, LocalDate.of(2026, 10, 20))
        assertTrue(recs.first() is Recommendation.BudgetOver)
        assertTrue(recs.any { it is Recommendation.Deficit })
    }

    @Test
    fun futureMonthHasNoRecommendations() {
        assertTrue(StatsCalculator.recommendations(initial, emptyList(), emptyList(), YearMonth.of(2027, 1), LocalDate.of(2026, 10, 20)).isEmpty())
    }
}
