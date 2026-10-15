package com.yuzufin.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.ui.common.rememberNotificationPermission
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.ScreenHeader
import java.time.LocalDate

/** Crear o editar una meta de ahorro. Ocupa toda la pantalla (sin barra inferior). */
@Composable
fun SavingsGoalFormScreen(
    viewModel: SavingsGoalFormViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val permission = rememberNotificationPermission()

    LaunchedEffect(state.finished, state.notFound) {
        if (state.finished || state.notFound) onClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        ScreenHeader(
            title = stringResource(if (state.isEdit) R.string.savings_form_edit else R.string.savings_form_new),
            onBack = onClose,
            actions = {
                if (state.isEdit) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            },
        )

        if (!state.loading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.savings_field_name)) },
                    placeholder = { Text(stringResource(R.string.savings_field_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = state.showErrors && !state.nameIsValid,
                    supportingText = {
                        if (state.showErrors && !state.nameIsValid) Text(stringResource(R.string.savings_error_name))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                val target = state.targetMinor
                OutlinedTextField(
                    value = state.targetText,
                    onValueChange = viewModel::onTargetChange,
                    label = { Text(stringResource(R.string.savings_field_target)) },
                    prefix = { Text("${state.currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = state.showErrors && !state.targetIsValid,
                    supportingText = {
                        when {
                            state.showErrors && !state.targetIsValid -> Text(stringResource(R.string.form_amount_error))
                            target != null && target > 0L -> Text(MoneyFormatter.format(target, state.currency))
                        }
                    },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                SwitchRow(
                    title = stringResource(R.string.savings_switch_deadline),
                    body = stringResource(R.string.savings_switch_deadline_body),
                    checked = state.hasDeadline,
                    onChange = viewModel::onHasDeadlineChange,
                )
                if (state.hasDeadline) {
                    val today = LocalDate.now()
                    DateField(
                        label = stringResource(R.string.savings_field_deadline),
                        date = state.deadline,
                        minDate = today,
                        maxDate = today.plusYears(30),
                        onDateChange = viewModel::onDeadlineChange,
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.savings_switch_auto),
                    body = stringResource(R.string.savings_switch_auto_body),
                    checked = state.autoEnabled,
                    onChange = viewModel::onAutoChange,
                )
                if (state.autoEnabled) {
                    val percentError = !state.percentFitsTotal || (state.showErrors && !state.percentIsValid)
                    OutlinedTextField(
                        value = state.percentText,
                        onValueChange = viewModel::onPercentChange,
                        label = { Text(stringResource(R.string.savings_field_percent)) },
                        suffix = { Text("%") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = percentError,
                        supportingText = {
                            when {
                                !state.percentFitsTotal ->
                                    Text(stringResource(R.string.savings_error_percent_total, state.otherPercent))
                                state.showErrors && !state.percentIsValid -> Text(stringResource(R.string.savings_error_percent))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SwitchRow(
                    title = stringResource(R.string.savings_switch_remind),
                    body = stringResource(R.string.savings_switch_remind_body),
                    checked = state.remind,
                    onChange = { value ->
                        viewModel.onRemindChange(value)
                        if (value && !permission.granted) permission.request()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.savings_delete_title)) },
            text = { Text(stringResource(R.string.savings_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
