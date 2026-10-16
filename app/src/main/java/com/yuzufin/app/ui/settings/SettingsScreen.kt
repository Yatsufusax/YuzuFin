package com.yuzufin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.yuzufin.app.data.SettingsRepository
import com.yuzufin.app.domain.AppSettings
import com.yuzufin.app.ui.common.rememberNotificationPermission
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.update.ApkInstaller
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.components.ScreenHeader
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.update.UpdateState
import com.yuzufin.app.ui.update.UpdateViewModel
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    updateViewModel: UpdateViewModel,
    modifier: Modifier = Modifier,
) {
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = updateViewModel.currentVersion
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.menu_settings), onBack = onBack)

        NameCard(
            savedName = settings?.userName.orEmpty(),
            loaded = settings != null,
            onSave = { name -> scope.launch { container.settingsRepository.setUserName(name) } },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        settings?.let { current ->
            AppearanceCard(
                palette = current.themePalette,
                mode = current.themeMode,
                onPaletteChange = { value -> scope.launch { container.settingsRepository.setThemePalette(value) } },
                onModeChange = { value -> scope.launch { container.settingsRepository.setThemeMode(value) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            RemindersCard(
                settings = current,
                onEnabledChange = { value -> scope.launch { container.settingsRepository.setRemindersEnabled(value) } },
                onHourChange = { hour -> scope.launch { container.settingsRepository.setReminderHour(hour) } },
                onShowAmountsChange = { value ->
                    scope.launch { container.settingsRepository.setShowAmountsInNotifications(value) }
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.settings_currency_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val currency = CurrencyInfo.fromCode(settings?.currencyCode)
            Text(
                text = "${currency.symbol} · ${currency.code}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_currency_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.settings_app_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_version, version.name),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(12.dp))

            when (val s = updateState) {
                UpdateState.Checking -> Text(
                    stringResource(R.string.settings_update_checking),
                    style = MaterialTheme.typography.bodyMedium,
                )
                UpdateState.UpToDate -> Text(
                    stringResource(R.string.settings_update_uptodate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                UpdateState.Error -> Text(
                    stringResource(R.string.settings_update_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is UpdateState.Available -> {
                    Text(
                        stringResource(R.string.settings_update_available, s.info.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (s.info.notes.isNotBlank()) {
                        Text(
                            text = s.info.notes.take(300),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    Text(
                        stringResource(R.string.settings_update_downloading, (s.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.ReadyToInstall -> Text(
                    stringResource(R.string.update_banner_ready),
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateState.DownloadFailed -> Text(
                    stringResource(R.string.update_forced_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                UpdateState.Idle -> Unit
            }

            Spacer(Modifier.height(12.dp))
            when (val s = updateState) {
                is UpdateState.Available -> Button(onClick = updateViewModel::download) {
                    Text(stringResource(R.string.settings_update_download))
                }
                is UpdateState.ReadyToInstall -> Button(
                    onClick = { runCatching { ApkInstaller.installOrRequestPermission(context, s.file) } },
                ) { Text(stringResource(R.string.settings_update_install)) }
                is UpdateState.Downloading -> Unit
                is UpdateState.DownloadFailed -> Button(onClick = updateViewModel::download) {
                    Text(stringResource(R.string.update_retry))
                }
                else -> Button(
                    onClick = { updateViewModel.check() },
                    enabled = s != UpdateState.Checking,
                ) { Text(stringResource(R.string.settings_check_updates)) }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_update_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YuzuCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.coming_soon_features_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            stringArrayResource(R.array.settings_features).forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
    }
}

@Composable
private fun NameCard(
    savedName: String,
    loaded: Boolean,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Se vuelve a leer del guardado solo cuando cambia el valor guardado.
    var text by remember(savedName, loaded) { mutableStateOf(savedName) }
    var justSaved by remember { mutableStateOf(false) }

    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.settings_name_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(SettingsRepository.MAX_NAME_LENGTH)
                justSaved = false
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            supportingText = {
                Text(
                    stringResource(
                        if (justSaved) R.string.settings_name_saved else R.string.settings_name_hint,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onSave(text)
                justSaved = true
            },
            enabled = loaded && text.trim() != savedName,
        ) { Text(stringResource(R.string.settings_name_save)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersCard(
    settings: AppSettings,
    onEnabledChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onShowAmountsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = rememberNotificationPermission()

    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.settings_reminders_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_reminders_switch), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_reminders_switch_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.remindersEnabled,
                onCheckedChange = { value ->
                    onEnabledChange(value)
                    if (value && !permission.granted) permission.request()
                },
            )
        }

        if (settings.remindersEnabled) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_reminders_hour), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_HOURS.forEach { hour ->
                    FilterChip(
                        selected = settings.reminderHour == hour,
                        onClick = { onHourChange(hour) },
                        label = { Text(String.format(java.util.Locale.ROOT, "%d:00", hour)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_reminders_hour_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_reminders_amounts), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_reminders_amounts_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showAmountsInNotifications, onCheckedChange = onShowAmountsChange)
            }

            if (!permission.granted) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.reminders_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                if (permission.needsSettings) {
                    OutlinedButton(onClick = permission.openSettings) {
                        Text(stringResource(R.string.reminders_open_settings))
                    }
                } else {
                    Button(onClick = permission.request) { Text(stringResource(R.string.reminders_enable)) }
                }
            }
        }
    }
}

private val REMINDER_HOURS = listOf(8, 9, 12, 18, 20)
