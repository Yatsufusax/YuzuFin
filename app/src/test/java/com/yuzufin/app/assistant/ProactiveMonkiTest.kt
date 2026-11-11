package com.yuzufin.app.assistant

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.MonkiProactiveMode
import com.yuzufin.app.domain.SavingsEntry
import com.yuzufin.app.domain.SavingsGoal
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProactiveMonkiTest {

    private val today = LocalDate.of(2026, 11, 10) // martes 10 de noviembre
    private val food = Category(id = 1L, name = "Comida", type = TransactionType.EXPENSE, iconKey = "food")
    private val fresh = ProactivePrefs(
        mode = MonkiProactiveMode.IMPORTANT,
        lastOpenEpochDay = today.toEpochDay(),
    )

    private fun tx(
        id: Long,
        type: TransactionType,
        amount: Long,
        categoryId: Long = 1L,
        date: LocalDate,
        description: String = "",
    ) = FinanceTransaction(
        id = id, type = type, amountMinor = amount,
        categoryId = categoryId, description = description, date = date,
    )

    private fun goal(id: Long, name: String, target: Long, saved: Long) = MonkiSnapshot(
        goals = listOf(SavingsGoal(id, name, target, deadline = null, autoPercent = 0, remind = false)),
        savingsEntries = if (saved > 0) {
            listOf(SavingsEntry(1L, id, saved, today.minusDays(1), note = "", sourceTransactionId = null))
        } else {
            emptyList()
        },
    )

    @Test
    fun `apagado no manda nada`() {
        val snapshot = goal(1L, "Viaje", target = 100, saved = 60)
        val notes = ProactiveMonki.notes(snapshot, today, fresh.copy(mode = MonkiProactiveMode.OFF))
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `meta al cincuenta avisa una sola vez con su clave`() {
        val notes = ProactiveMonki.notes(goal(1L, "Viaje", 100, saved = 60), today, fresh)
        val milestone = notes.single { it.kind == ProactiveKind.MILESTONE }
        assertEquals("monki:milestone:1:50", milestone.dedupeKey)
        assertTrue(milestone.text.contains("50"))
        assertTrue(milestone.text.contains("Viaje"))
    }

    @Test
    fun `meta completa anuncia el cien`() {
        val notes = ProactiveMonki.notes(goal(1L, "Moto", 100, saved = 100), today, fresh)
        assertEquals("monki:milestone:1:100", notes.single { it.kind == ProactiveKind.MILESTONE }.dedupeKey)
    }

    @Test
    fun `meta sin llegar al cincuenta no avisa`() {
        val notes = ProactiveMonki.notes(goal(1L, "Viaje", 100, saved = 30), today, fresh)
        assertTrue(notes.none { it.kind == ProactiveKind.MILESTONE })
    }

    @Test
    fun `ingreso grande respecto al promedio se celebra`() {
        val snapshot = MonkiSnapshot(
            transactions = listOf(
                tx(1L, TransactionType.INCOME, 100, date = LocalDate.of(2026, 10, 5)),
                tx(2L, TransactionType.INCOME, 250, date = LocalDate.of(2026, 11, 2)),
            ),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertEquals("monki:income:202611", notes.single { it.kind == ProactiveKind.INCOME }.dedupeKey)
    }

    @Test
    fun `sin meses completos no hay celebracion de ingreso`() {
        val snapshot = MonkiSnapshot(
            transactions = listOf(tx(1L, TransactionType.INCOME, 500, date = LocalDate.of(2026, 11, 2))),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertTrue(notes.none { it.kind == ProactiveKind.INCOME })
    }

    @Test
    fun `categoria al ciento cincuenta del promedio dispara aviso`() {
        val snapshot = MonkiSnapshot(
            categories = listOf(food),
            transactions = listOf(
                tx(1L, TransactionType.EXPENSE, 100, date = LocalDate.of(2026, 10, 12)),
                tx(2L, TransactionType.EXPENSE, 200, date = LocalDate.of(2026, 11, 5)),
            ),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        val over = notes.single { it.kind == ProactiveKind.OVERSPEND }
        assertTrue(over.dedupeKey.startsWith("monki:overspend:1:"))
        assertTrue(over.text.contains("Comida"))
    }

    @Test
    fun `con presupuesto no se repite el aviso de los presupuestos`() {
        val snapshot = MonkiSnapshot(
            categories = listOf(food),
            budgets = listOf(com.yuzufin.app.domain.Budget(1L, 1L, 100)),
            transactions = listOf(tx(1L, TransactionType.EXPENSE, 85, date = LocalDate.of(2026, 11, 5))),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertTrue(notes.none { it.kind == ProactiveKind.OVERSPEND })
    }

    @Test
    fun `overspend no avisa en la primera semana del mes`() {
        val early = LocalDate.of(2026, 11, 5)
        val snapshot = MonkiSnapshot(
            categories = listOf(food),
            transactions = listOf(tx(1L, TransactionType.EXPENSE, 500, date = early)),
        )
        val notes = ProactiveMonki.notes(
            snapshot, early, fresh.copy(lastOpenEpochDay = early.toEpochDay()),
        )
        assertTrue(notes.none { it.kind == ProactiveKind.OVERSPEND })
    }

    @Test
    fun `libre estimado bajo respecto al ingreso avisa`() {
        val snapshot = MonkiSnapshot(
            transactions = listOf(tx(1L, TransactionType.INCOME, 1000, date = LocalDate.of(2026, 11, 1))),
        ).let {
            it.copy(transactions = it.transactions + tx(2L, TransactionType.EXPENSE, 850, date = LocalDate.of(2026, 11, 3)))
        }
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertEquals("monki:lowfunds:202611", notes.single { it.kind == ProactiveKind.LOW_FUNDS }.dedupeKey)
    }

    @Test
    fun `sin ingresos no hay aviso de libre estimado`() {
        val snapshot = MonkiSnapshot(
            transactions = listOf(tx(1L, TransactionType.EXPENSE, 500, date = LocalDate.of(2026, 11, 3))),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertTrue(notes.none { it.kind == ProactiveKind.LOW_FUNDS })
    }

    @Test
    fun `racha de siete dias se celebra solo en modo completo`() {
        val week = (0L..6L).map { d ->
            tx(d + 1, TransactionType.EXPENSE, 10, date = today.minusDays(6 - d))
        }
        val snapshot = MonkiSnapshot(transactions = week)
        val full = ProactiveMonki.notes(snapshot, today, fresh.copy(mode = MonkiProactiveMode.FULL))
        assertEquals("monki:streak:7", full.single { it.kind == ProactiveKind.STREAK }.dedupeKey)
        val important = ProactiveMonki.notes(snapshot, today, fresh)
        assertTrue(important.none { it.kind == ProactiveKind.STREAK })
    }

    @Test
    fun `silencio largo pregunta si todo bien`() {
        val snapshot = MonkiSnapshot()
        val notes = ProactiveMonki.notes(
            snapshot, today, fresh.copy(mode = MonkiProactiveMode.FULL, lastOpenEpochDay = today.minusDays(10).toEpochDay()),
        )
        assertEquals(ProactiveKind.SILENCE, notes.single().kind)
    }

    @Test
    fun `silencio sin dato de apertura no avisa`() {
        val notes = ProactiveMonki.notes(
            MonkiSnapshot(), today, fresh.copy(mode = MonkiProactiveMode.FULL, lastOpenEpochDay = 0L),
        )
        assertTrue(notes.none { it.kind == ProactiveKind.SILENCE })
    }

    private fun bill(id: Long, amount: Long, due: LocalDate, paid: Boolean = false) = Bill(
        id = id, name = "Luz", amountMinor = amount, categoryId = 1L, dueDate = due,
        repeatDay = 0, remind = false, paid = paid, paidDate = null,
    )

    @Test
    fun `libre estimado solo descuenta las cuentas de este mes`() {
        val base = MonkiSnapshot(
            transactions = listOf(
                tx(1L, TransactionType.INCOME, 1000, date = LocalDate.of(2026, 11, 1)),
                tx(2L, TransactionType.EXPENSE, 500, date = LocalDate.of(2026, 11, 3)),
            ),
        )
        val otherMonth = base.copy(bills = listOf(bill(1L, 400, LocalDate.of(2026, 12, 20))))
        assertTrue(ProactiveMonki.notes(otherMonth, today, fresh).none { it.kind == ProactiveKind.LOW_FUNDS })
        val thisMonth = base.copy(bills = listOf(bill(1L, 400, LocalDate.of(2026, 11, 20))))
        assertTrue(ProactiveMonki.notes(thisMonth, today, fresh).any { it.kind == ProactiveKind.LOW_FUNDS })
        val paid = base.copy(bills = listOf(bill(1L, 400, LocalDate.of(2026, 11, 20), paid = true)))
        assertTrue(ProactiveMonki.notes(paid, today, fresh).none { it.kind == ProactiveKind.LOW_FUNDS })
    }

    @Test
    fun `tope de dos avisos por dia`() {
        val snapshot = MonkiSnapshot(
            categories = listOf(food),
            goals = listOf(SavingsGoal(1L, "Viaje", 100, null, 0, false)),
            savingsEntries = listOf(SavingsEntry(1L, 1L, 100, today.minusDays(1), "", null)),
            transactions = listOf(
                tx(1L, TransactionType.INCOME, 100, date = LocalDate.of(2026, 10, 5)),
                tx(2L, TransactionType.INCOME, 400, date = LocalDate.of(2026, 11, 2)),
                tx(3L, TransactionType.EXPENSE, 300, date = LocalDate.of(2026, 11, 5)),
            ),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh)
        assertTrue(notes.size <= 2)
        assertEquals(ProactiveKind.MILESTONE, notes.first().kind)
    }

    @Test
    fun `las claves de deduplicacion no se repiten entre si`() {
        val snapshot = MonkiSnapshot(
            categories = listOf(food),
            goals = listOf(SavingsGoal(1L, "Viaje", 100, null, 0, false)),
            savingsEntries = listOf(SavingsEntry(1L, 1L, 100, today.minusDays(1), "", null)),
            transactions = listOf(
                tx(1L, TransactionType.INCOME, 100, date = LocalDate.of(2026, 10, 5)),
                tx(2L, TransactionType.INCOME, 400, date = LocalDate.of(2026, 11, 2)),
                tx(3L, TransactionType.EXPENSE, 300, date = LocalDate.of(2026, 11, 5)),
            ),
        )
        val notes = ProactiveMonki.notes(snapshot, today, fresh.copy(mode = MonkiProactiveMode.FULL))
        assertEquals(notes.size, notes.map { it.dedupeKey }.distinct().size)
    }

    @Test
    fun `frases proactivas rellenan categoria monto y meta`() {
        val py = MonkiProactive(MonkiRegion.PARAGUAY, seed = 0)
        val text = py.overspend("Comida", "Gs. 50.000", "este mes")
        assertTrue(text.contains("Comida"))
        assertTrue(text.contains("Gs. 50.000"))
        assertTrue(text.contains("este mes"))
        val milestone = py.milestone("Viaje", 50)
        assertTrue(milestone.contains("Viaje"))
        assertTrue(milestone.contains("50"))
    }

    @Test
    fun `sin montos no se dice el importe ni queda contradictorio`() {
        val ar = MonkiProactive(MonkiRegion.ARGENTINA, seed = 0)
        assertTrue(ar.overspend("Comida", null, "este mes").contains("un montón"))
        MonkiRegion.entries.forEach { region ->
            (0..3).forEach { seed ->
                val voice = MonkiProactive(region, seed = seed)
                listOf(voice.income(null), voice.lowFunds(null)).forEach { text ->
                    assertFalse(text.contains("un montón"))
                    assertFalse(text.contains("{"))
                    assertFalse(text.contains("Gs."))
                }
            }
        }
    }

    @Test
    fun `las tres regiones tienen todas las frases`() {
        MonkiRegion.entries.forEach { region ->
            val voice = MonkiProactive(region, seed = 0)
            assertTrue(voice.overspend("Comida", null, "este mes").isNotBlank())
            assertTrue(voice.income(null).isNotBlank())
            assertTrue(voice.milestone("Viaje", 50).isNotBlank())
            assertTrue(voice.streak(7).isNotBlank())
            assertTrue(voice.lowFunds(null).isNotBlank())
            assertTrue(voice.silence(7).isNotBlank())
        }
    }
}
