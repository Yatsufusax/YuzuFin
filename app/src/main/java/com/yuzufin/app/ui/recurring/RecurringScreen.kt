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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
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
    val haptic = rememberHaptic()

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
                    IconButton(onClick = { editing = blank() }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.recurring_add))
                    }
                },
            )
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
                                    if (row.expense.active && row.next != null) {
                                        stringResource(R.string.recurring_next, formatShortDate(row.next))
                                    } else {
                                        stringResource(R.string.recurring_paused)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                MoneyFormatter.format(-row.expense.amountMinor, state.currency),
                                style = MaterialTheme.typography.titleSmall,
                            )
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
        var confirmDelete by remember(original.id) { mutableStateOf(false) }
        var showErrors by remember(original.id) { mutableStateOf(false) }

        val parsedAmount = AmountParser.parseToMinorUnits(amount, state.currency)
        val day = dayText.toIntOrNull()
        val valid = name.isNotBlank() && parsedAmount != null && parsedAmount > 0L && day != null && day in 1..31 &&
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
                text = { Text(stringResource(R.string.recurring_delete_body)) },
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
