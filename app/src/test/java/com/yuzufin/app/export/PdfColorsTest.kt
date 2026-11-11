package com.yuzufin.app.export

import com.yuzufin.app.domain.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PdfColorsTest {

    @Test
    fun greenKeepsTheOriginalBrandColors() {
        val c = PdfColors.of(ThemePalette.GREEN)
        assertEquals(0xFF0B3D2E.toInt(), c.ink)
        assertEquals(0xFF1B8A4C.toInt(), c.accent)
    }

    @Test
    fun hslMatchesKnownColors() {
        assertEquals(0xFFFF0000.toInt(), PdfColors.hsl(0f, 100f, 50f))
        assertEquals(0xFF00FF00.toInt(), PdfColors.hsl(120f, 100f, 50f))
        assertEquals(0xFFFFFFFF.toInt(), PdfColors.hsl(200f, 40f, 100f))
        assertEquals(0xFF000000.toInt(), PdfColors.hsl(200f, 40f, 0f))
    }

    @Test
    fun everyPaletteGivesItsOwnOpaqueColors() {
        val accents = ThemePalette.entries.map { PdfColors.of(it).accent }.toSet()
        assertEquals(ThemePalette.entries.size, accents.size)
        ThemePalette.entries.forEach {
            val c = PdfColors.of(it)
            assertEquals(0xFF, (c.ink ushr 24) and 0xFF)
            assertEquals(0xFF, (c.mint ushr 24) and 0xFF)
        }
        assertNotEquals(PdfColors.of(ThemePalette.GREEN).ink, PdfColors.of(ThemePalette.NAVY).ink)
    }
}
