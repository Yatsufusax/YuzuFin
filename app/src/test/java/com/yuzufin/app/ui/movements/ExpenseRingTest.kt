package com.yuzufin.app.ui.movements

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseRingTest {

    private val nov = YearMonth.of(2026, 11)
    private val filters = MovementFilters(month = nov)

    private fun cat(id: Long, name: String) =
        Category(id = id, name = name, type = TransactionType.EXPENSE, iconKey = "", archived = false)

    private fun tx(id: Long, amount: Long, categoryId: Long, internal: Boolean = false, type: TransactionType = TransactionType.EXPENSE) =
        FinanceTransaction(
            id = id, type = type, amountMinor = amount, categoryId = categoryId, description = "",
            date = LocalDate.of(2026, 11, 5), internalTransfer = internal,
        )

    private val categories = listOf(cat(1, "Comida"), cat(2, "Cuotas"), cat(3, "Transporte"), cat(4, "Salud"), cat(5, "Ocio"))

    @Test
    fun groupsByCategoryAndSortsBiggestFirst() {
        val list = listOf(tx(1, 100, 1), tx(2, 300, 2), tx(3, 100, 1))
        val ring = expenseRing(list, categories, filters)
        assertEquals(listOf("Cuotas", "Comida"), ring.map { it.name })
        assertEquals(listOf(60, 40), ring.map { it.percent })
    }

    @Test
    fun restGoesIntoOtherWhenThereAreMoreThanFour() {
        val list = listOf(tx(1, 500, 1), tx(2, 400, 2), tx(3, 300, 3), tx(4, 200, 4), tx(5, 100, 5))
        val ring = expenseRing(list, categories, filters)
        assertEquals(4, ring.size)
        assertNull(ring.last().categoryId)
        assertEquals(300L, ring.last().amountMinor)
    }

    @Test
    fun ignoresIncomeAndInternalLoans() {
        val list = listOf(tx(1, 100, 1), tx(2, 900, 2, internal = true), tx(3, 900, 3, type = TransactionType.INCOME))
        val ring = expenseRing(list, categories, filters)
        assertEquals(1, ring.size)
        assertEquals(100L, ring.first().amountMinor)
    }

    @Test
    fun stillShowsAllCategoriesWhenOneIsSelected() {
        val list = listOf(tx(1, 100, 1), tx(2, 100, 2))
        val ring = expenseRing(list, categories, filters.copy(categoryId = 1))
        assertEquals(2, ring.size)
    }

    @Test
    fun emptyWhenNothingWasSpent() {
        assertTrue(expenseRing(emptyList(), categories, filters).isEmpty())
    }
}
