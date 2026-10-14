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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    val uiState: StateFlow<IncomesUiState> = combine(
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
            upcoming = buildUpcoming(sources, today),
            sources = sources.map { SourceRow(it, if (it.active) IncomeScheduler.nextDueDate(it, today) else null) },
            history = occurrences
                .filter { it.status != OccurrenceStatus.PENDING }
                .take(HISTORY_LIMIT)
                .mapNotNull { occurrence ->
                    val name = names[occurrence.sourceId] ?: return@mapNotNull null
                    HistoryRow(occurrence.id, name, occurrence.dueDate, occurrence.amountMinor, occurrence.status)
                },
            remindersEnabled = appSettings.remindersEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncomesUiState())

    fun confirm(occurrenceId: Long, amountMinor: Long) {
        viewModelScope.launch { runCatching { income.confirm(occurrenceId, amountMinor) } }
    }

    fun skip(occurrenceId: Long) {
        viewModelScope.launch { runCatching { income.skip(occurrenceId) } }
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
