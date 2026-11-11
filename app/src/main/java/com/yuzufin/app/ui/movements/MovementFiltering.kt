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

/** Una porción del anillo de gastos. [categoryId] es null en «Otros» (lo que no entra en las mayores). */
data class RingSlice(val categoryId: Long?, val name: String, val amountMinor: Long, val percent: Int)

/**
 * Gastos del período por categoría: las mayores y, si hay más de [maxSlices], el resto junto en «Otros».
 * Respeta mes, búsqueda e importes, pero no la categoría ni el tipo elegidos: así el anillo sigue mostrando
 * todas las categorías aunque una esté seleccionada. Los movimientos internos de préstamos no cuentan.
 */
fun expenseRing(
    transactions: List<FinanceTransaction>,
    categories: List<Category>,
    filters: MovementFilters,
    maxSlices: Int = 4,
): List<RingSlice> {
    val base = filters.copy(categoryId = null, type = TypeFilter.EXPENSE)
    val spent = applyMovementFilters(transactions, categories, base).filter { !it.internalTransfer }
    val total = spent.sumOf { it.amountMinor }
    if (total <= 0L) return emptyList()
    val names = categories.associate { it.id to it.name }
    val sorted = spent.groupBy { it.categoryId }
        .map { (id, list) -> Triple(id, names[id].orEmpty(), list.sumOf { it.amountMinor }) }
        .sortedByDescending { it.third }
    fun percent(amount: Long) = ((amount * 100L + total / 2) / total).toInt()
    if (sorted.size <= maxSlices) {
        return sorted.map { RingSlice(it.first, it.second, it.third, percent(it.third)) }
    }
    val head = sorted.take(maxSlices - 1).map { RingSlice(it.first, it.second, it.third, percent(it.third)) }
    val rest = sorted.drop(maxSlices - 1).sumOf { it.third }
    return head + RingSlice(null, "", rest, percent(rest))
}
