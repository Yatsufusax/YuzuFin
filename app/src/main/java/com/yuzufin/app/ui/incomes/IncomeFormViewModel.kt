package com.yuzufin.app.ui.incomes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.IncomeRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.IncomeSchedule
import com.yuzufin.app.domain.IncomeSource
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IncomeFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val categories: List<Category> = emptyList(),
    val name: String = "",
    val amountText: String = "",
    val schedule: IncomeSchedule = IncomeSchedule.MONTHLY,
    val day1Text: String = "5",
    val day2Text: String = "31",
    val customDates: List<LocalDate> = emptyList(),
    val dateToAdd: LocalDate = LocalDate.now(),
    val categoryId: Long = 0L,
    val requiresConfirmation: Boolean = true,
    val remind: Boolean = true,
    val active: Boolean = true,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val amountMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, currency)
    val nameIsValid: Boolean get() = name.isNotBlank()
    val amountIsValid: Boolean get() = (amountMinor ?: 0L) > 0L

    private fun day(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..31 }
    val day1: Int? get() = day(day1Text)
    val day2: Int? get() = day(day2Text)

    val scheduleIsValid: Boolean
        get() = when (schedule) {
            IncomeSchedule.MONTHLY -> day1 != null
            IncomeSchedule.TWICE_MONTHLY -> day1 != null && day2 != null && day1 != day2
            IncomeSchedule.CUSTOM -> customDates.isNotEmpty()
        }

    val isValid: Boolean get() = nameIsValid && amountIsValid && scheduleIsValid && categoryId != 0L
}

class IncomeFormViewModel(
    private val income: IncomeRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
    private val editingId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomeFormState(isEdit = editingId != 0L))
    val state: StateFlow<IncomeFormState> = _state.asStateFlow()

    private var original: IncomeSource? = null

    init {
        viewModelScope.launch {
            val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
            val categories = finance.categories.first().filter { it.type == TransactionType.INCOME && it.iconKey != LOAN_ICON_KEY && !it.archived }
            val defaultCategory = (categories.firstOrNull { it.iconKey == "salary" } ?: categories.firstOrNull())?.id ?: 0L

            if (editingId == 0L) {
                _state.update {
                    it.copy(loading = false, currency = currency, categories = categories, categoryId = defaultCategory)
                }
            } else {
                val source = income.getSource(editingId)
                if (source == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    original = source
                    _state.update {
                        it.copy(
                            loading = false,
                            currency = currency,
                            categories = categories,
                            name = source.name,
                            amountText = AmountParser.toInputText(source.amountMinor, currency),
                            schedule = source.schedule,
                            day1Text = source.daysOfMonth.getOrNull(0)?.toString() ?: "5",
                            day2Text = source.daysOfMonth.getOrNull(1)?.toString() ?: "31",
                            customDates = source.customDates.sorted(),
                            categoryId = if (categories.any { c -> c.id == source.categoryId }) source.categoryId else defaultCategory,
                            requiresConfirmation = source.requiresConfirmation,
                            remind = source.remind,
                            active = source.active,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(40)) }
    fun onAmountChange(value: String) = _state.update { it.copy(amountText = AmountParser.sanitize(value, it.currency)) }
    fun onScheduleChange(value: IncomeSchedule) = _state.update { it.copy(schedule = value) }
    fun onDay1Change(value: String) = _state.update { it.copy(day1Text = value.filter(Char::isDigit).take(2)) }
    fun onDay2Change(value: String) = _state.update { it.copy(day2Text = value.filter(Char::isDigit).take(2)) }
    fun onPresetTwice(first: Int, second: Int) =
        _state.update { it.copy(day1Text = first.toString(), day2Text = second.toString()) }
    fun onDateToAddChange(date: LocalDate) = _state.update { it.copy(dateToAdd = date) }
    fun onAddCustomDate() = _state.update {
        if (it.dateToAdd in it.customDates) it else it.copy(customDates = (it.customDates + it.dateToAdd).sorted())
    }
    fun onRemoveCustomDate(date: LocalDate) = _state.update { it.copy(customDates = it.customDates - date) }
    fun onCategoryChange(id: Long) = _state.update { it.copy(categoryId = id) }
    fun onRequiresConfirmationChange(value: Boolean) = _state.update { it.copy(requiresConfirmation = value) }
    fun onRemindChange(value: Boolean) = _state.update { it.copy(remind = value) }
    fun onActiveChange(value: Boolean) = _state.update { it.copy(active = value) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val amount = s.amountMinor ?: return
        val today = LocalDate.now()

        val days = when (s.schedule) {
            IncomeSchedule.MONTHLY -> listOf(s.day1 ?: return)
            IncomeSchedule.TWICE_MONTHLY -> listOf(s.day1 ?: return, s.day2 ?: return).sorted()
            IncomeSchedule.CUSTOM -> emptyList()
        }
        val dates = if (s.schedule == IncomeSchedule.CUSTOM) s.customDates.sorted() else emptyList()

        // Desde cuándo se generan cobros: hoy, para no inventar cobros del pasado. En fechas
        // sueltas se parte de la más antigua, así un cobro extraordinario ya ocurrido queda
        // pendiente de confirmar. Si no cambió el calendario al editar, se conserva la fecha.
        val previous = original
        val scheduleChanged = previous == null ||
            previous.schedule != s.schedule || previous.daysOfMonth != days || previous.customDates != dates
        // Una fuente que estaba pausada y se reactiva parte de hoy: no se generan los cobros de la pausa.
        val reactivated = previous != null && !previous.active && s.active
        val start = when {
            reactivated -> today
            !scheduleChanged -> previous!!.startDate
            dates.isNotEmpty() && dates.first().isBefore(today) -> dates.first()
            else -> today
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                income.saveSource(
                    IncomeSource(
                        id = editingId,
                        name = s.name.trim(),
                        amountMinor = amount,
                        categoryId = s.categoryId,
                        schedule = s.schedule,
                        daysOfMonth = days,
                        customDates = dates,
                        startDate = start,
                        requiresConfirmation = s.requiresConfirmation,
                        active = s.active,
                        remind = s.remind,
                    ),
                )
                // Si hoy es día de cobro, aparece enseguida.
                income.reconcile(today)
                _state.update { it.copy(finished = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        if (editingId == 0L) return
        viewModelScope.launch {
            try {
                income.deleteSource(editingId)
                _state.update { it.copy(finished = true) }
            } catch (e: Exception) {
                // Se queda en la pantalla; no se registra ningún dato en el log.
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, editingId: Long) = viewModelFactory {
            initializer {
                IncomeFormViewModel(
                    container.incomeRepository,
                    container.financeRepository,
                    container.settingsRepository,
                    editingId,
                )
            }
        }
    }
}
