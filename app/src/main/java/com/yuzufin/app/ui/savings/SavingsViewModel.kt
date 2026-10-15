package com.yuzufin.app.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.SavingsEntry
import com.yuzufin.app.domain.SavingsGoal
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.SavingsResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Una meta lista para mostrar, con su avance ya calculado. */
data class GoalRow(
    val goal: SavingsGoal,
    val saved: Long,
    val fraction: Float,
    val percent: Int,
    val remaining: Long,
    val complete: Boolean,
    val overdue: Boolean,
    val monthlyNeeded: Long?,
)

data class EntryRow(val entry: SavingsEntry, val goalName: String)

data class SavingsUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    /** Saldo total menos lo apartado. */
    val available: Long = 0L,
    val totalSaved: Long = 0L,
    val goals: List<GoalRow> = emptyList(),
    val history: List<EntryRow> = emptyList(),
)

class SavingsViewModel(
    finance: FinanceRepository,
    private val savings: SavingsRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SavingsUiState> = combine(
        finance.initialBalance,
        finance.transactions,
        savings.goals,
        savings.entries,
        settings.settings,
    ) { initial, transactions, goals, entries, appSettings ->
        if (initial == null) {
            SavingsUiState()
        } else {
            val today = LocalDate.now()
            val balance = BalanceCalculator.availableBalance(initial, transactions)
            val savedByGoal = entries.groupBy { it.goalId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
            val totalSaved = savedByGoal.values.sum()
            val names = goals.associate { it.id to it.name }
            SavingsUiState(
                loading = false,
                currency = CurrencyInfo.fromCode(appSettings.currencyCode),
                available = SavingsMath.spendable(balance, totalSaved),
                totalSaved = totalSaved,
                goals = goals.map { goal ->
                    val saved = savedByGoal[goal.id] ?: 0L
                    GoalRow(
                        goal = goal,
                        saved = saved,
                        fraction = SavingsMath.fraction(goal.targetMinor, saved),
                        percent = SavingsMath.percent(goal.targetMinor, saved),
                        remaining = SavingsMath.remaining(goal.targetMinor, saved),
                        complete = SavingsMath.isComplete(goal.targetMinor, saved),
                        overdue = SavingsMath.isOverdue(goal.targetMinor, saved, today, goal.deadline),
                        monthlyNeeded = SavingsMath.monthlyNeeded(goal.targetMinor, saved, today, goal.deadline),
                    )
                },
                history = entries.take(HISTORY_LIMIT).mapNotNull { entry ->
                    names[entry.goalId]?.let { EntryRow(entry, it) }
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavingsUiState())

    /** [amountMinor] positivo aporta, negativo retira. [onResult] corre en el hilo principal. */
    fun addEntry(goalId: Long, amountMinor: Long, date: LocalDate, note: String, onResult: (SavingsResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                savings.addEntry(goalId, amountMinor, date, note)
            } catch (e: Exception) {
                SavingsResult.INVALID
            }
            onResult(result)
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { runCatching { savings.deleteEntry(id) } }
    }

    companion object {
        private const val HISTORY_LIMIT = 10

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SavingsViewModel(container.financeRepository, container.savingsRepository, container.settingsRepository)
            }
        }
    }
}
