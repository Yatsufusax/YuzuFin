package com.yuzufin.app.ui.incomes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.yuzufin.app.domain.IncomeSchedule
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.formatFullDate
import com.yuzufin.app.ui.common.rememberNotificationPermission
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.ScreenHeader
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IncomeFormScreen(
    viewModel: IncomeFormViewModel,
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
            title = stringResource(if (state.isEdit) R.string.income_form_edit else R.string.income_form_new),
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
                    label = { Text(stringResource(R.string.income_field_name)) },
                    placeholder = { Text(stringResource(R.string.income_field_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = state.showErrors && !state.nameIsValid,
                    supportingText = {
                        if (state.showErrors && !state.nameIsValid) Text(stringResource(R.string.income_error_name))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                val amount = state.amountMinor
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(stringResource(R.string.income_field_amount)) },
                    prefix = { Text("${state.currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = state.showErrors && !state.amountIsValid,
                    supportingText = {
                        when {
                            state.showErrors && !state.amountIsValid -> Text(stringResource(R.string.form_amount_error))
                            amount != null && amount > 0L -> Text(MoneyFormatter.format(amount, state.currency))
                        }
                    },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.income_field_schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val options = listOf(
                        IncomeSchedule.MONTHLY to R.string.income_schedule_monthly,
                        IncomeSchedule.TWICE_MONTHLY to R.string.income_schedule_twice,
                        IncomeSchedule.CUSTOM to R.string.income_schedule_custom,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (schedule, label) ->
                            SegmentedButton(
                                selected = state.schedule == schedule,
                                onClick = { viewModel.onScheduleChange(schedule) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                label = { Text(stringResource(label), maxLines = 1) },
                            )
                        }
                    }

                    when (state.schedule) {
                        IncomeSchedule.MONTHLY -> {
                            DayField(
                                label = stringResource(R.string.income_field_day),
                                value = state.day1Text,
                                onChange = viewModel::onDay1Change,
                                isError = state.showErrors && state.day1 == null,
                            )
                            AssistChip(
                                onClick = { viewModel.onDay1Change("31") },
                                label = { Text(stringResource(R.string.income_last_day)) },
                            )
                        }
                        IncomeSchedule.TWICE_MONTHLY -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DayField(
                                    label = stringResource(R.string.income_field_day_first),
                                    value = state.day1Text,
                                    onChange = viewModel::onDay1Change,
                                    isError = state.showErrors && state.day1 == null,
                                    modifier = Modifier.weight(1f),
                                )
                                DayField(
                                    label = stringResource(R.string.income_field_day_second),
                                    value = state.day2Text,
                                    onChange = viewModel::onDay2Change,
                                    isError = state.showErrors && (state.day2 == null || state.day2 == state.day1),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { viewModel.onPresetTwice(15, 31) },
                                    label = { Text(stringResource(R.string.income_preset_15_last)) },
                                )
                                AssistChip(
                                    onClick = { viewModel.onPresetTwice(1, 15) },
                                    label = { Text(stringResource(R.string.income_preset_1_15)) },
                                )
                            }
                            if (state.showErrors && state.day1 != null && state.day1 == state.day2) {
                                Text(
                                    stringResource(R.string.income_error_same_day),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IncomeSchedule.CUSTOM -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.customDates.forEach { date ->
                                    AssistChip(
                                        onClick = { viewModel.onRemoveCustomDate(date) },
                                        label = { Text(formatFullDate(date)) },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.action_delete),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                    )
                                }
                            }
                            if (state.showErrors && state.customDates.isEmpty()) {
                                Text(
                                    stringResource(R.string.income_error_dates),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            DateField(
                                label = stringResource(R.string.income_field_date_add),
                                date = state.dateToAdd,
                                minDate = LocalDate.now().minusYears(1),
                                maxDate = LocalDate.now().plusYears(5),
                                onDateChange = viewModel::onDateToAddChange,
                            )
                            OutlinedButton(onClick = viewModel::onAddCustomDate) {
                                Text(stringResource(R.string.income_add_date))
                            }
                        }
                    }
                    Text(
                        stringResource(
                            if (state.schedule == IncomeSchedule.CUSTOM) R.string.income_custom_note
                            else R.string.income_day_note,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.categories.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.form_category),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.categories.forEach { category ->
                                FilterChip(
                                    selected = state.categoryId == category.id,
                                    onClick = { viewModel.onCategoryChange(category.id) },
                                    label = { Text(category.name) },
                                    leadingIcon = {
                                        Icon(
                                            categoryIcon(category.iconKey),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                SwitchRow(
                    title = stringResource(R.string.income_switch_confirm),
                    body = stringResource(
                        if (state.requiresConfirmation) R.string.income_switch_confirm_on else R.string.income_switch_confirm_off,
                    ),
                    checked = state.requiresConfirmation,
                    onChange = viewModel::onRequiresConfirmationChange,
                )
                SwitchRow(
                    title = stringResource(R.string.income_switch_remind),
                    body = stringResource(R.string.income_switch_remind_body),
                    checked = state.remind,
                    onChange = { value ->
                        viewModel.onRemindChange(value)
                        if (value && !permission.granted) permission.request()
                    },
                )
                if (state.isEdit) {
                    SwitchRow(
                        title = stringResource(R.string.income_switch_active),
                        body = stringResource(R.string.income_switch_active_body),
                        checked = state.active,
                        onChange = viewModel::onActiveChange,
                    )
                }
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
            title = { Text(stringResource(R.string.income_delete_title)) },
            text = { Text(stringResource(R.string.income_delete_body)) },
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
private fun DayField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        supportingText = { if (isError) Text(stringResource(R.string.income_error_day)) },
        modifier = modifier.fillMaxWidth(),
    )
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
