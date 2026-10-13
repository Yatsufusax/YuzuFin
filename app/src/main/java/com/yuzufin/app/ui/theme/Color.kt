package com.yuzufin.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Paleta de marca: verde vivo arriba, tarjetas blancas, verde bosque para contraste.
// ---------------------------------------------------------------------------
internal val BrandBright = Color(0xFF30DA8B)
internal val MintTile = Color(0xFFBCF2D0)
internal val Forest = Color(0xFF0F2A24)

// ---------------------------------------------------------------------------
// Tema claro
// ---------------------------------------------------------------------------
internal val LightColors = lightColorScheme(
    primary = Color(0xFF0F8A5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F4EB),
    onPrimaryContainer = Color(0xFF0B4F37),
    secondary = Color(0xFF1E6F5C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7EFE3),
    onSecondaryContainer = Color(0xFF0B3D31),
    tertiary = Color(0xFFB26A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFD5),
    onTertiaryContainer = Color(0xFF5C3600),
    background = Color(0xFFEDF3EF),
    onBackground = Color(0xFF16201C),
    surface = Color.White,
    onSurface = Color(0xFF16201C),
    surfaceVariant = Color(0xFFE4EBE7),
    onSurfaceVariant = Color(0xFF55625C),
    outline = Color(0xFF8A9791),
    outlineVariant = Color(0xFFD3DCD7),
    error = Color(0xFFD14343),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF7A1212),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6FAF8),
    surfaceContainer = Color(0xFFF0F5F2),
    surfaceContainerHigh = Color(0xFFEAF0ED),
    surfaceContainerHighest = Color(0xFFE4EBE7),
)

// ---------------------------------------------------------------------------
// Tema oscuro
// ---------------------------------------------------------------------------
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF17392C),
    onPrimaryContainer = Color(0xFFBCF2D0),
    secondary = Color(0xFF8FD5BC),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF1C4A3D),
    onSecondaryContainer = Color(0xFFC7F0E0),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF4A3000),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF0B1714),
    onBackground = Color(0xFFE1EAE5),
    surface = Color(0xFF13221D),
    onSurface = Color(0xFFE1EAE5),
    surfaceVariant = Color(0xFF24342E),
    onSurfaceVariant = Color(0xFFA9B8B1),
    outline = Color(0xFF73837C),
    outlineVariant = Color(0xFF33433D),
    error = Color(0xFFFF8A84),
    onError = Color(0xFF540C0C),
    errorContainer = Color(0xFF5E1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0E1B17),
    surfaceContainerLow = Color(0xFF111F1B),
    surfaceContainer = Color(0xFF16251F),
    surfaceContainerHigh = Color(0xFF1B2B25),
    surfaceContainerHighest = Color(0xFF21322B),
)

// ---------------------------------------------------------------------------
// Colores propios de la app que Material 3 no cubre.
// ---------------------------------------------------------------------------
@Immutable
data class YuzuColors(
    /** Verde de los ingresos y saldos positivos. */
    val income: Color,
    /** Rojo moderado de gastos, alertas y saldos negativos. */
    val expense: Color,
    /** Color superior del degradado de cabecera de Inicio. */
    val headerTop: Color,
    /** Degradado del banner destacado (inicio y fin). */
    val bannerStart: Color,
    val bannerEnd: Color,
    val onBanner: Color,
    /** Botón oscuro de énfasis (como “Crear ahorro” en la referencia). */
    val strongButton: Color,
    val onStrongButton: Color,
)

internal val LightYuzuColors = YuzuColors(
    income = Color(0xFF0F8A5A),
    expense = Color(0xFFD14343),
    headerTop = Color(0xFF30DA8B),
    bannerStart = Color(0xFF35EA83),
    bannerEnd = Color(0xFF2ACB7C),
    onBanner = Color(0xFF0C2B20),
    strongButton = Color(0xFF1E2128),
    onStrongButton = Color.White,
)

internal val DarkYuzuColors = YuzuColors(
    income = Color(0xFF3DDC97),
    expense = Color(0xFFFF8A84),
    headerTop = Color(0xFF0F3D2E),
    bannerStart = Color(0xFF1FA56C),
    bannerEnd = Color(0xFF168059),
    onBanner = Color.White,
    strongButton = Color(0xFFE1EAE5),
    onStrongButton = Color(0xFF0B1714),
)

internal val LocalYuzuColors = staticCompositionLocalOf { LightYuzuColors }

/** Acceso cómodo: `MaterialTheme.yuzu.income`. */
val MaterialTheme.yuzu: YuzuColors
    @Composable
    @ReadOnlyComposable
    get() = LocalYuzuColors.current
