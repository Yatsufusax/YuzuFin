package com.yuzufin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** Esquinas generosas, como las tarjetas de la referencia bancaria. */
internal val YuzuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun YuzuFinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val yuzuColors = if (darkTheme) DarkYuzuColors else LightYuzuColors

    CompositionLocalProvider(LocalYuzuColors provides yuzuColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = YuzuTypography,
            shapes = YuzuShapes,
            content = content,
        )
    }
}
