package com.yuzufin.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomeSchedulerTest {

    private fun source(
        schedule: IncomeSchedule,
        days: List<Int> = emptyList(),
        custom: List<LocalDate> = emptyList(),
        start: LocalDate = LocalDate.of(2026, 1, 1),
        active: Boolean = true,
    ) = IncomeSource(
        id = 1, name = "Sueldo", amountMinor = 1_000_000L, categoryId = 10L,
        schedule = schedule, daysOfMonth = days, customDates = custom, startDate = start,
        requiresConfirmation = true, active = active, remind = true,
    )

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun mensual_cae_en_el_dia_elegido() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5))
        assertEquals(
            listOf(d(2026, 10, 5), d(2026, 11, 5), d(2026, 12, 5)),
            IncomeScheduler.dueDates(s, d(2026, 10, 1), d(2026, 12, 31)),
        )
    }

    @Test fun dia_31_usa_el_ultimo_dia_de_cada_mes() {
        val s = source(IncomeSchedule.MONTHLY, listOf(31))
        assertEquals(
            listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)),
            IncomeScheduler.dueDates(s, d(2026, 1, 1), d(2026, 4, 30)),
        )
    }

    @Test fun febrero_bisiesto_tiene_29() {
        val s = source(IncomeSchedule.MONTHLY, listOf(30))
        assertEquals(listOf(d(2028, 2, 29)), IncomeScheduler.dueDates(s, d(2028, 2, 1), d(2028, 2, 29)))
        assertEquals(listOf(d(2027, 2, 28)), IncomeScheduler.dueDates(s, d(2027, 2, 1), d(2027, 2, 28)))
    }

    @Test fun quincenal_15_y_fin_de_mes() {
        val s = source(IncomeSchedule.TWICE_MONTHLY, listOf(15, 31))
        assertEquals(
            listOf(d(2026, 2, 15), d(2026, 2, 28), d(2026, 3, 15), d(2026, 3, 31)),
            IncomeScheduler.dueDates(s, d(2026, 2, 1), d(2026, 3, 31)),
        )
    }

    @Test fun quincenal_30_y_31_en_febrero_no_se_repite() {
        val s = source(IncomeSchedule.TWICE_MONTHLY, listOf(30, 31))
        assertEquals(listOf(d(2026, 2, 28)), IncomeScheduler.dueDates(s, d(2026, 2, 1), d(2026, 2, 28)))
    }

    @Test fun los_limites_del_rango_son_inclusivos() {
        val s = source(IncomeSchedule.MONTHLY, listOf(10))
        assertEquals(listOf(d(2026, 5, 10)), IncomeScheduler.dueDates(s, d(2026, 5, 10), d(2026, 5, 10)))
        assertTrue(IncomeScheduler.dueDates(s, d(2026, 5, 11), d(2026, 6, 9)).isEmpty())
    }

    @Test fun rango_invertido_no_da_fechas() {
        val s = source(IncomeSchedule.MONTHLY, listOf(10))
        assertTrue(IncomeScheduler.dueDates(s, d(2026, 6, 1), d(2026, 5, 1)).isEmpty())
    }

    @Test fun fechas_a_eleccion_se_ordenan_y_filtran() {
        val s = source(IncomeSchedule.CUSTOM, custom = listOf(d(2026, 12, 20), d(2026, 10, 3), d(2026, 10, 3), d(2027, 1, 2)))
        assertEquals(
            listOf(d(2026, 10, 3), d(2026, 12, 20)),
            IncomeScheduler.dueDates(s, d(2026, 10, 1), d(2026, 12, 31)),
        )
    }

    @Test fun proximo_cobro_cruza_el_fin_de_anio() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5))
        assertEquals(d(2027, 1, 5), IncomeScheduler.nextDueDate(s, d(2026, 12, 20)))
        // El mismo día del cobro, el próximo es el del mes siguiente.
        assertEquals(d(2026, 11, 5), IncomeScheduler.nextDueDate(s, d(2026, 10, 5)))
    }

    @Test fun proximo_de_fechas_sueltas_y_sin_proximo() {
        val s = source(IncomeSchedule.CUSTOM, custom = listOf(d(2026, 10, 3), d(2026, 12, 20)))
        assertEquals(d(2026, 12, 20), IncomeScheduler.nextDueDate(s, d(2026, 10, 3)))
        assertNull(IncomeScheduler.nextDueDate(s, d(2026, 12, 20)))
    }

    @Test fun faltantes_solo_desde_la_fecha_de_inicio_de_la_fuente() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5), start = d(2026, 10, 6))
        val missing = IncomeScheduler.missingOccurrences(s, emptySet(), d(2026, 12, 31), d(2026, 1, 1))
        assertEquals(listOf(d(2026, 11, 5), d(2026, 12, 5)), missing)
    }

    @Test fun faltantes_no_van_antes_del_saldo_inicial() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5), start = d(2026, 1, 1))
        val missing = IncomeScheduler.missingOccurrences(s, emptySet(), d(2026, 11, 30), d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 5), d(2026, 11, 5)), missing)
    }

    @Test fun faltantes_no_repite_lo_ya_registrado() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5))
        val existing = setOf(d(2026, 10, 5))
        val first = IncomeScheduler.missingOccurrences(s, existing, d(2026, 12, 31), d(2026, 10, 1))
        assertEquals(listOf(d(2026, 11, 5), d(2026, 12, 5)), first)
        // Registrados los de la primera pasada, no queda nada: reconciliar dos veces no duplica.
        val second = IncomeScheduler.missingOccurrences(s, existing + first, d(2026, 12, 31), d(2026, 10, 1))
        assertTrue(second.isEmpty())
    }

    @Test fun el_dia_de_hoy_cuenta_pero_el_de_manana_no() {
        val s = source(IncomeSchedule.MONTHLY, listOf(14))
        assertEquals(listOf(d(2026, 10, 14)), IncomeScheduler.missingOccurrences(s, emptySet(), d(2026, 10, 14), d(2026, 10, 1)))
        assertTrue(IncomeScheduler.missingOccurrences(s, emptySet(), d(2026, 10, 13), d(2026, 10, 1)).isEmpty())
    }

    @Test fun fuente_pausada_no_genera_nada() {
        val s = source(IncomeSchedule.MONTHLY, listOf(5), active = false)
        assertTrue(IncomeScheduler.missingOccurrences(s, emptySet(), d(2026, 12, 31), d(2026, 1, 1)).isEmpty())
    }

    @Test fun dias_fuera_de_rango_se_ajustan() {
        val s = source(IncomeSchedule.MONTHLY, listOf(0))
        assertEquals(listOf(d(2026, 10, 1)), IncomeScheduler.dueDates(s, d(2026, 10, 1), d(2026, 10, 31)))
    }
}
