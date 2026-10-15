package com.yuzufin.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.Statement
import com.yuzufin.app.domain.StatementKind
import com.yuzufin.app.domain.StatementRow
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/**
 * Dibuja el extracto en un PDF A4 con la API de Android (sin librerías ni red).
 * Medidas en puntos (1 pt = 1/72 pulgada). Encabezado y resumen en la primera página, tabla con
 * saldo corrido repetida en las siguientes y pie con la nota de privacidad en todas.
 */
class StatementPdfWriter(private val context: Context) {

    private val ink = Color.parseColor("#0B3D2E")
    private val green = Color.parseColor("#1B8A4C")
    private val leafLight = Color.parseColor("#5DB85C")
    private val mint = Color.parseColor("#EAF4EF")
    private val line = Color.parseColor("#D3E5DB")
    private val gray = Color.parseColor("#5B6B65")
    private val red = Color.parseColor("#D9304F")
    private val pageBg = Color.parseColor("#FAFCFB")

    private fun paint(size: Float, color: Int, bold: Boolean = false, italic: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            textAlign = align
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

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val longFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
    private val stampFormat = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale("es"))

    private fun longDate(date: LocalDate) = date.format(longFormat).replace(".", "")

    fun write(
        statement: Statement,
        currency: CurrencyInfo,
        userName: String,
        generatedAt: LocalDateTime,
        output: File,
    ) {
        val rows = statement.rows
        val pageCount = if (rows.size <= FIRST_PAGE_ROWS) 1
        else 1 + ceil((rows.size - FIRST_PAGE_ROWS) / OTHER_PAGE_ROWS.toDouble()).toInt()

        val document = PdfDocument()
        try {
            var next = 0
            for (pageIndex in 0 until pageCount) {
                val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageIndex + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                canvas.drawRect(0f, 0f, PAGE_W, PAGE_H, fill(pageBg))

                val capacity: Int
                val tableTop: Float
                if (pageIndex == 0) {
                    drawFirstHeader(canvas, statement, userName, generatedAt)
                    drawSummary(canvas, statement, currency)
                    drawText(canvas, context.getString(R.string.statement_detail), MARGIN, 356f, paint(15f, ink, bold = true))
                    tableTop = 368f
                    capacity = FIRST_PAGE_ROWS
                } else {
                    drawCompactHeader(canvas, statement)
                    tableTop = 78f
                    capacity = OTHER_PAGE_ROWS
                }

                drawTableHeader(canvas, tableTop)
                var y = tableTop + HEADER_H
                if (rows.isEmpty()) {
                    drawText(canvas, context.getString(R.string.statement_empty), MARGIN + 8f, y + 28f, paint(11f, gray))
                }
                val end = minOf(rows.size, next + capacity)
                while (next < end) {
                    drawRow(canvas, rows[next], currency, y)
                    y += ROW_H
                    next++
                }
                drawFooter(canvas, pageIndex + 1, pageCount)
                document.finishPage(page)
            }
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    // ---------- Encabezados ----------

    private fun drawFirstHeader(canvas: Canvas, s: Statement, userName: String, generatedAt: LocalDateTime) {
        drawLogo(canvas, MARGIN, 28f, 50f)
        drawWordmark(canvas, MARGIN + 60f, 62f, 30f)
        drawText(canvas, context.getString(R.string.statement_tagline), MARGIN + 61f, 82f, paint(11f, gray))

        val right = PAGE_W - MARGIN
        drawText(canvas, context.getString(R.string.statement_title), right, 52f, paint(24f, ink, bold = true, align = Paint.Align.RIGHT))
        drawText(canvas, context.getString(R.string.statement_subtitle), right, 71f, paint(10.5f, gray, align = Paint.Align.RIGHT))
        drawText(
            canvas, "${longDate(s.from)} – ${longDate(s.to)}", right, 92f,
            paint(12f, ink, bold = true, align = Paint.Align.RIGHT),
        )
        drawText(
            canvas,
            context.getString(R.string.statement_generated, generatedAt.format(stampFormat).replace(".", "")),
            right, 107f, paint(8.5f, gray, align = Paint.Align.RIGHT),
        )
        canvas.drawLine(MARGIN, 122f, right, 122f, stroke(line, 1f))

        // Persona
        val cx = MARGIN + 21f
        val cy = 160f
        canvas.drawCircle(cx, cy, 21f, fill(ink))
        canvas.drawCircle(cx, cy - 5f, 6f, stroke(Color.WHITE, 1.8f))
        canvas.drawArc(RectF(cx - 11f, cy + 3f, cx + 11f, cy + 21f), 200f, 140f, false, stroke(Color.WHITE, 1.8f))

        val name = userName.trim().ifEmpty { context.getString(R.string.statement_user_default) }
        drawText(canvas, fit(name, paint(15f, ink, bold = true), 300f), MARGIN + 54f, 156f, paint(15f, ink, bold = true))
        drawText(canvas, context.getString(R.string.statement_account), MARGIN + 54f, 174f, paint(10.5f, gray))

        val quote = paint(11.5f, green, italic = true, align = Paint.Align.RIGHT)
        drawText(canvas, context.getString(R.string.statement_motto_1), right - 36f, 153f, quote)
        drawText(canvas, context.getString(R.string.statement_motto_2), right - 36f, 169f, quote)
        drawLeaves(canvas, right - 30f, 140f, 28f, soft = true)
    }

    private fun drawCompactHeader(canvas: Canvas, s: Statement) {
        drawLogo(canvas, MARGIN, 26f, 26f)
        drawWordmark(canvas, MARGIN + 33f, 47f, 17f)
        val right = PAGE_W - MARGIN
        drawText(canvas, context.getString(R.string.statement_title), right, 38f, paint(12f, ink, bold = true, align = Paint.Align.RIGHT))
        drawText(canvas, "${longDate(s.from)} – ${longDate(s.to)}", right, 52f, paint(9f, gray, align = Paint.Align.RIGHT))
        canvas.drawLine(MARGIN, 62f, right, 62f, stroke(line, 1f))
    }

    // ---------- Resumen ----------

    private fun drawSummary(canvas: Canvas, s: Statement, currency: CurrencyInfo) {
        val top = 200f
        val box = RectF(MARGIN, top, PAGE_W - MARGIN, top + 128f)
        canvas.drawRoundRect(box, 14f, 14f, fill(mint))
        drawText(canvas, context.getString(R.string.statement_summary), MARGIN + 16f, top + 26f, paint(14f, ink, bold = true))

        if (s.internalNet != 0L) {
            // Los préstamos mueven el saldo pero no son ingreso ni gasto: se aclara aparte.
            val note = context.getString(
                R.string.statement_internal_note,
                MoneyFormatter.format(s.internalNet, currency, showPlus = s.internalNet > 0L),
            )
            drawText(canvas, note, box.right - 16f, top + 26f, paint(9f, gray, align = Paint.Align.RIGHT))
        }

        val colW = (box.width() - 32f) / 4f
        val columns = listOf(
            Triple(R.string.statement_opening, s.openingBalance, ink),
            Triple(R.string.statement_income, s.incomeTotal, green),
            Triple(R.string.statement_expense, s.expenseTotal, red),
            Triple(R.string.statement_closing, s.closingBalance, ink),
        )
        val dates = listOf(s.from, null, null, s.to)
        columns.forEachIndexed { index, (labelRes, amount, color) ->
            val x = MARGIN + 16f + colW * index
            if (index > 0) canvas.drawLine(x - 8f, top + 42f, x - 8f, top + 112f, stroke(line, 1f))
            val iconCx = x + 13f
            val iconCy = top + 56f
            when (index) {
                0 -> drawIconCircle(canvas, iconCx, iconCy, ink) { drawWalletGlyph(it, iconCx, iconCy) }
                1 -> drawIconCircle(canvas, iconCx, iconCy, green) { drawArrowGlyph(it, iconCx, iconCy, up = true) }
                2 -> drawIconCircle(canvas, iconCx, iconCy, red) { drawArrowGlyph(it, iconCx, iconCy, up = false) }
                else -> drawIconCircle(canvas, iconCx, iconCy, ink) { drawCoinsGlyph(it, iconCx, iconCy) }
            }
            drawText(canvas, context.getString(labelRes), x, top + 86f, paint(10.5f, ink, bold = true))
            val text = when (index) {
                1 -> MoneyFormatter.format(amount, currency, showPlus = true)
                2 -> if (amount == 0L) MoneyFormatter.format(0, currency) else "-" + MoneyFormatter.format(amount, currency)
                else -> MoneyFormatter.format(amount, currency)
            }
            val amountPaint = paint(12.5f, color, bold = true)
            drawText(canvas, fit(text, amountPaint, colW - 14f), x, top + 104f, amountPaint)
            dates[index]?.let { drawText(canvas, longDate(it), x, top + 118f, paint(8f, gray)) }
        }
    }

    // ---------- Tabla ----------

    private val colDate = MARGIN + 8f
    private val colDesc = MARGIN + 70f
    private val colCat = MARGIN + 215f
    private val colType = MARGIN + 305f
    private val colAmountRight = MARGIN + 435f
    private val colBalanceRight = PAGE_W - MARGIN - 8f

    private fun drawTableHeader(canvas: Canvas, top: Float) {
        canvas.drawRoundRect(RectF(MARGIN, top, PAGE_W - MARGIN, top + HEADER_H), 8f, 8f, fill(mint))
        val p = paint(9f, ink, bold = true)
        val pr = paint(9f, ink, bold = true, align = Paint.Align.RIGHT)
        val y = top + 16f
        drawText(canvas, context.getString(R.string.statement_col_date), colDate, y, p)
        drawText(canvas, context.getString(R.string.statement_col_desc), colDesc, y, p)
        drawText(canvas, context.getString(R.string.statement_col_cat), colCat, y, p)
        drawText(canvas, context.getString(R.string.statement_col_type), colType, y, p)
        drawText(canvas, context.getString(R.string.statement_col_amount), colAmountRight, y, pr)
        drawText(canvas, context.getString(R.string.statement_col_balance), colBalanceRight, y, pr)
    }

    private fun drawRow(canvas: Canvas, row: StatementRow, currency: CurrencyInfo, top: Float) {
        val base = top + 15f
        val text = paint(9f, ink)
        val gray9 = paint(9f, gray)
        drawText(canvas, row.date.format(dayFormat), colDate, base, gray9)

        val description = if (row.kind == StatementKind.INITIAL) context.getString(R.string.statement_initial_row) else row.description
        drawText(canvas, fit(description, text, colCat - colDesc - 8f), colDesc, base, text)
        drawText(canvas, fit(row.categoryName.ifEmpty { "-" }, gray9, colType - colCat - 8f), colCat, base, gray9)

        val kindRes = when {
            row.internal -> R.string.statement_kind_internal
            row.kind == StatementKind.INITIAL -> R.string.statement_kind_initial
            row.kind == StatementKind.INCOME -> R.string.statement_kind_income
            else -> R.string.statement_kind_expense
        }
        drawText(canvas, context.getString(kindRes), colType, base, gray9)

        val amountText = when (row.kind) {
            StatementKind.INITIAL -> MoneyFormatter.format(row.signedAmountMinor, currency)
            StatementKind.INCOME -> MoneyFormatter.format(row.signedAmountMinor, currency, showPlus = true)
            StatementKind.EXPENSE -> MoneyFormatter.format(row.signedAmountMinor, currency)
        }
        val amountColor = when (row.kind) {
            StatementKind.INITIAL -> ink
            StatementKind.INCOME -> green
            StatementKind.EXPENSE -> red
        }
        drawText(canvas, amountText, colAmountRight, base, paint(9f, amountColor, bold = true, align = Paint.Align.RIGHT))
        drawText(
            canvas, MoneyFormatter.format(row.balanceAfter, currency), colBalanceRight, base,
            paint(9f, if (row.balanceAfter < 0) red else ink, align = Paint.Align.RIGHT),
        )
        canvas.drawLine(MARGIN, top + ROW_H, PAGE_W - MARGIN, top + ROW_H, stroke(line, 0.6f))
    }

    // ---------- Pie ----------

    private fun drawFooter(canvas: Canvas, pageNumber: Int, pageCount: Int) {
        val top = PAGE_H - 66f
        canvas.drawLine(MARGIN, top, PAGE_W - MARGIN, top, stroke(line, 1f))

        drawLogo(canvas, MARGIN, top + 12f, 28f)
        drawWordmark(canvas, MARGIN + 35f, top + 33f, 17f)
        drawText(canvas, context.getString(R.string.statement_tagline), MARGIN + 36f, top + 44f, paint(7f, gray))

        val quote = paint(8.5f, gray, italic = true)
        drawText(canvas, context.getString(R.string.statement_footer_quote_1), 200f, top + 28f, quote)
        drawText(canvas, context.getString(R.string.statement_footer_quote_2), 200f, top + 40f, quote)

        val divider = 340f
        canvas.drawLine(divider, top + 12f, divider, top + 52f, stroke(line, 1f))
        drawLock(canvas, divider + 12f, top + 20f)
        val xText = divider + 44f
        drawText(canvas, context.getString(R.string.statement_secure_title), xText, top + 22f, paint(8f, ink, bold = true))
        val body = paint(7.5f, gray)
        context.getString(R.string.statement_secure_body).split("\n").forEachIndexed { i, l ->
            drawText(canvas, l, xText, top + 33f + i * 9f, body)
        }
        drawText(
            canvas, context.getString(R.string.statement_page, pageNumber, pageCount),
            PAGE_W - MARGIN, PAGE_H - 12f, paint(7.5f, gray, align = Paint.Align.RIGHT),
        )
    }

    // ---------- Dibujos ----------

    private fun drawWordmark(canvas: Canvas, x: Float, baseline: Float, size: Float) {
        val yuzu = paint(size, ink, bold = true)
        drawText(canvas, "Yuzu", x, baseline, yuzu)
        val width = yuzu.measureText("Yuzu")
        drawText(canvas, "Fin", x + width, baseline, paint(size, leafLight, bold = true))
    }

    private fun drawLogo(canvas: Canvas, x: Float, y: Float, size: Float) = drawLeaves(canvas, x, y, size, soft = false)

    /** Dos hojas, la oscura delante de la clara. */
    private fun drawLeaves(canvas: Canvas, x: Float, y: Float, s: Float, soft: Boolean) {
        fun px(v: Float) = x + v * s
        fun py(v: Float) = y + v * s
        val back = Path().apply {
            moveTo(px(0.50f), py(0.95f))
            cubicTo(px(0.58f), py(0.62f), px(0.76f), py(0.38f), px(1.00f), py(0.22f))
            cubicTo(px(1.02f), py(0.62f), px(0.82f), py(0.90f), px(0.50f), py(0.95f))
            close()
        }
        val front = Path().apply {
            moveTo(px(0.46f), py(0.96f))
            cubicTo(px(0.04f), py(0.80f), px(-0.02f), py(0.34f), px(0.52f), py(0.04f))
            cubicTo(px(0.62f), py(0.42f), px(0.66f), py(0.72f), px(0.46f), py(0.96f))
            close()
        }
        canvas.drawPath(back, fill(if (soft) Color.parseColor("#B7DDB5") else leafLight))
        canvas.drawPath(front, fill(if (soft) Color.parseColor("#7FBF8C") else Color.parseColor("#14633F")))
        val vein = stroke(if (soft) Color.parseColor("#EAF4EF") else Color.parseColor("#7FD39B"), s * 0.03f)
        canvas.drawLine(px(0.46f), py(0.90f), px(0.52f), py(0.20f), vein)
    }

    private fun drawIconCircle(canvas: Canvas, cx: Float, cy: Float, color: Int, glyph: (Canvas) -> Unit) {
        canvas.drawCircle(cx, cy, 15f, fill(color))
        glyph(canvas)
    }

    private fun drawArrowGlyph(canvas: Canvas, cx: Float, cy: Float, up: Boolean) {
        val p = stroke(Color.WHITE, 2f)
        val d = if (up) -1f else 1f
        canvas.drawLine(cx, cy + 6f * -d, cx, cy + 6f * d, p)
        canvas.drawLine(cx - 5f, cy + 1f * d, cx, cy + 6f * d, p)
        canvas.drawLine(cx + 5f, cy + 1f * d, cx, cy + 6f * d, p)
    }

    private fun drawWalletGlyph(canvas: Canvas, cx: Float, cy: Float) {
        val p = stroke(Color.WHITE, 1.6f)
        canvas.drawRoundRect(RectF(cx - 7f, cy - 5f, cx + 7f, cy + 6f), 2.5f, 2.5f, p)
        canvas.drawCircle(cx + 3f, cy + 0.5f, 1.2f, fill(Color.WHITE))
        canvas.drawLine(cx - 5f, cy - 5f, cx + 3f, cy - 7.5f, p)
    }

    private fun drawCoinsGlyph(canvas: Canvas, cx: Float, cy: Float) {
        val p = stroke(Color.WHITE, 1.6f)
        canvas.drawOval(RectF(cx - 6f, cy - 7f, cx + 6f, cy - 2f), p)
        canvas.drawLine(cx - 6f, cy - 4.5f, cx - 6f, cy + 5f, p)
        canvas.drawLine(cx + 6f, cy - 4.5f, cx + 6f, cy + 5f, p)
        canvas.drawArc(RectF(cx - 6f, cy, cx + 6f, cy + 10f), 0f, 180f, false, p)
    }

    private fun drawLock(canvas: Canvas, x: Float, y: Float) {
        val shackle = stroke(ink, 2f)
        canvas.drawArc(RectF(x + 6f, y, x + 22f, y + 18f), 180f, 180f, false, shackle)
        canvas.drawRoundRect(RectF(x + 2f, y + 10f, x + 26f, y + 32f), 5f, 5f, fill(ink))
        canvas.drawCircle(x + 14f, y + 20f, 2.6f, fill(Color.WHITE))
        canvas.drawLine(x + 14f, y + 21f, x + 14f, y + 26f, stroke(Color.WHITE, 1.8f))
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) =
        canvas.drawText(text, x, y, paint)

    /** Recorta con “…” para que el texto no pise la columna siguiente. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val room = maxWidth - paint.measureText(ellipsis)
        val count = paint.breakText(text, true, room, null)
        return text.take(count).trimEnd() + ellipsis
    }

    private companion object {
        const val PAGE_W = 595f
        const val PAGE_H = 842f
        const val MARGIN = 36f
        const val ROW_H = 22f
        const val HEADER_H = 24f

        // (760 - 392) / 22 y (760 - 102) / 22, redondeado hacia abajo.
        const val FIRST_PAGE_ROWS = 16
        const val OTHER_PAGE_ROWS = 29
    }
}
