package com.yuzufin.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.data.BackupManager
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.YuzuCard
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Crear y restaurar copias de seguridad. Las dos acciones pasan por el selector de archivos de Android. */
@Composable
fun BackupCard(modifier: Modifier = Modifier) {
    val backup = rememberAppContainer().backupManager
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restored by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                message = if (backup.export(uri)) R.string.backup_export_ok else R.string.backup_export_failed
                busy = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.backup_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy,
                onClick = { message = null; exportLauncher.launch("YuzuFin-copia-${LocalDate.now()}.zip") },
            ) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    message = null
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
            ) { Text(stringResource(R.string.backup_restore)) }
        }
        val text = if (busy) R.string.backup_working else message
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        val result = backup.restore(uri)
                        busy = false
                        if (result == BackupManager.RestoreResult.OK) {
                            restored = true
                        } else {
                            message = when (result) {
                                BackupManager.RestoreResult.NOT_A_BACKUP -> R.string.backup_error_not_backup
                                BackupManager.RestoreResult.TOO_NEW -> R.string.backup_error_too_new
                                BackupManager.RestoreResult.CORRUPT -> R.string.backup_error_corrupt
                                else -> R.string.backup_error_failed
                            }
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (restored) {
        // La base anterior quedó cerrada: hay que reiniciar la app para cargar la copia.
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restored_title)) },
            text = { Text(stringResource(R.string.backup_restored_body)) },
            confirmButton = {
                TextButton(onClick = { closeApp(context) }) { Text(stringResource(R.string.backup_restored_action)) }
            },
        )
    }
}

private fun closeApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
    if (launch != null) context.startActivity(launch)
    Runtime.getRuntime().exit(0)
}
