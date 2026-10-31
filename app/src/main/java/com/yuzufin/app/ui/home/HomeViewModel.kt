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
import com.yuzufin.app.data.RecurringRepository
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.BillStatus
import com.yuzufin.app.domain.ObligationMath
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.buildMovementItems
import com.yuzufin.app.ui.incomes.PendingIncome
import com.yuzufin.app.ui.incomes.UpcomingIncome
import com.yuzufin.app.ui.incomes.buildPending
import com.yuzufin.app.ui.incomes.buildUpcoming
import com.yuzufin.app.ui.recurring.PendingRecurring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    /**
     * Lo que conviene resolver ya, en este orden: cobros por confirmar, facturas vencidas o por
     * vencer (hasta 3 días) y gastos fijos que ya tocan. Inicio muestra el primero y el resto
     * detrás de “Ver N más”.
     */
    val attention: List<AttentionItem> = emptyList(),
)

data class UpcomingBill(val bill: Bill, val status: BillStatus)

/** Algo que requiere una acción de la persona. */
sealed interface AttentionItem {
    /** Cobro que ya llegó y espera “Ya cobré”. */
    data class IncomeToConfirm(val income: PendingIncome) : AttentionItem

    /** Factura vencida o que vence en pocos días; [daysLeft] es negativo si ya venció. */
    data class BillDue(val bill: Bill, val status: BillStatus, val daysLeft: Int) : AttentionItem

    /** Gasto fijo cuyo pago ya tocó y sigue sin confirmarse. */
    data class RecurringDue(val recurring: PendingRecurring) : AttentionItem
}

/** Días de anticipación con los que una factura pasa a “requiere atención”. */
private const val BILL_ATTENTION_DAYS = 3

private data class HomeExtras(
    val sources: List<com.yuzufin.app.domain.IncomeSource>,
    val occurrences: List<com.yuzufin.app.domain.IncomeOccurrence>,
    val savingEntries: List<com.yuzufin.app.domain.SavingsEntry>,
    val bills: List<Bill>,
    val recurringDue: List<PendingRecurring>,
)

class HomeViewModel(
    finance: FinanceRepository,
    private val income: IncomeRepository,
    savings: SavingsRepository,
    obligations: ObligationsRepository,
    settings: SettingsRepository,
    recurring: RecurringRepository,
) : ViewModel() {

    /** Cada vez que sube, se vuelven a consultar los datos (por si alguna emisión no llegó). */
    private val refresh = MutableStateFlow(0)
    private val inFlight = HashSet<Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = refresh.flatMapLatest { combine(
        finance.initialBalance,
        finance.transactions,
        finance.categories,
        settings.settings,
        combine(
            income.sources,
            income.occurrences,
            savings.entries,
            obligations.bills,
            // Gastos fijos que ya tocan y no se confirmaron (igual que en la pantalla Gastos fijos).
            combine(recurring.expenses, recurring.occurrences) { expenses, occurrences ->
                val names = expenses.associateBy { it.id }
                val today = LocalDate.now()
                occurrences
                    .filter { it.status == OccurrenceStatus.PENDING && !it.dueDate.isAfter(today) }
                    .mapNotNull { occurrence ->
                        val expense = names[occurrence.expenseId] ?: return@mapNotNull null
                        PendingRecurring(occurrence.id, expense.name, occurrence.dueDate, occurrence.amountMinor)
                    }
                    .sortedBy { it.dueDate }
            },
        ) { sources, occurrences, entries, bills, recurringDue ->
            HomeExtras(sources, occurrences, entries, bills, recurringDue)
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
            val pendingIncomes = buildPending(sources, occurrences)
            val dueBills = incomeData.bills
                .filter { !it.paid }
                .sortedBy { it.dueDate }
                .map { bill ->
                    val days = (bill.dueDate.toEpochDay() - today.toEpochDay()).toInt()
                    AttentionItem.BillDue(bill, ObligationMath.status(bill, today), days)
                }
                .filter { it.daysLeft <= BILL_ATTENTION_DAYS }
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
                pendingIncomes = pendingIncomes,
                nextIncome = buildUpcoming(sources, today).firstOrNull(),
                upcomingBills = incomeData.bills
                    .filter { !it.paid }
                    .sortedBy { it.dueDate }
                    .take(3)
                    .map { UpcomingBill(it, ObligationMath.status(it, today)) },
                attention = buildList<AttentionItem> {
                    pendingIncomes.forEach { add(AttentionItem.IncomeToConfirm(it)) }
                    addAll(dueBills)
                    incomeData.recurringDue.forEach { add(AttentionItem.RecurringDue(it)) }
                },
            )
        }
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Ignora toques repetidos sobre el mismo cobro y, al terminar, fuerza que Inicio se actualice. */
    fun confirmIncome(occurrenceId: Long, amountMinor: Long) {
        if (!inFlight.add(occurrenceId)) return
        viewModelScope.launch {
            try {
                runCatching { income.confirm(occurrenceId, amountMinor) }
            } finally {
                inFlight.remove(occurrenceId)
                refresh.value += 1
            }
        }
    }

    fun skipIncome(occurrenceId: Long) {
        if (!inFlight.add(occurrenceId)) return
        viewModelScope.launch {
            try {
                runCatching { income.skip(occurrenceId) }
            } finally {
                inFlight.remove(occurrenceId)
                refresh.value += 1
            }
        }
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
                    container.recurringRepository,
                )
            }
        }
    }
}
