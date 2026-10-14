package com.yuzufin.app.ui.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzufin.app.R
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.MovementItem
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.dayLabel
import com.yuzufin.app.ui.common.formatMonthYear
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.yuzu

@Composable
fun MovementsScreen(
    contentPadding: PaddingValues,
    onEdit: (Long) -> Unit,
    onNewMovement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val viewModel: MovementsViewModel = viewModel(factory = MovementsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filters = state.filters

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.nav_movements)) }

        item {
            OutlinedTextField(
                value = filters.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.movements_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        item { MonthSelector(state, viewModel) }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filters.type == TypeFilter.ALL,
                        onClick = { viewModel.setType(TypeFilter.ALL) },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = filters.type == TypeFilter.INCOME,
                        onClick = { viewModel.setType(TypeFilter.INCOME) },
                        label = { Text(stringResource(R.string.filter_income)) },
                    )
                }
                item {
                    FilterChip(
                        selected = filters.type == TypeFilter.EXPENSE,
                        onClick = { viewModel.setType(TypeFilter.EXPENSE) },
                        label = { Text(stringResource(R.string.filter_expense)) },
                    )
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = filters.categoryId == category.id,
                        onClick = {
                            viewModel.setCategory(if (filters.categoryId == category.id) null else category.id)
                        },
                        label = { Text(category.name) },
                    )
                }
            }
        }

        if (state.groups.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.movements_total_income,
                            MoneyFormatter.format(state.totalIncome, state.currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.yuzu.income,
                    )
                    Text(
                        text = stringResource(
                            R.string.movements_total_expense,
                            MoneyFormatter.format(state.totalExpense, state.currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.yuzu.expense,
                    )
                }
            }
        }

        if (state.groups.isEmpty() && !state.loading) {
            item {
                EmptyMovements(
                    hasAnyMovements = state.hasAnyMovements,
                    onNewMovement = onNewMovement,
                )
            }
        }

        items(state.groups, key = { it.date.toEpochDay() }) { group ->
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = dayLabel(group.date),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                YuzuCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    group.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MovementListRow(item = item, state = state, onClick = { onEdit(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(state: MovementsUiState, viewModel: MovementsViewModel) {
    val month = state.filters.month
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::previousMonth, enabled = month != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.movements_previous_month),
            )
        }
        Text(
            text = if (month != null) formatMonthYear(month) else stringResource(R.string.movements_all_history),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = viewModel::nextMonth, enabled = state.canGoToNextMonth) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.movements_next_month),
            )
        }
        FilterChip(
            selected = month == null,
            onClick = { if (month == null) viewModel.showCurrentMonth() else viewModel.showAllHistory() },
            label = { Text(stringResource(R.string.movements_all_history_chip)) },
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun MovementListRow(item: MovementItem, state: MovementsUiState, onClick: () -> Unit) {
    val colors = MaterialTheme.yuzu
    val isIncome = item.type == TransactionType.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = categoryIcon(item.iconKey),
            size = 44.dp,
            container = (if (isIncome) colors.income else colors.expense).copy(alpha = 0.14f),
            tint = if (isIncome) colors.income else colors.expense,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (isIncome) {
                MoneyFormatter.format(item.amountMinor, state.currency, showPlus = true)
            } else {
                MoneyFormatter.format(-item.amountMinor, state.currency)
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) colors.income else colors.expense,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyMovements(hasAnyMovements: Boolean, onNewMovement: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (hasAnyMovements) R.string.movements_empty_filtered else R.string.movements_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasAnyMovements) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onNewMovement) { Text(stringResource(R.string.movements_register_first)) }
        }
    }
}
