package com.yuzufin.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class StartMode { FROM_TODAY, WITH_HISTORY }

data class OnboardingState(
    /** 0 = moneda, 1 = punto de partida. */
    val step: Int = 0,
    val userName: String = "",
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val mode: StartMode = StartMode.FROM_TODAY,
    val amountText: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
) {
    /** El saldo puede ser 0, pero hay que escribirlo (campo vacío = falta completar). */
    val amountMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, currency)
}

class OnboardingViewModel(
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun onCurrencyChange(currency: CurrencyInfo) = _state.update {
        // Al cambiar de moneda el texto del importe puede dejar de ser válido: se limpia.
        it.copy(currency = currency, amountText = "")
    }

    fun onNameChange(name: String) = _state.update { it.copy(userName = name.take(SettingsRepository.MAX_NAME_LENGTH)) }

    fun onModeChange(mode: StartMode) = _state.update {
        it.copy(mode = mode, startDate = if (mode == StartMode.FROM_TODAY) LocalDate.now() else it.startDate)
    }

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = AmountParser.sanitize(text, it.currency))
    }

    fun onStartDateChange(date: LocalDate) = _state.update { it.copy(startDate = date) }

    fun next() = _state.update { if (it.step == 0) it.copy(step = 1) else it }

    fun back() = _state.update { if (it.step > 0) it.copy(step = it.step - 1, showErrors = false) else it }

    /** Guarda todo. Al terminar, la app detecta el cambio y muestra la pantalla principal. */
    fun finish() {
        val s = _state.value
        if (s.saving) return
        val amount = s.amountMinor
        if (amount == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val startDate = if (s.mode == StartMode.FROM_TODAY) LocalDate.now() else s.startDate
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                finance.completeSetup(amountMinor = amount, startDate = startDate)
                settings.completeOnboarding(s.currency, s.userName)
            } catch (e: Exception) {
                // Sin detalles en el log: no se escribe ningún dato financiero.
                _state.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OnboardingViewModel(container.financeRepository, container.settingsRepository) }
        }
    }
}
