package com.yuzufin.app.ui.obligations

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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.rememberNotificationPermission
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.ScreenHeader
import java.time.LocalDate

/** Crear o editar una factura. Ocupa toda la pantalla (sin barra inferior). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillFormScreen(
    viewModel: BillFormViewModel,
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
            title = stringResource(if (state.isEdit) R.string.obl_bill_form_edit else R.string.obl_bill_form_new),
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
                    label = { Text(stringResource(R.string.obl_field_bill_name)) },
                    placeholder = { Text(stringResource(R.string.obl_field_bill_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = state.showErrors && !state.nameIsValid,
                    supportingText = {
                        if (state.showErrors && !state.nameIsValid) Text(stringResource(R.string.obl_error_name))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                val amount = state.amountMinor
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(stringResource(R.string.obl_field_bill_amount)) },
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

                val today = LocalDate.now()
                DateField(
                    label = stringResource(R.string.obl_field_due),
                    date = state.dueDate,
                    minDate = today.minusYears(1),
                    maxDate = today.plusYears(5),
                    onDateChange = viewModel::onDueDateChange,
                )

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
                    title = stringResource(R.string.obl_switch_repeat),
                    body = stringResource(R.string.obl_switch_repeat_body),
                    checked = state.repeatMonthly,
                    onChange = viewModel::onRepeatChange,
                )
                SwitchRow(
                    title = stringResource(R.string.obl_switch_remind),
                    body = stringResource(R.string.obl_switch_remind_body),
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
        DeleteDialog(
            title = R.string.obl_bill_delete_title,
            body = R.string.obl_bill_delete_body,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Crear o editar un préstamo. Al editar solo se cambian persona, nota y aviso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanFormScreen(
    viewModel: LoanFormViewModel,
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
            title = stringResource(if (state.isEdit) R.string.obl_loan_form_edit else R.string.obl_loan_form_new),
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
                val options = listOf(
                    LoanDirection.LENT to R.string.obl_direction_lent,
                    LoanDirection.BORROWED to R.string.obl_direction_borrowed,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (direction, label) ->
                        SegmentedButton(
                            selected = state.direction == direction,
                            onClick = { if (!state.isEdit) viewModel.onDirectionChange(direction) },
                            enabled = !state.isEdit || state.direction == direction,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }

                OutlinedTextField(
                    value = state.person,
                    onValueChange = viewModel::onPersonChange,
                    label = { Text(stringResource(R.string.obl_field_person)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    isError = state.showErrors && !state.personIsValid,
                    supportingText = {
                        if (state.showErrors && !state.personIsValid) Text(stringResource(R.string.obl_error_person))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                val amount = state.amountMinor
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(stringResource(R.string.obl_field_loan_amount)) },
                    prefix = { Text("${state.currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    enabled = !state.isEdit,
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

                if (state.isEdit) {
                    Text(
                        stringResource(R.string.obl_loan_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DateField(
                        label = stringResource(R.string.obl_field_loan_date),
                        date = state.date,
                        minDate = state.minDate,
                        maxDate = LocalDate.now(),
                        onDateChange = viewModel::onDateChange,
                    )
                    Text(
                        stringResource(
                            if (state.direction == LoanDirection.LENT) R.string.obl_loan_hint_lent else R.string.obl_loan_hint_borrowed,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.obl_field_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                SwitchRow(
                    title = stringResource(R.string.obl_switch_loan_remind),
                    body = stringResource(R.string.obl_switch_loan_remind_body),
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
        DeleteDialog(
            title = R.string.obl_loan_delete_title,
            body = R.string.obl_loan_delete_body,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DeleteDialog(title: Int, body: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
