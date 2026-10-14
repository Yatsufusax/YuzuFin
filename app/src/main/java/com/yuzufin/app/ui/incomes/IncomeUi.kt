package com.yuzufin.app.ui.incomes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import com.yuzufin.app.R
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.IncomeOccurrence
import com.yuzufin.app.domain.IncomeSchedule
import com.yuzufin.app.domain.IncomeScheduler
import com.yuzufin.app.domain.IncomeSource
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.ui.common.formatFullDate
import com.yuzufin.app.ui.common.formatShortDate
import java.time.LocalDate

/** Un cobro que ya llegó y espera confirmación. No toca el saldo todavía. */
data class PendingIncome(
    val occurrenceId: Long,
    val sourceName: String,
    val dueDate: LocalDate,
    val amountMinor: Long,
)

/** El próximo cobro previsto de una fuente activa. Es una previsión, no dinero real. */
data class UpcomingIncome(
    val sourceId: Long,
    val sourceName: String,
    val date: LocalDate,
    val amountMinor: Long,
)

fun buildPending(sources: List<IncomeSource>, occurrences: List<IncomeOccurrence>): List<PendingIncome> {
    val names = sources.associate { it.id to it.name }
    return occurrences
        .filter { it.status == OccurrenceStatus.PENDING }
        .mapNotNull { occurrence ->
            val name = names[occurrence.sourceId] ?: return@mapNotNull null
            PendingIncome(occurrence.id, name, occurrence.dueDate, occurrence.amountMinor)
        }
        .sortedBy { it.dueDate }
}

fun buildUpcoming(sources: List<IncomeSource>, today: LocalDate): List<UpcomingIncome> =
    sources
        .filter { it.active }
        .mapNotNull { source ->
            IncomeScheduler.nextDueDate(source, today)?.let { date ->
                UpcomingIncome(source.id, source.name, date, source.amountMinor)
            }
        }
        .sortedBy { it.date }

/** “Cada mes, el día 5”, “El 15 y el último día de cada mes”, “3 fechas elegidas”. */
@Composable
fun scheduleSummary(source: IncomeSource): String {
    @Composable
    fun day(value: Int): String =
        if (value >= 31) stringResource(R.string.income_day_last) else stringResource(R.string.income_day_number, value)

    return when (source.schedule) {
        IncomeSchedule.MONTHLY ->
            stringResource(R.string.income_schedule_monthly_summary, day(source.daysOfMonth.firstOrNull() ?: 1))
        IncomeSchedule.TWICE_MONTHLY -> stringResource(
            R.string.income_schedule_twice_summary,
            day(source.daysOfMonth.getOrNull(0) ?: 1),
            day(source.daysOfMonth.getOrNull(1) ?: 15),
        )
        IncomeSchedule.CUSTOM ->
            stringResource(R.string.income_schedule_custom_summary, source.customDates.size)
    }
}

/** Pide el importe real cobrado (viene con el previsto) y confirma el cobro. */
@Composable
fun ConfirmIncomeDialog(
    item: PendingIncome,
    currency: CurrencyInfo,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(item.occurrenceId) {
        mutableStateOf(AmountParser.toInputText(item.amountMinor, currency))
    }
    val amount = AmountParser.parseToMinorUnits(text, currency)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.income_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.income_confirm_body, item.sourceName, formatFullDate(item.dueDate)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = AmountParser.sanitize(it, currency) },
                    label = { Text(stringResource(R.string.income_confirm_amount)) },
                    prefix = { Text("${currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = amount == null || amount <= 0L,
                    supportingText = {
                        if (amount != null && amount > 0L) Text(MoneyFormatter.format(amount, currency))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.income_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && amount > 0L,
                onClick = { amount?.let(onConfirm) },
            ) { Text(stringResource(R.string.income_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Fila de un cobro pendiente con sus dos acciones. */
@Composable
fun PendingIncomeRow(
    item: PendingIncome,
    currency: CurrencyInfo,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.sourceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    formatShortDate(item.dueDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                MoneyFormatter.format(item.amountMinor, currency),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConfirm) { Text(stringResource(R.string.income_already_paid)) }
            OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.income_skip)) }
        }
        Spacer(Modifier.height(2.dp))
    }
}
