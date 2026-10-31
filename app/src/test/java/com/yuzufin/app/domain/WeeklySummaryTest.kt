package com.yuzufin.app.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySummaryTest {

    // 2026-11-01 es domingo.
    private val sunday = LocalDate.of(2026, 11, 1)

    private fun tx(
        amount: Long,
        categoryId: Long = 1,
        date: LocalDate = sunday,
        type: TransactionType = TransactionType.EXPENSE,
        internal: Boolean = false,
    ) = FinanceTransaction(
        id = 0,
        type = type,
        amountMinor = amount,
        categoryId = categoryId,
        description = "",
        date = date,
        internalTransfer = internal,
    )

    @Test fun suma_los_gastos_de_los_ultimos_7_dias_y_elige_la_categoria_mayor() {
        val s = WeeklySummaryCalculator.compute(
            listOf(
                tx(10_000, categoryId = 1),
                tx(30_000, categoryId = 2, date = sunday.minusDays(3)),
                tx(5_000, categoryId = 2, date = sunday.minusDays(6)),
            ),
            sunday,
        )
        assertEquals(45_000, s.totalExpenseMinor)
        assertEquals(3, s.expenseCount)
        assertEquals(2L, s.topCategoryId)
        assertEquals(35_000, s.topCategoryMinor)
    }

    @Test fun la_semana_empieza_6_dias_antes_y_termina_hoy() {
        val s = WeeklySummaryCalculator.compute(
            listOf(
                tx(1_000, date = sunday.minusDays(7)),
                tx(2_000, date = sunday.minusDays(6)),
                tx(4_000, date = sunday),
                tx(8_000, date = sunday.plusDays(1)),
            ),
            sunday,
        )
        assertEquals(6_000, s.totalExpenseMinor)
    }

    @Test fun ignora_ingresos_y_movimientos_internos() {
        val s = WeeklySummaryCalculator.compute(
            listOf(
                tx(50_000, type = TransactionType.INCOME),
                tx(20_000, internal = true),
                tx(3_000),
            ),
            sunday,
        )
        assertEquals(3_000, s.totalExpenseMinor)
        assertEquals(1, s.expenseCount)
    }

    @Test fun sin_gastos_no_hay_categoria() {
        val s = WeeklySummaryCalculator.compute(emptyList(), sunday)
        assertEquals(0, s.totalExpenseMinor)
        assertEquals(0, s.expenseCount)
        assertNull(s.topCategoryId)
    }

    @Test fun si_empatan_gana_la_categoria_de_id_mas_bajo() {
        val s = WeeklySummaryCalculator.compute(listOf(tx(5_000, categoryId = 7), tx(5_000, categoryId = 3)), sunday)
        assertEquals(3L, s.topCategoryId)
    }

    @Test fun solo_avisa_el_domingo_desde_las_20() {
        assertTrue(WeeklySummaryCalculator.shouldSend(true, sunday.atTime(20, 5)))
        assertTrue(WeeklySummaryCalculator.shouldSend(true, sunday.atTime(23, 30)))
        assertFalse(WeeklySummaryCalculator.shouldSend(true, sunday.atTime(19, 59)))
        assertFalse(WeeklySummaryCalculator.shouldSend(true, sunday.plusDays(1).atTime(21, 0)))
        assertFalse(WeeklySummaryCalculator.shouldSend(false, sunday.atTime(21, 0)))
    }

    @Test fun el_proximo_aviso_es_el_domingo_a_las_20() {
        // Sábado 10:00 -> domingo 20:00 = 34 horas.
        val saturday = LocalDateTime.of(2026, 10, 31, 10, 0)
        assertEquals(Duration.ofHours(34), WeeklySummaryCalculator.delayUntilNext(saturday))
        // Domingo 19:00 -> hoy a las 20:00.
        assertEquals(Duration.ofHours(1), WeeklySummaryCalculator.delayUntilNext(sunday.atTime(19, 0)))
        // Domingo 20:00 en punto (ya pasó) -> el domingo siguiente.
        assertEquals(Duration.ofDays(7), WeeklySummaryCalculator.delayUntilNext(sunday.atTime(20, 0)))
        // Lunes 08:00 -> domingo 20:00 = 6 días y 12 horas.
        assertEquals(Duration.ofHours(6 * 24 + 12), WeeklySummaryCalculator.delayUntilNext(sunday.plusDays(1).atTime(8, 0)))
    }
}
