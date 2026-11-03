package com.yuzufin.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuzufin.app.AppContainer
import com.yuzufin.app.assistant.MonkiContext
import com.yuzufin.app.assistant.MonkiDataSource
import com.yuzufin.app.assistant.MonkiFinanceEngine
import com.yuzufin.app.assistant.RepositoryMonkiDataSource
import com.yuzufin.app.core.money.CurrencyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MonkiMessage(val id: Long, val text: String, val fromUser: Boolean)

data class MonkiChatState(
    val currencyCode: String = CurrencyInfo.PYG.code,
    val messages: List<MonkiMessage> = emptyList(),
    val waiting: Boolean = false,
    /** Lo que Monki recuerda para entender "¿y el mes pasado?". */
    val context: MonkiContext = MonkiContext(),
)

/**
 * Estado del chat. Vive en el ViewModel (no en la pantalla) para que no se pierda al rotar.
 * Monki solo lee los datos: recibe un [MonkiDataSource], que tiene una única función de lectura,
 * y no tiene acceso a ningún repositorio con funciones para escribir o borrar.
 */
class MonkiViewModel(
    private val data: MonkiDataSource,
) : ViewModel() {
    private val engine = MonkiFinanceEngine()
    private var nextId = 0L

    private val _state = MutableStateFlow(MonkiChatState())
    val state: StateFlow<MonkiChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val snapshot = data.load()
            // La frase vacía es un saludo: ya viene con la voz de la región de la moneda.
            val greeting = engine.reply("", snapshot, MonkiContext(), LocalDate.now(), seed = nextId.toInt())
            _state.update {
                it.copy(
                    currencyCode = snapshot.currency.code,
                    messages = listOf(MonkiMessage(nextId++, greeting.text, false)),
                )
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
            val context = _state.value.context
            var newContext = context
            val text = try {
                // El número de mensaje cambia en cada pregunta: así los chistes no se repiten seguidos.
                val reply = engine.reply(clean, data.load(), context, LocalDate.now(), seed = nextId.toInt())
                newContext = reply.context
                reply.text
            } catch (e: Exception) {
                // Sin detalles en el log: no se escribe ningún dato financiero.
                "Uy, no pude leer tus datos ahora. Probá de nuevo en un momento."
            }
            _state.update {
                it.copy(
                    messages = it.messages + MonkiMessage(nextId++, text, false),
                    waiting = false,
                    context = newContext,
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MonkiViewModel(
                RepositoryMonkiDataSource(
                    finance = container.financeRepository,
                    budgets = container.budgetRepository,
                    savings = container.savingsRepository,
                    obligations = container.obligationsRepository,
                    settings = container.settingsRepository,
                ),
            ) as T
        }
    }
}
