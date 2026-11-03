package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Collections

/** Monki 0.5: seguimiento de preguntas, proyección, simulaciones y razonamiento prudente sobre los datos. */
class MonkiReasoningTest {
    private val engine = MonkiFinanceEngine()
    private val today = LocalDate.of(2026, 11, 20)

    private val food = Category(1, "Alimentación", TransactionType.EXPENSE, "food")
    private val transport = Category(2, "Transporte", TransactionType.EXPENSE, "transport")
    private val leisure = Category(3, "Ocio", TransactionType.EXPENSE, "leisure")
    private val utilities = Category(4, "Servicios", TransactionType.EXPENSE, "utilities")
    private val salary = Category(10, "Salario", TransactionType.INCOME, "salary")
    private val categories = listOf(food, transport, leisure, utilities, salary)

    private var nextId = 1L
    private fun expense(amount: Long, category: Long, description: String, date: LocalDate) =
        FinanceTransaction(nextId++, TransactionType.EXPENSE, amount, category, description, date)

    private fun income(amount: Long, date: LocalDate) =
        FinanceTransaction(nextId++, TransactionType.INCOME, amount, 10, "Sueldo", date)

    private fun snapshot(
        transactions: List<FinanceTransaction>,
        bills: List<Bill> = emptyList(),
        currency: CurrencyInfo = CurrencyInfo.PYG,
    ) = MonkiSnapshot(
        transactions = transactions,
        categories = categories,
        initial = InitialBalance(1_000_000, LocalDate.of(2026, 1, 1)),
        bills = bills,
        currency = currency,
    )

    /** Este mes: 4 movimientos que suman Gs. 368.000 (el caso de las pruebas reales). */
    private fun thisMonth() = listOf(
        expense(200_000, 1, "Super", LocalDate.of(2026, 11, 2)),
        expense(100_000, 2, "Nafta", LocalDate.of(2026, 11, 5)),
        expense(50_000, 1, "Cena", LocalDate.of(2026, 11, 8)),
        expense(18_000, 1, "Chipa", LocalDate.of(2026, 11, 12)),
    )

    private fun withHistory() = snapshot(
        thisMonth() + listOf(
            expense(100_000, 1, "Super", LocalDate.of(2026, 10, 10)),
            expense(50_000, 2, "Nafta", LocalDate.of(2026, 10, 15)),
            expense(30_000, 3, "Cine", LocalDate.of(2026, 10, 20)),
            expense(90_000, 1, "Super", LocalDate.of(2026, 9, 12)),
        ),
    )

    /** Diez cafés chicos y tres gastos grandes. */
    private fun coffeeHabit(withIncome: Long? = null): MonkiSnapshot {
        val coffees = (1..10).map { expense(8_000, 1, "Café", LocalDate.of(2026, 11, it)) }
        val big = listOf(
            expense(200_000, 1, "Super", LocalDate.of(2026, 11, 2)),
            expense(150_000, 2, "Nafta", LocalDate.of(2026, 11, 5)),
            expense(120_000, 3, "Cine", LocalDate.of(2026, 11, 8)),
        )
        val extra = withIncome?.let { listOf(income(it, LocalDate.of(2026, 11, 1))) }.orEmpty()
        return snapshot(coffees + big + extra)
    }

    private fun ask(question: String, snap: MonkiSnapshot, context: MonkiContext = MonkiContext(), day: LocalDate = today) =
        engine.reply(question, snap, context, day, seed = 0)

    private fun amounts(text: String): Set<Long> =
        Regex("Gs\\. ([\\d.]+)").findAll(text).map { it.groupValues[1].replace(".", "").toLong() }.toSet()

    // ----- A. Referencias temporales y contexto -------------------------------------------------

    @Test fun anteriorMesIsAPeriodNotACategorySearch() {
        val snap = withHistory()
        val first = ask("¿Cuánto gasté este mes?", snap)
        assertTrue(first.text.contains("Gs. 368.000") && first.text.contains("4 movimientos"))
        val second = ask("¿Y el anterior mes?", snap, first.context)
        assertTrue(second.text.contains("Gs. 180.000"))
        assertTrue(second.text.contains("el mes pasado"))
        assertFalse(second.text.contains("No encuentro nada"))
    }

    @Test fun bareAnteriorKeepsTheIntentAndMovesOnePeriodBack() {
        val snap = withHistory()
        val thisMonth = ask("¿Cuánto gasté este mes?", snap)
        val last = ask("¿Y el anterior?", snap, thisMonth.context)
        assertTrue(last.text.contains("Gs. 180.000"))
        // Y otra vez "el anterior": septiembre.
        val before = ask("¿Y el anterior?", snap, last.context)
        assertTrue(before.text.contains("Gs. 90.000"))
        assertTrue(before.text.contains("septiembre"))
    }

    @Test fun haceDosMesesIsAnAbsolutePeriod() {
        val snap = withHistory()
        val reply = ask("¿Y hace dos meses?", snap, ask("¿Cuánto gasté este mes?", snap).context)
        assertTrue(reply.text.contains("Gs. 90.000"))
        assertEquals(MonkiIntent.EXPENSES, reply.context.intent)
    }

    @Test fun comparedWithThePreviousOneComparesTheConversationPeriod() {
        val snap = withHistory()
        val lastMonth = ask("¿Cuánto gasté el mes pasado?", snap)
        val reply = ask("¿Y comparado con el anterior?", snap, lastMonth.context)
        assertTrue(reply.text.contains("Gs. 180.000"))
        assertTrue(reply.text.contains("Gs. 90.000"))
        assertTrue(reply.text.contains("septiembre"))
    }

    @Test fun categoryFollowUpStillWorks() {
        val snap = withHistory()
        val reply = ask("¿Y en comida?", snap, ask("¿Cuánto gasté este mes?", snap).context)
        assertTrue(reply.text.contains("Gs. 268.000"))
    }

    @Test fun anteriorIsNeverATopic() {
        val q = MonkiIntentParser.parse("¿Y el anterior?", today, hasContext = true)
        assertNull(q.topic)
        assertTrue(q.followUp && q.previousPeriod)
        val noContext = MonkiIntentParser.parse("¿Y el anterior?", today, hasContext = false)
        assertFalse(noContext.followUp)
    }

    @Test fun periodHelpers() {
        val twoAgo = MonkiPeriods.read("hace dos meses", today).periods.single()
        assertEquals(LocalDate.of(2026, 9, 1), twoAgo.start)
        assertEquals(LocalDate.of(2026, 9, 30), twoAgo.end)
        assertEquals(MonkiPeriod.Kind.LAST_MONTH, MonkiPeriods.read("el anterior mes", today).periods.single().kind)
        val thisMonth = MonkiPeriods.read("este mes", today).periods.single()
        assertEquals(LocalDate.of(2026, 10, 1), MonkiPeriods.previous(thisMonth, today).start)
        val last7 = MonkiPeriods.read("esta semana", today).periods.single()
        assertEquals(today.minusDays(13), MonkiPeriods.previous(last7, today).start)
    }

    // ----- Intenciones nuevas -------------------------------------------------------------------

    private fun intent(q: String) = MonkiIntentParser.parse(q, today, hasContext = false).intent

    @Test fun newQuestionsAreRecognised() {
        assertEquals(MonkiIntent.ANT_SPENDING, intent("¿Qué gasto hormiga me está perjudicando más?"))
        assertEquals(MonkiIntent.PROJECTION, intent("Si sigo gastando así, ¿cómo terminaré este mes?"))
        assertEquals(MonkiIntent.SAVING_TIPS, intent("¿Qué podría dejar de comprar para ahorrar más?"))
        assertEquals(MonkiIntent.SIMULATION, intent("¿Cuánto dinero podría ahorrar si reduzco mis gastos un 15 %?"))
        assertEquals(MonkiIntent.HABITS, intent("¿Qué hábito financiero debería cambiar?"))
        assertEquals(MonkiIntent.EXPLAIN, intent("¿Por qué siento que mi dinero desaparece tan rápido?"))
    }

    @Test fun existingSavingsQuestionsAreNotStolen() {
        assertEquals(MonkiIntent.SAVINGS, intent("¿Cuánto tengo ahorrado?"))
        assertEquals(MonkiIntent.SAVINGS, intent("¿Cómo van mis metas de ahorro?"))
    }

    @Test fun percentIsReadAndValidated() {
        val ok = MonkiIntentParser.parse("¿Y si recorto un 15 % mis gastos?", today, false)
        assertEquals(15, ok.percent)
        val bad = MonkiIntentParser.parse("¿Cuánto ahorro si reduzco mis gastos un 250%?", today, false)
        assertNull(bad.percent)
        assertTrue(bad.invalidPercent)
    }

    // ----- E. Simulación ------------------------------------------------------------------------

    @Test fun simulationUsesRealExpensesOfThePeriod() {
        val reply = ask("¿Cuánto dinero podría ahorrar si reduzco mis gastos un 15 %?", snapshot(thisMonth()))
        val text = reply.text
        assertTrue(text.contains("hipotético"))
        assertEquals(setOf(368_000L, 55_200L, 312_800L), amounts(text))
        assertTrue(text.contains("15%"))
        assertFalse(text.contains("meta"))
    }

    @Test fun simulationRespectsTheRequestedPeriod() {
        val reply = ask("¿Cuánto ahorro si reduzco un 10% mis gastos del mes pasado?", withHistory())
        assertEquals(setOf(180_000L, 18_000L, 162_000L), amounts(reply.text))
    }

    @Test fun simulationWithoutExpensesInventsNothing() {
        val reply = ask("¿Cuánto ahorro si reduzco mis gastos un 15%?", snapshot(emptyList()))
        assertTrue(amounts(reply.text).isEmpty())
        assertTrue(reply.text.contains("No encuentro gastos"))
    }

    @Test fun simulationRejectsAbsurdPercentages() {
        val reply = ask("¿Cuánto ahorro si reduzco mis gastos un 250%?", snapshot(thisMonth()))
        assertTrue(amounts(reply.text).isEmpty())
        assertTrue(reply.text.contains("entre 1% y 100%"))
    }

    // ----- C. Proyección ------------------------------------------------------------------------

    @Test fun projectionIsAnEstimateBuiltFromPaceAndRemainingDays() {
        val reply = ask("Si sigo gastando así, ¿cómo terminaré este mes?", snapshot(thisMonth()))
        val text = reply.text
        assertTrue(text.contains("estimación"))
        assertTrue(text.contains("Quedan 10 días"))
        // 368.000 en 20 días = 18.400 por día; 368.000 × 30 / 20 = 552.000.
        assertEquals(setOf(368_000L, 18_400L, 552_000L), amounts(text))
        assertFalse(text.contains("Gastaste Gs. 368.000"))
    }

    @Test fun projectionComparesWithIncomeAndMentionsUpcomingBills() {
        val bill = Bill(1, "Luz", 150_000, 2, LocalDate.of(2026, 11, 25), 0, false, false, null)
        val snap = snapshot(thisMonth() + income(500_000, LocalDate.of(2026, 11, 1)), listOf(bill))
        val text = ask("¿Cuánto voy a gastar este mes?", snap).text
        assertTrue(text.contains("superaría lo ingresado por unos Gs. 52.000"))
        assertTrue(text.contains("Gs. 150.000"))
    }

    @Test fun projectionTooEarlyDoesNotGuess() {
        val text = ask("¿Cómo terminaré este mes?", snapshot(thisMonth()), day = LocalDate.of(2026, 11, 4)).text
        assertTrue(text.contains("muy pronto"))
        assertTrue(amounts(text).isEmpty())
    }

    // ----- B. Gastos hormiga --------------------------------------------------------------------

    @Test fun antSpendingFindsSmallRepeatedExpenses() {
        val text = ask("¿Qué gasto hormiga me está perjudicando más?", coffeeHabit()).text
        assertTrue(text.contains("Café"))
        assertTrue(text.contains("10 veces"))
        assertTrue(text.contains("Gs. 80.000"))
        // No confunde "hormiga" con la categoría que más gasta.
        assertFalse(text.contains("Nafta"))
        assertFalse(text.contains("Super"))
    }

    @Test fun antSpendingNeedsEnoughMovements() {
        val text = ask("¿Qué gasto hormiga me está perjudicando más?", snapshot(thisMonth())).text
        assertTrue(text.contains("todavía no puedo"))
        assertTrue(amounts(text).isEmpty())
    }

    @Test fun antSpendingDoesNotInventPatterns() {
        val varied = snapshot(
            listOf(
                expense(200_000, 1, "Super", LocalDate.of(2026, 11, 2)),
                expense(100_000, 2, "Nafta", LocalDate.of(2026, 11, 3)),
                expense(50_000, 1, "Cena", LocalDate.of(2026, 11, 4)),
                expense(18_000, 1, "Chipa", LocalDate.of(2026, 11, 5)),
                expense(75_000, 3, "Cine", LocalDate.of(2026, 11, 6)),
                expense(60_000, 4, "Luz", LocalDate.of(2026, 11, 7)),
                expense(33_000, 2, "Bolt", LocalDate.of(2026, 11, 8)),
                expense(41_000, 1, "Pizza", LocalDate.of(2026, 11, 9)),
            ),
        )
        val text = ask("¿Qué gasto hormiga me está perjudicando más?", varied).text
        assertTrue(text.contains("no veo gastos hormiga claros"))
    }

    // ----- D. Recomendaciones para ahorrar --------------------------------------------------------

    @Test fun savingTipsUseMovementsNotSavingsGoals() {
        val text = ask("¿Qué podría dejar de comprar para ahorrar más?", coffeeHabit()).text
        assertFalse(text.contains("metas de ahorro"))
        assertTrue(text.contains("Alimentación"))
        assertTrue(text.contains("Café"))
        // 10 % hipotético de 280.000 (café + super) = 28.000.
        assertTrue(text.contains("Gs. 28.000"))
        assertTrue(text.contains("obligaciones"))
        // Nunca sugiere recortar transporte o servicios.
        assertFalse(text.contains("• Transporte"))
        assertFalse(text.contains("• Servicios"))
    }

    @Test fun savingTipsWithFewDataSaySo() {
        val text = ask("¿Qué podría dejar de comprar para ahorrar más?", snapshot(thisMonth())).text
        assertTrue(text.contains("todavía no puedo"))
        assertTrue(amounts(text).isEmpty())
    }

    // ----- F. Hábitos ---------------------------------------------------------------------------

    @Test fun habitsAreCluesNotADiagnosis() {
        val text = ask("¿Qué hábito financiero debería cambiar?", coffeeHabit(withIncome = 400_000)).text
        assertTrue(text.contains("pistas, no un diagnóstico"))
        assertTrue(text.contains("Café"))
        assertTrue(text.contains("gastás más de lo que entra"))
        assertTrue(setOf(80_000L, 550_000L, 400_000L).containsAll(amounts(text)))
    }

    @Test fun habitsWithFewMovementsDoNotDiagnose() {
        val text = ask("¿Qué hábito financiero debería cambiar?", snapshot(thisMonth())).text
        assertTrue(text.contains("todavía no puedo hablar de hábitos"))
        assertTrue(amounts(text).isEmpty())
    }

    // ----- G. Preguntas abiertas ------------------------------------------------------------------

    @Test fun openQuestionExplainsWhereTheMoneyGoes() {
        val reply = ask("¿Por qué siento que mi dinero desaparece tan rápido?", coffeeHabit(withIncome = 400_000))
        val text = reply.text
        assertFalse(text.contains("No logré entender"))
        assertTrue(text.contains("ingresaron Gs. 400.000"))
        assertTrue(text.contains("gastaste Gs. 550.000"))
        assertTrue(text.contains("137%"))
        assertTrue(text.contains("Alimentación"))
        assertEquals(MonkiIntent.EXPLAIN, reply.context.intent)
    }

    @Test fun openQuestionAsksForMissingIncome() {
        val text = ask("¿Por qué mi plata desaparece?", coffeeHabit()).text
        assertTrue(text.contains("No tengo ingresos registrados"))
    }

    @Test fun openQuestionWithoutDataSaysWhatIsNeeded() {
        val text = ask("¿Por qué siento que mi dinero desaparece tan rápido?", snapshot(emptyList())).text
        assertTrue(text.contains("necesito más movimientos"))
        assertTrue(amounts(text).isEmpty())
    }

    // ----- Sin datos: nada inventado --------------------------------------------------------------

    private val newQuestions = listOf(
        "¿Qué gasto hormiga me está perjudicando más?",
        "Si sigo gastando así, ¿cómo terminaré este mes?",
        "¿Qué podría dejar de comprar para ahorrar más?",
        "¿Cuánto dinero podría ahorrar si reduzco mis gastos un 15 %?",
        "¿Qué hábito financiero debería cambiar?",
        "¿Por qué siento que mi dinero desaparece tan rápido?",
        "¿Y el anterior?",
        "¿Y comparado con el anterior?",
    )

    @Test fun withoutDataNoAmountIsInvented() {
        var context = MonkiContext()
        for (question in newQuestions) {
            val reply = ask(question, snapshot(emptyList()), context)
            assertTrue("«$question» inventó importes: ${reply.text}", amounts(reply.text).isEmpty())
            context = reply.context
        }
    }

    @Test fun regionalHumourIsKeptInNewAnswers() {
        val pyg = (0..20).map { engine.reply("¿Qué gasto hormiga me está perjudicando más?", coffeeHabit(), MonkiContext(), today, it).text }
        val usd = (0..20).map {
            engine.reply("¿Qué gasto hormiga me está perjudicando más?", coffeeHabit().copy(currency = CurrencyInfo.fromCode("USD")), MonkiContext(), today, it).text
        }
        assertTrue(pyg.any { it.contains("chipa") || it.contains("lomito") })
        assertTrue(usd.all { !it.contains("chipa") && !it.contains("morfi") })
    }

    // ----- Solo lectura ---------------------------------------------------------------------------

    @Test fun newQuestionsNeverTouchTheData() {
        val original = coffeeHabit(withIncome = 400_000)
        val transactions = Collections.unmodifiableList(original.transactions.toMutableList())
        val snap = original.copy(
            transactions = transactions,
            categories = Collections.unmodifiableList(original.categories.toMutableList()),
        )
        val before = transactions.toList()
        var context = MonkiContext()
        for ((index, question) in (listOf("¿Cuánto gasté este mes?") + newQuestions).withIndex()) {
            val reply = engine.reply(question, snap, context, today, index)
            assertTrue(reply.text.isNotBlank())
            context = reply.context
        }
        assertEquals(before, transactions)
    }
}
