package com.yuzufin.app.reminders

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test fun antes_de_la_hora_es_hoy() {
        assertEquals(Duration.ofHours(2), ReminderScheduler.delayUntilNext(at(14, 7), 9))
    }

    @Test fun despues_de_la_hora_es_manana() {
        assertEquals(Duration.ofHours(22), ReminderScheduler.delayUntilNext(at(14, 11), 9))
    }

    @Test fun justo_a_la_hora_espera_un_dia_completo() {
        assertEquals(Duration.ofDays(1), ReminderScheduler.delayUntilNext(at(14, 9), 9))
    }

    @Test fun cruza_el_fin_de_mes() {
        val now = LocalDateTime.of(2026, 10, 31, 23, 30)
        assertEquals(Duration.ofHours(9).plusMinutes(30), ReminderScheduler.delayUntilNext(now, 9))
    }

    @Test fun hora_invalida_se_ajusta() {
        assertEquals(Duration.ofHours(1), ReminderScheduler.delayUntilNext(at(14, 22), 99))
    }
}
