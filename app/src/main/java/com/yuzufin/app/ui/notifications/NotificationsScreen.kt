package com.yuzufin.app.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.domain.AppNotification
import com.yuzufin.app.domain.NotificationKind
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.common.formatShortDate
import com.yuzufin.app.ui.components.IconBadge
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Campanilla: historial de los avisos que generó la app. Al abrirla se marcan como leídos. */
@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = rememberAppContainer().notificationRepository
    val scope = rememberCoroutineScope()
    val list by repository.notifications.collectAsStateWithLifecycle(initialValue = null)
    var unreadAtOpen by remember { mutableStateOf<Set<Long>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // Se recuerda qué estaba sin leer al abrir (para resaltarlo) y se marca todo como leído.
    LaunchedEffect(list) {
        val current = list ?: return@LaunchedEffect
        if (unreadAtOpen == null) {
            unreadAtOpen = current.filter { !it.read }.map { it.id }.toSet()
            repository.markAllRead()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.notif_title),
            onBack = onBack,
            actions = {
                if (!list.isNullOrEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.notif_clear_all))
                    }
                }
            },
        )
        val rows = list
        when {
            rows == null -> Unit
            rows.isEmpty() -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        highlighted = unreadAtOpen?.contains(item.id) == true,
                        onDelete = { scope.launch { repository.delete(item.id) } },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.notif_footer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.notif_clear_title)) },
            text = { Text(stringResource(R.string.notif_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { repository.clear() }
                }) { Text(stringResource(R.string.notif_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.notif_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.notif_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotificationRow(item: AppNotification, highlighted: Boolean, onDelete: () -> Unit) {
    YuzuCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon = kindIcon(item.kind))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (highlighted) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    whenLabel(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.notif_delete),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun kindIcon(kind: NotificationKind): ImageVector = when (kind) {
    NotificationKind.INCOME_PENDING, NotificationKind.INCOME_AUTO, NotificationKind.INCOME_SOON -> Icons.Rounded.Payments
    NotificationKind.BILL -> Icons.Rounded.Receipt
    NotificationKind.LOAN_OWED, NotificationKind.LOAN_OWE -> Icons.Rounded.SwapHoriz
    NotificationKind.SAVINGS -> Icons.Rounded.Savings
    NotificationKind.BUDGET -> Icons.Rounded.PieChart
    NotificationKind.OTHER -> Icons.Rounded.AccountBalanceWallet
}

@Composable
private fun whenLabel(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val time = moment.format(DateTimeFormatter.ofPattern("HH:mm"))
    val date = moment.toLocalDate()
    val day = when (date) {
        today -> stringResource(R.string.notif_today)
        today.minusDays(1) -> stringResource(R.string.notif_yesterday)
        else -> formatShortDate(date)
    }
    return "$day · $time"
}
