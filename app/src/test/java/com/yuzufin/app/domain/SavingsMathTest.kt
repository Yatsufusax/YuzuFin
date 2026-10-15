package com.yuzufin.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsMathTest {

    private val today = LocalDate.of(2026, 10, 14)

    @Test fun disponible_es_saldo_menos_apartado() {
        assertEquals(700_000L, SavingsMath.spendable(1_000_000L, 300_000L))
    }

    @Test fun disponible_puede_ser_negativo_si_se_gasto_lo_apartado() {
        assertEquals(-50_000L, SavingsMath.spendable(250_000L, 300_000L))
    }

    @Test fun patrimonio_no_cuenta_el_ahorro_dos_veces() {
        val total = 1_000_000L
        val saved = 300_000L
        val spendable = SavingsMath.spendable(total, saved)
        assertEquals(total, BalanceCalculator.netWorth(spendable, saved, receivable = 0L, debts = 0L))
    }

    @Test fun falta_y_cumplida() {
        assertEquals(400L, SavingsMath.remaining(1_000L, 600L))
        assertEquals(0L, SavingsMath.remaining(1_000L, 1_500L))
        assertTrue(SavingsMath.isComplete(1_000L, 1_000L))
        assertTrue(SavingsMath.isComplete(1_000L, 1_200L))
        assertFalse(SavingsMath.isComplete(1_000L, 999L))
    }

    @Test fun porcentaje_se_redondea_hacia_abajo_y_tiene_tope() {
        assertEquals(0, SavingsMath.percent(1_000L, 0L))
        assertEquals(99, SavingsMath.percent(1_000L, 999L))
        assertEquals(50, SavingsMath.percent(1_000L, 500L))
        assertEquals(100, SavingsMath.percent(1_000L, 5_000L))
        assertEquals(0, SavingsMath.percent(0L, 100L))
    }

    @Test fun avance_entre_cero_y_uno() {
        assertEquals(0f, SavingsMath.fraction(1_000L, -5L), 0f)
        assertEquals(0.5f, SavingsMath.fraction(1_000L, 500L), 0.0001f)
        assertEquals(1f, SavingsMath.fraction(1_000L, 9_000L), 0f)
    }

    @Test fun meses_que_quedan_cuenta_el_mes_actual() {
        assertEquals(1, SavingsMath.monthsLeft(today, LocalDate.of(2026, 10, 31)))
        assertEquals(2, SavingsMath.monthsLeft(today, LocalDate.of(2026, 11, 1)))
        assertEquals(4, SavingsMath.monthsLeft(today, LocalDate.of(2027, 1, 20)))
        assertEquals(13, SavingsMath.monthsLeft(today, LocalDate.of(2027, 10, 1)))
    }

    @Test fun fecha_pasada_cuenta_como_un_mes_y_sin_fecha_es_null() {
        assertEquals(1, SavingsMath.monthsLeft(today, LocalDate.of(2026, 3, 1)))
        assertNull(SavingsMath.monthsLeft(today, null))
    }

    @Test fun aporte_mensual_se_redondea_hacia_arriba() {
        // Faltan 1.000.000 en 3 meses (oct, nov, dic): 333.334 por mes para no quedarse corto.
        assertEquals(333_334L, SavingsMath.monthlyNeeded(1_000_000L, 0L, today, LocalDate.of(2026, 12, 31)))
        assertEquals(500_000L, SavingsMath.monthlyNeeded(1_000_000L, 0L, today, LocalDate.of(2026, 11, 30)))
        assertEquals(200_000L, SavingsMath.monthlyNeeded(1_000_000L, 800_000L, today, LocalDate.of(2026, 10, 30)))
    }

    @Test fun aporte_mensual_es_null_sin_fecha_o_con_meta_cumplida() {
        assertNull(SavingsMath.monthlyNeeded(1_000L, 0L, today, null))
        assertNull(SavingsMath.monthlyNeeded(1_000L, 1_000L, today, LocalDate.of(2026, 12, 31)))
    }

    @Test fun vencida_solo_si_paso_la_fecha_y_falta_dinero() {
        assertTrue(SavingsMath.isOverdue(1_000L, 500L, today, LocalDate.of(2026, 10, 13)))
        assertFalse(SavingsMath.isOverdue(1_000L, 500L, today, LocalDate.of(2026, 10, 14)))
        assertFalse(SavingsMath.isOverdue(1_000L, 1_000L, today, LocalDate.of(2026, 1, 1)))
        assertFalse(SavingsMath.isOverdue(1_000L, 500L, today, null))
    }

    @Test fun aporte_automatico_es_el_porcentaje_del_cobro() {
        assertEquals(200_000L, SavingsMath.autoContribution(2_000_000L, 10, goalRemaining = 5_000_000L, incomeLeft = 2_000_000L))
    }

    @Test fun aporte_automatico_no_pasa_de_lo_que_falta_a_la_meta() {
        assertEquals(50_000L, SavingsMath.autoContribution(2_000_000L, 10, goalRemaining = 50_000L, incomeLeft = 2_000_000L))
        assertEquals(0L, SavingsMath.autoContribution(2_000_000L, 10, goalRemaining = 0L, incomeLeft = 2_000_000L))
    }

    @Test fun aporte_automatico_no_pasa_de_lo_que_queda_del_cobro() {
        assertEquals(30_000L, SavingsMath.autoContribution(2_000_000L, 50, goalRemaining = 9_000_000L, incomeLeft = 30_000L))
    }

    @Test fun aporte_automatico_sin_porcentaje_o_sin_cobro_es_cero() {
        assertEquals(0L, SavingsMath.autoContribution(2_000_000L, 0, 1_000L, 1_000L))
        assertEquals(0L, SavingsMath.autoContribution(0L, 10, 1_000L, 1_000L))
    }

    @Test fun aporte_automatico_trunca_los_decimales() {
        // 10 % de 1.005 = 100,5: se aparta 100, nunca más de lo calculado.
        assertEquals(100L, SavingsMath.autoContribution(1_005L, 10, 1_000L, 1_005L))
    }

    @Test fun clave_de_mes() {
        assertEquals(202610, SavingsMath.monthKey(LocalDate.of(2026, 10, 31)))
        assertEquals(202701, SavingsMath.monthKey(LocalDate.of(2027, 1, 1)))
    }
}
