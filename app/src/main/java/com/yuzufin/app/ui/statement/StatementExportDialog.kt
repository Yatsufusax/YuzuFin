package com.yuzufin.app.ui.statement

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.yuzufin.app.AppContainer
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.StatementBuilder
import com.yuzufin.app.export.StatementPdfWriter
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.DateField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class ExportRange(@StringRes val labelRes: Int) {
    THIS_MONTH(R.string.export_this_month),
    LAST_MONTH(R.string.export_last_month),
    ALL(R.string.export_all),
    CUSTOM(R.string.export_custom),
}

/** Diálogo para elegir el período, generar el extracto en PDF y abrir el menú de compartir. */
@Composable
fun StatementExportDialog(onDismiss: () -> Unit) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rangeIndex by rememberSaveable { mutableIntStateOf(0) }
    val range = ExportRange.entries[rangeIndex]
    val today = remember { LocalDate.now() }
    var from by remember { mutableStateOf(today.withDayOfMonth(1)) }
    var to by remember { mutableStateOf(today) }
    var working by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    val customInvalid = range == ExportRange.CUSTOM && from.isAfter(to)

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.export_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExportRange.entries.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !working, role = Role.RadioButton) { rangeIndex = index },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = index == rangeIndex, onClick = null, enabled = !working)
                        Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (range == ExportRange.CUSTOM) {
                    DateField(
                        label = stringResource(R.string.export_from),
                        date = from,
                        minDate = LocalDate.of(2000, 1, 1),
                        maxDate = today,
                        onDateChange = { from = it },
                        isError = customInvalid,
                    )
                    DateField(
                        label = stringResource(R.string.export_to),
                        date = to,
                        minDate = LocalDate.of(2000, 1, 1),
                        maxDate = today,
                        onDateChange = { to = it },
                        isError = customInvalid,
                    )
                }
                errorRes?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.export_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && !customInvalid,
                onClick = {
                    working = true
                    errorRes = null
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                generate(context, container, range, from, to)
                            }
                        }
                        working = false
                        val file = result.getOrNull()
                        if (file != null) {
                            share(context, file)
                            onDismiss()
                        } else {
                            errorRes = if (result.exceptionOrNull() is SetupMissing) R.string.export_no_setup
                            else R.string.export_error
                        }
                    }
                },
            ) {
                Text(stringResource(if (working) R.string.export_generating else R.string.export_generate))
            }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private class SetupMissing : Exception()

private suspend fun generate(
    context: Context,
    container: AppContainer,
    range: ExportRange,
    customFrom: LocalDate,
    customTo: LocalDate,
): File {
    val finance = container.financeRepository
    val initial = finance.initialBalance.first() ?: throw SetupMissing()
    val transactions = finance.transactions.first()
    val categories = finance.categories.first().associate { it.id to it.name }
    val settings = container.settingsRepository.settings.first()
    val currency = CurrencyInfo.fromCode(settings.currencyCode)

    val today = LocalDate.now()
    val (from, to) = when (range) {
        ExportRange.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        ExportRange.LAST_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(1)
            first to first.withDayOfMonth(first.lengthOfMonth())
        }
        ExportRange.ALL -> {
            val last = transactions.maxOfOrNull { it.date }
            initial.startDate to (if (last != null && last.isAfter(today)) last else today)
        }
        ExportRange.CUSTOM -> customFrom to customTo
    }

    val statement = StatementBuilder.build(initial, transactions, categories, from, to)

    // Solo queda el último extracto en el teléfono: cada uno contiene los movimientos.
    val dir = File(context.cacheDir, "exports")
    dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd")
    val file = File(dir, "Extracto-YuzuFin-${statement.from.format(stamp)}-${statement.to.format(stamp)}.pdf")

    StatementPdfWriter(context).write(
        statement = statement,
        currency = currency,
        userName = settings.userName,
        generatedAt = LocalDateTime.now(),
        output = file,
    )
    return file
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.export_share_title))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
