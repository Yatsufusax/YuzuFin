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

    // ----- Entrega con margen (corrección del resumen que nunca llegaba) -----------------------

    @Test fun toca_el_resumen_del_domingo_desde_las_20_hasta_el_martes_a_las_20() {
        fun due(now: LocalDateTime) = WeeklySummaryCalculator.dueSunday(true, now)
        assertEquals(sunday, due(sunday.atTime(20, 0)))
        assertEquals(sunday, due(sunday.atTime(23, 59)))
        // Android lo demoró hasta el lunes o el martes: igual se entrega el resumen de ese domingo.
        assertEquals(sunday, due(sunday.plusDays(1).atTime(0, 30)))
        assertEquals(sunday, due(sunday.plusDays(2).atTime(20, 0)))
        // Pasado el margen, o antes de las 20:00 del domingo, no toca.
        assertNull(due(sunday.plusDays(2).atTime(20, 1)))
        assertNull(due(sunday.plusDays(4).atTime(12, 0)))
        assertNull(due(sunday.atTime(19, 59)))
        // El domingo siguiente toca el nuevo resumen.
        assertEquals(sunday.plusWeeks(1), due(sunday.plusWeeks(1).atTime(20, 5)))
    }

    @Test fun desactivado_nunca_toca() {
        assertNull(WeeklySummaryCalculator.dueSunday(false, sunday.atTime(21, 0)))
    }

    @Test fun el_resumen_tardio_cuenta_la_semana_que_termino_el_domingo() {
        val monday = sunday.plusDays(1)
        val s = WeeklySummaryCalculator.compute(
            listOf(tx(7_000, date = sunday), tx(9_000, date = monday)),
            WeeklySummaryCalculator.dueSunday(true, monday.atTime(9, 0))!!,
        )
        // El gasto del lunes pertenece a la semana siguiente.
        assertEquals(7_000, s.totalExpenseMinor)
    }

    @Test fun misma_clave_toda_la_semana_y_otra_la_siguiente() {
        val keys = listOf(
            sunday.atTime(20, 5), sunday.atTime(23, 0), sunday.plusDays(1).atTime(20, 0), sunday.plusDays(2).atTime(19, 0),
        ).map { WeeklySummaryCalculator.dedupeKey(WeeklySummaryCalculator.dueSunday(true, it)!!) }
        assertEquals(1, keys.toSet().size)
        assertEquals("weekly:${sunday.toEpochDay()}", keys.first())
        assertFalse(keys.first() == WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(1)))
    }

    /**
     * Simula el revisor diario con retrasos de Android (de 0 a 26 horas, repetibles) durante 9 semanas:
     * cada domingo se entrega exactamente un resumen, sin duplicados y sin semanas perdidas.
     */
    @Test fun el_revisor_diario_entrega_una_vez_por_semana_aunque_android_lo_demore() {
        val sent = ArrayList<String>()
        val delays = listOf(0L, 3, 26, 1, 8, 19, 0, 12, 25, 2, 6, 14, 0, 22, 5)
        var run = sunday.minusWeeks(1).plusDays(3).atTime(20, 0) // primer revisor: miércoles a las 20:00 (ya fuera del margen del domingo previo)
        val end = sunday.plusWeeks(8).plusDays(3).atTime(0, 0)
        var step = 0
        while (run.isBefore(end)) {
            val now = run.plusHours(delays[step % delays.size])
            WeeklySummaryCalculator.dueSunday(true, now)
                ?.let { WeeklySummaryCalculator.dedupeKey(it) }
                ?.takeIf { it !in sent }   // lo que hace NotificationRepository.log
                ?.let { sent.add(it) }
            run = run.plusDays(1) // el siguiente revisor, 24 horas después
            step++
        }
        val expected = (0..8).map { WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(it.toLong())) }
        assertEquals(expected, sent)
    }
}
