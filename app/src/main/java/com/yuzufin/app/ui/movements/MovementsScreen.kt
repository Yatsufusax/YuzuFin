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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
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
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.yuzufin.app.ui.statement.StatementExportDialog
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
    var showExport by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MovementItem?>(null) }
    val photoIds by container.attachmentRepository.transactionIdsWithPhotos.collectAsStateWithLifecycle(emptySet())
    val scope = rememberCoroutineScope()
    if (showExport) StatementExportDialog(onDismiss = { showExport = false })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_movements),
                actions = {
                    IconButton(onClick = { showExport = true }) {
                        Icon(
                            Icons.Rounded.PictureAsPdf,
                            contentDescription = stringResource(R.string.export_open),
                        )
                    }
                },
            )
        }

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
                        SwipeableMovementRow(
                            item = item,
                            state = state,
                            hasPhoto = item.id in photoIds,
                            onClick = { if (!item.isInternal) onEdit(item.id) },
                            onRequestDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { container.financeRepository.deleteTransaction(target.id) }
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMovementRow(
    item: MovementItem,
    state: MovementsUiState,
    hasPhoto: Boolean,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    // Los movimientos de préstamos se gestionan desde Obligaciones: no se deslizan.
    if (item.isInternal) {
        MovementListRow(item = item, state = state, hasPhoto = hasPhoto, onClick = onClick)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            // Siempre vuelve a su lugar: el borrado real ocurre después de confirmar.
            false
        },
    )
    val swiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (swiping) MaterialTheme.yuzu.expense else Color.Transparent)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (swiping) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
            MovementListRow(item = item, state = state, hasPhoto = hasPhoto, onClick = onClick)
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
private fun MovementListRow(item: MovementItem, state: MovementsUiState, hasPhoto: Boolean, onClick: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasPhoto) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = stringResource(R.string.photos_has),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
            Text(
                text = if (item.isInternal) stringResource(R.string.home_internal_transfer) else item.categoryName,
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
