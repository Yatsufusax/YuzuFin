package com.yuzufin.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.Budget
import com.yuzufin.app.domain.BudgetProgress
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.CategoryChange
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.MonthPoint
import com.yuzufin.app.domain.Recommendation
import com.yuzufin.app.domain.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class CategoryRow(
    val category: Category?,
    val change: CategoryChange,
    val sharePercent: Int,
)

data class StatsUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val month: YearMonth = YearMonth.now(),
    val isCurrentMonth: Boolean = true,
    val point: MonthPoint = MonthPoint(YearMonth.now(), 0, 0),
    val previousPoint: MonthPoint = MonthPoint(YearMonth.now().minusMonths(1), 0, 0),
    val series: List<MonthPoint> = emptyList(),
    val categoryRows: List<CategoryRow> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val categoryIcons: Map<Long, String> = emptyMap(),
    val recommendations: List<Recommendation> = emptyList(),
    val projectedExpense: Long? = null,
    val dailyAverage: Long? = null,
    val savingsRate: Int? = null,
    val totalSaved: Long = 0L,
    val committedNext30: Long = 0L,
)

private data class StatsBase(
    val initial: InitialBalance?,
    val transactions: List<FinanceTransaction>,
    val categories: List<Category>,
    val currency: CurrencyInfo,
    val budgets: List<Budget>,
)

class StatsViewModel(container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it.isBefore(YearMonth.now())) it.plusMonths(1) else it }

    private val base = combine(
        container.financeRepository.initialBalance,
        container.financeRepository.transactions,
        container.financeRepository.categories,
        container.settingsRepository.settings,
        container.budgetRepository.budgets,
    ) { initial, transactions, categories, settings, budgets ->
        StatsBase(initial, transactions, categories, CurrencyInfo.fromCode(settings.currencyCode), budgets)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        base,
        month,
        container.savingsRepository.entries,
        container.obligationsRepository.bills,
    ) { data, selected, entries, bills ->
        val today = LocalDate.now()
        val point = StatsCalculator.monthPoint(data.initial, data.transactions, selected)
        val previousPoint = StatsCalculator.monthPoint(data.initial, data.transactions, selected.minusMonths(1))
        val spent = StatsCalculator.expenseByCategory(data.initial, data.transactions, selected)
        val previousSpent = StatsCalculator.expenseByCategory(data.initial, data.transactions, selected.minusMonths(1))
        val byId = data.categories.associateBy { it.id }
        val rows = StatsCalculator.changes(spent, previousSpent)
            .filter { it.currentMinor > 0L }
            .map { CategoryRow(byId[it.categoryId], it, StatsCalculator.percentOf(it.currentMinor, point.expense)) }
        val isCurrent = selected == YearMonth.from(today)
        val elapsedDays = if (isCurrent) today.dayOfMonth else selected.lengthOfMonth()
        val limit = today.plusDays(30)
        StatsUiState(
            loading = false,
            currency = data.currency,
            month = selected,
            isCurrentMonth = isCurrent,
            point = point,
            previousPoint = previousPoint,
            series = StatsCalculator.series(data.initial, data.transactions, selected, 6),
            categoryRows = rows,
            budgets = StatsCalculator.budgetProgress(data.budgets, spent),
            categoryNames = data.categories.associate { it.id to it.name },
            categoryIcons = data.categories.associate { it.id to it.iconKey },
            recommendations = StatsCalculator.recommendations(data.initial, data.transactions, data.budgets, selected, today),
            projectedExpense = StatsCalculator.projectedExpense(point.expense, today, selected),
            dailyAverage = if (point.expense > 0L && elapsedDays > 0) point.expense / elapsedDays else null,
            savingsRate = StatsCalculator.savingsRatePercent(point.income, point.expense),
            totalSaved = entries.sumOf { it.amountMinor },
            committedNext30 = bills.filter { !it.paid && !it.dueDate.isAfter(limit) }.sumOf { it.amountMinor },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    companion object {
        fun factory(container: AppContainer) = viewModelFactory { initializer { StatsViewModel(container) } }
    }
}
