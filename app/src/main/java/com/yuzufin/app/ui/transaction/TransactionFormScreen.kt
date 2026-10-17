package com.yuzufin.app.ui.transaction

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.yuzufin.app.R
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.sharePdf
import java.io.File
import java.util.UUID
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.ScreenHeader
import java.time.LocalDate

/** Alta, edición y borrado de un ingreso o gasto. [onClose] vuelve a la pantalla anterior. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFormScreen(
    viewModel: TransactionFormViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val container = rememberAppContainer()
    var captureTarget by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = captureTarget
        captureTarget = null
        if (path != null) {
            val file = File(path)
            if (ok && file.length() > 0L) viewModel.addPhotoFromFile(file) else file.delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.addPhoto(uri)
    }

    LaunchedEffect(state.voucherFile) {
        state.voucherFile?.let {
            sharePdf(context, it)
            viewModel.voucherShared()
        }
    }

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
            title = stringResource(if (state.isEdit) R.string.form_title_edit else R.string.form_title_new),
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
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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

                val amount = state.amountMinor
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text(stringResource(R.string.form_amount)) },
                    prefix = { Text("${state.currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = state.showErrors && !state.amountIsValid,
                    supportingText = {
                        when {
                            state.showErrors && !state.amountIsValid ->
                                Text(stringResource(R.string.form_amount_error))
                            state.wouldOverdraw ->
                                Text(
                                    text = stringResource(
                                        R.string.form_overdraw_warning,
                                        MoneyFormatter.format(state.baseBalance, state.currency),
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            amount != null && amount > 0L ->
                                Text(MoneyFormatter.format(amount, state.currency))
                        }
                    },
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.form_category),
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

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.form_description)) },
                    placeholder = { Text(stringResource(R.string.form_description_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val today = LocalDate.now()
                DateField(
                    label = stringResource(R.string.form_date),
                    date = state.date,
                    minDate = state.minDate,
                    maxDate = today,
                    onDateChange = viewModel::onDateChange,
                    isError = state.showErrors && (state.date.isBefore(state.minDate) || state.date.isAfter(today)),
                )
                Text(
                    text = stringResource(R.string.form_date_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                AttachmentsSection(
                    state = state,
                    files = container.attachmentFileStore,
                    onTakePhoto = {
                        try {
                            val dir = File(context.cacheDir, "capture").apply { mkdirs() }
                            val file = File(dir, "${UUID.randomUUID()}.jpg")
                            captureTarget = file.path
                            takePicture.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                        } catch (e: ActivityNotFoundException) {
                            captureTarget = null
                            viewModel.reportPhotoError()
                        }
                    },
                    onPickPhoto = {
                        try {
                            pickPhoto.launch("image/*")
                        } catch (e: ActivityNotFoundException) {
                            viewModel.reportPhotoError()
                        }
                    },
                    onRemove = viewModel::removePhoto,
                    onVoucher = viewModel::generateVoucher,
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
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
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
