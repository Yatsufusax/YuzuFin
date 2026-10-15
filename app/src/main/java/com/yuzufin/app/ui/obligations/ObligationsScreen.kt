package com.yuzufin.app.ui.obligations

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.yuzufin.app.domain.BillStatus
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.LoanResult
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.formatShortDate
import com.yuzufin.app.ui.components.DateField
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.PillLabel
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.SectionHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.yuzu
import java.time.LocalDate

/**
 * Obligaciones: facturas por pagar y préstamos. Las facturas pendientes bajan el "libre estimado"
 * pero no el saldo; los préstamos mueven el saldo sin ser ingresos ni gastos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObligationsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAddBill: () -> Unit,
    onEditBill: (Long) -> Unit,
    onAddLoan: (LoanDirection) -> Unit,
    onEditLoan: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: ObligationsViewModel = viewModel(factory = ObligationsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var paying by remember { mutableStateOf<BillRow?>(null) }
    var repaying by remember { mutableStateOf<LoanRow?>(null) }
    var deletingPaymentId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.menu_obligations),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { if (tab == 0) onAddBill() else onAddLoan(LoanDirection.LENT) }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(if (tab == 0) R.string.obl_add_bill else R.string.obl_add_loan),
                        )
                    }
                },
            )
        }

        if (!state.loading) {
            item { SummaryCard(state) }
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    listOf(R.string.obl_tab_bills, R.string.obl_tab_loans).forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = tab == index,
                            onClick = { tab = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }
            }
        }

        if (!state.loading && tab == 0) {
            billsSection(state, onAddBill, onEditBill, onPay = { paying = it })
        }
        if (!state.loading && tab == 1) {
            loansSection(
                state = state,
                onAddLoan = onAddLoan,
                onEditLoan = onEditLoan,
                onRepay = { repaying = it },
                onDeletePayment = { deletingPaymentId = it },
            )
        }
    }

    paying?.let { row ->
        PayBillDialog(
            row = row,
            currency = state.currency,
            onConfirm = { amount, date ->
                viewModel.payBill(row.bill.id, amount, date)
                paying = null
            },
            onDismiss = { paying = null },
        )
    }

    repaying?.let { row ->
        RepayDialog(
            row = row,
            currency = state.currency,
            onSubmit = { amount, date, note, onResult ->
                viewModel.repay(row.loan.id, amount, date, note) { result ->
                    if (result == LoanResult.OK) repaying = null
                    onResult(result)
                }
            },
            onDismiss = { repaying = null },
        )
    }

    deletingPaymentId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingPaymentId = null },
            title = { Text(stringResource(R.string.obl_payment_delete_title)) },
            text = { Text(stringResource(R.string.obl_payment_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingPaymentId = null
                        viewModel.deletePayment(id)
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPaymentId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SummaryFigure(label: Int, value: Long, currency: CurrencyInfo, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(MoneyFormatter.format(value, currency), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun SummaryCard(state: ObligationsUiState) {
    val currency = state.currency
    val onSurface = MaterialTheme.colorScheme.onSurface
    val negative = MaterialTheme.yuzu.expense
    YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryFigure(R.string.obl_available, state.spendable, currency, if (state.spendable < 0) negative else onSurface, Modifier.weight(1f))
            SummaryFigure(R.string.obl_committed, state.committed, currency, onSurface, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryFigure(
                R.string.obl_free,
                state.freeEstimate,
                currency,
                if (state.freeEstimate < 0) negative else MaterialTheme.yuzu.income,
                Modifier.weight(1f),
            )
            SummaryFigure(R.string.obl_net_worth, state.netWorth, currency, if (state.netWorth < 0) negative else onSurface, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryFigure(R.string.obl_receivable, state.receivable, currency, onSurface, Modifier.weight(1f))
            SummaryFigure(R.string.obl_debts, state.debts, currency, onSurface, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.obl_summary_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.billsSection(
    state: ObligationsUiState,
    onAddBill: () -> Unit,
    onEditBill: (Long) -> Unit,
    onPay: (BillRow) -> Unit,
) {
    if (state.pendingBills.isEmpty()) {
        item {
            YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                IconBadge(Icons.Rounded.CalendarMonth)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.obl_bills_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.obl_bills_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAddBill) { Text(stringResource(R.string.obl_add_bill)) }
            }
        }
    } else {
        item {
            SectionHeader(
                title = stringResource(R.string.obl_bills_pending),
                modifier = Modifier.padding(horizontal = 16.dp),
                actionLabel = stringResource(R.string.obl_add_bill),
                onAction = onAddBill,
            )
        }
        item {
            YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                state.pendingBills.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    PendingBillRow(row, state.currency, onEdit = { onEditBill(row.bill.id) }, onPay = { onPay(row) })
                }
            }
        }
    }

    if (state.paidBills.isNotEmpty()) {
        item {
            SectionHeader(
                title = stringResource(R.string.obl_bills_paid),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            YuzuCard(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                state.paidBills.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.bill.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.obl_bill_paid_on, formatShortDate(row.bill.paidDate ?: row.bill.dueDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            MoneyFormatter.format(row.bill.amountMinor, state.currency),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingBillRow(row: BillRow, currency: CurrencyInfo, onEdit: () -> Unit, onPay: () -> Unit) {
    val bill = row.bill
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bill.name, style = MaterialTheme.typography.titleSmall)
                    if (row.status == BillStatus.OVERDUE) {
                        PillLabel(
                            text = stringResource(R.string.obl_pill_overdue),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                val dueText = when (row.status) {
                    BillStatus.OVERDUE -> stringResource(R.string.obl_bill_overdue, formatShortDate(bill.dueDate))
                    BillStatus.DUE_TODAY -> stringResource(R.string.obl_bill_due_today)
                    else -> stringResource(R.string.obl_bill_due, formatShortDate(bill.dueDate))
                }
                Text(
                    dueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.status == BillStatus.OVERDUE) MaterialTheme.yuzu.expense else muted,
                )
                if (bill.repeatDay > 0) {
                    Text(stringResource(R.string.obl_bill_repeats), style = MaterialTheme.typography.bodySmall, color = muted)
                }
            }
            Text(MoneyFormatter.format(bill.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
        }
        Button(onClick = onPay) { Text(stringResource(R.string.obl_pay)) }
    }
}

private fun LazyListScope.loansSection(
    state: ObligationsUiState,
    onAddLoan: (LoanDirection) -> Unit,
    onEditLoan: (Long) -> Unit,
    onRepay: (LoanRow) -> Unit,
    onDeletePayment: (Long) -> Unit,
) {
    if (state.lent.isEmpty() && state.borrowed.isEmpty()) {
        item {
            YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                IconBadge(Icons.Rounded.SwapHoriz)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.obl_loans_empty_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.obl_loans_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onAddLoan(LoanDirection.LENT) }) { Text(stringResource(R.string.obl_add_loan)) }
            }
        }
        return
    }
    if (state.lent.isNotEmpty()) {
        item {
            SectionHeader(
                title = stringResource(R.string.obl_lent_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                actionLabel = stringResource(R.string.obl_add_loan),
                onAction = { onAddLoan(LoanDirection.LENT) },
            )
        }
        state.lent.forEach { row ->
            item(key = "loan-${row.loan.id}") { LoanCard(row, state.currency, onEditLoan, onRepay, onDeletePayment) }
        }
    }
    if (state.borrowed.isNotEmpty()) {
        item {
            SectionHeader(
                title = stringResource(R.string.obl_borrowed_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                actionLabel = stringResource(R.string.obl_add_loan),
                onAction = { onAddLoan(LoanDirection.BORROWED) },
            )
        }
        state.borrowed.forEach { row ->
            item(key = "loan-${row.loan.id}") { LoanCard(row, state.currency, onEditLoan, onRepay, onDeletePayment) }
        }
    }
}

@Composable
private fun LoanCard(
    row: LoanRow,
    currency: CurrencyInfo,
    onEdit: (Long) -> Unit,
    onRepay: (LoanRow) -> Unit,
    onDeletePayment: (Long) -> Unit,
) {
    val loan = row.loan
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by rememberSaveable(loan.id) { mutableStateOf(false) }
    YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(loan.id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(loan.person, style = MaterialTheme.typography.titleMedium)
                val detail = formatShortDate(loan.startDate) + if (loan.note.isNotBlank()) " · ${loan.note}" else ""
                Text(detail, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (row.settled) {
                PillLabel(
                    text = stringResource(R.string.obl_loan_settled),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
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
                R.string.obl_loan_progress,
                MoneyFormatter.format(row.outstanding, currency),
                MoneyFormatter.format(loan.principalMinor, currency),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!row.settled) {
                Button(onClick = { onRepay(row) }) {
                    Text(
                        stringResource(
                            if (loan.direction == LoanDirection.LENT) R.string.obl_repay_lent else R.string.obl_repay_borrowed,
                        ),
                    )
                }
            }
            if (row.payments.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.obl_payments_hide)
                        } else {
                            stringResource(R.string.obl_payments_show, row.payments.size)
                        },
                    )
                }
            }
        }
        if (expanded) {
            row.payments.forEach { payment ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(MoneyFormatter.format(payment.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
                        val detail = formatShortDate(payment.date) + if (payment.note.isNotBlank()) " · ${payment.note}" else ""
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    IconButton(onClick = { onDeletePayment(payment.id) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayBillDialog(
    row: BillRow,
    currency: CurrencyInfo,
    onConfirm: (Long, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    var text by remember(row.bill.id) { mutableStateOf(AmountParser.toInputText(row.bill.amountMinor, currency)) }
    var date by remember(row.bill.id) { mutableStateOf(today) }
    val amount = AmountParser.parseToMinorUnits(text, currency)
    val valid = amount != null && amount > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.obl_pay_title, row.bill.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = AmountParser.sanitize(it, currency) },
                    label = { Text(stringResource(R.string.obl_pay_amount)) },
                    prefix = { Text("${currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = !valid,
                    supportingText = { if (valid) Text(MoneyFormatter.format(amount ?: 0L, currency)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.obl_pay_date),
                    date = date,
                    minDate = today.minusYears(5),
                    maxDate = today,
                    onDateChange = { date = it },
                )
                Text(
                    stringResource(R.string.obl_pay_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { amount?.let { onConfirm(it, date) } }) {
                Text(stringResource(R.string.obl_pay))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RepayDialog(
    row: LoanRow,
    currency: CurrencyInfo,
    onSubmit: (Long, LocalDate, String, (LoanResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now()
    val lent = row.loan.direction == LoanDirection.LENT
    var text by remember(row.loan.id) { mutableStateOf(AmountParser.toInputText(row.outstanding, currency)) }
    var date by remember(row.loan.id) { mutableStateOf(today) }
    var note by remember(row.loan.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<LoanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val amount = AmountParser.parseToMinorUnits(text, currency)
    val valid = amount != null && amount > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (lent) R.string.obl_repay_title_lent else R.string.obl_repay_title_borrowed,
                    row.loan.person,
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
                    label = { Text(stringResource(R.string.obl_repay_amount)) },
                    prefix = { Text("${currency.symbol} ") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (currency.fractionDigits == 0) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        when (error) {
                            LoanResult.TOO_MUCH -> Text(stringResource(R.string.obl_repay_error))
                            null -> Text(
                                stringResource(R.string.obl_repay_outstanding, MoneyFormatter.format(row.outstanding, currency)),
                            )
                            else -> Text(stringResource(R.string.obl_repay_error_generic))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = stringResource(R.string.obl_repay_date),
                    date = date,
                    minDate = row.loan.startDate,
                    maxDate = today,
                    onDateChange = { date = it },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    label = { Text(stringResource(R.string.obl_repay_note)) },
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
                        if (result != LoanResult.OK) error = result
                    }
                },
            ) {
                Text(stringResource(if (lent) R.string.obl_repay_lent else R.string.obl_repay_borrowed))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
