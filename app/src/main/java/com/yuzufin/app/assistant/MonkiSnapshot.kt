package com.yuzufin.app.assistant

import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.BudgetRepository
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.Budget
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.Loan
import com.yuzufin.app.domain.LoanPayment
import com.yuzufin.app.domain.SavingsEntry
import com.yuzufin.app.domain.SavingsGoal
import kotlinx.coroutines.flow.first

/**
 * Foto de solo lectura de los datos de Monea en un momento dado. Es lo único que el motor de Monki
 * puede ver: son listas inmutables y no hay forma de escribir nada desde acá.
 */
data class MonkiSnapshot(
    val transactions: List<FinanceTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val initial: InitialBalance? = null,
    val budgets: List<Budget> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val savingsEntries: List<SavingsEntry> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val loanPayments: List<LoanPayment> = emptyList(),
    val currency: CurrencyInfo = CurrencyInfo.PYG,
)

/**
 * De dónde saca Monki los datos. Tiene un único método y solo lee: el chat no recibe ningún
 * repositorio con funciones para guardar, editar o borrar.
 */
interface MonkiDataSource {
    suspend fun load(): MonkiSnapshot
}

/** Lee los datos reales de Monea. Solo usa los flujos de lectura de los repositorios. */
class RepositoryMonkiDataSource(
    private val finance: FinanceRepository,
    private val budgets: BudgetRepository,
    private val savings: SavingsRepository,
    private val obligations: ObligationsRepository,
    private val settings: SettingsRepository,
) : MonkiDataSource {

    override suspend fun load(): MonkiSnapshot = MonkiSnapshot(
        transactions = finance.transactions.first(),
        categories = finance.categories.first(),
        initial = finance.initialBalance.first(),
        budgets = budgets.budgets.first(),
        goals = savings.goals.first(),
        savingsEntries = savings.entries.first(),
        bills = obligations.bills.first(),
        loans = obligations.loans.first(),
        loanPayments = obligations.payments.first(),
        currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode),
    )
}
