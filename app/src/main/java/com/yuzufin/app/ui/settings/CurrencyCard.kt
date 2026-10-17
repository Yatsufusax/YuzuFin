package com.yuzufin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.CurrencyConversion
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.YuzuCard
import kotlinx.coroutines.launch
import java.math.BigDecimal

@Composable
fun CurrencyCard(currency: CurrencyInfo, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.settings_currency_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text = "${currency.symbol} · ${currency.code}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_currency_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.currency_change)) }
    }
    if (open) CurrencyChangeDialog(current = currency, onDismiss = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrencyChangeDialog(current: CurrencyInfo, onDismiss: () -> Unit) {
    val converter = rememberAppContainer().currencyConverter
    val scope = rememberCoroutineScope()
    val options = CurrencyInfo.supported.filter { it.code != current.code }
    var target by remember { mutableStateOf(options.first()) }
    var keepNumbers by remember { mutableStateOf(false) }
    var rateText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val (anchorA, anchorB) = CurrencyConversion.anchor(current, target)
    val rate = CurrencyConversion.parseRate(rateText)
    val newPerOld: BigDecimal? = if (keepNumbers) BigDecimal.ONE else rate?.let { CurrencyConversion.newPerOld(current, target, it) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.currency_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.currency_dialog_target), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = option.code == target.code,
                            onClick = { target = option },
                            label = { Text("${option.symbol} ${option.code}") },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !keepNumbers,
                        onClick = { keepNumbers = false },
                        label = { Text(stringResource(R.string.currency_mode_convert)) },
                    )
                    FilterChip(
                        selected = keepNumbers,
                        onClick = { keepNumbers = true },
                        label = { Text(stringResource(R.string.currency_mode_keep)) },
                    )
                }
                if (!keepNumbers) {
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { raw -> rateText = raw.replace('.', ',').filter { it.isDigit() || it == ',' }.take(14) },
                        label = { Text(stringResource(R.string.currency_rate_label)) },
                        prefix = { Text("1 ${anchorA.code} = ") },
                        suffix = { Text(anchorB.code) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text(stringResource(R.string.currency_rate_hint)) },
                    )
                } else {
                    Text(stringResource(R.string.currency_keep_note), style = MaterialTheme.typography.bodySmall)
                }
                if (newPerOld != null) {
                    val factor = CurrencyConversion.factor(current, target, newPerOld)
                    var sample = if (current.fractionDigits == 0) 100_000L else 100L
                    repeat(current.fractionDigits) { sample *= 10 }
                    Text(
                        stringResource(
                            R.string.currency_preview,
                            MoneyFormatter.format(sample, current),
                            MoneyFormatter.format(CurrencyConversion.convert(sample, factor), target),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    stringResource(R.string.currency_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (failed) {
                    Text(stringResource(R.string.currency_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = newPerOld != null && !busy,
                onClick = {
                    val chosen = newPerOld ?: return@TextButton
                    scope.launch {
                        busy = true
                        failed = false
                        try {
                            converter.change(target, chosen)
                            onDismiss()
                        } catch (e: Exception) {
                            failed = true
                            busy = false
                        }
                    }
                },
            ) { Text(stringResource(if (busy) R.string.backup_working else R.string.currency_confirm)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
