package com.yuzufin.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.yuzufin.app.domain.TextScale

/** Tope del tamaño de letra final (teléfono × Monea) para que nada se rompa con textos enormes. */
private const val MAX_FONT_SCALE = 2f

/**
 * Aplica el tamaño de texto que la persona eligió en Ajustes. Se multiplica por el tamaño de
 * letra del teléfono (Accesibilidad de Android), así que ambos suman.
 */
@Composable
fun ProvideTextScale(scale: TextScale, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val density = remember(base.density, base.fontScale, scale) {
        Density(base.density, (base.fontScale * scale.factor).coerceAtMost(MAX_FONT_SCALE))
    }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/**
 * Limita el tamaño de letra de una zona de tamaño fijo (la barra de abajo, la tarjeta del saldo),
 * donde un texto más grande se cortaría. Todo lo demás de la app sí crece sin límite razonable.
 */
@Composable
fun CapFontScale(max: Float, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    if (base.fontScale <= max) {
        content()
        return
    }
    val density = remember(base.density, max) { Density(base.density, max) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
