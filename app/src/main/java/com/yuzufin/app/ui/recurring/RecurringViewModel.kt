package com.yuzufin.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.RecurringRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.domain.RecurringExpense
import com.yuzufin.app.domain.RecurringScheduler
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecurringRow(
    val expense: RecurringExpense,
    val categoryName: String,
    val iconKey: String,
    val next: LocalDate?,
    /** Cuotas pagadas o generadas (solo gastos en cuotas). */
    val installmentsUsed: Int = 0,
    /** Cuotas que todavía se pueden adelantar. */
    val installmentsLeft: Int = 0,
    /** Gasto en cuotas que ya terminó su ciclo. */
    val finished: Boolean = false,
)

data class PendingRecurring(
    val occurrenceId: Long,
    val name: String,
    val dueDate: LocalDate,
    val amountMinor: Long,
)

data class RecurringUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val rows: List<RecurringRow> = emptyList(),
    val pending: List<PendingRecurring> = emptyList(),
    /** Categorías de gasto que se pueden elegir (sin las archivadas). */
    val categories: List<Category> = emptyList(),
    val allCategories: List<Category> = emptyList(),
)

class RecurringViewModel(
    private val recurring: RecurringRepository,
    finance: FinanceRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val refresh = MutableStateFlow(0)
    private val inFlight = HashSet<Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecurringUiState> = refresh.flatMapLatest { combine(
        recurring.expenses,
        recurring.occurrences,
        finance.categories,
        settings.settings,
    ) { expenses, occurrences, categories, appSettings ->
        val today = LocalDate.now()
        val byId = categories.associateBy { it.id }
        val names = expenses.associateBy { it.id }
        RecurringUiState(
            loading = false,
            currency = CurrencyInfo.fromCode(appSettings.currencyCode),
            rows = expenses.map { expense ->
                val category = byId[expense.categoryId]
                val mine = occurrences.filter { it.expenseId == expense.id }
                val existing = mine.map { it.dueDate }.toSet()
                val pendingCount = mine.count { it.status == OccurrenceStatus.PENDING }
                val finished = RecurringScheduler.isFinished(expense, mine.size, pendingCount)
                RecurringRow(
                    expense = expense,
                    categoryName = category?.name.orEmpty(),
                    iconKey = category?.iconKey.orEmpty(),
                    next = if (expense.active && !finished) {
                        RecurringScheduler.upcomingFree(expense, existing, mine.size, today, 1).firstOrNull()
                    } else {
                        null
                    },
                    installmentsUsed = RecurringScheduler.used(expense, mine.size),
                    installmentsLeft = if (expense.isInstallments) RecurringScheduler.remaining(expense, mine.size) else 0,
                    finished = finished,
                )
            },
            pending = occurrences
                .filter { it.status == OccurrenceStatus.PENDING && !it.dueDate.isAfter(today) }
                .mapNotNull { occurrence ->
                    val expense = names[occurrence.expenseId] ?: return@mapNotNull null
                    PendingRecurring(occurrence.id, expense.name, occurrence.dueDate, occurrence.amountMinor)
                }
                .sortedBy { it.dueDate },
            categories = categories.filter { it.type == TransactionType.EXPENSE && !it.archived },
            allCategories = categories,
        )
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun save(expense: RecurringExpense) {
        viewModelScope.launch {
            recurring.save(expense)
            recurring.reconcile(LocalDate.now())
        }
    }

    /** Ignora toques repetidos sobre el mismo pago y, al terminar, fuerza que la pantalla se actualice. */
    fun confirm(occurrenceId: Long, amountMinor: Long) {
        if (!inFlight.add(occurrenceId)) return
        viewModelScope.launch {
            try {
                runCatching { recurring.confirm(occurrenceId, amountMinor) }
            } finally {
                inFlight.remove(occurrenceId)
                refresh.value += 1
            }
        }
    }

    /** Paga por adelantado [count] cuotas de un gasto en cuotas, cada una por [amountMinor]. */
    fun advance(expenseId: Long, count: Int, amountMinor: Long) {
        if (!inFlight.add(-expenseId)) return
        viewModelScope.launch {
            try {
                runCatching { recurring.advance(expenseId, count, amountMinor, LocalDate.now()) }
            } finally {
                inFlight.remove(-expenseId)
                refresh.value += 1
            }
        }
    }

    fun skip(occurrenceId: Long) {
        if (!inFlight.add(occurrenceId)) return
        viewModelScope.launch {
            try {
                runCatching { recurring.skip(occurrenceId) }
            } finally {
                inFlight.remove(occurrenceId)
                refresh.value += 1
            }
        }
    }

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { recurring.setActive(id, active, LocalDate.now()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { recurring.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                RecurringViewModel(container.recurringRepository, container.financeRepository, container.settingsRepository)
            }
        }
    }
}
