package com.yuzufin.app.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import com.yuzufin.app.ui.components.MoneaCard
import com.yuzufin.app.ui.settings.closeApp
import kotlinx.coroutines.launch

/**
 * «¿Ya usabas Monea?»: permite restaurar una copia de seguridad desde la configuración inicial,
 * por ejemplo al cambiar de teléfono. Usa el mismo [BackupManager] que Ajustes. Al terminar la
 * app se reinicia sola y, como la copia trae la configuración inicial hecha, entra directo a Inicio.
 */
@Composable
fun RestoreBackupCard(modifier: Modifier = Modifier) {
    val backup = rememberAppContainer().backupManager
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restored by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    MoneaCard(modifier = modifier) {
        Text(stringResource(R.string.onboarding_restore_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.onboarding_restore_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                message = null
                importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            },
        ) { Text(stringResource(R.string.onboarding_restore_action)) }
        val text = if (busy) stringResource(R.string.backup_working) else message
        if (text != null) {
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.onboarding_restore_confirm_body)) },
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
                            message = context.getString(
                                when (result) {
                                    BackupManager.RestoreResult.NOT_A_BACKUP -> R.string.backup_error_not_backup
                                    BackupManager.RestoreResult.TOO_NEW -> R.string.backup_error_too_new
                                    BackupManager.RestoreResult.CORRUPT -> R.string.backup_error_corrupt
                                    else -> R.string.backup_error_failed
                                },
                            )
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
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
