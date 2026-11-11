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

    private fun plan(total: Int, paidBefore: Int = 0) =
        expense(10, start = LocalDate.of(2026, 11, 1)).copy(totalInstallments = total, paidBefore = paidBefore)

    @Test
    fun installmentsStopAfterTheTotal() {
        val e = plan(total = 3)
        val missing = RecurringScheduler.missing(e, emptySet(), LocalDate.of(2027, 6, 30), LocalDate.of(2026, 1, 1))
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10)),
            missing,
        )
    }

    @Test
    fun paidBeforeReducesWhatIsGenerated() {
        val e = plan(total = 6, paidBefore = 4)
        val missing = RecurringScheduler.missing(e, emptySet(), LocalDate.of(2027, 6, 30), LocalDate.of(2026, 1, 1))
        assertEquals(2, missing.size)
        assertEquals(2, RecurringScheduler.remaining(e, 0))
        assertEquals(4, RecurringScheduler.used(e, 0))
    }

    @Test
    fun plainExpenseNeverEnds() {
        val e = expense(5, start = LocalDate.of(2026, 1, 1))
        assertEquals(Int.MAX_VALUE, RecurringScheduler.remaining(e, 500))
        assertTrue(!RecurringScheduler.isFinished(e, 500, 0))
    }

    @Test
    fun upcomingFreeSkipsAdvancedInstallments() {
        val e = plan(total = 6)
        // Ya se adelantaron noviembre y diciembre.
        val existing = setOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10))
        val next = RecurringScheduler.upcomingFree(e, existing, existing.size, LocalDate.of(2026, 11, 11), 1)
        assertEquals(listOf(LocalDate.of(2027, 1, 10)), next)
    }

    @Test
    fun upcomingFreeNeverPassesTheTotal() {
        val e = plan(total = 3)
        val existing = setOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10))
        val next = RecurringScheduler.upcomingFree(e, existing, existing.size, LocalDate.of(2026, 11, 11), 5)
        assertEquals(listOf(LocalDate.of(2027, 1, 10)), next)
    }

    @Test
    fun finishedOnlyWhenNothingIsLeftOrPending() {
        val e = plan(total = 2)
        assertTrue(!RecurringScheduler.isFinished(e, 2, pending = 1))
        assertTrue(RecurringScheduler.isFinished(e, 2, pending = 0))
        assertTrue(!RecurringScheduler.isFinished(e, 1, pending = 0))
    }
}
