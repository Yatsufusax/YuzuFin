package com.yuzufin.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yuzufin.app.R
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import com.yuzufin.app.ui.components.YuzuCard
import com.yuzufin.app.ui.theme.paletteSwatch
import com.yuzufin.app.ui.theme.paletteSwatchAccent
import androidx.compose.foundation.selection.selectable

/** Apariencia: modo (sistema / claro / oscuro) y paleta de color. Se aplica al instante. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceCard(
    palette: ThemePalette,
    mode: ThemeMode,
    onPaletteChange: (ThemePalette) -> Unit,
    onModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewDark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    YuzuCard(modifier = modifier) {
        Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.settings_mode_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(R.string.settings_mode_system, mode == ThemeMode.SYSTEM) { onModeChange(ThemeMode.SYSTEM) }
            ModeChip(R.string.settings_mode_light, mode == ThemeMode.LIGHT) { onModeChange(ThemeMode.LIGHT) }
            ModeChip(R.string.settings_mode_dark, mode == ThemeMode.DARK) { onModeChange(ThemeMode.DARK) }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_palette_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemePalette.entries.forEach { option ->
                PaletteSwatch(
                    palette = option,
                    dark = previewDark,
                    selected = option == palette,
                    onClick = { onPaletteChange(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_appearance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeChip(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(labelRes)) })
}

@Composable
private fun PaletteSwatch(palette: ThemePalette, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val name = stringResource(paletteNameRes(palette))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(paletteSwatch(palette, dark), paletteSwatchAccent(palette, dark)))),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun paletteNameRes(palette: ThemePalette): Int = when (palette) {
    ThemePalette.GREEN -> R.string.palette_green
    ThemePalette.VIOLET -> R.string.palette_violet
    ThemePalette.NAVY -> R.string.palette_navy
    ThemePalette.RED -> R.string.palette_red
    ThemePalette.ORANGE -> R.string.palette_orange
    ThemePalette.PINK -> R.string.palette_pink
    ThemePalette.TEAL -> R.string.palette_teal
}
