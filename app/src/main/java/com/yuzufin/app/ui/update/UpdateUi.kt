package com.yuzufin.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.data.update.ApkInstaller
import com.yuzufin.app.ui.theme.yuzu

/**
 * Efectos que no dibujan nada: abre el instalador cuando la descarga terminó y muestra el
 * aviso emergente cuando la búsqueda automática al iniciar encontró una versión nueva.
 */
@Composable
fun UpdateEffects(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dialogOpen by viewModel.dialogOpen.collectAsStateWithLifecycle()

    val ready = state as? UpdateState.ReadyToInstall
    LaunchedEffect(ready?.file, ready?.installLaunched) {
        if (ready != null && !ready.installLaunched) {
            runCatching { ApkInstaller.installOrRequestPermission(context, ready.file) }
            viewModel.onInstallLaunched()
        }
    }

    val available = state as? UpdateState.Available
    if (dialogOpen && available != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_update_available, available.info.versionName))
                    if (available.info.notes.isNotBlank()) {
                        Text(
                            text = available.info.notes.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissDialog()
                        viewModel.download()
                    },
                ) { Text(stringResource(R.string.settings_update_download)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text(stringResource(R.string.update_dialog_later))
                }
            },
        )
    }
}

/** Tarjeta verde de Inicio: solo aparece si hay una versión nueva (o se está descargando). */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.yuzu

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.ReadyToInstall -> s.info
        else -> return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(colors.bannerStart, colors.bannerEnd)))
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.update_banner_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onBanner,
        )
        Text(
            text = stringResource(R.string.settings_update_available, info.versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBanner,
        )
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is UpdateState.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_update_downloading, (s.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBanner,
                )
            }
            is UpdateState.ReadyToInstall -> {
                Text(
                    text = stringResource(R.string.update_banner_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBanner,
                )
                Spacer(Modifier.height(8.dp))
                StrongButton(R.string.settings_update_install) {
                    runCatching { ApkInstaller.installOrRequestPermission(context, s.file) }
                }
            }
            else -> StrongButton(R.string.settings_update_download, onClick = viewModel::download)
        }
    }
}

@Composable
private fun StrongButton(labelRes: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.yuzu
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.strongButton,
            contentColor = colors.onStrongButton,
        ),
    ) {
        Text(stringResource(labelRes))
    }
}
