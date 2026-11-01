package com.yuzufin.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuzufin.app.AppContainer
import com.yuzufin.app.assistant.MonkiFinanceEngine
import com.yuzufin.app.core.money.CurrencyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MonkiMessage(val id: Long, val text: String, val fromUser: Boolean)

data class MonkiChatState(
    val currencyCode: String = CurrencyInfo.PYG.code,
    val messages: List<MonkiMessage> = emptyList(),
    val waiting: Boolean = false,
)

/**
 * Estado del chat. Vive en el ViewModel (no en la pantalla) para que no se pierda al rotar.
 * Monki solo lee los datos: no tiene ninguna función que escriba o borre movimientos.
 */
class MonkiViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val engine = MonkiFinanceEngine()
    private var nextId = 0L

    private val _state = MutableStateFlow(MonkiChatState())
    val state: StateFlow<MonkiChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = snapshot()
            val greeting = engine.answer("", snapshot.transactions, snapshot.categories, snapshot.currency, LocalDate.now())
            _state.update {
                it.copy(currencyCode = snapshot.currency.code, messages = listOf(MonkiMessage(nextId++, greeting, false)))
            }
        }
    }

    fun send(question: String) {
        val clean = question.trim()
        if (clean.isEmpty() || _state.value.waiting) return
        _state.update {
            it.copy(messages = it.messages + MonkiMessage(nextId++, clean, true), waiting = true)
        }
        viewModelScope.launch {
            val reply = try {
                val s = snapshot()
                engine.answer(clean, s.transactions, s.categories, s.currency, LocalDate.now())
            } catch (e: Exception) {
                // Sin detalles en el log: no se escribe ningún dato financiero.
                "Uy, no pude leer tus datos ahora. Probá de nuevo en un momento."
            }
            _state.update {
                it.copy(messages = it.messages + MonkiMessage(nextId++, reply, false), waiting = false)
            }
        }
    }

    private data class Snapshot(
        val transactions: List<com.yuzufin.app.domain.FinanceTransaction>,
        val categories: List<com.yuzufin.app.domain.Category>,
        val currency: CurrencyInfo,
    )

    private suspend fun snapshot(): Snapshot {
        val (transactions, categories, settings) = combine(
            container.financeRepository.transactions,
            container.financeRepository.categories,
            container.settingsRepository.settings,
        ) { t, c, s -> Triple(t, c, s) }.first()
        return Snapshot(transactions, categories, CurrencyInfo.fromCode(settings.currencyCode))
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MonkiViewModel(container) as T
        }
    }
}
