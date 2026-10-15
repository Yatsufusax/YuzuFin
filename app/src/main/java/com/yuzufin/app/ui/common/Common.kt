package com.yuzufin.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yuzufin.app.R
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Movimiento listo para mostrar: ya trae el nombre y el icono de su categoría. */
data class MovementItem(
    val id: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryName: String,
    val iconKey: String,
    val description: String,
    val date: LocalDate,
    /** Movimiento interno de un préstamo: se gestiona desde Obligaciones. */
    val isInternal: Boolean = false,
) {
    val title: String get() = description.ifBlank { categoryName }
}

fun buildMovementItems(
    transactions: List<FinanceTransaction>,
    categories: List<com.yuzufin.app.domain.Category>,
): List<MovementItem> {
    val byId = categories.associateBy { it.id }
    return transactions.map { t ->
        val category = byId[t.categoryId]
        MovementItem(
            id = t.id,
            type = t.type,
            amountMinor = t.amountMinor,
            categoryName = category?.name.orEmpty(),
            iconKey = category?.iconKey ?: "other",
            description = t.description,
            date = t.date,
            isInternal = t.internalTransfer,
        )
    }
}

fun categoryIcon(key: String): ImageVector = when (key) {
    "food" -> Icons.Rounded.Restaurant
    "transport" -> Icons.Rounded.DirectionsBus
    "home" -> Icons.Rounded.Home
    "utilities" -> Icons.Rounded.Bolt
    "health" -> Icons.Rounded.LocalHospital
    "education" -> Icons.Rounded.School
    "leisure" -> Icons.Rounded.Movie
    "shopping" -> Icons.Rounded.ShoppingCart
    "salary" -> Icons.Rounded.Payments
    "extra" -> Icons.Rounded.Work
    "loan" -> Icons.Rounded.SwapHoriz
    "other" -> Icons.Rounded.MoreHoriz
    else -> Icons.Rounded.Category
}

private val spanish: Locale = Locale.forLanguageTag("es")

private fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

/** `Octubre 2026`. */
fun formatMonthYear(month: YearMonth): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", spanish)).capitalized()

/** `Lun 12 oct`. */
fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", spanish)).capitalized()

/** `12 de octubre de 2026`. */
fun formatFullDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish))

/** “Hoy”, “Ayer” o la fecha corta. */
@Composable
fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> formatShortDate(date)
    }
}
