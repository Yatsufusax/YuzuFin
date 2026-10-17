package com.yuzufin.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.StatsCalculator
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.stats.ProgressBar
import com.yuzufin.app.ui.stats.budgetColor
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY

private data class BudgetsData(
    val currency: CurrencyInfo,
    val categories: List<Category>,
    val limits: Map<Long, Long>,
    val spent: Map<Long, Long>,
)

/** Un tope mensual por categoría de gasto. Se edita con un diálogo; límite vacío = sin tope. */
@Composable
fun BudgetsScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val data by remember {
        combine(
            container.financeRepository.categories,
            container.financeRepository.transactions,
            container.financeRepository.initialBalance,
            container.budgetRepository.budgets,
            container.settingsRepository.settings,
        ) { categories, transactions, initial, budgets, settings ->
            BudgetsData(
                currency = CurrencyInfo.fromCode(settings.currencyCode),
                categories = categories.filter {
                    it.type == TransactionType.EXPENSE && !it.archived && it.iconKey != LOAN_ICON_KEY
                },
                limits = budgets.associate { it.categoryId to it.limitMinor },
                spent = StatsCalculator.expenseByCategory(initial, transactions, YearMonth.now()),
            )
        }
    }.collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf<Category?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.budgets_title), onBack = onBack)
        Text(
            stringResource(R.string.budgets_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val current = data
        if (current != null) {
            if (current.categories.isEmpty()) {
                Text(
                    stringResource(R.string.budgets_empty_categories),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            current.categories.forEach { category ->
                val limit = current.limits[category.id]
                val spent = current.spent[category.id] ?: 0L
                YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(icon = categoryIcon(category.iconKey), size = 40.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (limit != null) {
                                    stringResource(
                                        R.string.stats_budget_progress,
                                        MoneyFormatter.format(spent, current.currency),
                                        MoneyFormatter.format(limit, current.currency),
                                    )
                                } else {
                                    stringResource(R.string.budgets_spent, MoneyFormatter.format(spent, current.currency))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { editing = category }) {
                            Text(stringResource(if (limit == null) R.string.budgets_set else R.string.budgets_edit))
                        }
                    }
                    if (limit != null) {
                        val progress = com.yuzufin.app.domain.BudgetProgress(category.id, limit, spent)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(fraction = progress.fraction, color = budgetColor(progress.level))
                    }
                }
            }
        }
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }

    val category = editing
    val loaded = data
    if (category != null && loaded != null) {
        LimitDialog(
            category = category,
            currency = loaded.currency,
            currentLimit = loaded.limits[category.id],
            onDismiss = { editing = null },
            onSave = { limit ->
                editing = null
                scope.launch { container.budgetRepository.setLimit(category.id, limit) }
            },
        )
    }
}

@Composable
private fun LimitDialog(
    category: Category,
    currency: CurrencyInfo,
    currentLimit: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(currentLimit?.let { AmountParser.toInputText(it, currency) }.orEmpty()) }
    val parsed = AmountParser.parseToMinorUnits(text, currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budgets_dialog_title, category.name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = AmountParser.sanitize(it, currency) },
                label = { Text(stringResource(R.string.budgets_dialog_hint)) },
                prefix = { Text(currency.symbol + " ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(parsed ?: 0L) }, enabled = parsed != null && parsed > 0L) {
                Text(stringResource(R.string.budgets_dialog_save))
            }
        },
        dismissButton = {
            Row {
                if (currentLimit != null) {
                    TextButton(onClick = { onSave(0L) }) { Text(stringResource(R.string.budgets_dialog_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
