package com.yuzufin.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.buildMovementItems
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

class HomeViewModel(
    finance: FinanceRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        finance.initialBalance,
        finance.transactions,
        finance.categories,
        settings.settings,
    ) { initial, transactions, categories, appSettings ->
        if (initial == null) {
            HomeUiState()
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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { HomeViewModel(container.financeRepository, container.settingsRepository) }
        }
    }
}
