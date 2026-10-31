package com.yuzufin.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitReminderTest {

    private val today = LocalDate.of(2026, 10, 31)

    private fun tx(date: LocalDate, internal: Boolean = false) = FinanceTransaction(
        id = 1,
        type = TransactionType.EXPENSE,
        amountMinor = 5000,
        categoryId = 1,
        description = "",
        date = date,
        internalTransfer = internal,
    )

    @Test fun sin_movimientos_no_hay_nada_anotado() {
        assertFalse(HabitReminder.hasEntryOn(emptyList(), today))
    }

    @Test fun un_movimiento_de_hoy_cuenta() {
        assertTrue(HabitReminder.hasEntryOn(listOf(tx(today)), today))
    }

    @Test fun un_movimiento_de_ayer_no_cuenta() {
        assertFalse(HabitReminder.hasEntryOn(listOf(tx(today.minusDays(1))), today))
    }

    @Test fun un_movimiento_interno_de_prestamo_no_cuenta() {
        assertFalse(HabitReminder.hasEntryOn(listOf(tx(today, internal = true)), today))
    }

    @Test fun avisa_solo_si_esta_activo_la_hora_elegida_y_no_hay_nada_anotado() {
        val hours = setOf(13, 21)
        assertTrue(HabitReminder.shouldRemind(true, 21, hours, hasEntryToday = false))
        assertFalse(HabitReminder.shouldRemind(true, 21, hours, hasEntryToday = true))
        assertFalse(HabitReminder.shouldRemind(false, 21, hours, hasEntryToday = false))
        assertFalse(HabitReminder.shouldRemind(true, 18, hours, hasEntryToday = false))
    }

    @Test fun acepta_hasta_dos_horas_de_demora() {
        assertTrue(HabitReminder.isOnTime(21, 21))
        assertTrue(HabitReminder.isOnTime(23, 21))
        assertFalse(HabitReminder.isOnTime(0, 21))
        assertFalse(HabitReminder.isOnTime(20, 21))
    }

    @Test fun la_demora_cruza_la_medianoche() {
        assertTrue(HabitReminder.isOnTime(0, 23))
        assertTrue(HabitReminder.isOnTime(1, 23))
        assertFalse(HabitReminder.isOnTime(3, 23))
    }

    @Test fun momento_del_dia_segun_la_hora() {
        assertEquals(HabitSlot.MORNING, HabitReminder.slotFor(10))
        assertEquals(HabitSlot.MIDDAY, HabitReminder.slotFor(13))
        assertEquals(HabitSlot.AFTERNOON, HabitReminder.slotFor(15))
        assertEquals(HabitSlot.AFTERNOON, HabitReminder.slotFor(18))
        assertEquals(HabitSlot.EVENING, HabitReminder.slotFor(20))
        assertEquals(HabitSlot.EVENING, HabitReminder.slotFor(21))
        assertEquals(HabitSlot.NIGHT, HabitReminder.slotFor(22))
        assertEquals(HabitSlot.NIGHT, HabitReminder.slotFor(2))
    }

    @Test fun lee_y_escribe_las_horas() {
        assertEquals(setOf(13, 18, 21), HabitReminder.parseHours("21, 13,18"))
        assertEquals(emptySet<Int>(), HabitReminder.parseHours(""))
        assertEquals(emptySet<Int>(), HabitReminder.parseHours(null))
        assertEquals(setOf(9), HabitReminder.parseHours("9,25,x,-1"))
        assertEquals("13,18,21", HabitReminder.formatHours(setOf(21, 13, 18)))
    }
}
