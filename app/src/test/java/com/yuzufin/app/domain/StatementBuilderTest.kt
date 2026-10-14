package com.yuzufin.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementBuilderTest {

    private val start = LocalDate.of(2026, 9, 1)
    private val initial = InitialBalance(amountMinor = 2_000_000L, startDate = start)
    private val names = mapOf(1L to "Alimentación", 2L to "Ingreso")

    private fun tx(id: Long, type: TransactionType, amount: Long, date: LocalDate, description: String = "", category: Long = 1L) =
        FinanceTransaction(id, type, amount, category, description, date)

    private val all = listOf(
        tx(1, TransactionType.INCOME, 1_750_000L, LocalDate.of(2026, 9, 5), "Sueldo", 2),
        tx(2, TransactionType.EXPENSE, 350_000L, LocalDate.of(2026, 9, 8), "Supermercado"),
        tx(3, TransactionType.EXPENSE, 80_000L, LocalDate.of(2026, 9, 10)),
        tx(4, TransactionType.INCOME, 500_000L, LocalDate.of(2026, 10, 3), "Extra", 2),
        tx(5, TransactionType.EXPENSE, 100_000L, LocalDate.of(2026, 8, 20), "Antes del inicio"),
    )

    @Test fun periodo_completo_incluye_saldo_inicial_y_cierra_bien() {
        val s = StatementBuilder.build(initial, all, names, start, LocalDate.of(2026, 9, 30))
        assertEquals(2_000_000L, s.openingBalance)
        assertEquals(1_750_000L, s.incomeTotal)
        assertEquals(430_000L, s.expenseTotal)
        assertEquals(3_320_000L, s.closingBalance)
        assertEquals(StatementKind.INITIAL, s.rows.first().kind)
        assertEquals(4, s.rows.size)
    }

    @Test fun saldo_corrido_fila_por_fila() {
        val s = StatementBuilder.build(initial, all, names, start, LocalDate.of(2026, 9, 30))
        assertEquals(listOf(2_000_000L, 3_750_000L, 3_400_000L, 3_320_000L), s.rows.map { it.balanceAfter })
    }

    @Test fun el_cierre_coincide_con_el_saldo_disponible_de_todo_el_historial() {
        val s = StatementBuilder.build(initial, all, names, LocalDate.of(2000, 1, 1), LocalDate.of(2030, 1, 1))
        assertEquals(BalanceCalculator.availableBalance(initial, all), s.closingBalance)
        assertEquals(start, s.from)
    }

    @Test fun periodo_posterior_arranca_con_el_saldo_de_ese_dia() {
        val s = StatementBuilder.build(initial, all, names, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(3_320_000L, s.openingBalance)
        assertEquals(500_000L, s.incomeTotal)
        assertEquals(0L, s.expenseTotal)
        assertEquals(3_820_000L, s.closingBalance)
        assertEquals(1, s.rows.size)
        assertTrue(s.rows.none { it.kind == StatementKind.INITIAL })
    }

    @Test fun siempre_cierre_es_apertura_mas_ingresos_menos_gastos() {
        val periods = listOf(
            LocalDate.of(2026, 9, 6) to LocalDate.of(2026, 9, 9),
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 5),
            LocalDate.of(2026, 9, 11) to LocalDate.of(2026, 12, 1),
        )
        for ((from, to) in periods) {
            val s = StatementBuilder.build(initial, all, names, from, to)
            assertEquals(s.openingBalance + s.incomeTotal - s.expenseTotal, s.closingBalance)
        }
    }

    @Test fun ignora_movimientos_anteriores_al_saldo_inicial() {
        val s = StatementBuilder.build(initial, all, names, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30))
        assertTrue(s.rows.none { it.description == "Antes del inicio" })
        assertEquals(start, s.from)
    }

    @Test fun descripcion_vacia_usa_la_categoria() {
        val s = StatementBuilder.build(initial, all, names, start, LocalDate.of(2026, 9, 30))
        assertEquals("Alimentación", s.rows.first { it.signedAmountMinor == -80_000L }.description)
    }

    @Test fun periodo_anterior_al_inicio_queda_vacio() {
        val s = StatementBuilder.build(initial, all, names, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31))
        assertTrue(s.rows.isEmpty())
        assertEquals(s.openingBalance, s.closingBalance)
    }

    @Test fun mismo_dia_se_ordena_por_id() {
        val d = LocalDate.of(2026, 9, 12)
        val list = listOf(
            tx(9, TransactionType.EXPENSE, 10L, d, "B"),
            tx(8, TransactionType.EXPENSE, 20L, d, "A"),
        )
        val s = StatementBuilder.build(initial, list, names, start, d)
        assertEquals(listOf("A", "B"), s.rows.filter { it.kind != StatementKind.INITIAL }.map { it.description })
    }
}
