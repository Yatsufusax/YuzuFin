package com.yuzufin.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class SummaryCardMathTest {

    @Test
    fun spentAboutHalfOfTheIncome() {
        val p = summaryProgress(income = 6_000_000L, expense = 2_858_000L)
        assertEquals(48, p.percent)
        assertEquals(0.476f, p.fraction, 0.001f)
        assertFalse(p.over)
    }

    @Test
    fun spendingExactlyTheIncomeIsNotOver() {
        val p = summaryProgress(income = 1_000L, expense = 1_000L)
        assertEquals(100, p.percent)
        assertEquals(1f, p.fraction, 0f)
        assertFalse(p.over)
    }

    @Test
    fun spendingMoreThanTheIncomeIsOverAndTheBarStaysFull() {
        val p = summaryProgress(income = 1_000L, expense = 1_500L)
        assertEquals(150, p.percent)
        assertEquals(1f, p.fraction, 0f)
        assertTrue(p.over)
    }

    @Test
    fun percentIsCappedSoItNeverBreaksTheLayout() {
        assertEquals(999, summaryProgress(income = 1L, expense = 1_000_000L).percent)
    }

    @Test
    fun withoutIncomeThereIsNoPercent() {
        val spent = summaryProgress(income = 0L, expense = 500L)
        assertNull(spent.percent)
        assertTrue(spent.over)
        assertEquals(1f, spent.fraction, 0f)

        val nothing = summaryProgress(income = 0L, expense = 0L)
        assertNull(nothing.percent)
        assertFalse(nothing.over)
        assertEquals(0f, nothing.fraction, 0f)
    }

    @Test
    fun monthOptionsGoBackFromTodayUntilTheFirstMonth() {
        val today = YearMonth.of(2026, 11)
        val options = summaryMonthOptions(today, first = YearMonth.of(2026, 8))
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)),
            options,
        )
    }

    @Test
    fun monthOptionsAreLimitedToTwelveMonths() {
        val options = summaryMonthOptions(YearMonth.of(2026, 11), first = YearMonth.of(2000, 1))
        assertEquals(SUMMARY_MONTHS_BACK, options.size)
        assertEquals(YearMonth.of(2025, 12), options.last())
    }

    @Test
    fun monthOptionsAlwaysIncludeTheCurrentMonth() {
        val today = YearMonth.of(2026, 11)
        assertEquals(listOf(today), summaryMonthOptions(today, first = YearMonth.of(2027, 1)))
    }

    @Test
    fun monthLabelAddsTheYearOnlyForOtherYears() {
        val today = YearMonth.of(2026, 11)
        assertEquals("Octubre", summaryMonthLabel(YearMonth.of(2026, 10), today))
        assertEquals("Octubre 2025", summaryMonthLabel(YearMonth.of(2025, 10), today))
    }
}
