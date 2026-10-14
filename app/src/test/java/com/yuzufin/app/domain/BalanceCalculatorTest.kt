package com.yuzufin.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val start = LocalDate.of(2026, 10, 1)
    private val initial = InitialBalance(amountMinor = 1_000_000L, startDate = start)

    private fun tx(type: TransactionType, amount: Long, date: LocalDate, id: Long = 0) =
        FinanceTransaction(
            id = id,
            type = type,
            amountMinor = amount,
            categoryId = 1L,
            description = "",
            date = date,
        )

    @Test fun sin_movimientos_el_saldo_es_el_inicial() {
        assertEquals(1_000_000L, BalanceCalculator.availableBalance(initial, emptyList()))
    }

    @Test fun suma_ingresos_y_resta_gastos() {
        val list = listOf(
            tx(TransactionType.INCOME, 500_000L, start.plusDays(1)),
            tx(TransactionType.EXPENSE, 200_000L, start.plusDays(2)),
            tx(TransactionType.EXPENSE, 50_000L, start.plusDays(3)),
        )
        assertEquals(1_250_000L, BalanceCalculator.availableBalance(initial, list))
    }

    @Test fun ignora_movimientos_anteriores_a_la_fecha_inicial() {
        val list = listOf(
            tx(TransactionType.EXPENSE, 999_999L, start.minusDays(1)),
            tx(TransactionType.INCOME, 100L, start.minusDays(30)),
        )
        assertEquals(1_000_000L, BalanceCalculator.availableBalance(initial, list))
    }

    @Test fun el_mismo_dia_inicial_si_cuenta() {
        val list = listOf(tx(TransactionType.EXPENSE, 100_000L, start))
        assertEquals(900_000L, BalanceCalculator.availableBalance(initial, list))
    }

    @Test fun el_saldo_puede_ser_negativo() {
        val list = listOf(tx(TransactionType.EXPENSE, 1_500_000L, start.plusDays(1)))
        assertEquals(-500_000L, BalanceCalculator.availableBalance(initial, list))
    }

    @Test fun editar_o_eliminar_corrige_el_saldo() {
        val original = tx(TransactionType.EXPENSE, 300_000L, start.plusDays(1), id = 1)
        assertEquals(700_000L, BalanceCalculator.availableBalance(initial, listOf(original)))
        val edited = original.copy(amountMinor = 100_000L)
        assertEquals(900_000L, BalanceCalculator.availableBalance(initial, listOf(edited)))
        assertEquals(1_000_000L, BalanceCalculator.availableBalance(initial, emptyList()))
    }

    @Test fun totales_del_mes() {
        val oct = YearMonth.of(2026, 10)
        val list = listOf(
            tx(TransactionType.INCOME, 800_000L, LocalDate.of(2026, 10, 5)),
            tx(TransactionType.EXPENSE, 150_000L, LocalDate.of(2026, 10, 6)),
            tx(TransactionType.EXPENSE, 50_000L, LocalDate.of(2026, 11, 2)),
            tx(TransactionType.EXPENSE, 70_000L, LocalDate.of(2026, 9, 30)),
        )
        val totals = BalanceCalculator.monthTotals(initial, list, oct)
        assertEquals(800_000L, totals.income)
        assertEquals(150_000L, totals.expense)
        assertEquals(650_000L, totals.net)
    }

    @Test fun libre_y_patrimonio() {
        assertEquals(700_000L, BalanceCalculator.freeEstimate(1_000_000L, 300_000L))
        assertEquals(1_350_000L, BalanceCalculator.netWorth(1_000_000L, 500_000L, 100_000L, 250_000L))
    }
}
