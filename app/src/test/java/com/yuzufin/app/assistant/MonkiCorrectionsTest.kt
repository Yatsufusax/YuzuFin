package com.yuzufin.app.assistant

import com.yuzufin.app.domain.Budget
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

/**
 * Monki 0.5, segunda revisión: seguimiento de simulaciones, consultas por categoría, gastos hormiga con
 * ahorro hipotético y texto del umbral. Cada prueba corresponde a un caso de las pruebas reales.
 */
class MonkiCorrectionsTest {
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

    private fun snapshot(transactions: List<FinanceTransaction>, budgets: List<Budget> = emptyList()) = MonkiSnapshot(
        transactions = transactions,
        categories = categories,
        initial = InitialBalance(1_000_000, LocalDate.of(2026, 1, 1)),
        budgets = budgets,
    )

    /** Noviembre: alimentación 268.000 y transporte 100.000 (total 368.000). */
    private fun thisMonth() = listOf(
        expense(200_000, 1, "Super", LocalDate.of(2026, 11, 2)),
        expense(100_000, 2, "Nafta", LocalDate.of(2026, 11, 5)),
        expense(50_000, 1, "Cena", LocalDate.of(2026, 11, 8)),
        expense(18_000, 1, "Chipa", LocalDate.of(2026, 11, 12)),
    )

    /** Más octubre (180.000: alimentación 100.000, transporte 50.000, ocio 30.000) y septiembre (90.000). */
    private fun withHistory(budgets: List<Budget> = emptyList()) = snapshot(
        thisMonth() + listOf(
            expense(100_000, 1, "Super", LocalDate.of(2026, 10, 10)),
            expense(50_000, 2, "Nafta", LocalDate.of(2026, 10, 15)),
            expense(30_000, 3, "Cine", LocalDate.of(2026, 10, 20)),
            expense(90_000, 1, "Super", LocalDate.of(2026, 9, 12)),
        ),
        budgets,
    )

    /** Diez cafés de 8.000 y tres gastos grandes: total 550.000, hormiga 80.000. */
    private fun coffeeHabit(): MonkiSnapshot {
        val coffees = (1..10).map { expense(8_000, 1, "Café", LocalDate.of(2026, 11, it)) }
        val big = listOf(
            expense(200_000, 1, "Super", LocalDate.of(2026, 11, 2)),
            expense(150_000, 2, "Nafta", LocalDate.of(2026, 11, 5)),
            expense(120_000, 3, "Cine", LocalDate.of(2026, 11, 8)),
        )
        return snapshot(coffees + big)
    }

    private fun ask(question: String, snap: MonkiSnapshot, context: MonkiContext = MonkiContext()) =
        engine.reply(question, snap, context, today, seed = 0)

    private fun amounts(text: String): Set<Long> =
        Regex("Gs\\. ([\\d.]+)").findAll(text).map { it.groupValues[1].replace(".", "").toLong() }.toSet()

    private fun intent(q: String) = MonkiIntentParser.parse(q, today, hasContext = false).intent

    private val simulation = "¿Qué pasaría si reduzco mis gastos un 50%?"

    // ----- 1. Seguimiento después de simulaciones ---------------------------------------------------

    @Test fun simulationFollowedByLastMonthKeepsThePercent() {
        val snap = withHistory()
        val first = ask(simulation, snap)
        assertEquals(MonkiIntent.SIMULATION, first.context.intent)
        assertEquals(50, first.context.percent)
        // Noviembre: 368.000 → ahorro 184.000.
        assertEquals(setOf(368_000L, 184_000L), amounts(first.text))

        val second = ask("¿Y el mes pasado?", snap, first.context)
        assertFalse(second.text.contains("No logré entender"))
        assertTrue(second.text.contains("hipotético"))
        assertTrue(second.text.contains("el mes pasado"))
        assertTrue(second.text.contains("50%"))
        // Octubre: 180.000 → ahorro 90.000, mismo 50 %.
        assertEquals(setOf(180_000L, 90_000L), amounts(second.text))
        assertEquals(MonkiIntent.SIMULATION, second.context.intent)
        assertEquals(50, second.context.percent)
    }

    @Test fun simulationFollowedByOtherTimeReferences() {
        val snap = withHistory()
        val base = ask(simulation, snap).context

        val september = ask("¿Y en septiembre?", snap, base)
        assertTrue(september.text.contains("en septiembre"))
        assertEquals(setOf(90_000L, 45_000L), amounts(september.text))

        val twoMonthsAgo = ask("¿Y hace dos meses?", snap, base)
        assertTrue(twoMonthsAgo.text.contains("hace 2 meses"))
        assertEquals(setOf(90_000L, 45_000L), amounts(twoMonthsAgo.text))

        // Semana pasada = del 7 al 13 de noviembre: 50.000 + 18.000.
        val lastWeek = ask("¿Y la semana pasada?", snap, base)
        assertEquals(setOf(68_000L, 34_000L), amounts(lastWeek.text))

        // Últimos 30 días: solo hay gastos de noviembre.
        val last30 = ask("¿Y en los últimos 30 días?", snap, base)
        assertEquals(MonkiIntent.SIMULATION, last30.context.intent)
        assertTrue(last30.text.contains("50%"))
        assertEquals(setOf(368_000L, 184_000L), amounts(last30.text))
    }

    @Test fun simulationFollowedByAnteriorMovesOnePeriodBackEachTime() {
        val snap = withHistory()
        val first = ask(simulation, snap)
        val october = ask("¿Y el anterior?", snap, first.context)
        assertEquals(setOf(180_000L, 90_000L), amounts(october.text))
        val september = ask("¿Y el anterior?", snap, october.context)
        assertEquals(setOf(90_000L, 45_000L), amounts(september.text))
        assertTrue(september.text.contains("septiembre"))
        assertEquals(50, september.context.percent)
    }

    @Test fun simulationFollowUpWithoutDataExplainsItAndInventsNothing() {
        val snap = snapshot(thisMonth())
        val first = ask(simulation, snap)
        val second = ask("¿Y el mes pasado?", snap, first.context)
        assertTrue(second.text.contains("No encuentro gastos registrados el mes pasado"))
        assertTrue(amounts(second.text).isEmpty())
        // El escenario sigue vivo para la próxima referencia.
        assertEquals(50, second.context.percent)
        val third = ask("¿Y este mes?", snap, second.context)
        assertEquals(setOf(368_000L, 184_000L), amounts(third.text))
    }

    @Test fun savingsWordingAfterASimulationStillFollowsTheSimulation() {
        val snap = withHistory()
        val base = ask(simulation, snap).context
        val reply = ask("¿Y cuánto ahorraría el mes pasado?", snap, base)
        assertEquals(MonkiIntent.SIMULATION, reply.context.intent)
        assertEquals(setOf(180_000L, 90_000L), amounts(reply.text))
        // Pero una consulta de metas sigue siendo una consulta de metas.
        val goals = ask("¿Cómo van mis metas de ahorro del mes pasado?", snap, base)
        assertFalse(goals.text.contains("hipotético"))
        assertTrue(goals.text.contains("metas de ahorro"))
    }

    @Test fun savingsQuestionsWithoutASimulationAreNotStolen() {
        assertEquals(MonkiIntent.SAVINGS, intent("¿Cuánto tengo ahorrado?"))
        val snap = withHistory()
        val afterExpenses = ask("¿Cuánto gasté este mes?", snap).context
        val reply = ask("¿Cuánto ahorré el mes pasado?", snap, afterExpenses)
        assertFalse(reply.text.contains("hipotético"))
    }

    @Test fun anExplicitNewPercentReplacesTheRememberedOne() {
        val snap = withHistory()
        val base = ask(simulation, snap).context
        val reply = ask("¿Y si reduzco un 30% el mes pasado?", snap, base)
        assertEquals("Respuesta real: ${reply.text}", setOf(180_000L, 54_000L, 126_000L), amounts(reply.text))
        assertEquals(30, reply.context.percent)
    }

    @Test fun percentIsNotCarriedIntoOtherIntents() {
        val snap = withHistory()
        val base = ask(simulation, snap).context
        val reply = ask("¿Cuánto gasté el mes pasado?", snap, base)
        assertEquals(MonkiIntent.EXPENSES, reply.context.intent)
        assertNull(reply.context.percent)
        assertFalse(reply.text.contains("hipotético"))
    }

    @Test fun invalidPercentLeavesNoScenarioToFollow() {
        val snap = withHistory()
        val first = ask("¿Cuánto ahorro si reduzco mis gastos un 250%?", snap)
        assertTrue(first.text.contains("entre 1% y 100%"))
        assertNull(first.context.percent)
        val second = ask("¿Y el mes pasado?", snap, first.context)
        assertTrue(amounts(second.text).isEmpty())
    }

    @Test fun followUpIsRecognisedAsFollowUpOnlyWithContext() {
        val withContext = MonkiIntentParser.parse("¿Y el mes pasado?", today, hasContext = true)
        assertTrue(withContext.followUp)
        assertEquals(MonkiPeriod.Kind.LAST_MONTH, withContext.periods.single().kind)
        assertFalse(MonkiIntentParser.parse("¿Y el mes pasado?", today, hasContext = false).followUp)
    }

    // ----- 2. Consultas sobre categorías -------------------------------------------------------------

    @Test fun categoryQuestionsAreRecognisedInNaturalPhrasings() {
        val phrasings = listOf(
            "¿En qué categoría estoy gastando demasiado?",
            "¿En qué categorías gasto de más?",
            "¿En qué estoy gastando demasiado?",
            "¿Dónde me estoy pasando con mis gastos?",
            "¿En qué me estoy pasando con los gastos?",
            "¿Qué categoría me está costando demasiado?",
            "¿En qué categorías estoy gastando mucho?",
            "¿En qué rubro me excedo?",
            "en que categoria me paso",
        )
        for (question in phrasings) {
            assertEquals("«$question»", MonkiIntent.CATEGORY_EXCESS, intent(question))
        }
    }

    @Test fun categoryQuestionsDoNotStealOtherIntents() {
        assertEquals(MonkiIntent.SPENDING_CHECK, intent("¿Estoy gastando demasiado?"))
        assertEquals(MonkiIntent.SPENDING_CHECK, intent("¿Gasté mucho este mes?"))
        assertEquals(MonkiIntent.TOP_CATEGORIES, intent("¿En qué estoy gastando más?"))
        assertEquals(MonkiIntent.TOP_CATEGORIES, intent("¿En qué categoría gasto más?"))
        assertEquals(MonkiIntent.HABITS, intent("¿Qué hábito financiero debería cambiar?"))
        assertEquals(MonkiIntent.EXPLAIN, intent("¿Por qué siento que mi dinero desaparece tan rápido?"))
        assertEquals(MonkiIntent.SAVING_TIPS, intent("¿Qué podría dejar de comprar para ahorrar más?"))
    }

    @Test fun categoriesWithoutBudgetsAreRankedButNeverCalledExcessive() {
        // Con ingresos bajos: la respuesta vieja decía "gastaste más de lo que ingresó" y no nombraba categorías.
        val snap = snapshot(thisMonth() + income(100_000, LocalDate.of(2026, 11, 1)))
        val text = ask("¿En qué categoría estoy gastando demasiado?", snap).text
        assertTrue(text.contains("Alimentación"))
        assertTrue(text.contains("Transporte"))
        assertEquals(setOf(268_000L, 100_000L, 368_000L), amounts(text))
        assertTrue(text.contains("72%"))
        assertTrue(text.contains("27%"))
        assertTrue(text.contains("presupuestos"))
        assertFalse(text.contains("ingres"))
        assertFalse(text.contains("superaron"))
        assertFalse(text.contains("superó"))
    }

    @Test fun categoriesWithBudgetsReportTheOnesThatWentOver() {
        val budgets = listOf(Budget(1, 1, 200_000), Budget(2, 2, 150_000))
        val text = ask("¿En qué categoría estoy gastando demasiado?", snapshot(thisMonth(), budgets)).text
        assertTrue(text.contains("superaron su presupuesto"))
        assertTrue(text.contains("Alimentación"))
        // 268.000 gastados, tope 200.000, 68.000 de más (134%).
        assertEquals(setOf(268_000L, 200_000L, 68_000L), amounts(text))
        assertTrue(text.contains("134%"))
        // Transporte (100.000 de 150.000) está dentro de su tope: no se menciona como exceso.
        assertFalse(text.contains("Transporte"))
    }

    @Test fun nearTheLimitIsReportedWithoutCallingItOver() {
        val budgets = listOf(Budget(1, 1, 300_000), Budget(2, 2, 150_000))
        val text = ask("¿En qué categorías gasto de más?", snapshot(thisMonth(), budgets)).text
        assertTrue(text.contains("Ninguna categoría con presupuesto superó su tope"))
        assertTrue(text.contains("Cerca de su límite"))
        assertTrue(text.contains("89%"))
        assertFalse(text.contains("superaron su presupuesto"))
    }

    @Test fun categoriesOutsideTheBudgetsAreShownWithoutAJudgement() {
        val budgets = listOf(Budget(2, 2, 150_000))
        val text = ask("¿En qué categoría estoy gastando demasiado?", snapshot(thisMonth(), budgets)).text
        assertTrue(text.contains("Ninguna categoría con presupuesto superó su tope"))
        assertTrue(text.contains("Fuera de tus presupuestos"))
        assertTrue(text.contains("Alimentación"))
        assertTrue(text.contains("Gs. 268.000"))
        assertTrue(text.contains("no puedo decir si es demasiado"))
    }

    @Test fun categoryQuestionRespectsThePeriodAndItsBudgetMonth() {
        // Octubre: alimentación 100.000 contra un tope de 80.000.
        val budgets = listOf(Budget(1, 1, 80_000))
        val text = ask("¿En qué categoría gasté demasiado el mes pasado?", withHistory(budgets)).text
        assertTrue(text.contains("el mes pasado"))
        assertEquals(setOf(100_000L, 80_000L, 20_000L, 50_000L, 30_000L), amounts(text))
        assertTrue(text.contains("125%"))
    }

    @Test fun budgetsAreNotAppliedToAPeriodThatIsNotAMonth() {
        val budgets = listOf(Budget(1, 1, 80_000))
        val text = ask("¿En qué categoría me paso en los últimos 90 días?", withHistory(budgets)).text
        assertTrue(text.contains("mensuales"))
        assertFalse(text.contains("superaron"))
        assertTrue(text.contains("Alimentación"))
    }

    @Test fun categoryQuestionWithoutExpensesInventsNothing() {
        val text = ask("¿En qué categoría estoy gastando demasiado?", snapshot(emptyList())).text
        assertTrue(text.contains("No encuentro gastos"))
        assertTrue(amounts(text).isEmpty())
    }

    @Test fun categoryQuestionCanBeFollowedByAnotherPeriod() {
        val budgets = listOf(Budget(1, 1, 80_000))
        val snap = withHistory(budgets)
        val first = ask("¿En qué categoría estoy gastando demasiado?", snap)
        assertEquals(MonkiIntent.CATEGORY_EXCESS, first.context.intent)
        val second = ask("¿Y el mes pasado?", snap, first.context)
        assertTrue(second.text.contains("125%"))
    }

    // ----- 3. Gastos hormiga y ahorro potencial hipotético -------------------------------------------

    @Test fun antSpendingAnswersHowMuchCouldBeSavedAsHypotheticalScenarios() {
        val reply = ask("¿Cuánto podría ahorrar si controlo mis gastos pequeños?", coffeeHabit())
        val text = reply.text
        assertEquals(MonkiIntent.ANT_SPENDING, reply.context.intent)
        assertTrue(text.contains("Café"))
        assertTrue(text.contains("10 veces"))
        // Gasto histórico: 80.000 de 550.000 (14%), dicho como lo ya gastado.
        assertTrue(text.contains("Gs. 80.000"))
        assertTrue(text.contains("14%"))
        assertTrue(text.contains("Eso es lo que ya gastaste, no lo que podrías ahorrar"))
        // Ahorro potencial hipotético: 10 %, 25 % y 50 % de 80.000.
        assertTrue(text.contains("hipotéticos"))
        assertTrue(text.contains("Si los reducís un 10%: unos Gs. 8.000"))
        assertTrue(text.contains("Si los reducís un 25%: unos Gs. 20.000"))
        assertTrue(text.contains("Si los reducís un 50%: unos Gs. 40.000"))
        assertTrue(text.contains("no asumo que los elimines"))
        // Umbral = mediana (8.000), más las cifras anteriores; nada más.
        assertEquals(setOf(80_000L, 550_000L, 8_000L, 20_000L, 40_000L), amounts(text))
        // No mezcla categorías grandes con hormiga.
        assertFalse(text.contains("Nafta"))
        assertFalse(text.contains("Super"))
    }

    @Test fun antScenariosReuseTheSharedReductionCalculator() {
        val text = ask("¿Cuánto podría ahorrar si controlo mis gastos pequeños?", coffeeHabit()).text
        for (percent in listOf(10, 25, 50)) {
            val expected = com.yuzufin.app.domain.StatsCalculator.reduction(80_000, percent)
            assertTrue(amounts(text).contains(expected))
        }
    }

    @Test fun antSpendingUsesTheRequestedPercentWhenThereIsOne() {
        val text = ask("¿Cuánto ahorro si reduzco mis gastos hormiga un 20%?", coffeeHabit()).text
        assertTrue(text.contains("Si los reducís un 20%: unos Gs. 16.000"))
        assertFalse(text.contains("un 25%"))
        assertFalse(text.contains("un 50%"))
    }

    @Test fun antSpendingWithTooFewMovementsDoesNotGuess() {
        val text = ask("¿Cuánto podría ahorrar si controlo mis gastos pequeños?", snapshot(thisMonth())).text
        assertTrue(text.contains("todavía no puedo"))
        assertTrue(amounts(text).isEmpty())
        assertFalse(text.contains("hipotético"))
    }

    @Test fun antSpendingWithoutAnyPatternHasNothingToSimulate() {
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
        val text = ask("¿Cuánto podría ahorrar si controlo mis gastos pequeños?", varied).text
        assertTrue(text.contains("no veo gastos hormiga claros"))
        assertTrue(text.contains("no tengo sobre qué calcular un ahorro potencial"))
        assertFalse(text.contains("Si los reducís"))
    }

    // ----- 4. Explicación del umbral -----------------------------------------------------------------

    /** Ocho gastos: la mediana es (16.000 + 80.000) / 2 = 48.000, un importe que no es de ningún movimiento. */
    private fun evenCount() = snapshot(
        listOf(
            expense(10_000, 2, "Bolt", LocalDate.of(2026, 11, 3)),
            expense(12_000, 2, "Bolt", LocalDate.of(2026, 11, 6)),
            expense(14_000, 2, "Bolt", LocalDate.of(2026, 11, 9)),
            expense(16_000, 2, "Bolt", LocalDate.of(2026, 11, 12)),
            expense(80_000, 1, "Cena", LocalDate.of(2026, 11, 4)),
            expense(90_000, 1, "Cena", LocalDate.of(2026, 11, 7)),
            expense(100_000, 1, "Super", LocalDate.of(2026, 11, 2)),
            expense(120_000, 1, "Super", LocalDate.of(2026, 11, 10)),
        ),
    )

    @Test fun thresholdTextDescribesTheMedianExactlyAsComputed() {
        val text = ask("¿Qué gasto hormiga me está perjudicando más?", evenCount()).text
        assertTrue(text.contains("mediana"))
        assertFalse(text.contains("la mitad de tus gastos"))
        // El umbral que se informa es el calculado (48.000), no un importe fijo.
        assertTrue(text.contains("Gs. 48.000"))
        assertFalse(text.contains("30.000"))
        // Solo cuentan los Bolt (52.000 en total): 10 % = 5.200, 25 % = 13.000, 50 % = 26.000.
        assertTrue(amounts(text).containsAll(setOf(52_000L, 5_200L, 13_000L, 26_000L)))
    }

    @Test fun thresholdFollowsTheDataNotAnExample() {
        // La misma pregunta con otros importes cambia el umbral informado.
        val text = ask("¿Qué gasto hormiga me está perjudicando más?", coffeeHabit()).text
        assertTrue(text.contains("mediana"))
        assertTrue(text.contains("Gs. 8.000"))
        assertFalse(text.contains("Gs. 48.000"))
    }

    // ----- 5. Solo lectura y cifras inventadas --------------------------------------------------------

    private val correctionQuestions = listOf(
        simulation,
        "¿Y el mes pasado?",
        "¿Y el anterior?",
        "¿En qué categoría estoy gastando demasiado?",
        "¿Y el mes pasado?",
        "¿Cuánto podría ahorrar si controlo mis gastos pequeños?",
        "¿Y el mes pasado?",
    )

    @Test fun withoutDataNoAmountIsInvented() {
        var context = MonkiContext()
        for (question in correctionQuestions) {
            val reply = ask(question, snapshot(emptyList()), context)
            assertTrue("«$question» inventó importes: ${reply.text}", amounts(reply.text).isEmpty())
            context = reply.context
        }
    }

    @Test fun everyAmountComesFromTheData() {
        val snap = withHistory(listOf(Budget(1, 1, 200_000)))
        var context = MonkiContext()
        // Importes que se pueden derivar de los datos de la prueba: totales por período, el tope, la resta
        // contra el tope, la mitad (50 %), sumas por categoría y la mediana de los últimos 90 días (70.000).
        val derivable = setOf(
            368_000L, 184_000L, 180_000L, 90_000L, 45_000L, 268_000L, 100_000L, 200_000L, 68_000L,
            50_000L, 30_000L, 70_000L, 458_000L,
        )
        for (question in correctionQuestions) {
            val reply = ask(question, snap, context)
            val unknown = amounts(reply.text) - derivable
            assertTrue("«$question» mostró importes que no salen de los datos: $unknown", unknown.isEmpty())
            context = reply.context
        }
    }

    @Test fun readOnlyProtectionIsKept() {
        assertEquals(listOf("load"), MonkiDataSource::class.java.declaredMethods.map { it.name })
        val original = withHistory(listOf(Budget(1, 1, 200_000)))
        val transactions = Collections.unmodifiableList(original.transactions.toMutableList())
        val budgets = Collections.unmodifiableList(original.budgets.toMutableList())
        val snap = original.copy(
            transactions = transactions,
            budgets = budgets,
            categories = Collections.unmodifiableList(original.categories.toMutableList()),
        )
        val before = transactions.toList()
        val budgetsBefore = budgets.toList()
        var context = MonkiContext()
        for ((index, question) in correctionQuestions.withIndex()) {
            val reply = engine.reply(question, snap, context, today, index)
            assertTrue(reply.text.isNotBlank())
            context = reply.context
        }
        assertEquals(before, transactions)
        assertEquals(budgetsBefore, budgets)
    }
}
