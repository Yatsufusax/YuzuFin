package com.yuzufin.app.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.data.AttachmentFileStore
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.dayLabel
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.Mascot
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.mascotForCurrency
import java.time.LocalDate

private const val GRID_COLUMNS = 4
private const val SHOWN_CATEGORIES = 5
private val CELL_WIDTH = 68.dp

/**
 * Pantalla de anotar rápido (movimientos nuevos): importe en una tarjeta con el monito y teclado
 * propio, las categorías más usadas en una fila (con “Todas” que abre la grilla completa), la fecha
 * en un solo chip y el resto (nota, fotos) plegado en “Más detalles”.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.QuickEntryBody(
    state: TransactionFormState,
    viewModel: TransactionFormViewModel,
    files: AttachmentFileStore,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showAllCategories by rememberSaveable { mutableStateOf(false) }
    var dateMenu by remember { mutableStateOf(false) }
    val currency = state.currency
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val feedback = LocalHapticFeedback.current
    val onKey: (String) -> Unit = { key ->
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.onKey(key)
    }

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SegmentedButton(
            selected = state.type == TransactionType.EXPENSE,
            onClick = { viewModel.onTypeChange(TransactionType.EXPENSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.filter_expense)) },
        )
        SegmentedButton(
            selected = state.type == TransactionType.INCOME,
            onClick = { viewModel.onTypeChange(TransactionType.INCOME) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.filter_income)) },
        )
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.fromVoice) VoiceFilledNotice()

        AmountCard(state)

        // Sumas rápidas: 10.000 / 50.000 / 100.000 (o 5 / 10 / 50 en monedas con decimales).
        // Se ocultan apenas empieza a tipear, para despejar la pantalla.
        if (state.amountText.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                quickSteps(currency).forEach { step ->
                    AssistChip(
                        onClick = { viewModel.onQuickAdd(step) },
                        label = { Text("+ ${MoneyFormatter.formatNumber(step, currency)}") },
                    )
                }
            }
        }

        if (state.frequent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.quick_frequent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.frequent.forEach { item ->
                        AssistChip(
                            onClick = { viewModel.onFrequentPick(item) },
                            label = { Text("${item.description} · ${MoneyFormatter.formatNumber(item.amountMinor, currency)}") },
                        )
                    }
                }
            }
        }

        // Las 5 más usadas (la elegida siempre se ve) y “Todas” para el resto.
        val picked = state.categories.firstOrNull { it.id == state.categoryId }
        val shown = state.categories.take(SHOWN_CATEGORIES).let { top ->
            if (picked != null && picked !in top) listOf(picked) + top.take(SHOWN_CATEGORIES - 1) else top
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shown.forEach { category ->
                CategoryCell(
                    category = category,
                    selected = state.categoryId == category.id,
                    onClick = { viewModel.onCategoryChange(category.id) },
                    modifier = Modifier.width(CELL_WIDTH),
                )
            }
            if (state.categories.size > shown.size) {
                AllCategoriesCell(onClick = { showAllCategories = true }, modifier = Modifier.width(CELL_WIDTH))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { dateMenu = true },
                    label = { Text(dayLabel(state.date)) },
                    trailingIcon = {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
                DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.date_today)) },
                        onClick = {
                            viewModel.onDateChange(today)
                            dateMenu = false
                        },
                    )
                    if (!yesterday.isBefore(state.minDate)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.date_yesterday)) },
                            onClick = {
                                viewModel.onDateChange(yesterday)
                                dateMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_other_date)) },
                        onClick = {
                            dateMenu = false
                            expanded = true
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.quick_less else R.string.quick_more))
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded) {
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.form_description)) },
                placeholder = { Text(stringResource(R.string.form_description_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = stringResource(R.string.form_date),
                date = state.date,
                minDate = state.minDate,
                maxDate = today,
                onDateChange = viewModel::onDateChange,
                isError = state.showErrors && (state.date.isBefore(state.minDate) || state.date.isAfter(today)),
            )
            AttachmentsSection(
                state = state,
                files = files,
                onTakePhoto = onTakePhoto,
                onPickPhoto = onPickPhoto,
                onRemove = viewModel::removePhoto,
                onVoucher = viewModel::generateVoucher,
            )
        }
    }

    Keypad(currency = currency, onKey = onKey)

    val amountLabel = state.amountMinor?.takeIf { it > 0L }?.let { MoneyFormatter.format(it, currency) }
    Button(
        onClick = viewModel::save,
        enabled = !state.saving,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(52.dp),
    ) {
        Text(
            text = if (amountLabel != null) {
                stringResource(R.string.quick_save_amount, amountLabel)
            } else {
                stringResource(R.string.action_save)
            },
            fontWeight = FontWeight.Bold,
        )
    }

    if (showAllCategories) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAllCategories = false }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.quick_pick_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                state.categories.chunked(GRID_COLUMNS).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { category ->
                            CategoryCell(
                                category = category,
                                selected = state.categoryId == category.id,
                                onClick = {
                                    viewModel.onCategoryChange(category.id)
                                    showAllCategories = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(GRID_COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta del importe: categoría y día arriba, el monto grande, la nota y el monito a un costado.
 * El aviso de error o de saldo va debajo, fuera de la tarjeta, para que se lea bien.
 */
@Composable
private fun AmountCard(state: TransactionFormState) {
    val currency = state.currency
    val amount = state.amountMinor
    val display = if (currency.fractionDigits == 0) {
        MoneyFormatter.format(amount ?: 0L, currency)
    } else {
        "${currency.symbol} ${state.amountText.ifEmpty { "0" }}"
    }
    val size = when {
        display.length <= 12 -> 40.sp
        display.length <= 16 -> 34.sp
        else -> 26.sp
    }
    val categoryName = state.categories.firstOrNull { it.id == state.categoryId }?.name
    val onCard = MaterialTheme.colorScheme.onPrimary
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(categoryName, dayLabel(state.date)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = display,
                    fontSize = size,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = if (state.amountText.isEmpty()) onCard.copy(alpha = 0.6f) else onCard,
                )
                if (state.description.isNotBlank()) {
                    Text(
                        text = "“${state.description}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = onCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Mascot(mascotForCurrency(currency.code), size = 76.dp)
        }
        when {
            state.showErrors && !state.amountIsValid -> Text(
                text = stringResource(R.string.form_amount_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            state.wouldOverdraw -> Text(
                text = stringResource(
                    R.string.form_overdraw_warning,
                    MoneyFormatter.format(state.baseBalance, currency),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

/** Montos de las sumas rápidas, en unidades mínimas. */
private fun quickSteps(currency: CurrencyInfo): List<Long> {
    var factor = 1L
    repeat(currency.fractionDigits) { factor *= 10L }
    return if (currency.fractionDigits == 0) {
        listOf(10_000L, 50_000L, 100_000L)
    } else {
        listOf(5L * factor, 10L * factor, 50L * factor)
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = categoryIcon(category.iconKey),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Keypad(currency: CurrencyInfo, onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (currency.fractionDigits == 0) "000" else ",", "0", TransactionFormViewModel.KEY_BACKSPACE),
    )
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key -> KeyButton(key = key, onKey = onKey, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Tecla del teclado. La de borrar vacía todo el importe si se mantiene apretada. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(key: String, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val isBackspace = key == TransactionFormViewModel.KEY_BACKSPACE
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = { onKey(key) },
                onLongClick = if (isBackspace) ({ onKey(TransactionFormViewModel.KEY_CLEAR) }) else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isBackspace) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Backspace,
                contentDescription = stringResource(R.string.quick_key_backspace),
            )
        } else {
            Text(
                text = key,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Cartelito que avisa que el movimiento se completó con la voz y que conviene revisarlo. */
@Composable
private fun VoiceFilledNotice() {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.voice_filled_notice),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/** Última celda de la fila de categorías: abre la grilla completa. */
@Composable
private fun AllCategoriesCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.quick_all),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Confirmación corta (el monito y “¡Anotado!”) mientras se cierra la pantalla de un movimiento nuevo. */
@Composable
fun ColumnScope.QuickDoneBody(state: TransactionFormState) {
    val categoryName = state.categories.firstOrNull { it.id == state.categoryId }?.name
    val amount = state.amountMinor?.let { MoneyFormatter.format(it, state.currency) }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Mascot(MascotPose.COIN_HUG, size = 150.dp)
        Text(
            text = stringResource(R.string.quick_done),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(categoryName, amount, dayLabel(state.date)).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
