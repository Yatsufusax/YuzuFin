package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonkiFinanceEngineTest {
    private val engine = MonkiFinanceEngine()
    private val today = LocalDate.of(2026, 11, 1)

    @Test fun weeklyExpensesExcludeInternalTransfersAndFutureMovements() {
        val items = listOf(
            tx(1, TransactionType.EXPENSE, 120_000, today.minusDays(1)),
            tx(2, TransactionType.EXPENSE, 80_000, today.minusDays(2), internal = true),
            tx(3, TransactionType.EXPENSE, 50_000, today.plusDays(1)),
            tx(4, TransactionType.EXPENSE, 30_000, today.minusDays(9)),
        )
        val answer = engine.answer("¿Cuánto gasté esta semana?", items, emptyList(), CurrencyInfo.PYG, today)
        assertTrue(answer.contains("Gs. 120.000"))
    }

    @Test fun argentinaUsesRegionalVoice() {
        val answer = engine.answer("hola", emptyList(), emptyList(), CurrencyInfo.ARS, today)
        assertTrue(answer.contains("querés"))
    }

    @Test fun nonRegionalCurrencyUsesNeutralVoice() {
        val answer = engine.answer("hola", emptyList(), emptyList(), CurrencyInfo.USD, today)
        assertTrue(answer.contains("¿Qué te gustaría"))
    }

    @Test fun questionWithoutPeriodDefaultsToLastSevenDays() {
        val items = listOf(tx(1, TransactionType.EXPENSE, 45_000, today.minusDays(3)))
        val answer = engine.answer("cuanto gaste", items, emptyList(), CurrencyInfo.PYG, today)
        assertTrue(answer.contains("Gs. 45.000"))
    }

    @Test fun incomeQuestionUsesOnlyIncome() {
        val items = listOf(
            tx(1, TransactionType.INCOME, 2_000_000, today.minusDays(2)),
            tx(2, TransactionType.EXPENSE, 100_000, today.minusDays(2)),
        )
        val answer = engine.answer("¿Cuánto ingresó esta semana?", items, emptyList(), CurrencyInfo.PYG, today)
        assertTrue(answer.contains("Gs. 2.000.000"))
    }

    @Test fun categoryQuestionIsNotMistakenForTotalExpenses() {
        val cats = listOf(com.yuzufin.app.domain.Category(7, "Comida", TransactionType.EXPENSE, "food"))
        val items = listOf(FinanceTransaction(1, TransactionType.EXPENSE, 60_000, 7, "", today.minusDays(1)))
        val answer = engine.answer("¿En qué gasté más?", items, cats, CurrencyInfo.PYG, today)
        assertTrue(answer.contains("Comida"))
    }

    @Test fun regionFollowsCurrency() {
        assertTrue(MonkiRegion.fromCurrency("PYG") == MonkiRegion.PARAGUAY)
        assertTrue(MonkiRegion.fromCurrency("ARS") == MonkiRegion.ARGENTINA)
        listOf("USD", "EUR", "BRL").forEach { assertTrue(MonkiRegion.fromCurrency(it) == MonkiRegion.NEUTRAL) }
    }

    private fun tx(id: Long, type: TransactionType, amount: Long, date: LocalDate, internal: Boolean = false) =
        FinanceTransaction(id, type, amount, 0, "", date, internal)
}
