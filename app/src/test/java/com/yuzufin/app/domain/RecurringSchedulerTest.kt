package com.yuzufin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringSchedulerTest {

    private fun expense(day: Int, start: LocalDate, active: Boolean = true) = RecurringExpense(
        id = 1, name = "Internet", amountMinor = 100_000, categoryId = 1,
        dayOfMonth = day, startDate = start, active = active, remind = true,
    )

    @Test
    fun monthlyDatesInRange() {
        val dates = RecurringScheduler.dueDates(5, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 4, 10))
        assertEquals(listOf(LocalDate.of(2026, 2, 5), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 4, 5)), dates)
    }

    @Test
    fun day31FallsOnLastDayOfShortMonths() {
        val dates = RecurringScheduler.dueDates(31, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)
    }

    @Test
    fun emptyWhenRangeIsReversed() {
        assertTrue(RecurringScheduler.dueDates(5, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1)).isEmpty())
    }

    @Test
    fun nextDueDateIsAfterGivenDay() {
        assertEquals(LocalDate.of(2026, 3, 5), RecurringScheduler.nextDueDate(5, LocalDate.of(2026, 2, 5)))
        assertEquals(LocalDate.of(2026, 2, 5), RecurringScheduler.nextDueDate(5, LocalDate.of(2026, 2, 4)))
    }

    @Test
    fun missingSkipsExistingAndRespectsStartAndFloor() {
        val e = expense(5, start = LocalDate.of(2026, 2, 1))
        val missing = RecurringScheduler.missing(
            e, existing = setOf(LocalDate.of(2026, 3, 5)), today = LocalDate.of(2026, 4, 20),
            floor = LocalDate.of(2026, 1, 1),
        )
        assertEquals(listOf(LocalDate.of(2026, 2, 5), LocalDate.of(2026, 4, 5)), missing)
        // La fecha inicial de las cuentas es posterior: no se generan pagos anteriores.
        val later = RecurringScheduler.missing(e, emptySet(), LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 1))
        assertEquals(listOf(LocalDate.of(2026, 4, 5)), later)
    }

    @Test
    fun pausedExpenseGeneratesNothing() {
        val e = expense(5, start = LocalDate.of(2026, 1, 1), active = false)
        assertTrue(RecurringScheduler.missing(e, emptySet(), LocalDate.of(2026, 4, 20), LocalDate.of(2026, 1, 1)).isEmpty())
    }
}
