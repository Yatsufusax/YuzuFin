package com.yuzufin.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.domain.MonkiProactiveMode
import com.yuzufin.app.ui.components.MoneaCard

/** Cuánto te escribe Monki por su cuenta: todo, solo lo importante o nada. Los mensajes van a la campanilla. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonkiProactiveCard(
    mode: MonkiProactiveMode,
    onModeChange: (MonkiProactiveMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    MoneaCard(modifier = modifier) {
        Text(stringResource(R.string.settings_monki_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == MonkiProactiveMode.FULL,
                onClick = { onModeChange(MonkiProactiveMode.FULL) },
                label = { Text(stringResource(R.string.settings_monki_full)) },
            )
            FilterChip(
                selected = mode == MonkiProactiveMode.IMPORTANT,
                onClick = { onModeChange(MonkiProactiveMode.IMPORTANT) },
                label = { Text(stringResource(R.string.settings_monki_important)) },
            )
            FilterChip(
                selected = mode == MonkiProactiveMode.OFF,
                onClick = { onModeChange(MonkiProactiveMode.OFF) },
                label = { Text(stringResource(R.string.settings_monki_off)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_monki_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
