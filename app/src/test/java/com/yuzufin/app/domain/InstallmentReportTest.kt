package com.yuzufin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InstallmentReportTest {

    private fun expense(total: Int, paidBefore: Int = 0) = RecurringExpense(
        id = 1, name = "Central Shop", amountMinor = 450_000, categoryId = 1,
        dayOfMonth = 10, startDate = LocalDate.of(2026, 9, 1), active = true, remind = true,
        totalInstallments = total, paidBefore = paidBefore,
    )

    private fun occ(id: Long, due: LocalDate, status: OccurrenceStatus, tx: Long? = null) =
        RecurringOccurrence(id, 1, due, 450_000, status, tx)

    @Test
    fun marksAdvancedAndProjectsTheRest() {
        val occurrences = listOf(
            occ(1, LocalDate.of(2026, 9, 10), OccurrenceStatus.CONFIRMED, 11),
            occ(2, LocalDate.of(2026, 10, 10), OccurrenceStatus.CONFIRMED, 12),
            occ(3, LocalDate.of(2026, 11, 10), OccurrenceStatus.CONFIRMED, 13),
            occ(4, LocalDate.of(2026, 12, 10), OccurrenceStatus.CONFIRMED, 14),
        )
        val dates = mapOf(
            11L to LocalDate.of(2026, 9, 10), 12L to LocalDate.of(2026, 10, 9),
            13L to LocalDate.of(2026, 10, 28), 14L to LocalDate.of(2026, 10, 28),
        )
        val plan = InstallmentReportBuilder.build(expense(6), "Compras", occurrences, dates)

        assertEquals(6, plan.lines.size)
        assertEquals(
            listOf(
                InstallmentState.PAID, InstallmentState.PAID, InstallmentState.ADVANCED,
                InstallmentState.ADVANCED, InstallmentState.UPCOMING, InstallmentState.UPCOMING,
            ),
            plan.lines.map { it.state },
        )
        assertEquals(4, plan.paidCount)
        assertEquals(1_800_000L, plan.paidMinor)
        assertEquals(900_000L, plan.openMinor)
        assertEquals(2_700_000L, plan.totalMinor)
        assertEquals(LocalDate.of(2027, 1, 10), plan.nextDue)
        assertFalse(plan.finished)
    }

    @Test
    fun paidBeforeComesFirstAndCounts() {
        val plan = InstallmentReportBuilder.build(expense(6, paidBefore = 2), "Compras", emptyList(), emptyMap())
        assertEquals(6, plan.lines.size)
        assertEquals(InstallmentState.PAID_BEFORE, plan.lines[0].state)
        assertEquals(InstallmentState.PAID_BEFORE, plan.lines[1].state)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), plan.lines.map { it.number })
        assertEquals(2, plan.paidCount)
    }

    @Test
    fun finishedWhenEverythingIsPaid() {
        val occurrences = listOf(
            occ(1, LocalDate.of(2026, 9, 10), OccurrenceStatus.CONFIRMED, 11),
            occ(2, LocalDate.of(2026, 10, 10), OccurrenceStatus.CONFIRMED, 12),
        )
        val plan = InstallmentReportBuilder.build(expense(2), "Compras", occurrences, emptyMap())
        assertTrue(plan.finished)
        assertEquals(0, plan.openCount)
        assertEquals(LocalDate.of(2026, 10, 10), plan.lastPaidDate)
    }

    @Test
    fun dueInMonthSumsOnlyOpenInstallmentsOfThatMonth() {
        val plan = InstallmentReportBuilder.build(expense(3), "Compras", emptyList(), emptyMap())
        assertEquals(450_000L, plan.dueInMonth(LocalDate.of(2026, 10, 15)))
        assertEquals(0L, plan.dueInMonth(LocalDate.of(2026, 8, 15)))
    }
}
