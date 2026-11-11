package com.yuzufin.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.yuzufin.app.R
import java.io.File
import java.io.FileOutputStream

/**
 * Herramientas de dibujo comunes a los PDF de Monea (comprobante de cuotas y extractos).
 *
 * Las medidas están en "px de maqueta": una hoja de [W] x [H] que se escala a un A4 (595 x 842 pt).
 * Así las maquetas y el código usan los mismos números.
 */
class PdfKit(private val context: Context, val c: PdfColors) {

    // ---------- Pinceles ----------

    fun text(
        size: Float,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
        spacing: Float = 0f,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = align
        letterSpacing = spacing
        typeface = Typeface.create(
            "sans-serif",
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
    }

    fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Trazo medio (como "Outfit" semibold en las maquetas) con la fuente del sistema. */
    private fun wordPaint(size: Float, color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        letterSpacing = -0.03f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // ---------- Texto ----------

    fun draw(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) = canvas.drawText(text, x, y, paint)

    /** Recorta con "…" para que el texto no pise la columna siguiente. */
    fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val room = maxWidth - paint.measureText(ellipsis)
        if (room <= 0f) return ellipsis
        val count = paint.breakText(text, true, room, null)
        return text.take(count).trimEnd() + ellipsis
    }

    fun round(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, paint: Paint) =
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)

    // ---------- Marca ----------

    /** "mon" + "ea": [first] y [second] son los colores de cada parte. */
    fun wordmark(canvas: Canvas, x: Float, baseline: Float, size: Float, first: Int, second: Int) {
        val a = wordPaint(size, first)
        canvas.drawText("mon", x, baseline, a)
        canvas.drawText("ea", x + a.measureText("mon"), baseline, wordPaint(size, second))
    }

    /** Cuadrado con la "M", como en las maquetas. */
    fun mark(canvas: Canvas, x: Float, y: Float, size: Float) {
        round(canvas, x, y, x + size, y + size, size * 0.3f, fill(c.bright))
        val p = text(size * 0.5f, c.ink, bold = true, align = Paint.Align.CENTER)
        canvas.drawText("M", x + size / 2f, y + size * 0.68f, p)
    }

    /** Banda de color de la cabecera. [lines] van a la derecha: primero el título, después los datos. */
    fun headerBand(canvas: Canvas, title: String, lines: List<String>) {
        canvas.drawRect(0f, 0f, W, BAND_H, fill(c.ink))
        mark(canvas, M, 36f, 40f)
        wordmark(canvas, M + 52f, 56f, 26f, PdfColors.WHITE, c.bright)
        draw(canvas, context.getString(R.string.pdf_tagline), M + 52f, 76f, text(11f, c.soft))

        val right = W - M
        draw(canvas, title.uppercase(), right, 50f, text(11f, c.soft, bold = true, align = Paint.Align.RIGHT, spacing = 0.14f))
        lines.forEachIndexed { i, line ->
            draw(canvas, line, right, 70f + i * 16f, text(12f, PdfColors.ON_INK, align = Paint.Align.RIGHT))
        }
    }

    /** Cabecera chica para las páginas siguientes. */
    fun compactBand(canvas: Canvas, title: String, subtitle: String) {
        canvas.drawRect(0f, 0f, W, COMPACT_H, fill(c.ink))
        mark(canvas, M, 16f, 32f)
        wordmark(canvas, M + 42f, 40f, 22f, PdfColors.WHITE, c.bright)
        val right = W - M
        draw(canvas, title.uppercase(), right, 33f, text(10f, c.soft, bold = true, align = Paint.Align.RIGHT, spacing = 0.12f))
        draw(canvas, subtitle, right, 49f, text(10.5f, PdfColors.ON_INK, align = Paint.Align.RIGHT))
    }

    /** Pie de página: wordmark, frase, aviso de privacidad con candado y número de página. */
    fun footer(canvas: Canvas, margin: Float, pageNumber: Int, pageCount: Int, secureBody: List<String>) {
        val top = FOOTER_TOP
        canvas.drawLine(margin, top, W - margin, top, stroke(c.line, 1f))

        wordmark(canvas, margin, top + 36f, 27f, INK_BLACK, c.accent)
        draw(canvas, context.getString(R.string.pdf_tagline), margin, top + 50f, text(9.5f, PdfColors.GRAY, spacing = 0.03f))

        val quote = text(11f, PdfColors.GRAY_DARK, italic = true, align = Paint.Align.CENTER)
        draw(canvas, context.getString(R.string.statement_footer_quote_1), 347f, top + 29f, quote)
        draw(canvas, context.getString(R.string.statement_footer_quote_2), 347f, top + 45f, quote)

        canvas.drawLine(474f, top + 12f, 474f, top + 54f, stroke(c.line, 1f))
        lock(canvas, 494f, top + 12f)
        draw(canvas, context.getString(R.string.statement_secure_title), 538f, top + 26f, text(11f, c.ink, bold = true))
        val body = text(9.5f, PdfColors.GRAY)
        secureBody.forEachIndexed { i, l -> draw(canvas, l, 538f, top + 40f + i * 12.5f, body) }

        draw(
            canvas, context.getString(R.string.statement_page, pageNumber, pageCount),
            W - margin, H - 14f, text(10f, PdfColors.GRAY, align = Paint.Align.RIGHT),
        )
    }

    /** Candado del aviso de privacidad (34 x 40). */
    private fun lock(canvas: Canvas, x: Float, y: Float) {
        val shackle = Path().apply {
            moveTo(x + 9f, y + 17f)
            lineTo(x + 9f, y + 12f)
            cubicTo(x + 9f, y + 3f, x + 25f, y + 3f, x + 25f, y + 12f)
            lineTo(x + 25f, y + 17f)
        }
        canvas.drawPath(shackle, stroke(c.ink, 3.4f))
        round(canvas, x + 3f, y + 16f, x + 31f, y + 38f, 5f, fill(c.ink))
        canvas.drawCircle(x + 17f, y + 25.5f, 3f, fill(PdfColors.WHITE))
        round(canvas, x + 15.6f, y + 26f, x + 18.4f, y + 32.5f, 1.4f, fill(PdfColors.WHITE))
    }

    // ---------- Piezas ----------

    /** Etiqueta redondeada (estado de una cuota). Devuelve su ancho. */
    fun pill(canvas: Canvas, label: String, x: Float, baseline: Float, bg: Int, fg: Int): Float {
        val p = text(11f, fg, bold = true)
        val w = p.measureText(label) + 20f
        round(canvas, x, baseline - 14f, x + w, baseline + 5f, 9.5f, fill(bg))
        draw(canvas, label, x + 10f, baseline, p)
        return w
    }

    /** Barra de progreso en segmentos (cuotas) o continua si son muchas. */
    fun segments(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, total: Int, done: Int) {
        if (total <= 0) return
        val gap = if (total > 18) 2f else if (total > 8) 4f else 6f
        val w = (width - gap * (total - 1)) / total
        for (i in 0 until total) {
            val left = x + i * (w + gap)
            round(canvas, left, y, left + w, y + height, height / 2f, fill(if (i < done) c.accent else c.line))
        }
    }

    // ---------- Páginas ----------

    /**
     * Genera un PDF de varias páginas. Se dibuja dos veces: la primera solo para contar las páginas
     * (así cada una puede decir "Página 2 de 3") y la segunda para guardar el archivo.
     */
    fun build(output: File, render: (PageSession) -> Unit) {
        val counter = PageSession(PdfDocument(), 0)
        try {
            render(counter)
            counter.finish()
        } finally {
            counter.document.close()
        }
        val total = counter.pageNumber.coerceAtLeast(1)

        val real = PageSession(PdfDocument(), total)
        try {
            render(real)
            real.finish()
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { real.document.writeTo(it) }
        } finally {
            real.document.close()
        }
    }

    /** Una hoja A4 en curso. [canvas] ya está escalado a las medidas de maqueta. */
    class PageSession(val document: PdfDocument, val totalPages: Int) {
        var pageNumber = 0
            private set
        private var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
            private set

        fun next(background: Int): Canvas {
            finish()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
            val started = document.startPage(info)
            page = started
            canvas = started.canvas
            canvas.drawColor(background)
            canvas.save()
            canvas.scale(SCALE, SCALE)
            return canvas
        }

        fun finish() {
            val current = page ?: return
            canvas.restore()
            document.finishPage(current)
            page = null
        }
    }

    companion object {
        const val W = 794f
        const val H = 1123f
        const val M = 48f
        const val BAND_H = 112f
        const val COMPACT_H = 64f
        const val FOOTER_TOP = 1036f

        /** Hasta dónde puede llegar el contenido antes del pie. */
        const val CONTENT_LIMIT = 1020f

        const val PAGE_W = 595
        const val PAGE_H = 842
        const val SCALE = 595f / 794f

        private const val INK_BLACK = 0xFF111111.toInt()
    }
}
