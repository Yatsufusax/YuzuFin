package com.yuzufin.app.ui.transaction

import com.yuzufin.app.domain.CurrencyConversion
import java.math.BigDecimal
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import android.net.Uri
import com.yuzufin.app.data.AttachmentRepository
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.ObligationsRepository
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.MAX_ATTACHMENTS
import com.yuzufin.app.export.VoucherBuilder
import java.io.File
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.domain.VoiceParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Foto del formulario. [attachmentId] = 0 si todavía no se guardó con el movimiento. */
data class PhotoItem(val attachmentId: Long, val fileName: String)

/** Gasto o ingreso que el usuario repite seguido: se ofrece para completar el formulario de un toque. */
data class FrequentItem(
    val description: String,
    val amountMinor: Long,
    val categoryId: Long,
)

data class TransactionFormState(
    /** El importe, la categoría o la descripción se completaron con lo dicho por voz. */
    val fromVoice: Boolean = false,
    val frequent: List<FrequentItem> = emptyList(),
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isEdit: Boolean = false,
    val currency: CurrencyInfo = CurrencyInfo.PYG,
    /** Moneda en la que se anota el movimiento (null = la principal). Solo en movimientos nuevos. */
    val entryCurrency: CurrencyInfo? = null,
    /** Tipo de cambio escrito: cuántas unidades de la moneda principal vale 1 de la otra. */
    val rateText: String = "",
    /** Último tipo de cambio usado con cada moneda (código → texto), para proponerlo. */
    val knownRates: Map<String, String> = emptyMap(),
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
    val photos: List<PhotoItem> = emptyList(),
    /** Hay fotos agregadas o quitadas que todavía no se guardaron. */
    val photosDirty: Boolean = false,
    val photoBusy: Boolean = false,
    val photoError: Boolean = false,
    val voucherBusy: Boolean = false,
    /** PDF recién generado, listo para compartir; la pantalla lo consume con [TransactionFormViewModel.voucherShared]. */
    val voucherFile: File? = null,
    val voucherError: Boolean = false,
    /** Factura pendiente que el gasto que se está guardando parece pagar: se le pregunta a la persona. */
    val billMatch: Bill? = null,
) {
    val photoLimitReached: Boolean get() = photos.size >= MAX_ATTACHMENTS

    /** Moneda en la que se está escribiendo el importe. */
    val inputCurrency: CurrencyInfo get() = entryCurrency ?: currency

    /** Lo escrito, en unidades mínimas de [inputCurrency]. */
    val enteredMinor: Long? get() = AmountParser.parseToMinorUnits(amountText, inputCurrency)

    val rate: BigDecimal? get() = CurrencyConversion.parseEnteredRate(rateText)

    /** Importe en la moneda principal (es lo que cuenta para el saldo y las estadísticas). */
    val amountMinor: Long?
        get() {
            val other = entryCurrency ?: return enteredMinor
            val entered = enteredMinor ?: return null
            val r = rate ?: return null
            return CurrencyConversion.convertOrNull(entered, CurrencyConversion.factor(other, currency, r))
        }

    val amountIsValid: Boolean get() = (amountMinor ?: 0L) > 0L

    /** Un gasto mayor al saldo se permite (el saldo puede quedar negativo), pero se avisa. */
    val wouldOverdraw: Boolean
        get() = type == TransactionType.EXPENSE && amountIsValid && (amountMinor ?: 0L) > baseBalance
}

private const val MAX_RATE_LENGTH = 14

/** [editingId] = 0 para un movimiento nuevo. */
class TransactionFormViewModel(
    private val finance: FinanceRepository,
    private val settings: SettingsRepository,
    val editingId: Long,
    initialType: TransactionType,
    private val attachments: AttachmentRepository,
    private val voucherBuilder: VoucherBuilder,
    private val obligations: ObligationsRepository,
    /** Frase dicha por voz para prellenar el movimiento nuevo (vacía si no hay). */
    private val say: String = "",
    /** Se llama después de guardar: Monki revisa si hay algo que avisar. */
    private val onSaved: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionFormState(type = initialType))
    val state: StateFlow<TransactionFormState> = _state.asStateFlow()

    private var allCategories: List<Category> = emptyList()
    private var history: List<FinanceTransaction> = emptyList()
    private var existingTx: FinanceTransaction? = null
    private val removedPhotoIds = mutableListOf<Long>()
    private var saved = false

    /** Ya se le preguntó si el gasto paga una factura (o no había nada que preguntar). */
    private var billChecked = false

    /** Factura que la persona confirmó que este gasto paga. */
    private var billToSettle: Bill? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val currency = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
        val initial = finance.initialBalance.first()
        val transactions = finance.transactions.first()
        history = transactions.filter { !it.internalTransfer }
        val knownRates = transactions
            .filter { it.originalCurrency != null && it.exchangeRate != null }
            .groupBy { it.originalCurrency.orEmpty() }
            .mapValues { (_, list) -> list.maxWith(compareBy<FinanceTransaction>({ it.date }, { it.id })).exchangeRate.orEmpty().replace('.', ',') }
        // La categoría "Préstamos" es interna: la usan los préstamos, no el registro manual.
        allCategories = finance.categories.first().filter { it.iconKey != LOAN_ICON_KEY }
        val existing = if (editingId != 0L) finance.getTransaction(editingId) else null
        existingTx = existing
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

        val photos = if (existing != null) {
            attachments.list(existing.id).map { PhotoItem(it.id, it.fileName) }
        } else {
            emptyList()
        }

        _state.update {
            it.copy(
                loading = false,
                isEdit = existing != null,
                currency = currency,
                knownRates = knownRates,
                type = type,
                amountText = existing?.let { e -> AmountParser.toInputText(e.amountMinor, currency) }.orEmpty(),
                categories = categoriesFor(type),
                categoryId = existing?.categoryId ?: categoriesFor(type).firstOrNull()?.id,
                frequent = if (existing == null) frequentFor(type) else emptyList(),
                description = existing?.description.orEmpty(),
                date = existing?.date ?: today,
                minDate = initial?.startDate ?: today,
                baseBalance = baseBalance,
                photos = photos,
            )
        }
        if (existing == null && say.isNotBlank()) applySpoken(currency)
    }

    /** Prellena importe, categoría y descripción con lo que se entendió de la voz; la persona lo revisa. */
    private fun applySpoken(currency: CurrencyInfo) {
        val spoken = VoiceParser.parse(say, allCategories)
        val minor = spoken.amount?.let { whole ->
            var factor = 1L
            repeat(currency.fractionDigits) { factor *= 10L }
            // Un importe absurdo (desborde de Long) se descarta antes que guardar algo mal.
            if (whole > Long.MAX_VALUE / factor / 10) null else whole * factor
        }
        _state.update {
            it.copy(
                fromVoice = true,
                amountText = minor?.let { m -> AmountParser.toInputText(m, currency) } ?: it.amountText,
                categoryId = spoken.categoryId ?: it.categoryId,
                description = spoken.description.ifBlank { it.description },
            )
        }
    }

    fun onTypeChange(type: TransactionType) = _state.update {
        if (it.type == type) {
            it
        } else {
            val categories = categoriesFor(type)
            it.copy(
                type = type,
                categories = categories,
                categoryId = categories.firstOrNull()?.id,
                frequent = if (it.isEdit) emptyList() else frequentFor(type),
            )
        }
    }

    /** Categorías del tipo, las más usadas primero (el orden original desempata). */
    private fun categoriesFor(type: TransactionType): List<Category> {
        val usage = history.groupingBy { it.categoryId }.eachCount()
        return allCategories.filter { it.type == type }.sortedByDescending { usage[it.id] ?: 0 }
    }

    /** Hasta 4 movimientos con descripción que se repiten (mismo texto, importe y categoría) en los últimos 120 días. */
    private fun frequentFor(type: TransactionType): List<FrequentItem> {
        val since = LocalDate.now().minusDays(120)
        val valid = allCategories.filter { it.type == type && !it.archived }.map { it.id }.toSet()
        return history
            .filter { it.type == type && it.description.isNotBlank() && !it.date.isBefore(since) && it.categoryId in valid }
            .groupBy { Triple(it.description.trim().lowercase(), it.amountMinor, it.categoryId) }
            .filterValues { it.size >= 2 }
            .values
            .sortedByDescending { it.size }
            .take(4)
            .map { group ->
                val latest = group.maxByOrNull { it.date } ?: group.first()
                FrequentItem(latest.description.trim(), latest.amountMinor, latest.categoryId)
            }
    }

    /** Completa importe, categoría y descripción con un gasto frecuente. */
    fun onFrequentPick(item: FrequentItem) = _state.update {
        it.copy(
            entryCurrency = null,
            rateText = "",
            amountText = AmountParser.toInputText(item.amountMinor, it.currency),
            categoryId = item.categoryId,
            description = item.description.take(80),
        )
    }

    /** Suma un monto (en unidades mínimas) al importe que se está tipeando. */
    fun onQuickAdd(stepMinor: Long) = _state.update {
        val total = (it.enteredMinor ?: 0L) + stepMinor
        it.copy(amountText = AmountParser.sanitize(AmountParser.toInputText(total, it.inputCurrency), it.inputCurrency))
    }

    /** Tecla del teclado propio: dígito, "000", coma, o [KEY_BACKSPACE] / [KEY_CLEAR]. */
    fun onKey(key: String) = _state.update {
        val current = it.amountText
        val next = when (key) {
            KEY_BACKSPACE -> current.dropLast(1)
            KEY_CLEAR -> ""
            "," -> when {
                it.inputCurrency.fractionDigits == 0 || current.contains(',') -> current
                current.isEmpty() -> "0,"
                else -> "$current,"
            }
            "000" -> if (current.isEmpty() || current == "0" || current.endsWith(",")) current else current + key
            else -> if (current == "0") key else current + key
        }
        it.copy(amountText = AmountParser.sanitize(next, it.inputCurrency))
    }

    fun onAmountChange(text: String) = _state.update {
        it.copy(amountText = AmountParser.sanitize(text, it.inputCurrency))
    }

    /** Elige la moneda en la que se anota. Si es la principal, se vuelve al modo de siempre. */
    fun onEntryCurrencyPick(code: String) = _state.update {
        val picked = CurrencyInfo.fromCode(code)
        if (picked.code == it.currency.code) {
            it.copy(entryCurrency = null, rateText = "", amountText = AmountParser.sanitize(it.amountText, it.currency))
        } else {
            val sameAsBefore = it.entryCurrency?.code == picked.code
            it.copy(
                entryCurrency = picked,
                rateText = if (sameAsBefore) it.rateText else it.knownRates[picked.code].orEmpty(),
                amountText = AmountParser.sanitize(it.amountText, picked),
            )
        }
    }

    /** Tipo de cambio escrito a mano: dígitos, puntos de miles y una coma decimal. */
    fun onRateChange(text: String) = _state.update {
        val allowed = text.filter { c -> c in '0'..'9' || c == ',' || c == '.' }
        val firstComma = allowed.indexOf(',')
        val clean = if (firstComma < 0) allowed else allowed.substring(0, firstComma + 1) + allowed.substring(firstComma + 1).replace(",", "")
        it.copy(rateText = clean.take(MAX_RATE_LENGTH))
    }

    fun onCategoryChange(id: Long) = _state.update { it.copy(categoryId = id) }

    fun onDescriptionChange(text: String) = _state.update { it.copy(description = text.take(80)) }

    fun onDateChange(date: LocalDate) = _state.update { it.copy(date = date) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading || s.billMatch != null) return
        val amount = s.amountMinor
        val categoryId = s.categoryId
        val dateOk = !s.date.isBefore(s.minDate) && !s.date.isAfter(LocalDate.now())
        if (amount == null || amount <= 0L || categoryId == null || !dateOk) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        // Lo anotado en otra moneda. Al editar, se conserva solo si el importe no cambió.
        val original: Triple<String?, Long?, String?>? = when {
            s.entryCurrency != null ->
                Triple(s.entryCurrency.code, s.enteredMinor, s.rate?.stripTrailingZeros()?.toPlainString())
            editingId != 0L && existingTx?.originalCurrency != null && existingTx?.amountMinor == amount ->
                Triple(existingTx?.originalCurrency, existingTx?.originalAmountMinor, existingTx?.exchangeRate)
            else -> null
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Gasto nuevo: si parece pagar una factura pendiente (aunque venza más adelante), se pregunta.
            if (editingId == 0L && s.type == TransactionType.EXPENSE && !billChecked) {
                val match = runCatching {
                    obligations.findBillPaidBy(categoryId, amount, s.description, s.date)
                }.getOrNull()
                if (match != null) {
                    _state.update { it.copy(saving = false, billMatch = match) }
                    return@launch
                }
            }
            // ¿Es el primer gasto que anota? (para el festejo del monito)
            val isFirstExpense = editingId == 0L && s.type == TransactionType.EXPENSE &&
                runCatching { finance.transactions.first().none { it.type == TransactionType.EXPENSE } }
                    .getOrDefault(false)
            val id = finance.saveTransaction(
                FinanceTransaction(
                    id = editingId,
                    type = s.type,
                    amountMinor = amount,
                    categoryId = categoryId,
                    description = s.description.trim(),
                    date = s.date,
                    originalCurrency = original?.first,
                    originalAmountMinor = original?.second,
                    exchangeRate = original?.third,
                ),
            )
            runCatching { onSaved() }
            billToSettle?.let { bill ->
                // El gasto ya existe: la factura se marca pagada con él y se arma la del mes siguiente.
                runCatching { obligations.settleBillWithTransaction(bill.id, id, s.date) }
            }
            attachments.apply(
                transactionId = id,
                addNames = _state.value.photos.filter { it.attachmentId == 0L }.map { it.fileName },
                removeIds = removedPhotoIds.toList(),
            )
            saved = true
            if (isFirstExpense) runCatching { settings.markFirstExpense() }
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    /** Respuesta a “¿este gasto paga la factura X?”: sí la marca pagada, no la deja como estaba. */
    fun answerBillMatch(paysBill: Boolean) {
        val bill = _state.value.billMatch ?: return
        billChecked = true
        billToSettle = if (paysBill) bill else null
        _state.update { it.copy(billMatch = null) }
        save()
    }

    /** Cerró el aviso sin responder: no se guarda nada y puede seguir editando. */
    fun dismissBillMatch() = _state.update { it.copy(billMatch = null) }

    fun addPhoto(uri: Uri) {
        if (_state.value.photoLimitReached || _state.value.photoBusy) return
        _state.update { it.copy(photoBusy = true, photoError = false) }
        viewModelScope.launch {
            val name = attachments.files.importFrom(uri)
            _state.update {
                if (name == null) {
                    it.copy(photoBusy = false, photoError = true)
                } else {
                    it.copy(photoBusy = false, photos = it.photos + PhotoItem(0L, name), photosDirty = true)
                }
            }
        }
    }

    /** Foto recién sacada con la cámara (archivo temporal): se importa y el temporal se borra. */
    fun addPhotoFromFile(file: File) {
        if (_state.value.photoLimitReached || _state.value.photoBusy) {
            file.delete()
            return
        }
        _state.update { it.copy(photoBusy = true, photoError = false) }
        viewModelScope.launch {
            val name = attachments.files.importFrom(Uri.fromFile(file))
            file.delete()
            _state.update {
                if (name == null) {
                    it.copy(photoBusy = false, photoError = true)
                } else {
                    it.copy(photoBusy = false, photos = it.photos + PhotoItem(0L, name), photosDirty = true)
                }
            }
        }
    }

    fun reportPhotoError() = _state.update { it.copy(photoError = true) }

    fun dismissPhotoError() = _state.update { it.copy(photoError = false, voucherError = false) }

    fun removePhoto(item: PhotoItem) {
        if (item.attachmentId == 0L) {
            attachments.files.delete(listOf(item.fileName))
        } else {
            removedPhotoIds += item.attachmentId
        }
        _state.update { it.copy(photos = it.photos - item, photosDirty = true) }
    }

    /** Genera el comprobante del movimiento ya guardado (no incluye cambios sin guardar). */
    fun generateVoucher() {
        val s = _state.value
        if (editingId == 0L || s.voucherBusy || s.photosDirty) return
        _state.update { it.copy(voucherBusy = true, voucherError = false) }
        viewModelScope.launch {
            val file = try {
                voucherBuilder.build(editingId)
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(voucherBusy = false, voucherFile = file, voucherError = file == null) }
        }
    }

    fun voucherShared() = _state.update { it.copy(voucherFile = null) }

    override fun onCleared() {
        // Si se cerró sin guardar, las fotos nuevas que se habían importado no quedan huérfanas.
        if (!saved) {
            attachments.files.delete(_state.value.photos.filter { it.attachmentId == 0L }.map { it.fileName })
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
        const val KEY_BACKSPACE = "back"
        const val KEY_CLEAR = "clear"

        fun factory(container: AppContainer, editingId: Long, initialType: TransactionType, say: String = "") =
            viewModelFactory {
                initializer {
                    TransactionFormViewModel(
                        container.financeRepository,
                        container.settingsRepository,
                        editingId,
                        initialType,
                        container.attachmentRepository,
                        container.voucherBuilder,
                        container.obligationsRepository,
                        say,
                        onSaved = { container.checkMonkiNow() },
                    )
                }
            }
    }
}
