package com.yuzufin.app.assistant

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Collections

/** Monki solo lee: estas pruebas fallan si alguna vez se le da una forma de escribir. */
class MonkiReadOnlyTest {

    @Test fun dataSourceOffersASingleReadOperation() {
        val methods = MonkiDataSource::class.java.declaredMethods.map { it.name }
        assertEquals(listOf("load"), methods)
    }

    @Test fun engineNeverTouchesTheListsItIsGiven() {
        val today = LocalDate.of(2026, 11, 2)
        val transactions = Collections.unmodifiableList(
            mutableListOf(
                FinanceTransaction(1, TransactionType.EXPENSE, 45_000, 1, "Almuerzo", today),
                FinanceTransaction(2, TransactionType.INCOME, 500_000, 2, "Extra", today),
            ),
        )
        val categories = Collections.unmodifiableList(
            mutableListOf(
                Category(1, "Alimentación", TransactionType.EXPENSE, "food"),
                Category(2, "Salario", TransactionType.INCOME, "salary"),
            ),
        )
        val snapshot = MonkiSnapshot(transactions = transactions, categories = categories)
        val before = transactions.toList()
        val engine = MonkiFinanceEngine()

        // Una conversación entera con todas las consultas: si algo intentara modificar una lista,
        // lanzaría UnsupportedOperationException.
        var context = MonkiContext()
        val questions = listOf(
            "hola", "¿Cuánto gasté este mes?", "¿Y el mes pasado?", "¿En qué estoy gastando más?",
            "¿Cómo ando de dinero?", "¿Estoy gastando demasiado?", "¿Cómo vengo comparado con el mes pasado?",
            "¿Cuánto voy a gastar este mes?", "¿Cómo van mis presupuestos?", "¿Cuánto tengo ahorrado?",
            "¿Qué facturas vencen?", "¿Me deben plata?", "¿Cómo vienen mis gastos los últimos meses?",
            "ayuda", "gracias",
        )
        for ((index, question) in questions.withIndex()) {
            val reply = engine.reply(question, snapshot, context, today, index)
            assertTrue(reply.text.isNotBlank())
            context = reply.context
        }
        assertEquals(before, transactions)
    }
}
