package com.yuzufin.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    @Test
    fun hslPrimaryColors() {
        assertEquals(Color(1f, 0f, 0f), hsl(0f, 1f, 0.5f))
        assertEquals(Color(0f, 1f, 0f), hsl(120f, 1f, 0.5f))
        assertEquals(Color(0f, 0f, 1f), hsl(240f, 1f, 0.5f))
        assertEquals(Color.White, hsl(200f, 0.5f, 1f))
    }

    @Test
    fun everyPaletteHasDistinctLightAndDarkBackground() {
        ThemePalette.entries.forEach { palette ->
            assertNotEquals(
                moneaColorScheme(palette, dark = false).background,
                moneaColorScheme(palette, dark = true).background,
            )
        }
    }

    @Test
    fun greenKeepsOriginalColors() {
        assertEquals(LightColors, moneaColorScheme(ThemePalette.GREEN, dark = false))
        assertEquals(DarkMoneaColors, moneaExtraColors(ThemePalette.GREEN, dark = true))
    }

    @Test
    fun unknownNamesFallBack() {
        assertEquals(ThemePalette.GREEN, ThemePalette.fromName("nope"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
    }

    // --- Accesibilidad: contraste (WCAG AA) en todas las paletas, claro y oscuro ---

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(label: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val ratio = contrast(fg, bg)
        if (ratio < min) failures += "$label: %.2f < %.1f".format(ratio, min)
    }

    @Test
    fun textHasEnoughContrastInEveryPalette() {
        val failures = mutableListOf<String>()
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val tag = "${palette.name}/${if (dark) "oscuro" else "claro"}"
                val c = moneaColorScheme(palette, dark)
                val m = moneaExtraColors(palette, dark)
                // Texto normal: 4,5 a 1.
                check("$tag texto/superficie", c.onSurface, c.surface, 4.5, failures)
                check("$tag texto/fondo", c.onBackground, c.background, 4.5, failures)
                check("$tag texto suave/superficie", c.onSurfaceVariant, c.surface, 4.5, failures)
                check("$tag texto suave/fondo", c.onSurfaceVariant, c.background, 4.5, failures)
                check("$tag color principal/superficie", c.primary, c.surface, 4.5, failures)
                check("$tag color principal/fondo", c.primary, c.background, 4.5, failures)
                check("$tag sobre principal", c.onPrimary, c.primary, 4.5, failures)
                check("$tag sobre contenedor", c.onPrimaryContainer, c.primaryContainer, 4.5, failures)
                check("$tag ingresos/superficie", m.income, c.surface, 4.5, failures)
                check("$tag ingresos/fondo", m.income, c.background, 4.5, failures)
                check("$tag gastos/superficie", m.expense, c.surface, 4.5, failures)
                check("$tag gastos/fondo", m.expense, c.background, 4.5, failures)
                check("$tag sobre banner (inicio)", m.onBanner, m.bannerStart, 4.5, failures)
                check("$tag sobre banner (fin)", m.onBanner, m.bannerEnd, 4.5, failures)
                // Bordes de campos y controles: 3 a 1.
                check("$tag borde/superficie", c.outline, c.surface, 3.0, failures)
                check("$tag borde/fondo", c.outline, c.background, 3.0, failures)
            }
        }
        assertTrue("Contraste insuficiente:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
