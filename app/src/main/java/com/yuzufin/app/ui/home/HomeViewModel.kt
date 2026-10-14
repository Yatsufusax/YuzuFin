package com.yuzufin.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.IncomeRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.buildMovementItems
import com.yuzufin.app.ui.incomes.PendingIncome
import com.yuzufin.app.ui.incomes.UpcomingIncome
import com.yuzufin.app.ui.incomes.buildPending
import com.yuzufin.app.ui.incomes.buildUpcoming
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val balance: Long = 0L,
    /** Obligaciones pendientes: llegan en la Fase 5, por ahora 0. */
    val committed: Long = 0L,
    val freeEstimate: Long = 0L,
    /** Ahorros: llegan en la Fase 4, por ahora 0. */
    val saved: Long = 0L,
    val netWorth: Long = 0L,
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val recent: List<MovementItem> = emptyList(),
    val hasMovements: Boolean = false,
    /** Nombre elegido por la persona; vacío si no puso ninguno. */
    val userName: String = "",
    /** Cobros que ya llegaron y esperan “Ya cobré”. No están en el saldo. */
    val pendingIncomes: List<PendingIncome> = emptyList(),
    /** Próximo cobro previsto (no es dinero real todavía). */
    val nextIncome: UpcomingIncome? = null,
)

class HomeViewModel(
    finance: FinanceRepository,
    private val income: IncomeRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        finance.initialBalance,
        finance.transactions,
        finance.categories,
        settings.settings,
        combine(income.sources, income.occurrences) { sources, occurrences -> sources to occurrences },
    ) { initial, transactions, categories, appSettings, incomeData ->
        val (sources, occurrences) = incomeData
        if (initial == null) {
            HomeUiState(userName = appSettings.userName)
        } else {
            val balance = BalanceCalculator.availableBalance(initial, transactions)
            val month = BalanceCalculator.monthTotals(initial, transactions, YearMonth.now())
            val committed = 0L
            val saved = 0L
            HomeUiState(
                loading = false,
                currency = CurrencyInfo.fromCode(appSettings.currencyCode),
                balance = balance,
                committed = committed,
                freeEstimate = BalanceCalculator.freeEstimate(balance, committed),
                saved = saved,
                netWorth = BalanceCalculator.netWorth(balance, saved, receivable = 0L, debts = 0L),
                monthIncome = month.income,
                monthExpense = month.expense,
                recent = buildMovementItems(transactions.take(4), categories),
                hasMovements = transactions.isNotEmpty(),
                userName = appSettings.userName,
                pendingIncomes = buildPending(sources, occurrences),
                nextIncome = buildUpcoming(sources, LocalDate.now()).firstOrNull(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun confirmIncome(occurrenceId: Long, amountMinor: Long) {
        viewModelScope.launch { runCatching { income.confirm(occurrenceId, amountMinor) } }
    }

    fun skipIncome(occurrenceId: Long) {
        viewModelScope.launch { runCatching { income.skip(occurrenceId) } }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(container.financeRepository, container.incomeRepository, container.settingsRepository)
            }
        }
    }
}
