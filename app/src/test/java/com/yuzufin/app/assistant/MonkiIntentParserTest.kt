package com.yuzufin.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonkiIntentParserTest {
    private val today = LocalDate.of(2026, 11, 2)

    private fun parse(text: String, context: Boolean = false) = MonkiIntentParser.parse(text, today, context)

    private fun intent(text: String) = parse(text).intent

    @Test fun differentWaysToAskForExpenses() {
        assertEquals(MonkiIntent.EXPENSES, intent("¿Cuánto gasté este mes?"))
        assertEquals(MonkiIntent.EXPENSES, intent("¿Cuánta plata se me fue?"))
        assertEquals(MonkiIntent.EXPENSES, intent("cuanto salio todo esto la semana pasada"))
        assertEquals(MonkiIntent.EXPENSES, intent("¿cuánto pagué ayer?"))
    }

    @Test fun categoriesBalanceAndChecks() {
        assertEquals(MonkiIntent.TOP_CATEGORIES, intent("¿En qué estoy gastando más?"))
        assertEquals(MonkiIntent.TOP_CATEGORIES, intent("¿Dónde gasté más la semana pasada?"))
        assertEquals(MonkiIntent.BALANCE, intent("¿Cómo ando de dinero?"))
        assertEquals(MonkiIntent.BALANCE, intent("dame un resumen"))
        assertEquals(MonkiIntent.BALANCE, intent("¿Cuánto me queda para gastar?"))
        assertEquals(MonkiIntent.SPENDING_CHECK, intent("¿Estoy gastando demasiado?"))
    }

    @Test fun comparisonsTrendsAndProjection() {
        assertEquals(MonkiIntent.COMPARE, intent("¿Cómo vengo comparado con el mes pasado?"))
        assertEquals(MonkiIntent.COMPARE, intent("¿Estoy gastando más que el mes pasado?"))
        assertEquals(MonkiIntent.TREND, intent("¿Cómo vienen mis gastos los últimos meses?"))
        assertEquals(MonkiIntent.PROJECTION, intent("¿Cuánto voy a gastar este mes?"))
    }

    @Test fun otherAreas() {
        assertEquals(MonkiIntent.BUDGETS, intent("¿Cómo van mis presupuestos?"))
        assertEquals(MonkiIntent.SAVINGS, intent("¿Cuánto tengo ahorrado?"))
        assertEquals(MonkiIntent.BILLS, intent("¿Qué facturas vencen?"))
        assertEquals(MonkiIntent.LOANS, intent("¿Me deben plata?"))
        assertEquals(MonkiIntent.INCOME, intent("¿cuánto gané este año?"))
    }

    @Test fun conversationalIntents() {
        assertEquals(MonkiIntent.GREETING, intent("Hola"))
        assertEquals(MonkiIntent.GREETING, intent("Mba'éichapa"))
        assertEquals(MonkiIntent.GREETING, intent(""))
        assertEquals(MonkiIntent.THANKS, intent("gracias!"))
        assertEquals(MonkiIntent.HELP, intent("¿Qué podés hacer?"))
        assertEquals(MonkiIntent.UNKNOWN, intent("blablabla"))
    }

    @Test fun periodAndTopicAreExtracted() {
        val q = parse("¿Cuánto gasté en comida este mes?")
        assertEquals(MonkiPeriod.Kind.THIS_MONTH, q.periods.single().kind)
        assertEquals("comida", q.topic)
        assertNull(parse("¿Cuánto gasté en total este mes?").topic)
        assertNull(parse("¿En qué estoy gastando más?").topic)
    }

    @Test fun followUpsNeedAConversation() {
        val withContext = parse("¿Y el mes pasado?", context = true)
        assertEquals(MonkiIntent.UNKNOWN, withContext.intent)
        assertTrue(withContext.followUp)
        assertEquals(MonkiPeriod.Kind.LAST_MONTH, withContext.periods.single().kind)

        assertFalse(parse("¿Y el mes pasado?", context = false).followUp)

        val topic = parse("¿Y la chipa?", context = true)
        assertTrue(topic.followUp)
        assertEquals("chipa", topic.topic)
    }

    @Test fun lastMonthIsFlaggedAsAmbiguous() {
        assertTrue(parse("¿Cuánto gasté el último mes?").ambiguousPeriod)
    }
}
