package com.yuzufin.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.SavingsResult
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.formatShortDate
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.yuzu
import java.time.LocalDate

/** Un aporte o retiro en curso: de qué meta y de qué tipo. */
private data class EntryTarget(val row: GoalRow, val withdraw: Boolean)

/**
 * Metas de ahorro. Lo apartado se descuenta de lo disponible pero no es un gasto: el saldo total
 * (y el extracto) no cambian.
 */
@Composable
fun SavingsScreen(
    contentPadding: PaddingValues,
    onAddGoal: () -> Unit,
    onEditGoal: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: SavingsViewModel = viewModel(factory = SavingsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }
    var deletingEntryId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_savings),
                actions = {
                    IconButton(onClick = onAddGoal) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.savings_add_goal))
                    }
                },
            )
        }

        if (!state.loading) {
            item { SummaryCard(state) }
        }

        if (!state.loading && state.goals.isEmpty()) {
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    IconBadge(Icons.Rounded.Savings)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.savings_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.savings_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddGoal) { Text(stringResource(R.string.savings_add_goal)) }
                }
            }
        }

        if (state.goals.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.savings_goals_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actionLabel = stringResource(R.string.savings_add_goal),
                    onAction = onAddGoal,
                )
            }
            state.goals.forEach { row ->
                item(key = "goal-${row.goal.id}") {
                    GoalCard(
                        row = row,
                        currency = state.currency,
                        onEdit = { onEditGoal(row.goal.id) },
                        onContribute = { entryTarget = EntryTarget(row, withdraw = false) },
                        onWithdraw = { entryTarget = EntryTarget(row, withdraw = true) },
                    )
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.savings_history_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    state.history.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        HistoryRow(item, state.currency, onDelete = { deletingEntryId = item.entry.id })
                    }
                }
            }
        }
    }

    entryTarget?.let { target ->
        SavingsEntryDialog(
            target = target,
            currency = state.currency,
            available = state.available,
            onSubmit = { amount, date, note, onResult ->
                viewModel.addEntry(target.row.goal.id, if (target.withdraw) -amount else amount, date, note) { result ->
                    if (result == SavingsResult.OK) entryTarget = null
                    onResult(result)
                }
            },
            onDismiss = { entryTarget = null },
        )
    }

    deletingEntryId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingEntryId = null },
            title = { Text(stringResource(R.string.savings_entry_delete_title)) },
            text = { Text(stringResource(R.string.savings_entry_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingEntryId = null
                        viewModel.deleteEntry(id)
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntryId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: SavingsUiState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.savings_available), style = MaterialTheme.typography.bodySmall, color = muted)
                Text(
                    MoneyFormatter.format(state.available, state.currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.available < 0) MaterialTheme.yuzu.expense else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.savings_saved_total), style = MaterialTheme.typography.bodySmall, color = muted)
                Text(
                    MoneyFormatter.format(state.totalSaved, state.currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.yuzu.income,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.savings_summary_note), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun GoalCard(
    row: GoalRow,
    currency: CurrencyInfo,
    onEdit: () -> Unit,
    onContribute: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val goal = row.goal
    YuzuCard(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clickable(onClick = onEdit),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.savings_percent, row.percent),
                style = MaterialTheme.typography.titleSmall,
                color = if (row.complete) MaterialTheme.yuzu.income else muted,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { row.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                R.string.savings_progress,
                MoneyFormatter.format(row.saved, currency),
                MoneyFormatter.format(goal.targetMinor, currency),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        val deadline = goal.deadline
        when {
            row.complete -> InfoLine(stringResource(R.string.savings_complete), MaterialTheme.yuzu.income)
            row.overdue && deadline != null -> InfoLine(
                stringResource(R.string.savings_overdue, formatShortDate(deadline), MoneyFormatter.format(row.remaining, currency)),
                MaterialTheme.yuzu.expense,
            )
            deadline != null -> {
                InfoLine(
                    stringResource(R.string.savings_deadline, formatShortDate(deadline), MoneyFormatter.format(row.remaining, currency)),
                    muted,
                )
                row.monthlyNeeded?.let {
                    InfoLine(stringResource(R.string.savings_monthly_needed, MoneyFormatter.format(it, currency)), muted)
                }
            }
            else -> InfoLine(stringResource(R.string.savings_remaining, MoneyFormatter.format(row.remaining, currency)), muted)
        }
        if (goal.autoPercent > 0) {
            InfoLine(stringResource(R.string.savings_auto_label, goal.autoPercent), muted)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onContribute) { Text(stringResource(R.string.savings_contribute)) }
            OutlinedButton(onClick = onWithdraw, enabled = row.saved > 0L) {
                Text(stringResource(R.string.savings_withdraw))
            }
        }
    }
}

@Composable
private fun InfoLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun HistoryRow(item: EntryRow, currency: CurrencyInfo, onDelete: () -> Unit) {
    val entry = item.entry
    val positive = entry.amountMinor > 0
    val label = when {
        !positive -> stringResource(R.string.savings_entry_withdrawal)
        entry.sourceTransactionId != null -> stringResource(R.string.savings_entry_auto)
        else -> stringResource(R.string.savings_entry_contribution)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.goalName, style = MaterialTheme.typography.titleSmall)
            val detail = buildString {
                append(label)
                append(" · ")
                append(formatShortDate(entry.date))
                if (entry.note.isNotBlank()) append(" · ").append(entry.note)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (if (positive) "+ " else "- ") + MoneyFormatter.format(kotlin.math.abs(entry.amountMinor), currency),
            style = MaterialTheme.typography.titleSmall,
            color = if (positive) MaterialTheme.yuzu.income else MaterialTheme.yuzu.expense,
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_delete),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Pide importe, fecha y nota para aportar o retirar. Los errores de validación se muestran sin cerrar. */
@Composable
private fun SavingsEntryDialog(
    target: EntryTarget,
    currency: CurrencyInfo,
    available: Long,
    onSubmit: (Long, LocalDate, String, (SavingsResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var text by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<SavingsResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val amount = AmountParser.parseToMinorUnits(text, currency)
    val valid = amount != null && amount > 0L
    val goal = target.row.goal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (target.withdraw) R.string.savings_withdraw_title else R.string.savings_contribute_title,
                    goal.name,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = AmountParser.sanitize(it, currency)
                        error = null
                    },
                    label = { Text(stringResource(R.string.savings_entry_amount)) },
                    prefix = { Text("${currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        val message = when (error) {
                            SavingsResult.NOT_ENOUGH_AVAILABLE -> stringResource(R.string.savings_error_available)
                            SavingsResult.NOT_ENOUGH_SAVED -> stringResource(R.string.savings_error_saved)
                            SavingsResult.INVALID -> stringResource(R.string.savings_error_generic)
                            else -> null
                        }
                        when {
                            message != null -> Text(message)
                            target.withdraw -> Text(
                                stringResource(R.string.savings_entry_saved, MoneyFormatter.format(target.row.saved, currency)),
                            )
                            else -> Text(stringResource(R.string.savings_entry_available, MoneyFormatter.format(available, currency)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.savings_entry_date),
                    date = date,
                    minDate = today.minusYears(5),
                    maxDate = today,
                    onDateChange = { date = it },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text(stringResource(R.string.savings_entry_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = {
                    busy = true
                    onSubmit(amount ?: 0L, date, note) { result ->
                        busy = false
                        if (result != SavingsResult.OK) error = result
                    }
                },
            ) {
                Text(stringResource(if (target.withdraw) R.string.savings_withdraw else R.string.savings_contribute))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
