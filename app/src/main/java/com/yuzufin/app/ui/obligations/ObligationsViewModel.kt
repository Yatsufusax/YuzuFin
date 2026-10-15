package com.yuzufin.app.ui.obligations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.AppSettings
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.BillStatus
import com.yuzufin.app.domain.Loan
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.LoanPayment
import com.yuzufin.app.domain.LoanResult
import com.yuzufin.app.domain.ObligationMath
import com.yuzufin.app.domain.SavingsMath
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BillRow(val bill: Bill, val status: BillStatus, val categoryName: String)

data class LoanRow(
    val loan: Loan,
    val payments: List<LoanPayment>,
    val outstanding: Long,
    val fraction: Float,
) {
    val settled: Boolean get() = outstanding <= 0L
}

data class ObligationsUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    /** Saldo total menos lo apartado en metas. */
    val spendable: Long = 0L,
    /** Facturas pendientes (no descuentan el saldo). */
    val committed: Long = 0L,
    val freeEstimate: Long = 0L,
    val receivable: Long = 0L,
    val debts: Long = 0L,
    val netWorth: Long = 0L,
    val pendingBills: List<BillRow> = emptyList(),
    val paidBills: List<BillRow> = emptyList(),
    val lent: List<LoanRow> = emptyList(),
    val borrowed: List<LoanRow> = emptyList(),
)

private data class BaseData(
    val initial: com.yuzufin.app.domain.InitialBalance?,
    val transactions: List<com.yuzufin.app.domain.FinanceTransaction>,
    val categories: List<com.yuzufin.app.domain.Category>,
    val settings: AppSettings,
    val saved: Long,
)

class ObligationsViewModel(
    finance: FinanceRepository,
    savings: SavingsRepository,
    private val obligations: ObligationsRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val base = combine(
        finance.initialBalance,
        finance.transactions,
        finance.categories,
        settings.settings,
        savings.entries,
    ) { initial, transactions, categories, appSettings, entries ->
        BaseData(initial, transactions, categories, appSettings, entries.sumOf { it.amountMinor })
    }

    private val loansData = combine(obligations.bills, obligations.loans, obligations.payments) { bills, loans, payments ->
        Triple(bills, loans, payments)
    }

    val uiState: StateFlow<ObligationsUiState> = combine(base, loansData) { data, (bills, loans, payments) ->
        val initial = data.initial
        if (initial == null) {
            ObligationsUiState()
        } else {
            val today = LocalDate.now()
            val spendable = SavingsMath.spendable(BalanceCalculator.availableBalance(initial, data.transactions), data.saved)
            val committed = ObligationMath.committed(bills)
            val receivable = ObligationMath.totalOutstanding(LoanDirection.LENT, loans, payments)
            val debts = ObligationMath.totalOutstanding(LoanDirection.BORROWED, loans, payments)
            val categoryNames = data.categories.associate { it.id to it.name }
            val billRows = bills.map { BillRow(it, ObligationMath.status(it, today), categoryNames[it.categoryId].orEmpty()) }
            val paymentsByLoan = payments.groupBy { it.loanId }
            val loanRows = loans.map { loan ->
                val own = paymentsByLoan[loan.id].orEmpty()
                val outstanding = ObligationMath.outstanding(loan.principalMinor, own)
                val fraction = if (loan.principalMinor > 0) {
                    ((loan.principalMinor - outstanding).toDouble() / loan.principalMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()
                } else {
                    0f
                }
                LoanRow(loan, own, outstanding, fraction)
            }
            ObligationsUiState(
                loading = false,
                currency = CurrencyInfo.fromCode(data.settings.currencyCode),
                spendable = spendable,
                committed = committed,
                freeEstimate = ObligationMath.freeEstimate(spendable, committed),
                receivable = receivable,
                debts = debts,
                netWorth = BalanceCalculator.netWorth(spendable, data.saved, receivable, debts),
                pendingBills = billRows.filter { it.status != BillStatus.PAID },
                paidBills = billRows.filter { it.status == BillStatus.PAID }
                    .sortedByDescending { it.bill.paidDate ?: it.bill.dueDate }
                    .take(PAID_LIMIT),
                // Los saldados van al final de cada lista.
                lent = loanRows.filter { it.loan.direction == LoanDirection.LENT }.sortedBy { it.settled },
                borrowed = loanRows.filter { it.loan.direction == LoanDirection.BORROWED }.sortedBy { it.settled },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObligationsUiState())

    fun payBill(id: Long, amountMinor: Long, date: LocalDate) {
        viewModelScope.launch { runCatching { obligations.payBill(id, amountMinor, date) } }
    }

    /** [onResult] corre en el hilo principal. */
    fun repay(loanId: Long, amountMinor: Long, date: LocalDate, note: String, onResult: (LoanResult) -> Unit) {
        viewModelScope.launch {
            val result = try {
                obligations.repay(loanId, amountMinor, date, note)
            } catch (e: Exception) {
                LoanResult.INVALID
            }
            onResult(result)
        }
    }

    fun deletePayment(id: Long) {
        viewModelScope.launch { runCatching { obligations.deletePayment(id) } }
    }

    companion object {
        private const val PAID_LIMIT = 5

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ObligationsViewModel(
                    container.financeRepository,
                    container.savingsRepository,
                    container.obligationsRepository,
                    container.settingsRepository,
                )
            }
        }
    }
}
