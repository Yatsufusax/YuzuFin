package com.yuzufin.app.export

import com.yuzufin.app.domain.ThemePalette
import kotlin.math.abs

/**
 * Colores de los PDF según la paleta elegida en la app. Los PDF son siempre claros (se imprimen o
 * se comparten), así que solo cambian los acentos, la cabecera y los fondos suaves; el rojo de los
 * gastos, el verde de los ingresos y el azul de «adelantada» no dependen de la paleta.
 * Todo es aritmética pura: no usa clases de Android, así que se puede probar en la JVM.
 */
data class PdfColors(
    val ink: Int,
    val accent: Int,
    val bright: Int,
    val mint: Int,
    val line: Int,
    val line2: Int,
    val soft: Int,
    val mid: Int,
    val mid2: Int,
    val pillBg: Int,
    val pillFg: Int,
) {
    companion object {
        const val RED = 0xFFD9304F.toInt()
        const val GREEN_INCOME = 0xFF1B8A4C.toInt()
        const val RED_SOFT = 0xFFFDECEF.toInt()
        const val BLUE_BG = 0xFFDCEBFA.toInt()
        const val BLUE_FG = 0xFF0F3F73.toInt()
        const val GRAY = 0xFF5B6B65.toInt()
        const val GRAY_DARK = 0xFF3F4B46.toInt()
        const val GRAY_BG = 0xFFF0F2F1.toInt()
        const val PAGE = 0xFFFAFCFB.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ON_INK = 0xFFE3F4EB.toInt()

        /** Verde original de Monea, con los valores exactos de las maquetas. */
        private val GREEN = PdfColors(
            ink = 0xFF0B3D2E.toInt(), accent = 0xFF1B8A4C.toInt(), bright = 0xFF30DA8B.toInt(),
            mint = 0xFFEAF4EF.toInt(), line = 0xFFD3E5DB.toInt(), line2 = 0xFFE3EEE8.toInt(),
            soft = 0xFFBCF2D0.toInt(), mid = 0xFF5DB85C.toInt(), mid2 = 0xFF8FD0A0.toInt(),
            pillBg = 0xFFE3F4EB.toInt(), pillFg = 0xFF0B4F37.toInt(),
        )

        /** Tono, acento y color claro de cada paleta (mismos valores que las paletas de la interfaz). */
        private data class Spec(val hue: Float, val accent: Int, val bright: Int)

        private fun specOf(palette: ThemePalette): Spec? = when (palette) {
            ThemePalette.GREEN -> null
            ThemePalette.VIOLET -> Spec(265f, 0xFF6D3FD0.toInt(), 0xFFB79CFF.toInt())
            ThemePalette.NAVY -> Spec(220f, 0xFF1F3F8F.toInt(), 0xFF8FB0FF.toInt())
            ThemePalette.RED -> Spec(355f, 0xFFC62F3E.toInt(), 0xFFFF8F98.toInt())
            ThemePalette.ORANGE -> Spec(25f, 0xFFAE5400.toInt(), 0xFFFFB067.toInt())
            ThemePalette.PINK -> Spec(330f, 0xFFC2327A.toInt(), 0xFFFF9CC8.toInt())
            ThemePalette.TEAL -> Spec(185f, 0xFF0B7A86.toInt(), 0xFF5ED8E6.toInt())
        }

        fun of(palette: ThemePalette): PdfColors {
            val spec = specOf(palette) ?: return GREEN
            val h = spec.hue
            return PdfColors(
                ink = hsl(h, 55f, 17f), accent = spec.accent, bright = spec.bright,
                mint = hsl(h, 35f, 95f), line = hsl(h, 25f, 88f), line2 = hsl(h, 22f, 92f),
                soft = hsl(h, 60f, 88f), mid = hsl(h, 45f, 50f), mid2 = hsl(h, 40f, 68f),
                pillBg = hsl(h, 35f, 93f), pillFg = hsl(h, 55f, 20f),
            )
        }

        /** Convierte HSL (grados, % y %) a un color ARGB opaco. */
        fun hsl(hue: Float, saturation: Float, lightness: Float): Int {
            val s = saturation / 100f
            val l = lightness / 100f
            val c = (1f - abs(2f * l - 1f)) * s
            val hp = (((hue % 360f) + 360f) % 360f) / 60f
            val x = c * (1f - abs(hp % 2f - 1f))
            val (r1, g1, b1) = when {
                hp < 1f -> Triple(c, x, 0f)
                hp < 2f -> Triple(x, c, 0f)
                hp < 3f -> Triple(0f, c, x)
                hp < 4f -> Triple(0f, x, c)
                hp < 5f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = l - c / 2f
            fun channel(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
        }
    }
}
