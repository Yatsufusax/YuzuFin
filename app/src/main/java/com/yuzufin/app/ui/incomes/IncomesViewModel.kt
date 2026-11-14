package com.yuzufin.app.ui.incomes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.IncomeRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.IncomeScheduler
import com.yuzufin.app.domain.IncomeSource
import com.yuzufin.app.domain.OccurrenceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SourceRow(val source: IncomeSource, val next: LocalDate?)

data class HistoryRow(
    val occurrenceId: Long,
    val sourceName: String,
    val dueDate: LocalDate,
    val amountMinor: Long,
    val status: OccurrenceStatus,
)

data class IncomesUiState(
    val loading: Boolean = true,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val pending: List<PendingIncome> = emptyList(),
    val upcoming: List<UpcomingIncome> = emptyList(),
    val sources: List<SourceRow> = emptyList(),
    val history: List<HistoryRow> = emptyList(),
    val remindersEnabled: Boolean = true,
) {
    val anySourceReminds: Boolean get() = sources.any { it.source.active && it.source.remind }
}

class IncomesViewModel(
    private val income: IncomeRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val refresh = MutableStateFlow(0)
    private val inFlight = HashSet<Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<IncomesUiState> = refresh.flatMapLatest { combine(
        income.sources,
        income.occurrences,
        settings.settings,
    ) { sources, occurrences, appSettings ->
        val today = LocalDate.now()
        val names = sources.associate { it.id to it.name }
        IncomesUiState(
            loading = false,
            currency = CurrencyInfo.fromCode(appSettings.currencyCode),
            pending = buildPending(sources, occurrences),
            upcoming = buildUpcoming(sources, today, occurrences),
            sources = sources.map { source ->
                val taken = occurrences.filter { it.sourceId == source.id }.map { it.dueDate }.toSet()
                SourceRow(source, if (source.active) IncomeScheduler.nextFreeDueDate(source, today, taken) else null)
            },
            history = occurrences
                .filter { it.status != OccurrenceStatus.PENDING }
                .take(HISTORY_LIMIT)
                .mapNotNull { occurrence ->
                    val name = names[occurrence.sourceId] ?: return@mapNotNull null
                    HistoryRow(occurrence.id, name, occurrence.dueDate, occurrence.amountMinor, occurrence.status)
                },
            remindersEnabled = appSettings.remindersEnabled,
        )
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomesUiState())

    /** Ignora toques repetidos sobre el mismo cobro y, al terminar, fuerza que la pantalla se actualice. */
    fun confirm(occurrenceId: Long, amountMinor: Long) {
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

    /** "Ya cobré" antes de la fecha prevista: registra ya ese cobro, con el ingreso fechado hoy. */
    fun collectEarly(sourceId: Long, dueDate: LocalDate, amountMinor: Long) {
        if (!inFlight.add(-sourceId)) return
        viewModelScope.launch {
            try {
                runCatching { income.collectEarly(sourceId, dueDate, amountMinor, LocalDate.now()) }
            } finally {
                inFlight.remove(-sourceId)
                refresh.value += 1
            }
        }
    }

    fun skip(occurrenceId: Long) {
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

    fun reopen(occurrenceId: Long) {
        viewModelScope.launch { runCatching { income.reopen(occurrenceId) } }
    }

    fun setActive(sourceId: Long, active: Boolean) {
        viewModelScope.launch { runCatching { income.setActive(sourceId, active, LocalDate.now()) } }
    }

    companion object {
        private const val HISTORY_LIMIT = 8

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { IncomesViewModel(container.incomeRepository, container.settingsRepository) }
        }
    }
}
