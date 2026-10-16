package com.yuzufin.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.yuzufin.app.domain.ThemePalette
import kotlin.math.abs

/**
 * Paletas de la app. El verde conserva los colores originales; las demás se generan a partir de
 * un tono (hue) para que los fondos, superficies y contenedores combinen con el color principal
 * tanto en modo claro como en oscuro.
 */
private class PaletteSpec(
    val hue: Float,
    val primaryLight: Color,
    val primaryDark: Color,
    val headerLight: Color,
    val bannerStartLight: Color,
    val bannerEndLight: Color,
    val onBannerLight: Color,
    val headerDark: Color,
    val bannerStartDark: Color,
    val bannerEndDark: Color,
)

private val White = Color.White

private val specs: Map<ThemePalette, PaletteSpec> = mapOf(
    ThemePalette.VIOLET to PaletteSpec(
        hue = 265f,
        primaryLight = Color(0xFF6D3FD0), primaryDark = Color(0xFFB79CFF),
        headerLight = Color(0xFFB9A0F5),
        bannerStartLight = Color(0xFF7B4DEB), bannerEndLight = Color(0xFF6038CF), onBannerLight = White,
        headerDark = Color(0xFF2B1A55),
        bannerStartDark = Color(0xFF5B35B8), bannerEndDark = Color(0xFF45288F),
    ),
    ThemePalette.NAVY to PaletteSpec(
        hue = 220f,
        primaryLight = Color(0xFF1F3F8F), primaryDark = Color(0xFF8FB0FF),
        headerLight = Color(0xFF8FB0F0),
        bannerStartLight = Color(0xFF2F55AD), bannerEndLight = Color(0xFF1D3B85), onBannerLight = White,
        headerDark = Color(0xFF12254F),
        bannerStartDark = Color(0xFF284A94), bannerEndDark = Color(0xFF1B3570),
    ),
    ThemePalette.RED to PaletteSpec(
        hue = 355f,
        primaryLight = Color(0xFFC62F3E), primaryDark = Color(0xFFFF8F98),
        headerLight = Color(0xFFF59AA2),
        bannerStartLight = Color(0xFFD93A49), bannerEndLight = Color(0xFFB92635), onBannerLight = White,
        headerDark = Color(0xFF4A1219),
        bannerStartDark = Color(0xFFA02B38), bannerEndDark = Color(0xFF7D1E29),
    ),
    ThemePalette.ORANGE to PaletteSpec(
        hue = 25f,
        primaryLight = Color(0xFFC25E00), primaryDark = Color(0xFFFFB067),
        headerLight = Color(0xFFFFC27A),
        bannerStartLight = Color(0xFFFFA64D), bannerEndLight = Color(0xFFF28A1E), onBannerLight = Color(0xFF3A1D00),
        headerDark = Color(0xFF4A2608),
        bannerStartDark = Color(0xFFB8650F), bannerEndDark = Color(0xFF92500B),
    ),
    ThemePalette.PINK to PaletteSpec(
        hue = 330f,
        primaryLight = Color(0xFFC2327A), primaryDark = Color(0xFFFF9CC8),
        headerLight = Color(0xFFF7A3C8),
        bannerStartLight = Color(0xFFF2599B), bannerEndLight = Color(0xFFD93C80), onBannerLight = White,
        headerDark = Color(0xFF4A1230),
        bannerStartDark = Color(0xFFA82A69), bannerEndDark = Color(0xFF86204F),
    ),
    ThemePalette.TEAL to PaletteSpec(
        hue = 185f,
        primaryLight = Color(0xFF0B7F8C), primaryDark = Color(0xFF5ED8E6),
        headerLight = Color(0xFF6FDCE6),
        bannerStartLight = Color(0xFF32CBDA), bannerEndLight = Color(0xFF1CA9B9), onBannerLight = Color(0xFF04262B),
        headerDark = Color(0xFF0E3A40),
        bannerStartDark = Color(0xFF138593), bannerEndDark = Color(0xFF0E6671),
    ),
)

/** Color principal de la paleta, para mostrar la muestra en el selector de Ajustes. */
fun paletteSwatch(palette: ThemePalette, dark: Boolean): Color {
    val spec = specs[palette] ?: return if (dark) DarkColors.primary else LightColors.primary
    return if (dark) spec.primaryDark else spec.primaryLight
}

/** Color del banner de la paleta (segundo color de la muestra). */
fun paletteSwatchAccent(palette: ThemePalette, dark: Boolean): Color {
    val spec = specs[palette] ?: return if (dark) DarkYuzuColors.bannerStart else LightYuzuColors.bannerStart
    return if (dark) spec.bannerStartDark else spec.bannerStartLight
}

fun yuzuColorScheme(palette: ThemePalette, dark: Boolean): ColorScheme {
    val spec = specs[palette] ?: return if (dark) DarkColors else LightColors
    val h = spec.hue
    return if (dark) {
        darkColorScheme(
            primary = spec.primaryDark,
            onPrimary = hsl(h, 0.80f, 0.12f),
            primaryContainer = hsl(h, 0.40f, 0.20f),
            onPrimaryContainer = hsl(h, 0.60f, 0.88f),
            secondary = hsl(h, 0.45f, 0.75f),
            onSecondary = hsl(h, 0.80f, 0.12f),
            secondaryContainer = hsl(h, 0.30f, 0.25f),
            onSecondaryContainer = hsl(h, 0.50f, 0.88f),
            tertiary = Color(0xFFFFB95C),
            onTertiary = Color(0xFF472A00),
            tertiaryContainer = Color(0xFF4A3000),
            onTertiaryContainer = Color(0xFFFFDDB0),
            background = hsl(h, 0.30f, 0.07f),
            onBackground = hsl(h, 0.15f, 0.90f),
            surface = hsl(h, 0.25f, 0.11f),
            onSurface = hsl(h, 0.15f, 0.90f),
            surfaceVariant = hsl(h, 0.18f, 0.19f),
            onSurfaceVariant = hsl(h, 0.10f, 0.70f),
            outline = hsl(h, 0.08f, 0.50f),
            outlineVariant = hsl(h, 0.12f, 0.25f),
            error = DarkColors.error,
            onError = DarkColors.onError,
            errorContainer = DarkColors.errorContainer,
            onErrorContainer = DarkColors.onErrorContainer,
            surfaceContainerLowest = hsl(h, 0.28f, 0.08f),
            surfaceContainerLow = hsl(h, 0.26f, 0.10f),
            surfaceContainer = hsl(h, 0.24f, 0.12f),
            surfaceContainerHigh = hsl(h, 0.22f, 0.15f),
            surfaceContainerHighest = hsl(h, 0.20f, 0.18f),
        )
    } else {
        lightColorScheme(
            primary = spec.primaryLight,
            onPrimary = Color.White,
            primaryContainer = hsl(h, 0.55f, 0.92f),
            onPrimaryContainer = hsl(h, 0.65f, 0.20f),
            secondary = hsl(h, 0.40f, 0.32f),
            onSecondary = Color.White,
            secondaryContainer = hsl(h, 0.40f, 0.89f),
            onSecondaryContainer = hsl(h, 0.55f, 0.16f),
            tertiary = Color(0xFFB26A00),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFEFD5),
            onTertiaryContainer = Color(0xFF5C3600),
            background = hsl(h, 0.25f, 0.95f),
            onBackground = hsl(h, 0.25f, 0.10f),
            surface = Color.White,
            onSurface = hsl(h, 0.25f, 0.10f),
            surfaceVariant = hsl(h, 0.16f, 0.90f),
            onSurfaceVariant = hsl(h, 0.10f, 0.38f),
            outline = hsl(h, 0.06f, 0.55f),
            outlineVariant = hsl(h, 0.12f, 0.84f),
            error = LightColors.error,
            onError = LightColors.onError,
            errorContainer = LightColors.errorContainer,
            onErrorContainer = LightColors.onErrorContainer,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, 0.30f, 0.98f),
            surfaceContainer = hsl(h, 0.25f, 0.96f),
            surfaceContainerHigh = hsl(h, 0.22f, 0.94f),
            surfaceContainerHighest = hsl(h, 0.18f, 0.92f),
        )
    }
}

fun yuzuExtraColors(palette: ThemePalette, dark: Boolean): YuzuColors {
    val spec = specs[palette] ?: return if (dark) DarkYuzuColors else LightYuzuColors
    val base = if (dark) DarkYuzuColors else LightYuzuColors
    return if (dark) {
        base.copy(
            headerTop = spec.headerDark,
            bannerStart = spec.bannerStartDark,
            bannerEnd = spec.bannerEndDark,
            onBanner = Color.White,
            strongButton = hsl(spec.hue, 0.15f, 0.90f),
            onStrongButton = hsl(spec.hue, 0.30f, 0.07f),
        )
    } else {
        base.copy(
            headerTop = spec.headerLight,
            bannerStart = spec.bannerStartLight,
            bannerEnd = spec.bannerEndLight,
            onBanner = spec.onBannerLight,
            strongButton = hsl(spec.hue, 0.20f, 0.14f),
            onStrongButton = Color.White,
        )
    }
}

/** Convierte tono (0–360), saturación y luminosidad (0–1) a un color. */
internal fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = (((hue % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
}
