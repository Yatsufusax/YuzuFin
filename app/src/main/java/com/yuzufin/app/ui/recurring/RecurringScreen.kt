package com.yuzufin.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.HintCard
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.RecurringExpense
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.formatShortDate
import com.yuzufin.app.ui.common.rememberHaptic
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.MoneaCard
import com.yuzufin.app.ui.common.sharePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Gastos fijos mensuales: la app te avisa cuando toca pagarlos y vos confirmás. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: RecurringViewModel = viewModel(factory = RecurringViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // null = sin diálogo; id 0 = gasto nuevo.
    var editing by remember { mutableStateOf<RecurringExpense?>(null) }
    var confirming by remember { mutableStateOf<PendingRecurring?>(null) }
    var advancing by remember { mutableStateOf<RecurringRow?>(null) }
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var receiptError by remember { mutableStateOf(false) }

    /** Genera el comprobante de un plan (o el resumen de todos con null) y abre el menú de compartir. */
    val exportReceipt: (Long?) -> Unit = { expenseId ->
        receiptError = false
        scope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) { InstallmentExport.generate(context, container, expenseId) }
            }.getOrNull()
            if (file != null) sharePdf(context, file) else receiptError = true
        }
    }

    val blank = {
        RecurringExpense(
            id = 0, name = "", amountMinor = 0, categoryId = state.categories.firstOrNull()?.id ?: 0,
            dayOfMonth = LocalDate.now().dayOfMonth, startDate = LocalDate.now(), active = true, remind = true,
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.menu_recurring),
                onBack = onBack,
                actions = {
                    if (state.rows.any { it.expense.isInstallments }) {
                        IconButton(onClick = { exportReceipt(null) }) {
                            Icon(Icons.Rounded.Description, contentDescription = stringResource(R.string.recurring_receipt_all))
                        }
                    }
                    IconButton(onClick = { editing = blank() }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.recurring_add))
                    }
                },
            )
        }
        item {
            HintCard(
                hintId = "recurring",
                titleRes = R.string.hint_recurring_title,
                bodyRes = R.string.hint_recurring_body,
                pose = MascotPose.PLANNING,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (receiptError) {
            item {
                Text(
                    stringResource(R.string.recurring_receipt_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (!state.loading && state.rows.isEmpty()) {
            item {
                MoneaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    IconBadge(Icons.Rounded.Repeat)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.recurring_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.recurring_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { editing = blank() }) { Text(stringResource(R.string.recurring_add)) }
                }
            }
        }

        if (state.pending.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.recurring_pending_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                MoneaCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.pending.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                R.string.recurring_pending_line,
                                formatShortDate(item.dueDate),
                                MoneyFormatter.format(-item.amountMinor, state.currency),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { confirming = item }) { Text(stringResource(R.string.recurring_paid)) }
                            OutlinedButton(onClick = { viewModel.skip(item.occurrenceId) }) {
                                Text(stringResource(R.string.recurring_skip))
                            }
                        }
                    }
                }
            }
        }

        if (state.rows.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.recurring_list_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                MoneaCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    state.rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = row.expense }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconBadge(categoryIcon(row.iconKey), size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.expense.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    when {
                                        row.finished -> stringResource(R.string.recurring_installments_done)
                                        row.expense.active && row.next != null -> stringResource(R.string.recurring_next, formatShortDate(row.next))
                                        else -> stringResource(R.string.recurring_paused)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (row.expense.isInstallments) {
                                    Text(
                                        stringResource(
                                            R.string.recurring_installments_progress,
                                            row.installmentsUsed,
                                            row.expense.totalInstallments,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                    ) {
                                        if (row.installmentsLeft > 0 && row.expense.active) {
                                            TextButton(
                                                onClick = { advancing = row },
                                                contentPadding = PaddingValues(0.dp),
                                            ) { Text(stringResource(R.string.recurring_advance), maxLines = 1, softWrap = false) }
                                        }
                                        TextButton(
                                            onClick = { exportReceipt(row.expense.id) },
                                            contentPadding = PaddingValues(0.dp),
                                        ) { Text(stringResource(R.string.recurring_receipt), maxLines = 1, softWrap = false) }
                                    }
                                    if (row.finished) {
                                        Text(
                                            stringResource(R.string.recurring_finished_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Text(
                                MoneyFormatter.format(-row.expense.amountMinor, state.currency),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (!row.finished) {
                                Switch(
                                    checked = row.expense.active,
                                    onCheckedChange = { viewModel.setActive(row.expense.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    advancing?.let { row ->
        var countText by remember(row.expense.id) { mutableStateOf("1") }
        var amountText by remember(row.expense.id) { mutableStateOf(AmountParser.toInputText(row.expense.amountMinor, state.currency)) }
        val count = countText.toIntOrNull()
        val parsed = AmountParser.parseToMinorUnits(amountText, state.currency)
        val countOk = count != null && count in 1..row.installmentsLeft
        AlertDialog(
            onDismissRequest = { advancing = null },
            title = { Text(stringResource(R.string.recurring_advance_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.expense.name, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.recurring_advance_body))
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.recurring_advance_count, row.installmentsLeft)) },
                        singleLine = true,
                        isError = !countOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = AmountParser.sanitize(it, state.currency) },
                        label = { Text(stringResource(R.string.recurring_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = countOk && parsed != null && parsed > 0L,
                    onClick = {
                        viewModel.advance(row.expense.id, count ?: return@TextButton, parsed ?: return@TextButton)
                        haptic()
                        advancing = null
                    },
                ) { Text(stringResource(R.string.recurring_advance_confirm)) }
            },
            dismissButton = { TextButton(onClick = { advancing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    confirming?.let { item ->
        var text by remember(item.occurrenceId) { mutableStateOf(AmountParser.toInputText(item.amountMinor, state.currency)) }
        val parsed = AmountParser.parseToMinorUnits(text, state.currency)
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recurring_confirm_body))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = AmountParser.sanitize(it, state.currency) },
                        label = { Text(stringResource(R.string.recurring_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null && parsed > 0L,
                    onClick = {
                        viewModel.confirm(item.occurrenceId, parsed ?: return@TextButton)
                        haptic()
                        confirming = null
                    },
                ) { Text(stringResource(R.string.recurring_paid)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    editing?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var amount by remember(original.id) {
            mutableStateOf(if (original.id == 0L) "" else AmountParser.toInputText(original.amountMinor, state.currency))
        }
        var categoryId by remember(original.id) { mutableStateOf(original.categoryId) }
        var dayText by remember(original.id) { mutableStateOf(original.dayOfMonth.toString()) }
        var remind by remember(original.id) { mutableStateOf(original.remind) }
        var inInstallments by remember(original.id) { mutableStateOf(original.isInstallments) }
        var totalText by remember(original.id) { mutableStateOf(if (original.isInstallments) original.totalInstallments.toString() else "") }
        var paidText by remember(original.id) { mutableStateOf(if (original.paidBefore > 0) original.paidBefore.toString() else "") }
        var confirmDelete by remember(original.id) { mutableStateOf(false) }
        var showErrors by remember(original.id) { mutableStateOf(false) }

        val parsedAmount = AmountParser.parseToMinorUnits(amount, state.currency)
        val day = dayText.toIntOrNull()
        val total = totalText.toIntOrNull() ?: 0
        val paidBefore = paidText.toIntOrNull() ?: 0
        val installmentsOk = !inInstallments || (total in 1..600 && paidBefore in 0 until total)
        val valid = installmentsOk && name.isNotBlank() && parsedAmount != null && parsedAmount > 0L && day != null && day in 1..31 &&
            state.allCategories.any { it.id == categoryId }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (original.id == 0L) R.string.recurring_add else R.string.recurring_edit)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text(stringResource(R.string.recurring_name)) },
                        singleLine = true,
                        isError = showErrors && name.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = AmountParser.sanitize(it, state.currency) },
                        label = { Text(stringResource(R.string.recurring_amount)) },
                        singleLine = true,
                        isError = showErrors && (parsedAmount == null || parsedAmount <= 0L),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.recurring_category), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Si el gasto usa una categoría archivada, se sigue mostrando para no perderla.
                        val options = state.categories + state.allCategories.filter { it.id == categoryId && it !in state.categories }
                        options.forEach { category ->
                            FilterChip(
                                selected = category.id == categoryId,
                                onClick = { categoryId = category.id },
                                label = { Text(category.name) },
                                leadingIcon = { Icon(categoryIcon(category.iconKey), contentDescription = null) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.recurring_day)) },
                        supportingText = { Text(stringResource(R.string.recurring_day_note)) },
                        singleLine = true,
                        isError = showErrors && (day == null || day !in 1..31),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recurring_installments_switch), modifier = Modifier.weight(1f))
                        Switch(checked = inInstallments, onCheckedChange = { inInstallments = it })
                    }
                    if (inInstallments) {
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.recurring_installments_total)) },
                            singleLine = true,
                            isError = showErrors && total !in 1..600,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = paidText,
                            onValueChange = { paidText = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.recurring_installments_paid)) },
                            supportingText = { Text(stringResource(R.string.recurring_installments_note)) },
                            singleLine = true,
                            isError = showErrors && paidBefore !in 0 until maxOf(total, 1),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.recurring_remind), modifier = Modifier.weight(1f))
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!valid) {
                            showErrors = true
                        } else {
                            viewModel.save(
                                original.copy(
                                    name = name.trim(),
                                    amountMinor = parsedAmount ?: return@TextButton,
                                    categoryId = categoryId,
                                    dayOfMonth = day ?: return@TextButton,
                                    remind = remind,
                                    totalInstallments = if (inInstallments) total else 0,
                                    paidBefore = if (inInstallments) paidBefore else 0,
                                ),
                            )
                            editing = null
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    if (original.id != 0L) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.recurring_delete_title)) },
                text = {
                    Text(stringResource(if (original.isInstallments) R.string.recurring_delete_installments_body else R.string.recurring_delete_body))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(original.id)
                            confirmDelete = false
                            editing = null
                        },
                    ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}
