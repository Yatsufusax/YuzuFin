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
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BillFormState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val notFound: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    val categories: List<Category> = emptyList(),
    val name: String = "",
    val amountText: String = "",
    val categoryId: Long = 0L,
    val dueDate: LocalDate = LocalDate.now().plusDays(7),
    val repeatMonthly: Boolean = false,
    val remind: Boolean = true,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    val amountMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, currency)
    val nameIsValid: Boolean get() = name.isNotBlank()
    val amountIsValid: Boolean get() = (amountMinor ?: 0L) > 0L
    val isValid: Boolean get() = nameIsValid && amountIsValid && categoryId != 0L
}

class BillFormViewModel(
    private val obligations: ObligationsRepository,
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
    private val editingId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(BillFormState(isEdit = editingId != 0L))
    val state: StateFlow<BillFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
            val categories = finance.categories.first()
                .filter { it.type == TransactionType.EXPENSE && it.iconKey != LOAN_ICON_KEY && !it.archived }
            val defaultCategory = (categories.firstOrNull { it.iconKey == "utilities" } ?: categories.firstOrNull())?.id ?: 0L
            if (editingId == 0L) {
                _state.update { it.copy(loading = false, currency = currency, categories = categories, categoryId = defaultCategory) }
            } else {
                val bill = obligations.getBill(editingId)
                if (bill == null || bill.paid) {
                    _state.update { it.copy(loading = false, notFound = true) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            currency = currency,
                            categories = categories,
                            name = bill.name,
                            amountText = AmountParser.toInputText(bill.amountMinor, currency),
                            categoryId = if (categories.any { c -> c.id == bill.categoryId }) bill.categoryId else defaultCategory,
                            dueDate = bill.dueDate,
                            repeatMonthly = bill.repeatDay > 0,
                            remind = bill.remind,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value.take(40)) }
    fun onAmountChange(value: String) = _state.update { it.copy(amountText = AmountParser.sanitize(value, it.currency)) }
    fun onCategoryChange(id: Long) = _state.update { it.copy(categoryId = id) }
    fun onDueDateChange(date: LocalDate) = _state.update { it.copy(dueDate = date) }
    fun onRepeatChange(value: Boolean) = _state.update { it.copy(repeatMonthly = value) }
    fun onRemindChange(value: Boolean) = _state.update { it.copy(remind = value) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val amount = s.amountMinor ?: return
        // Si vence el último día del mes se repite siempre el último día (31); si no, el mismo día.
        val repeatDay = when {
            !s.repeatMonthly -> 0
            s.dueDate.dayOfMonth == s.dueDate.lengthOfMonth() -> 31
            else -> s.dueDate.dayOfMonth
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                obligations.saveBill(
                    Bill(
                        id = editingId,
                        name = s.name.trim(),
                        amountMinor = amount,
                        categoryId = s.categoryId,
                        dueDate = s.dueDate,
                        repeatDay = repeatDay,
                        remind = s.remind,
                        paid = false,
                        paidDate = null,
                    ),
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
                obligations.deleteBill(editingId)
                _state.update { it.copy(finished = true) }
            } catch (e: Exception) {
                // Se queda en la pantalla; no se registra ningún dato en el log.
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, editingId: Long) = viewModelFactory {
            initializer {
                BillFormViewModel(
                    container.obligationsRepository,
                    container.financeRepository,
                    container.settingsRepository,
                    editingId,
                )
            }
        }
    }
}
