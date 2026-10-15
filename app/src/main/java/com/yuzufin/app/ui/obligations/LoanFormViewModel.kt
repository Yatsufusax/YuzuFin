package com.yuzufin.app.ui.obligations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.Loan
import com.yuzufin.app.domain.LoanDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LoanFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val direction: LoanDirection = LoanDirection.LENT,
    val person: String = "",
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val minDate: LocalDate = LocalDate.now().minusYears(5),
    val note: String = "",
    val remind: Boolean = true,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val amountMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, currency)
    val personIsValid: Boolean get() = person.isNotBlank()
    val amountIsValid: Boolean get() = (amountMinor ?: 0L) > 0L
    val isValid: Boolean get() = personIsValid && (isEdit || amountIsValid)
}

class LoanFormViewModel(
    private val obligations: ObligationsRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
    private val editingId: Long,
    initialDirection: LoanDirection,
) : ViewModel() {

    private val _state = MutableStateFlow(LoanFormState(isEdit = editingId != 0L, direction = initialDirection))
    val state: StateFlow<LoanFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
            // Antes del saldo inicial un movimiento no se cuenta, así que no se permite elegirlo.
            val minDate = finance.initialBalance.first()?.startDate ?: LocalDate.now().minusYears(5)
            if (editingId == 0L) {
                _state.update { it.copy(loading = false, currency = currency, minDate = minDate) }
            } else {
                val loan = obligations.getLoan(editingId)
                if (loan == null) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            currency = currency,
                            minDate = minDate,
                            direction = loan.direction,
                            person = loan.person,
                            amountText = AmountParser.toInputText(loan.principalMinor, currency),
                            date = loan.startDate,
                            note = loan.note,
                            remind = loan.remind,
                        )
                    }
                }
            }
        }
    }

    fun onDirectionChange(value: LoanDirection) = _state.update { it.copy(direction = value) }
    fun onPersonChange(value: String) = _state.update { it.copy(person = value.take(40)) }
    fun onAmountChange(value: String) = _state.update { it.copy(amountText = AmountParser.sanitize(value, it.currency)) }
    fun onDateChange(value: LocalDate) = _state.update { it.copy(date = value) }
    fun onNoteChange(value: String) = _state.update { it.copy(note = value.take(60)) }
    fun onRemindChange(value: Boolean) = _state.update { it.copy(remind = value) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (editingId == 0L) {
                    obligations.createLoan(
                        Loan(
                            id = 0,
                            person = s.person,
                            direction = s.direction,
                            principalMinor = s.amountMinor ?: return@launch,
                            startDate = s.date,
                            note = s.note,
                            remind = s.remind,
                        ),
                        LocalDate.now(),
                    )
                } else {
                    obligations.updateLoan(editingId, s.person, s.note, s.remind)
                }
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
                obligations.deleteLoan(editingId)
                _state.update { it.copy(finished = true) }
            } catch (e: Exception) {
                // Se queda en la pantalla; no se registra ningún dato en el log.
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, editingId: Long, direction: LoanDirection) = viewModelFactory {
            initializer {
                LoanFormViewModel(
                    container.obligationsRepository,
                    container.financeRepository,
                    container.settingsRepository,
                    editingId,
                    direction,
                )
            }
        }
    }
}
