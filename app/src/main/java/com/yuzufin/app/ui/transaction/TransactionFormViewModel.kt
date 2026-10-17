package com.yuzufin.app.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransactionFormState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isEdit: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    /** Primer día permitido: la fecha del saldo inicial (antes ya está dentro del saldo). */
    val minDate: LocalDate = LocalDate.now(),
    /** Saldo sin contar el movimiento que se está editando, para avisar si quedaría negativo. */
    val baseBalance: Long = 0L,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val amountMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, currency)

    val amountIsValid: Boolean get() = (amountMinor ?: 0L) > 0L

    /** Un gasto mayor al saldo se permite (el saldo puede quedar negativo), pero se avisa. */
    val wouldOverdraw: Boolean
        get() = type == TransactionType.EXPENSE && amountIsValid && (amountMinor ?: 0L) > baseBalance
}

/** [editingId] = 0 para un movimiento nuevo. */
class TransactionFormViewModel(
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
    private val editingId: Long,
    initialType: TransactionType,
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionFormState(type = initialType))
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    private var allCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
        val initial = finance.initialBalance.first()
        val transactions = finance.transactions.first()
        // La categoría "Préstamos" es interna: la usan los préstamos, no el registro manual.
        allCategories = finance.categories.first().filter { it.iconKey != LOAN_ICON_KEY }
        val existing = if (editingId != 0L) finance.getTransaction(editingId) else null
        // Las archivadas no se ofrecen, salvo la que ya tiene el movimiento que se está editando.
        allCategories = allCategories.filter { !it.archived || it.id == existing?.categoryId }

        if (editingId != 0L && (existing == null || existing.internalTransfer)) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }

        val type = existing?.type ?: _state.value.type
        val today = LocalDate.now()
        val baseBalance = if (initial != null) {
            BalanceCalculator.availableBalance(initial, transactions.filter { it.id != editingId })
        } else {
            0L
        }

        _state.update {
            it.copy(
                loading = false,
                isEdit = existing != null,
                currency = currency,
                type = type,
                amountText = existing?.let { e -> AmountParser.toInputText(e.amountMinor, currency) }.orEmpty(),
                categories = allCategories.filter { c -> c.type == type },
                categoryId = existing?.categoryId ?: allCategories.firstOrNull { c -> c.type == type }?.id,
                description = existing?.description.orEmpty(),
                date = existing?.date ?: today,
                minDate = initial?.startDate ?: today,
                baseBalance = baseBalance,
            )
        }
    }

    fun onTypeChange(type: TransactionType) = _state.update {
        if (it.type == type) {
            it
        } else {
            val categories = allCategories.filter { c -> c.type == type }
            it.copy(type = type, categories = categories, categoryId = categories.firstOrNull()?.id)
        }
    }

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = AmountParser.sanitize(text, it.currency))
    }

    fun onCategoryChange(id: Long) = _state.update { it.copy(categoryId = id) }

    fun onDescriptionChange(text: String) = _state.update { it.copy(description = text.take(80)) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        val amount = s.amountMinor
        val categoryId = s.categoryId
        val dateOk = !s.date.isBefore(s.minDate) && !s.date.isAfter(LocalDate.now())
        if (amount == null || amount <= 0L || categoryId == null || !dateOk) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            finance.saveTransaction(
                FinanceTransaction(
                    id = editingId,
                    type = s.type,
                    amountMinor = amount,
                    categoryId = categoryId,
                    description = s.description.trim(),
                    date = s.date,
                ),
            )
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun delete() {
        if (editingId == 0L || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            finance.deleteTransaction(editingId)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    companion object {
        fun factory(container: AppContainer, editingId: Long, initialType: TransactionType) =
            viewModelFactory {
                initializer {
                    TransactionFormViewModel(
                        container.financeRepository,
                        container.settingsRepository,
                        editingId,
                        initialType,
                    )
                }
            }
    }
}
