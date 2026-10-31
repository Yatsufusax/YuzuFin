package com.yuzufin.app.ui.movements

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementFilteringTest {

    private val october = YearMonth.of(2026, 10)

    private fun tx(
        id: Long,
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        categoryId: Long = 1,
        description: String = "",
        date: LocalDate = LocalDate.of(2026, 10, 15),
    ) = FinanceTransaction(
        id = id,
        type = type,
        amountMinor = amount,
        categoryId = categoryId,
        description = description,
        date = date,
    )

    private val all = listOf(
        tx(1, 10_000, description = "Almuerzo"),
        tx(2, 50_000, categoryId = 2, description = "Nafta"),
        tx(3, 200_000, type = TransactionType.INCOME, categoryId = 3, description = "Sueldo"),
        tx(4, 80_000, date = LocalDate.of(2026, 9, 30), description = "Súper"),
    )

    private val categories: List<Category> = emptyList()

    private fun ids(filters: MovementFilters) =
        applyMovementFilters(all, categories, filters).map { it.id }

    @Test fun sin_filtros_solo_aplica_el_mes() {
        assertEquals(listOf(1L, 2L, 3L), ids(MovementFilters(month = october)))
    }

    @Test fun todo_el_historial_incluye_otros_meses() {
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(MovementFilters(month = null)))
    }

    @Test fun monto_minimo_incluye_el_limite() {
        assertEquals(listOf(2L, 3L), ids(MovementFilters(month = october, minAmountMinor = 50_000)))
    }

    @Test fun monto_maximo_incluye_el_limite() {
        assertEquals(listOf(1L, 2L), ids(MovementFilters(month = october, maxAmountMinor = 50_000)))
    }

    @Test fun rango_de_montos() {
        val f = MovementFilters(month = october, minAmountMinor = 20_000, maxAmountMinor = 100_000)
        assertEquals(listOf(2L), ids(f))
    }

    @Test fun tipo_y_monto_se_combinan() {
        val f = MovementFilters(month = october, type = TypeFilter.EXPENSE, minAmountMinor = 20_000)
        assertEquals(listOf(2L), ids(f))
    }

    @Test fun categoria_y_texto() {
        assertEquals(listOf(2L), ids(MovementFilters(month = october, categoryId = 2)))
        assertEquals(listOf(1L), ids(MovementFilters(month = october, query = "  almu ")))
    }

    @Test fun busca_tambien_por_nombre_de_categoria() {
        val withNames = listOf(Category(id = 2, name = "Transporte", type = TransactionType.EXPENSE, iconKey = "", archived = false))
        val f = MovementFilters(month = october, query = "transp")
        assertEquals(listOf(2L), applyMovementFilters(all, withNames, f).map { it.id })
    }

    @Test fun hay_filtros_activos_solo_si_se_puso_alguno_ademas_del_mes() {
        assertFalse(MovementFilters(month = null).hasActiveFilters())
        assertTrue(MovementFilters(minAmountMinor = 1).hasActiveFilters())
        assertTrue(MovementFilters(maxAmountMinor = 1).hasActiveFilters())
        assertTrue(MovementFilters(query = "x").hasActiveFilters())
        assertTrue(MovementFilters(type = TypeFilter.INCOME).hasActiveFilters())
        assertTrue(MovementFilters(categoryId = 3).hasActiveFilters())
    }

    @Test fun quitar_filtros_mantiene_el_mes() {
        val f = MovementFilters(
            query = "a", type = TypeFilter.EXPENSE, categoryId = 2,
            month = october, minAmountMinor = 1, maxAmountMinor = 9,
        ).cleared()
        assertFalse(f.hasActiveFilters())
        assertEquals(october, f.month)
    }
}
