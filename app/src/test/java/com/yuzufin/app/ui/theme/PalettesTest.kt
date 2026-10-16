package com.yuzufin.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
                yuzuColorScheme(palette, dark = false).background,
                yuzuColorScheme(palette, dark = true).background,
            )
        }
    }

    @Test
    fun greenKeepsOriginalColors() {
        assertEquals(LightColors, yuzuColorScheme(ThemePalette.GREEN, dark = false))
        assertEquals(DarkYuzuColors, yuzuExtraColors(ThemePalette.GREEN, dark = true))
    }

    @Test
    fun unknownNamesFallBack() {
        assertEquals(ThemePalette.GREEN, ThemePalette.fromName("nope"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
    }
}
