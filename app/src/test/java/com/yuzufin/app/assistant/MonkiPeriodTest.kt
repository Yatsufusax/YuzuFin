package com.yuzufin.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonkiPeriodTest {
    private val today = LocalDate.of(2026, 11, 2) // lunes

    private fun read(text: String) = MonkiPeriods.read(MonkiText.normalize(text), today)

    private fun single(text: String): MonkiPeriod = read(text).periods.single()

    @Test fun singleDays() {
        assertEquals(today, single("hoy").start)
        assertEquals(LocalDate.of(2026, 11, 1), single("ayer").start)
        assertEquals(LocalDate.of(2026, 10, 31), single("anteayer").end)
        assertEquals(LocalDate.of(2026, 10, 31), single("antes de ayer").end)
    }

    @Test fun weeksAreRollingSevenDayWindows() {
        val thisWeek = single("esta semana")
        assertEquals(LocalDate.of(2026, 10, 27), thisWeek.start)
        assertEquals(today, thisWeek.end)
        assertEquals(7L, thisWeek.days)
        val previous = single("la semana pasada")
        assertEquals(LocalDate.of(2026, 10, 20), previous.start)
        assertEquals(LocalDate.of(2026, 10, 26), previous.end)
    }

    @Test fun months() {
        val thisMonth = single("este mes")
        assertEquals(LocalDate.of(2026, 11, 1), thisMonth.start)
        assertEquals(today, thisMonth.end)
        val last = single("el mes pasado")
        assertEquals(LocalDate.of(2026, 10, 1), last.start)
        assertEquals(LocalDate.of(2026, 10, 31), last.end)
        assertEquals(thisMonth.start, single("en noviembre").start)
    }

    @Test fun namedMonthsLookBackNeverForward() {
        val october = single("en octubre")
        assertEquals(LocalDate.of(2026, 10, 1), october.start)
        assertEquals("en octubre", october.phrase)
        val december = single("en diciembre")
        assertEquals(LocalDate.of(2025, 12, 1), december.start)
        assertEquals(LocalDate.of(2025, 12, 31), december.end)
        assertEquals("en diciembre de 2025", december.phrase)
    }

    @Test fun lastNDaysAndYears() {
        val fifteen = single("los últimos 15 días")
        assertEquals(LocalDate.of(2026, 10, 19), fifteen.start)
        assertEquals(15L, fifteen.days)
        assertEquals(LocalDate.of(2026, 1, 1), single("este año").start)
        assertEquals(LocalDate.of(2025, 12, 31), single("el año pasado").end)
    }

    @Test fun twoPeriodsKeepTheirOrder() {
        val periods = read("este mes y el mes pasado").periods
        assertEquals(listOf(MonkiPeriod.Kind.THIS_MONTH, MonkiPeriod.Kind.LAST_MONTH), periods.map { it.kind })
    }

    @Test fun lastMonthIsAmbiguousAndNotGuessed() {
        val reading = read("el último mes")
        assertTrue(reading.ambiguous)
        assertTrue(reading.periods.isEmpty())
    }

    @Test fun noPeriodMeansNone() {
        val reading = read("cuánto gasté en comida")
        assertTrue(reading.periods.isEmpty())
        assertFalse(reading.ambiguous)
    }

    @Test fun containsIsInclusiveOnBothEnds() {
        val week = single("esta semana")
        assertTrue(week.contains(week.start))
        assertTrue(week.contains(week.end))
        assertFalse(week.contains(week.start.minusDays(1)))
        assertFalse(week.contains(week.end.plusDays(1)))
    }
}
