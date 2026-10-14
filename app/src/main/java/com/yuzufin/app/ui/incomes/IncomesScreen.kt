package com.yuzufin.app.ui.incomes

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.formatShortDate
import com.yuzufin.app.ui.common.rememberNotificationPermission
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.PillLabel
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.yuzu

/**
 * Cobros e ingresos fijos. Separa claramente lo real (cobrado), lo que falta confirmar y lo
 * previsto: lo previsto nunca suma al saldo.
 */
@Composable
fun IncomesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: IncomesViewModel = viewModel(factory = IncomesViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<PendingIncome?>(null) }
    val colors = MaterialTheme.yuzu

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.menu_incomes),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onAddSource) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.income_add))
                    }
                },
            )
        }

        if (!state.loading && state.sources.isEmpty()) {
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    IconBadge(Icons.Rounded.AccountBalanceWallet)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.incomes_empty_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.incomes_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAddSource) { Text(stringResource(R.string.income_add)) }
                }
            }
        }

        if (state.remindersEnabled && state.anySourceReminds) {
            item { NotificationPermissionCard() }
        }

        if (state.pending.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.incomes_pending_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.pending.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        PendingIncomeRow(
                            item = item,
                            currency = state.currency,
                            onConfirm = { confirming = item },
                            onSkip = { viewModel.skip(item.occurrenceId) },
                        )
                    }
                }
            }
        }

        if (state.upcoming.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.incomes_upcoming_title), style = MaterialTheme.typography.titleMedium)
                    PillLabel(
                        text = stringResource(R.string.pill_forecast),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.upcoming.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.sourceName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatShortDate(item.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                MoneyFormatter.format(item.amountMinor, state.currency),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.incomes_upcoming_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.sources.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.incomes_sources_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actionLabel = stringResource(R.string.income_add),
                    onAction = onAddSource,
                )
            }
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    state.sources.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditSource(row.source.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(row.source.name, style = MaterialTheme.typography.titleSmall)
                                    if (row.source.remind) {
                                        Icon(
                                            Icons.Rounded.NotificationsActive,
                                            contentDescription = stringResource(R.string.income_reminder_on),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Text(
                                    MoneyFormatter.format(row.source.amountMinor, state.currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    scheduleSummary(row.source),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(
                                        if (row.source.requiresConfirmation) R.string.income_mode_manual_short
                                        else R.string.income_mode_auto_short,
                                    ) + if (!row.source.active) " · " + stringResource(R.string.income_paused) else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = row.source.active,
                                onCheckedChange = { viewModel.setActive(row.source.id, it) },
                            )
                        }
                    }
                }
            }
        }

        if (state.history.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.incomes_history_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    state.history.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.sourceName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatShortDate(row.dueDate) + " · " + MoneyFormatter.format(row.amountMinor, state.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (row.status == OccurrenceStatus.CONFIRMED) {
                                PillLabel(
                                    text = stringResource(R.string.pill_collected),
                                    container = colors.income.copy(alpha = 0.15f),
                                    content = colors.income,
                                )
                            } else {
                                PillLabel(
                                    text = stringResource(R.string.pill_skipped),
                                    container = MaterialTheme.colorScheme.surfaceVariant,
                                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { viewModel.reopen(row.occurrenceId) }) {
                                    Text(stringResource(R.string.income_reopen))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirming?.let { item ->
        ConfirmIncomeDialog(
            item = item,
            currency = state.currency,
            onConfirm = { amount ->
                viewModel.confirm(item.occurrenceId, amount)
                confirming = null
            },
            onDismiss = { confirming = null },
        )
    }
}

/** Avisa si los recordatorios están activos pero Android no deja mostrar notificaciones. */
@Composable
private fun NotificationPermissionCard() {
    val permission = rememberNotificationPermission()
    if (permission.granted) return
    YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Rounded.NotificationsActive, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reminders_permission_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.reminders_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (permission.needsSettings) {
            OutlinedButton(onClick = permission.openSettings) {
                Text(stringResource(R.string.reminders_open_settings))
            }
        } else {
            Button(onClick = permission.request) { Text(stringResource(R.string.reminders_enable)) }
        }
    }
}
