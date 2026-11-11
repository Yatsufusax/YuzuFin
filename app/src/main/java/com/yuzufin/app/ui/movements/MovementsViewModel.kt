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
import kotlinx.coroutines.launch
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
    /** Importe mínimo y máximo, en la unidad mínima de la moneda. `null` = sin límite. */
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
)

data class DayGroup(val date: LocalDate, val items: List<MovementItem>) {
    /** Lo que dejó el día: ingresos menos gastos (los movimientos internos de préstamos no cuentan). */
    val netMinor: Long
        get() = items.filter { !it.isInternal }.sumOf { if (it.type == TransactionType.INCOME) it.amountMinor else -it.amountMinor }
}

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
    /** Cantidad de movimientos que se ven con los filtros actuales. */
    val resultCount: Int = 0,
    val hasActiveFilters: Boolean = false,
    /** Gastos del período por categoría, para el anillo. */
    val ring: List<RingSlice> = emptyList(),
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
        val filtered = applyMovementFilters(transactions, categories, f)

        val groups = buildMovementItems(filtered, categories)
            .groupBy { it.date }
            .map { (date, items) -> DayGroup(date, items) }

        MovementsUiState(
            loading = false,
            currency = CurrencyInfo.fromCode(appSettings.currencyCode),
            filters = f,
            categories = categories.filter { f.type.matches(it.type) },
            groups = groups,
            // Los movimientos internos de préstamos no cuentan como ingreso ni gasto.
            totalIncome = filtered.filter { it.type == TransactionType.INCOME && !it.internalTransfer }.sumOf { it.amountMinor },
            totalExpense = filtered.filter { it.type == TransactionType.EXPENSE && !it.internalTransfer }.sumOf { it.amountMinor },
            hasAnyMovements = transactions.isNotEmpty(),
            canGoToNextMonth = f.month != null && f.month.isBefore(YearMonth.now()),
            resultCount = groups.sumOf { it.items.size },
            hasActiveFilters = f.hasActiveFilters(),
            ring = expenseRing(transactions, categories, f),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovementsUiState())

    /** Movimientos que aparecieron mientras esta pantalla vivía (recién guardados): se animan al entrar. */
    private val _fresh = MutableStateFlow<Set<Long>>(emptySet())
    val fresh: StateFlow<Set<Long>> = _fresh

    private var knownIds: Set<Long>? = null

    init {
        viewModelScope.launch {
            finance.transactions.collect { list ->
                val ids = list.mapTo(HashSet()) { it.id }
                val known = knownIds
                knownIds = ids
                if (known != null) {
                    val added = ids - known
                    if (added.isNotEmpty()) _fresh.value = added
                }
            }
        }
    }

    fun clearFresh() {
        _fresh.value = emptySet()
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query.take(60)) }

    /** Al cambiar el tipo se limpia la categoría, que podría no corresponder. */
    fun setType(type: TypeFilter) = filters.update { it.copy(type = type, categoryId = null) }

    fun setCategory(categoryId: Long?) = filters.update { it.copy(categoryId = categoryId) }

    /** Importe mínimo y máximo (cualquiera puede ser `null`). */
    fun setAmountRange(min: Long?, max: Long?) = filters.update { it.copy(minAmountMinor = min, maxAmountMinor = max) }

    fun clearFilters() = filters.update { it.cleared() }

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
