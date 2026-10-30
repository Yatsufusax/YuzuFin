package com.yuzufin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.yuzufin.app.domain.ThemePalette

/** Esquinas generosas, como las tarjetas de la referencia bancaria. */
internal val MoneaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun MoneaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.GREEN,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(palette, darkTheme) { moneaColorScheme(palette, darkTheme) }
    val moneaColors = remember(palette, darkTheme) { moneaExtraColors(palette, darkTheme) }

    CompositionLocalProvider(LocalMoneaColors provides moneaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MoneaTypography,
            shapes = MoneaShapes,
            content = content,
        )
    }
}
