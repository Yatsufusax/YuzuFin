package com.yuzufin.app.ui.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.SavingsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.SavingsGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SavingsGoalFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val name: String = "",
    val targetText: String = "",
    val hasDeadline: Boolean = false,
    val deadline: LocalDate = LocalDate.now().plusMonths(6),
    val autoEnabled: Boolean = false,
    val percentText: String = "10",
    /** Porcentaje que ya usan las demás metas; entre todas no pueden pasar de 100. */
    val otherPercent: Int = 0,
    val remind: Boolean = false,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val targetMinor: Long? get() = AmountParser.parseToMinorUnits(targetText, currency)
    val nameIsValid: Boolean get() = name.isNotBlank()
    val targetIsValid: Boolean get() = (targetMinor ?: 0L) > 0L
    val percent: Int? get() = percentText.toIntOrNull()?.takeIf { it in 1..100 }
    val percentIsValid: Boolean get() = !autoEnabled || percent != null
    val percentFitsTotal: Boolean get() = !autoEnabled || percent == null || percent!! + otherPercent <= 100
    val isValid: Boolean get() = nameIsValid && targetIsValid && percentIsValid && percentFitsTotal
}

class SavingsGoalFormViewModel(
    private val savings: SavingsRepository,
    private val settings: SettingsRepository,
    private val editingId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(SavingsGoalFormState(isEdit = editingId != 0L))
    val state: StateFlow<SavingsGoalFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
            val otherPercent = savings.goals.first().filter { it.id != editingId }.sumOf { it.autoPercent }
            if (editingId == 0L) {
                _state.update { it.copy(loading = false, currency = currency, otherPercent = otherPercent) }
            } else {
                val goal = savings.getGoal(editingId)
                if (goal == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            currency = currency,
                            otherPercent = otherPercent,
                            name = goal.name,
                            targetText = AmountParser.toInputText(goal.targetMinor, currency),
                            hasDeadline = goal.deadline != null,
                            deadline = goal.deadline ?: it.deadline,
                            autoEnabled = goal.autoPercent > 0,
                            percentText = if (goal.autoPercent > 0) goal.autoPercent.toString() else it.percentText,
                            remind = goal.remind,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(40)) }
    fun onTargetChange(value: String) = _state.update { it.copy(targetText = AmountParser.sanitize(value, it.currency)) }
    fun onHasDeadlineChange(value: Boolean) = _state.update { it.copy(hasDeadline = value) }
    fun onDeadlineChange(value: LocalDate) = _state.update { it.copy(deadline = value) }
    fun onAutoChange(value: Boolean) = _state.update { it.copy(autoEnabled = value) }
    fun onPercentChange(value: String) = _state.update { it.copy(percentText = value.filter(Char::isDigit).take(3)) }
    fun onRemindChange(value: Boolean) = _state.update { it.copy(remind = value) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val target = s.targetMinor ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                savings.saveGoal(
                    SavingsGoal(
                        id = editingId,
                        name = s.name.trim(),
                        targetMinor = target,
                        deadline = if (s.hasDeadline) s.deadline else null,
                        autoPercent = if (s.autoEnabled) s.percent ?: 0 else 0,
                        remind = s.remind,
                    ),
                    LocalDate.now(),
                )
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
                savings.deleteGoal(editingId)
                _state.update { it.copy(finished = true) }
            } catch (e: Exception) {
                // Se queda en la pantalla; no se registra ningún dato en el log.
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, editingId: Long) = viewModelFactory {
            initializer {
                SavingsGoalFormViewModel(container.savingsRepository, container.settingsRepository, editingId)
            }
        }
    }
}
