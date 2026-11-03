package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.Budget
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.Loan
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.SavingsEntry
import com.yuzufin.app.domain.SavingsGoal
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonkiFinanceEngineTest {
    private val engine = MonkiFinanceEngine()
    private val today = LocalDate.of(2026, 11, 2) // lunes

    private val food = Category(1, "Alimentación", TransactionType.EXPENSE, "food")
    private val transport = Category(2, "Transporte", TransactionType.EXPENSE, "transport")
    private val salary = Category(10, "Salario", TransactionType.INCOME, "salary")

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: Long,
        categoryId: Long,
        description: String,
        date: LocalDate,
        internal: Boolean = false,
    ) = FinanceTransaction(id, type, amount, categoryId, description, date, internal)

    private fun data(
        currency: CurrencyInfo = CurrencyInfo.PYG,
        today: LocalDate = this.today,
        extra: List<FinanceTransaction> = emptyList(),
    ) = MonkiSnapshot(
        transactions = listOf(
            tx(1, TransactionType.EXPENSE, 45_000, 1, "Almuerzo", today.withDayOfMonth(1)),
            tx(2, TransactionType.EXPENSE, 30_000, 1, "Chipa", this.today),
            tx(3, TransactionType.EXPENSE, 20_000, 2, "Bolt", LocalDate.of(2026, 10, 28)),
            tx(4, TransactionType.EXPENSE, 100_000, 1, "Super", LocalDate.of(2026, 10, 10)),
            tx(5, TransactionType.EXPENSE, 50_000, 2, "Nafta", LocalDate.of(2026, 10, 15)),
            tx(6, TransactionType.INCOME, 3_000_000, 10, "Sueldo", LocalDate.of(2026, 10, 5)),
            tx(7, TransactionType.INCOME, 500_000, 10, "Extra", LocalDate.of(2026, 11, 1)),
            // Préstamo: mueve el saldo pero no cuenta como gasto.
            tx(8, TransactionType.EXPENSE, 70_000, 1, "Préstamo", LocalDate.of(2026, 11, 1), internal = true),
            tx(9, TransactionType.EXPENSE, 60_000, 1, "Cena", LocalDate.of(2026, 10, 2)),
            tx(10, TransactionType.EXPENSE, 100_000, 2, "Mecánico", LocalDate.of(2026, 9, 15)),
        ) + extra,
        categories = listOf(food, transport, salary),
        initial = InitialBalance(1_000_000, LocalDate.of(2026, 1, 1)),
        budgets = listOf(Budget(1, 1, 90_000)),
        goals = listOf(SavingsGoal(1, "Viaje", 1_000_000, null, 0, false)),
        savingsEntries = listOf(SavingsEntry(1, 1, 200_000, LocalDate.of(2026, 10, 20), "", null)),
        bills = listOf(Bill(1, "Luz", 150_000, 2, LocalDate.of(2026, 11, 5), 0, false, false, null)),
        loans = listOf(Loan(1, "Juan", LoanDirection.LENT, 300_000, LocalDate.of(2026, 10, 1), "", false)),
        currency = currency,
    )

    private fun ask(
        question: String,
        context: MonkiContext = MonkiContext(),
        snapshot: MonkiSnapshot = data(),
        day: LocalDate = today,
    ) = engine.reply(question, snapshot, context, day, seed = 0)

    // ----- Gastos e ingresos -------------------------------------------------------------------

    @Test fun expensesThisMonthExcludeLoansAndShowRealTotal() {
        val text = ask("¿Cuánto gasté este mes?").text
        assertTrue(text.contains("Gs. 75.000"))
        assertTrue(text.contains("2 movimientos"))
        assertTrue(text.contains("Gs. 500.000")) // ingresos del mismo período
        assertFalse(text.contains("145.000")) // el préstamo de 70.000 no es un gasto
    }

    @Test fun periodsAreResolvedCorrectly() {
        assertTrue(ask("¿Cuánto gasté hoy?").text.contains("Gs. 30.000"))
        assertTrue(ask("¿Cuánto gasté ayer?").text.contains("Gs. 45.000"))
        assertTrue(ask("¿Cuánto gasté esta semana?").text.contains("Gs. 95.000"))
        assertTrue(ask("¿Cuánto gasté el mes pasado?").text.contains("Gs. 230.000"))
        assertTrue(ask("¿Cuánto gasté en octubre?").text.contains("Gs. 230.000"))
    }

    @Test fun futureMovementsAreIgnored() {
        val future = tx(50, TransactionType.EXPENSE, 999_000, 1, "Futuro", today.plusDays(1))
        val text = ask("¿Cuánto gasté este mes?", snapshot = data(extra = listOf(future))).text
        assertTrue(text.contains("Gs. 75.000"))
    }

    @Test fun incomeQuestionUsesIncomes() {
        val text = ask("¿Cuánto ingresó este mes?").text
        assertTrue(text.contains("Gs. 500.000"))
        assertTrue(text.contains("1 movimiento"))
    }

    @Test fun naturalPhrasingsAreUnderstood() {
        // Sin período ni contexto se pregunta, no se asume.
        val asked = ask("¿Cuánta plata se me fue?")
        assertTrue(asked.text.contains("período"))
        assertEquals(MonkiIntent.EXPENSES, asked.context.intent)
        assertNull(asked.context.period)
    }

    @Test fun emptyPeriodSaysSoClearly() {
        val text = ask("¿Cuánto gasté el año pasado?").text
        assertTrue(text.contains("No encuentro gastos registrados"))
    }

    // ----- Contexto de la conversación ---------------------------------------------------------

    @Test fun periodAnswersAnEarlierQuestion() {
        val asked = ask("¿Cuánto gasté?")
        val answered = ask("este mes", context = asked.context)
        assertTrue(answered.text.contains("Gs. 75.000"))
    }

    @Test fun followUpKeepsTheSubjectAndChangesThePeriod() {
        val first = ask("¿Cuánto gasté este mes?")
        val second = ask("¿Y el mes pasado?", context = first.context)
        assertTrue(second.text.contains("Gs. 230.000"))
        assertTrue(second.text.contains("el mes pasado"))
    }

    @Test fun followUpCanNarrowByCategory() {
        val first = ask("¿Cuánto gasté este mes?")
        val second = ask("¿Y en comida?", context = first.context)
        assertTrue(second.text.contains("Alimentación"))
        assertTrue(second.text.contains("Gs. 75.000"))
    }

    @Test fun followUpKeepsTheTopicWhenOnlyThePeriodChanges() {
        val first = ask("¿Cuánto gasté en chipa este mes?")
        assertTrue(first.text.contains("Gs. 30.000"))
        val second = ask("¿Y el mes pasado?", context = first.context)
        assertTrue(second.text.contains("No encuentro gastos de"))
    }

    @Test fun periodlessQuestionReusesThePreviousPeriod() {
        val first = ask("¿Cuánto gasté este mes?")
        val second = ask("¿Cuánta plata se me fue?", context = first.context)
        assertTrue(second.text.contains("Gs. 75.000"))
    }

    @Test fun ambiguousLastMonthIsAskedNotAssumed() {
        val reply = ask("¿Cuánto gasté el último mes?")
        assertTrue(reply.text.contains("30 días"))
    }

    @Test fun greetingDoesNotChangeTheConversation() {
        val first = ask("¿Cuánto gasté este mes?")
        val hello = ask("hola", context = first.context)
        assertEquals(first.context, hello.context)
    }

    // ----- Categorías y temas ------------------------------------------------------------------

    @Test fun topCategoriesAreRankedWithPercentages() {
        val text = ask("¿En qué gasté más en octubre?").text
        assertTrue(text.contains("1. Alimentación: Gs. 160.000 (69%)"))
        assertTrue(text.contains("2. Transporte: Gs. 70.000 (30%)"))
    }

    @Test fun topCategoriesUseTheContextPeriod() {
        val first = ask("¿Cuánto gasté este mes?")
        val second = ask("¿En qué estoy gastando más?", context = first.context)
        assertTrue(second.text.contains("Alimentación"))
        assertTrue(second.text.contains("este mes"))
    }

    @Test fun topicMatchesDescriptionsAndUnknownTopicsAreAdmitted() {
        val chipa = ask("¿Cuánto gasté en chipa este mes?").text
        assertTrue(chipa.contains("Gs. 30.000"))
        assertTrue(chipa.contains("1 movimiento"))
        val unknown = ask("¿Cuánto gasté en yates este mes?").text
        assertTrue(unknown.contains("No encuentro nada de «yates»"))
    }

    // ----- Saldo, análisis y comparaciones -----------------------------------------------------

    @Test fun balanceSeparatesTotalSavedAndFree() {
        val text = ask("¿Cómo ando de dinero?").text
        assertTrue(text.contains("Gs. 4.025.000")) // saldo total
        assertTrue(text.contains("Gs. 200.000")) // apartado
        assertTrue(text.contains("Gs. 3.825.000")) // disponible
        assertTrue(text.contains("Gs. 3.675.000")) // libre estimado
    }

    @Test fun balanceWithoutInitialBalanceSaysSo() {
        val text = ask("¿Cuánto tengo?", snapshot = data().copy(initial = null)).text
        assertTrue(text.contains("saldo inicial"))
    }

    @Test fun spendingCheckUsesFixedRulesAndExplainsThem() {
        // Sin presupuestos y con ahorro sano, no hay nada que avisar.
        val calm = ask("¿Estoy gastando demasiado?", snapshot = data().copy(budgets = emptyList())).text
        assertTrue(calm.contains("no veo señales de alerta"))
        assertTrue(calm.contains("reglas fijas"))

        // Este mes ingresaron 500.000 y se gastan 675.000: hay déficit.
        val deficit = listOf(
            tx(60, TransactionType.EXPENSE, 600_000, 1, "Compras", today.minusDays(1)),
        )
        val overspent = data(extra = deficit).copy(budgets = emptyList())
        val alert = ask("¿Estoy gastando demasiado?", snapshot = overspent).text
        assertTrue(alert.contains("Gastaste más de lo que ingresó"))
    }

    @Test fun compareMonthUsesTheSameDaysOfThePreviousMonth() {
        val text = ask("¿Cómo vengo comparado con el mes pasado?").text
        assertTrue(text.contains("Gs. 75.000"))
        assertTrue(text.contains("Gs. 60.000")) // del 1 al 2 de octubre
        assertTrue(text.contains("25% más"))
        assertTrue(text.contains("Lo que más subió fue Alimentación"))
    }

    @Test fun trendListsMonthsAndStatesTheChange() {
        val text = ask("¿Cómo vienen mis gastos los últimos meses?").text
        assertTrue(text.contains("septiembre: Gs. 100.000"))
        assertTrue(text.contains("octubre: Gs. 230.000"))
        assertTrue(text.contains("(hasta hoy)"))
        assertTrue(text.contains("subieron 130%"))
    }

    @Test fun projectionIsLabelledAsAnEstimate() {
        val early = ask("¿Cuánto voy a gastar este mes?").text
        assertTrue(early.contains("muy pronto"))
        val tenth = LocalDate.of(2026, 11, 10)
        val estimate = engine.reply("¿Cuánto voy a gastar este mes?", data(today = today), MonkiContext(), tenth, 0).text
        assertTrue(estimate.contains("estimación"))
        assertTrue(estimate.contains("Gs. 225.000")) // 75.000 en 10 días → 30 días
    }

    // ----- Presupuestos, ahorros, facturas y préstamos -------------------------------------------

    @Test fun budgetsShowProgressAndStatus() {
        val text = ask("¿Cómo van mis presupuestos?").text
        assertTrue(text.contains("Alimentación"))
        assertTrue(text.contains("83%"))
        assertTrue(text.contains("cerca del límite"))
    }

    @Test fun savingsShowProgressPerGoal() {
        val text = ask("¿Cuánto tengo ahorrado?").text
        assertTrue(text.contains("Viaje: Gs. 200.000 de Gs. 1.000.000 (20%)"))
        assertTrue(text.contains("te faltan Gs. 800.000"))
    }

    @Test fun billsShowDueDates() {
        val text = ask("¿Qué facturas vencen?").text
        assertTrue(text.contains("Luz"))
        assertTrue(text.contains("vence en 3 días"))
        assertTrue(text.contains("Gs. 150.000"))
    }

    @Test fun loansShowWhoOwesWhat() {
        val text = ask("¿Me deben plata?").text
        assertTrue(text.contains("Juan te debe Gs. 300.000"))
    }

    @Test fun emptyDataIsExplainedInsteadOfInvented() {
        val empty = MonkiSnapshot()
        assertTrue(ask("¿Cuánto gasté este mes?", snapshot = empty).text.contains("No encuentro gastos"))
        assertTrue(ask("¿Cómo van mis presupuestos?", snapshot = empty).text.contains("presupuestos"))
        assertTrue(ask("¿Cuánto tengo ahorrado?", snapshot = empty).text.contains("metas"))
        assertTrue(ask("¿Qué facturas vencen?", snapshot = empty).text.contains("facturas"))
        assertTrue(ask("¿Cómo ando de dinero?", snapshot = empty).text.contains("saldo inicial"))
    }

    @Test fun unknownQuestionIsAcknowledged() {
        assertTrue(ask("blablabla").text.contains("No logré entender"))
    }

    // ----- Voz y región -------------------------------------------------------------------------

    @Test fun paraguayAndArgentinaUseTheirOwnVoice() {
        val py = ask("hola", snapshot = data(CurrencyInfo.PYG)).text
        val ar = ask("hola", snapshot = data(CurrencyInfo.ARS)).text
        val neutral = ask("hola", snapshot = data(CurrencyInfo.USD)).text
        assertTrue(py.contains("Mba'éichapa"))
        assertTrue(ar.contains("guita"))
        assertTrue(neutral.contains("Monki"))
        assertNotEquals(py, ar)
    }

    @Test fun neutralVoiceDoesNotUseVoseo() {
        val voseo = ask("¿Cuánto gasté?", snapshot = data(CurrencyInfo.PYG)).text
        val tuteo = ask("¿Cuánto gasté?", snapshot = data(CurrencyInfo.USD)).text
        assertTrue(voseo.contains("querés"))
        assertTrue(tuteo.contains("quieres"))
    }

    @Test fun jokeFollowsTheSituationAndTheRegion() {
        // Gastaste poco frente a lo que ingresó: chiste de "gasto bajo", propio de cada región.
        for (currency in listOf(CurrencyInfo.PYG, CurrencyInfo.ARS, CurrencyInfo.USD)) {
            val region = MonkiRegion.fromCurrency(currency.code)
            val expected = MonkiVoice(region, 0).joke(JokeSituation.LOW)
            val text = ask("¿Cuánto gasté este mes?", snapshot = data(currency)).text
            assertTrue(text.contains(expected))
        }
    }

    @Test fun jokesChangeFromOneMessageToTheNext() {
        val jokes = (0..5).map { MonkiVoice(MonkiRegion.PARAGUAY, it).joke(JokeSituation.GENERAL) }.toSet()
        assertTrue(jokes.size > 1)
    }
}
