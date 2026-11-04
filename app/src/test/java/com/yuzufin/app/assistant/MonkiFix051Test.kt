package com.yuzufin.app.assistant

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Monki 0.5.1: comparación entre períodos, simulaciones por categoría y ahorro futuro frente a gasto
 * proyectado. Los datos reproducen los de las pruebas manuales en el Galaxy S22.
 */
class MonkiFix051Test {
    private val engine = MonkiFinanceEngine()
    private val today = LocalDate.of(2026, 11, 20)

    private val food = Category(1, "Alimentación", TransactionType.EXPENSE, "food")
    private val transport = Category(2, "Transporte", TransactionType.EXPENSE, "transport")
    private val leisure = Category(3, "Ocio", TransactionType.EXPENSE, "leisure")
    private val installments = Category(5, "Cuotas", TransactionType.EXPENSE, "installments")
    private val salary = Category(10, "Salario", TransactionType.INCOME, "salary")
    private val categories = listOf(food, transport, leisure, installments, salary)

    private var nextId = 1L
    private fun expense(amount: Long, category: Long, description: String, date: LocalDate) =
        FinanceTransaction(nextId++, TransactionType.EXPENSE, amount, category, description, date)

    private fun income(amount: Long, date: LocalDate) =
        FinanceTransaction(nextId++, TransactionType.INCOME, amount, 10, "Sueldo", date)

    private fun snapshot(transactions: List<FinanceTransaction>) = MonkiSnapshot(
        transactions = transactions,
        categories = categories,
        initial = InitialBalance(1_000_000, LocalDate.of(2026, 1, 1)),
    )

    /** Noviembre: Transporte 50.000 (2 movimientos), Cuotas 305.000 y Alimentación 13.000 = 368.000. */
    private fun thisMonth() = listOf(
        expense(30_000, 2, "Bus", LocalDate.of(2026, 11, 3)),
        expense(20_000, 2, "Nafta", LocalDate.of(2026, 11, 6)),
        expense(305_000, 5, "Cuota celular", LocalDate.of(2026, 11, 5)),
        expense(13_000, 1, "Chipa", LocalDate.of(2026, 11, 10)),
    )

    /** Octubre: Cuotas 2.000.000 y Alimentación 419.000 = 2.419.000. */
    private fun lastMonth() = listOf(
        expense(2_000_000, 5, "Cuota tarjeta", LocalDate.of(2026, 10, 5)),
        expense(419_000, 1, "Super", LocalDate.of(2026, 10, 12)),
    )

    private fun ask(question: String, snap: MonkiSnapshot, context: MonkiContext = MonkiContext()) =
        engine.reply(question, snap, context, today, seed = 0)

    private fun amounts(text: String): Set<Long> =
        Regex("Gs\\. ([\\d.]+)").findAll(text).map { it.groupValues[1].replace(".", "").toLong() }.toSet()

    private fun parse(question: String) = MonkiIntentParser.parse(question, today, hasContext = false)

    private val compareQuestion = "¿Gasté más este mes o el anterior?"

    // ----- 1. Comparación entre períodos ------------------------------------------------------------

    @Test fun thisMonthOrThePreviousOneIsAComparisonOfBothMonths() {
        val question = parse(compareQuestion)
        assertEquals(MonkiIntent.COMPARE, question.intent)
        assertEquals(2, question.periods.size)
        assertEquals(MonkiPeriod.Kind.THIS_MONTH, question.periods[0].kind)
        assertEquals(MonkiPeriod.Kind.LAST_MONTH, question.periods[1].kind)
    }

    @Test fun comparisonWithBothPeriodsSaysWhichIsMoreAndByHowMuch() {
        val text = ask(compareQuestion, snapshot(thisMonth() + lastMonth())).text
        assertTrue(text, text.contains("Gastaste más el mes pasado"))
        assertTrue(text, amounts(text).containsAll(setOf(368_000L, 2_419_000L, 2_051_000L)))
        // (2.419.000 - 368.000) / 2.419.000 = 84,8 % → 84 % con enteros.
        assertTrue(text, text.contains("84% menos este mes"))
    }

    @Test fun comparisonWorksInTheOtherOrderToo() {
        val text = ask("¿Gasté más el mes pasado o este mes?", snapshot(thisMonth() + lastMonth())).text
        assertTrue(text, text.contains("Gastaste más el mes pasado"))
        assertTrue(text, amounts(text).containsAll(setOf(368_000L, 2_419_000L, 2_051_000L)))
    }

    @Test fun comparisonWhenThePreviousPeriodHasNoRecordsDoesNotInventADifference() {
        val text = ask(compareQuestion, snapshot(thisMonth())).text
        assertTrue(text, text.contains("No encuentro gastos registrados el mes pasado"))
        assertTrue(text, text.contains("Gs. 368.000"))
        assertFalse(text, text.contains("Gastaste más"))
        assertFalse(text, text.contains("La diferencia es de"))
    }

    @Test fun comparisonWhenTheCurrentPeriodHasNoRecordsDoesNotInventADifference() {
        val text = ask(compareQuestion, snapshot(lastMonth())).text
        assertTrue(text, text.contains("No encuentro gastos registrados este mes"))
        assertTrue(text, text.contains("Gs. 2.419.000"))
        assertFalse(text, text.contains("Gastaste más"))
    }

    @Test fun comparisonWithNothingInEitherPeriodSaysSo() {
        val text = ask(compareQuestion, snapshot(emptyList())).text
        assertTrue(text, text.contains("No encuentro movimientos ni este mes ni el mes pasado"))
    }

    @Test fun existingComparisonsAreNotCapturedByTheNewRule() {
        assertEquals(MonkiIntent.COMPARE, parse("¿Estoy gastando más que el mes pasado?").intent)
        assertEquals(MonkiIntent.COMPARE, parse("¿Cómo vengo comparado con el mes pasado?").intent)
        // Elegir entre dos categorías (sin dos períodos) no es comparar períodos.
        assertNotEquals(MonkiIntent.COMPARE, parse("¿Gasté más en comida o en transporte?").intent)
    }

    // ----- 2. Simulaciones por categoría ------------------------------------------------------------

    @Test fun transportReductionOfTwentyPercentUsesOnlyTransport() {
        val reply = ask("Si reduzco mis gastos de transporte un 20%, ¿cuánto podría ahorrar?", snapshot(thisMonth()))
        assertEquals(MonkiIntent.SIMULATION, reply.context.intent)
        assertEquals(setOf(50_000L, 10_000L, 40_000L), amounts(reply.text))
        assertTrue(reply.text, reply.text.contains("2 movimientos"))
        assertFalse(reply.text, reply.text.contains("368.000"))
        assertEquals(20, reply.context.percent)
    }

    @Test fun installmentsReductionOfTenPercentUsesOnlyInstallments() {
        val reply = ask("Si reduzco mis gastos de Cuotas un 10%, ¿cuánto podría ahorrar?", snapshot(thisMonth()))
        assertEquals(setOf(305_000L, 30_500L, 274_500L), amounts(reply.text))
        assertFalse(reply.text, reply.text.contains("368.000"))
        assertEquals(10, reply.context.percent)
    }

    @Test fun categoryNamedWithoutTheWordDeIsStillRecognised() {
        val reply = ask("¿Cuánto ahorro si reduzco transporte un 20%?", snapshot(thisMonth()))
        assertEquals(setOf(50_000L, 10_000L, 40_000L), amounts(reply.text))
    }

    @Test fun generalSimulationWithoutCategoryStillUsesTheWholeMonth() {
        val snap = snapshot(thisMonth())
        val fifty = ask("¿Qué pasaría si reduzco mis gastos un 50%?", snap)
        assertEquals(setOf(368_000L, 184_000L), amounts(fifty.text))
        assertTrue(fifty.text, fifty.text.contains("Gastos registrados"))
        val twenty = ask("¿Cuánto ahorro si reduzco mis gastos un 20%?", snap)
        assertEquals(setOf(368_000L, 73_600L, 294_400L), amounts(twenty.text))
        // Una palabra suelta que no es una categoría no cambia la base de la simulación.
        val loose = ask("¿Cuánto podría ahorrar si reduzco todo un 20%?", snap)
        assertEquals(setOf(368_000L, 73_600L, 294_400L), amounts(loose.text))
    }

    @Test fun categoryWithoutMovementsIsExplainedWithoutInventingNumbers() {
        // "Ocio" existe como categoría, pero no tiene ningún movimiento.
        val never = ask("Si reduzco mis gastos de ocio un 10%, ¿cuánto podría ahorrar?", snapshot(thisMonth()))
        assertTrue(never.text, never.text.contains("No encuentro gastos de «Ocio»"))
        assertTrue(never.text, amounts(never.text).isEmpty())

        // Con movimientos solo en octubre, noviembre no tiene nada que simular.
        val onlyOctober = snapshot(thisMonth() + expense(30_000, 3, "Cine", LocalDate.of(2026, 10, 20)))
        val thisMonthReply = ask("Si reduzco mis gastos de ocio un 10%, ¿cuánto podría ahorrar?", onlyOctober)
        assertTrue(thisMonthReply.text, thisMonthReply.text.contains("No encuentro gastos de «Ocio» este mes"))
        assertTrue(thisMonthReply.text, amounts(thisMonthReply.text).isEmpty())

        // Un tema que no es categoría ni aparece en los gastos tampoco se inventa.
        val unknown = ask("Si reduzco mis gastos de yates un 10%, ¿cuánto podría ahorrar?", snapshot(thisMonth()))
        assertTrue(unknown.text, unknown.text.contains("No encuentro nada de «yates»"))
        assertTrue(unknown.text, amounts(unknown.text).isEmpty())
    }

    @Test fun categorySimulationKeepsItsCategoryWhenOnlyThePeriodChanges() {
        val snap = snapshot(
            thisMonth() + listOf(
                expense(40_000, 2, "Bus", LocalDate.of(2026, 10, 8)),
                expense(100_000, 1, "Super", LocalDate.of(2026, 10, 12)),
            ),
        )
        val first = ask("Si reduzco mis gastos de transporte un 20%, ¿cuánto podría ahorrar?", snap)
        val second = ask("¿Y el mes pasado?", snap, first.context)
        assertEquals(setOf(40_000L, 8_000L, 32_000L), amounts(second.text))
        assertFalse(second.text, second.text.contains("140.000"))
        assertEquals(20, second.context.percent)
    }

    // ----- 3. Proyección de gastos frente a ahorro futuro ---------------------------------------------

    @Test fun savingsForecastAndExpenseProjectionAreDifferentIntents() {
        assertEquals(MonkiIntent.SAVINGS_FORECAST, parse("¿Cuánto voy a ahorrar el próximo mes?").intent)
        assertEquals(MonkiIntent.PROJECTION, parse("¿Cuánto voy a gastar este mes?").intent)
        assertEquals(MonkiIntent.PROJECTION, parse("¿Cómo voy a terminar el mes?").intent)
        // Las simulaciones y las consultas de metas siguen siendo lo que eran.
        assertEquals(
            MonkiIntent.SIMULATION,
            parse("¿Cuánto ahorraría el próximo mes si reduzco mis gastos un 10%?").intent,
        )
        assertEquals(MonkiIntent.SAVINGS, parse("¿Cuánto tengo ahorrado?").intent)
    }

    @Test fun savingsForecastWithoutIncomeHistoryExplainsItAndDoesNotUseTheSevenDayRule() {
        val reply = ask("¿Cuánto voy a ahorrar el próximo mes?", snapshot(thisMonth() + lastMonth()))
        assertEquals(MonkiIntent.SAVINGS_FORECAST, parse("¿Cuánto voy a ahorrar el próximo mes?").intent)
        assertTrue(reply.text, reply.text.contains("datos suficientes"))
        assertTrue(reply.text, reply.text.contains("ingresos y gastos"))
        assertFalse(reply.text, reply.text.contains("cómo vas a cerrar"))
        assertFalse(reply.text, reply.text.contains("muy pronto"))
        assertTrue(reply.text, amounts(reply.text).isEmpty())
    }

    @Test fun savingsForecastWithASingleCompleteMonthStillAsksForMore() {
        val snap = snapshot(
            listOf(
                income(1_000_000, LocalDate.of(2026, 10, 1)),
                expense(800_000, 1, "Super", LocalDate.of(2026, 10, 12)),
            ),
        )
        val text = ask("¿Cuánto voy a ahorrar el próximo mes?", snap).text
        assertTrue(text, text.contains("por ahora tengo solo 1"))
        assertTrue(text, amounts(text).isEmpty())
    }

    @Test fun savingsForecastWithEnoughHistoryUsesTheAverageOfCompleteMonths() {
        val snap = snapshot(
            listOf(
                income(1_000_000, LocalDate.of(2026, 9, 1)),
                expense(600_000, 1, "Super", LocalDate.of(2026, 9, 12)),
                income(1_000_000, LocalDate.of(2026, 10, 1)),
                expense(800_000, 1, "Super", LocalDate.of(2026, 10, 12)),
                // El mes en curso no entra en el promedio.
                expense(900_000, 1, "Super", LocalDate.of(2026, 11, 3)),
            ),
        )
        val text = ask("¿Cuánto voy a ahorrar el próximo mes?", snap).text
        assertTrue(text, text.contains("Es una estimación"))
        assertTrue(text, text.contains("te quedarían unos Gs. 300.000"))
        assertTrue(text, text.contains("30%"))
        assertEquals(setOf(1_000_000L, 700_000L, 300_000L), amounts(text))
    }

    @Test fun savingsForecastSaysWhenThereWouldBeNoMarginToSave() {
        val snap = snapshot(
            listOf(
                income(500_000, LocalDate.of(2026, 9, 1)),
                expense(700_000, 1, "Super", LocalDate.of(2026, 9, 12)),
                income(500_000, LocalDate.of(2026, 10, 1)),
                expense(700_000, 1, "Super", LocalDate.of(2026, 10, 12)),
            ),
        )
        val text = ask("¿Cuánto voy a ahorrar el próximo mes?", snap).text
        assertTrue(text, text.contains("gastarías unos Gs. 200.000 más de lo que ingresa"))
        assertTrue(text, text.contains("no quedaría margen para ahorrar"))
    }

    @Test fun expenseProjectionQuestionsKeepTheirOwnRule() {
        // Día 2 del mes: sigue pidiendo 7 días de datos, pero solo para la proyección de gastos.
        val early = engine.reply(
            "¿Cuánto voy a gastar este mes?", snapshot(thisMonth()), MonkiContext(), LocalDate.of(2026, 11, 2), seed = 0,
        )
        assertTrue(early.text, early.text.contains("muy pronto"))
        val forecast = engine.reply(
            "¿Cuánto voy a ahorrar el próximo mes?", snapshot(thisMonth()), MonkiContext(), LocalDate.of(2026, 11, 2), seed = 0,
        )
        assertFalse(forecast.text, forecast.text.contains("muy pronto"))
    }
}
