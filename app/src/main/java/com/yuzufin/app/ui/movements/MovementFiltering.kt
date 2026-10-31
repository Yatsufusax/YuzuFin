package com.yuzufin.app.ui.movements

import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import java.time.YearMonth

/** Hay algún filtro puesto además del mes (el mes se maneja con sus propios botones). */
fun MovementFilters.hasActiveFilters(): Boolean =
    query.isNotBlank() || type != TypeFilter.ALL || categoryId != null ||
        minAmountMinor != null || maxAmountMinor != null

/** Quita los filtros pero mantiene el mes elegido. */
fun MovementFilters.cleared(): MovementFilters = copy(
    query = "",
    type = TypeFilter.ALL,
    categoryId = null,
    minAmountMinor = null,
    maxAmountMinor = null,
)

/** Reglas de los filtros, sin Android, para poder probarlas. */
fun MovementFilters.matches(transaction: FinanceTransaction, categoryName: String): Boolean {
    val text = query.trim().lowercase()
    return type.matches(transaction.type) &&
        (categoryId == null || transaction.categoryId == categoryId) &&
        (month == null || YearMonth.from(transaction.date) == month) &&
        (minAmountMinor == null || transaction.amountMinor >= minAmountMinor) &&
        (maxAmountMinor == null || transaction.amountMinor <= maxAmountMinor) &&
        (
            text.isEmpty() ||
                transaction.description.lowercase().contains(text) ||
                categoryName.lowercase().contains(text)
            )
}

fun applyMovementFilters(
    transactions: List<FinanceTransaction>,
    categories: List<Category>,
    filters: MovementFilters,
): List<FinanceTransaction> {
    val names = categories.associate { it.id to it.name }
    return transactions.filter { filters.matches(it, names[it.categoryId].orEmpty()) }
}
