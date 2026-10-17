package com.yuzufin.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.data.CategorySaveResult
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.TransactionType
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.categoryIcon
import com.yuzufin.app.ui.common.categoryIconKeys
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import kotlinx.coroutines.launch

/** Gestión de categorías: crear, renombrar, cambiar ícono, archivar/restaurar y eliminar si no se usan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(contentPadding: PaddingValues, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val finance = rememberAppContainer().financeRepository
    val scope = rememberCoroutineScope()
    val all by finance.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var typeName by rememberSaveable { mutableStateOf(TransactionType.EXPENSE.name) }
    val type = TransactionType.valueOf(typeName)
    // Los de préstamos son internos: no se editan desde acá.
    val visible = all.filter { it.type == type && it.iconKey != LOAN_ICON_KEY }
    var dialog by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var blocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.categories_title), onBack = onBack)
        Text(
            stringResource(R.string.categories_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = type == TransactionType.EXPENSE,
                onClick = { typeName = TransactionType.EXPENSE.name },
                label = { Text(stringResource(R.string.categories_tab_expense)) },
            )
            FilterChip(
                selected = type == TransactionType.INCOME,
                onClick = { typeName = TransactionType.INCOME.name },
                label = { Text(stringResource(R.string.categories_tab_income)) },
            )
        }
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.categories_add))
        }

        val active = visible.filter { !it.archived }
        val archived = visible.filter { it.archived }
        if (visible.isEmpty()) {
            Text(stringResource(R.string.categories_empty), modifier = Modifier.padding(horizontal = 16.dp))
        }
        active.forEach { category ->
            CategoryRow(
                category = category,
                onEdit = { dialog = category },
                onToggleArchive = { scope.launch { finance.setCategoryArchived(category.id, true) } },
                onDelete = { pendingDelete = category },
            )
        }
        if (archived.isNotEmpty()) {
            Text(
                stringResource(R.string.categories_archived_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            archived.forEach { category ->
                CategoryRow(
                    category = category,
                    onEdit = { dialog = category },
                    onToggleArchive = { scope.launch { finance.setCategoryArchived(category.id, false) } },
                    onDelete = { pendingDelete = category },
                )
            }
        }
        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }

    if (creating || dialog != null) {
        val editing = dialog
        CategoryDialog(
            existing = editing,
            onDismiss = { creating = false; dialog = null },
            onSave = { name, iconKey ->
                finance.saveCategory(editing?.id ?: 0L, name, editing?.type ?: type, iconKey).also { result ->
                    if (result == CategorySaveResult.OK) {
                        creating = false
                        dialog = null
                    }
                }
            },
        )
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.categories_delete)) },
            text = { Text(stringResource(R.string.categories_delete_confirm, category.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { if (!finance.deleteCategoryIfUnused(category.id)) blocked = true }
                }) { Text(stringResource(R.string.categories_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            text = { Text(stringResource(R.string.categories_delete_blocked)) },
            confirmButton = { TextButton(onClick = { blocked = false }) { Text(stringResource(R.string.action_accept)) } },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onToggleArchive: () -> Unit, onDelete: () -> Unit) {
    YuzuCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(icon = categoryIcon(category.iconKey), size = 40.dp)
                Text(category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onToggleArchive) {
                Icon(
                    if (category.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    contentDescription = stringResource(if (category.archived) R.string.categories_restore else R.string.categories_archive),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.categories_delete))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(
    existing: Category?,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, iconKey: String) -> CategorySaveResult,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var iconKey by remember { mutableStateOf(existing?.iconKey ?: "other") }
    var error by remember { mutableStateOf<CategorySaveResult?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.categories_dialog_new else R.string.categories_dialog_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30); error = null },
                    label = { Text(stringResource(R.string.categories_name_hint)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        when (error) {
                            CategorySaveResult.EMPTY_NAME -> Text(stringResource(R.string.categories_error_empty))
                            CategorySaveResult.DUPLICATE -> Text(stringResource(R.string.categories_error_duplicate))
                            else -> Unit
                        }
                    },
                )
                Text(stringResource(R.string.categories_icon_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryIconKeys.forEach { key ->
                        val selected = key == iconKey
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { iconKey = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                categoryIcon(key),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { error = onSave(name, iconKey).takeIf { it != CategorySaveResult.OK } } }) {
                Text(stringResource(R.string.categories_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
