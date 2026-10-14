package com.yuzufin.app.ui.movements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.buildMovementItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

enum class TypeFilter {
    ALL, INCOME, EXPENSE;

    fun matches(type: TransactionType): Boolean = when (this) {
        ALL -> true
        INCOME -> type == TransactionType.INCOME
        EXPENSE -> type == TransactionType.EXPENSE
    }
}

data class MovementFilters(
    val query: String = "",
    val type: TypeFilter = TypeFilter.ALL,
    val categoryId: Long? = null,
    /** `null` = todo el historial. */
    val month: YearMonth? = YearMonth.now(),
)

data class DayGroup(val date: LocalDate, val items: List<MovementItem>)

data class MovementsUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val filters: MovementFilters = MovementFilters(),
    /** Categorías para el filtro, según el tipo elegido. */
    val categories: List<Category> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val hasAnyMovements: Boolean = false,
    val canGoToNextMonth: Boolean = false,
)

class MovementsViewModel(
    finance: FinanceRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(MovementFilters())

    val uiState: StateFlow<MovementsUiState> = combine(
        filters,
        finance.transactions,
        finance.categories,
        settings.settings,
    ) { f, transactions, categories, appSettings ->
        val byId = categories.associateBy { it.id }
        val query = f.query.trim().lowercase()

        val filtered = transactions.filter { t ->
            f.type.matches(t.type) &&
                (f.categoryId == null || t.categoryId == f.categoryId) &&
                (f.month == null || YearMonth.from(t.date) == f.month) &&
                (
                    query.isEmpty() ||
                        t.description.lowercase().contains(query) ||
                        (byId[t.categoryId]?.name.orEmpty()).lowercase().contains(query)
                    )
        }

        val groups = buildMovementItems(filtered, categories)
            .groupBy { it.date }
            .map { (date, items) -> DayGroup(date, items) }

        MovementsUiState(
            loading = false,
            currency = CurrencyInfo.fromCode(appSettings.currencyCode),
            filters = f,
            categories = categories.filter { f.type.matches(it.type) },
            groups = groups,
            totalIncome = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
            totalExpense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor },
            hasAnyMovements = transactions.isNotEmpty(),
            canGoToNextMonth = f.month != null && f.month.isBefore(YearMonth.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementsUiState())

    fun setQuery(query: String) = filters.update { it.copy(query = query.take(60)) }

    /** Al cambiar el tipo se limpia la categoría, que podría no corresponder. */
    fun setType(type: TypeFilter) = filters.update { it.copy(type = type, categoryId = null) }

    fun setCategory(categoryId: Long?) = filters.update { it.copy(categoryId = categoryId) }

    fun showAllHistory() = filters.update { it.copy(month = null) }

    fun showCurrentMonth() = filters.update { it.copy(month = YearMonth.now()) }

    fun previousMonth() = filters.update {
        it.copy(month = (it.month ?: YearMonth.now()).minusMonths(1))
    }

    fun nextMonth() = filters.update {
        val current = it.month
        if (current != null && current.isBefore(YearMonth.now())) {
            it.copy(month = current.plusMonths(1))
        } else {
            it
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MovementsViewModel(container.financeRepository, container.settingsRepository) }
        }
    }
}
