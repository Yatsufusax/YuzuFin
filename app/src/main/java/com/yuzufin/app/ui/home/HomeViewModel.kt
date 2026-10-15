package com.yuzufin.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.IncomeRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.BillStatus
import com.yuzufin.app.domain.ObligationMath
import com.yuzufin.app.domain.SavingsMath
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
    /** Disponible: saldo total menos lo apartado en metas (puede ser negativo). */
    val balance: Long = 0L,
    /** Facturas pendientes: bajan el libre estimado, no el saldo. */
    val committed: Long = 0L,
    val freeEstimate: Long = 0L,
    /** Total apartado en metas de ahorro. */
    val saved: Long = 0L,
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
    /** Próximas facturas pendientes (las más cercanas primero, con las vencidas arriba). */
    val upcomingBills: List<UpcomingBill> = emptyList(),
)

data class UpcomingBill(val bill: Bill, val status: BillStatus)

private data class HomeExtras(
    val sources: List<com.yuzufin.app.domain.IncomeSource>,
    val occurrences: List<com.yuzufin.app.domain.IncomeOccurrence>,
    val savingEntries: List<com.yuzufin.app.domain.SavingsEntry>,
    val bills: List<Bill>,
)

class HomeViewModel(
    finance: FinanceRepository,
    private val income: IncomeRepository,
    savings: SavingsRepository,
    obligations: ObligationsRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        finance.initialBalance,
        finance.transactions,
        finance.categories,
        settings.settings,
        combine(income.sources, income.occurrences, savings.entries, obligations.bills) { sources, occurrences, entries, bills ->
            HomeExtras(sources, occurrences, entries, bills)
        },
    ) { initial, transactions, categories, appSettings, incomeData ->
        val sources = incomeData.sources
        val occurrences = incomeData.occurrences
        val savingEntries = incomeData.savingEntries
        if (initial == null) {
            HomeUiState(userName = appSettings.userName)
        } else {
            val saved = savingEntries.sumOf { it.amountMinor }
            // Lo apartado sigue siendo tuyo, pero no está disponible para gastar.
            val balance = SavingsMath.spendable(BalanceCalculator.availableBalance(initial, transactions), saved)
            val month = BalanceCalculator.monthTotals(initial, transactions, YearMonth.now())
            val today = LocalDate.now()
            val committed = ObligationMath.committed(incomeData.bills)
            HomeUiState(
                loading = false,
                currency = CurrencyInfo.fromCode(appSettings.currencyCode),
                balance = balance,
                committed = committed,
                freeEstimate = ObligationMath.freeEstimate(balance, committed),
                saved = saved,
                monthIncome = month.income,
                monthExpense = month.expense,
                recent = buildMovementItems(transactions.take(4), categories),
                hasMovements = transactions.isNotEmpty(),
                userName = appSettings.userName,
                pendingIncomes = buildPending(sources, occurrences),
                nextIncome = buildUpcoming(sources, today).firstOrNull(),
                upcomingBills = incomeData.bills
                    .filter { !it.paid }
                    .sortedBy { it.dueDate }
                    .take(3)
                    .map { UpcomingBill(it, ObligationMath.status(it, today)) },
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
                HomeViewModel(
                    container.financeRepository,
                    container.incomeRepository,
                    container.savingsRepository,
                    container.obligationsRepository,
                    container.settingsRepository,
                )
            }
        }
    }
}
