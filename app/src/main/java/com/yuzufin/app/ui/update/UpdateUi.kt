package com.yuzufin.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.data.update.ApkInstaller
import com.yuzufin.app.data.update.ReleaseNotes
import com.yuzufin.app.data.update.UpdateInfo

/**
 * Las actualizaciones son obligatorias: mientras haya una pendiente se muestra esta ventana,
 * que no se cierra tocando afuera ni con el botón atrás. Lista lo que cambió y guía la
 * instalación. También abre el instalador de Android apenas termina la descarga.
 */
@Composable
fun UpdateEffects(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val ready = state as? UpdateState.ReadyToInstall
    LaunchedEffect(ready?.file, ready?.installLaunched) {
        if (ready != null && !ready.installLaunched) {
            runCatching { ApkInstaller.installOrRequestPermission(context, ready.file) }
            viewModel.onInstallLaunched()
        }
    }

    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        is UpdateState.DownloadFailed -> s.info
        else -> return
    }
    ForcedUpdateDialog(
        info = info,
        state = state,
        onInstall = { ready?.let { runCatching { ApkInstaller.installOrRequestPermission(context, it.file) } } },
        onRetry = viewModel::download,
    )
}

@Composable
private fun ForcedUpdateDialog(
    info: UpdateInfo,
    state: UpdateState,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
) {
    val sections = remember(info.notes) { ReleaseNotes.parse(info.notes) }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.update_forced_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.update_forced_body, info.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (sections.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        sections.forEach { section ->
                            section.title?.let {
                                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            section.items.forEach { item ->
                                Text("•  $item", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                when (state) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.settings_update_downloading, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    is UpdateState.ReadyToInstall -> Text(
                        stringResource(R.string.update_forced_ready),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is UpdateState.DownloadFailed -> Text(
                        stringResource(R.string.update_forced_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        stringResource(R.string.update_forced_preparing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.ReadyToInstall -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_install_now))
                }
                is UpdateState.DownloadFailed -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.update_retry))
                }
                else -> TextButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.update_downloading_button))
                }
            }
        },
    )
}
