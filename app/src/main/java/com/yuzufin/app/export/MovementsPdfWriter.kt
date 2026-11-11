package com.yuzufin.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.Statement
import com.yuzufin.app.domain.StatementKind
import com.yuzufin.app.domain.StatementRow
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Diseño del extracto de movimientos. */
enum class StatementStyle {
    /** Claro, con tarjetas, gráfico por categoría y tabla con saldo corrido. */
    CLASSIC,

    /** Editorial: saldo grande, barra de gastos y movimientos día por día. */
    EDITORIAL,
}

/** Gasto total de una categoría en el período. */
data class CategorySlice(val name: String, val amountMinor: Long)

/** Pura: agrupa los gastos del extracto por categoría. Los préstamos internos no cuentan. */
fun expenseByCategory(statement: Statement, otherLabel: String, maxSlices: Int = 4): List<CategorySlice> {
    val totals = LinkedHashMap<String, Long>()
    for (row in statement.rows) {
        if (row.kind != StatementKind.EXPENSE || row.internal) continue
        val key = row.categoryName.ifBlank { "-" }
        totals[key] = (totals[key] ?: 0L) + abs(row.signedAmountMinor)
    }
    val sorted = totals.entries.sortedByDescending { it.value }.map { CategorySlice(it.key, it.value) }
    if (sorted.size <= maxSlices) return sorted
    val head = sorted.take(maxSlices - 1)
    return head + CategorySlice(otherLabel, sorted.drop(maxSlices - 1).sumOf { it.amountMinor })
}

/** Pura: porcentaje de lo que ingresó y no se gastó; null si no hubo ingresos. */
fun savedPercent(statement: Statement): Int? {
    if (statement.incomeTotal <= 0L) return null
    return ((statement.incomeTotal - statement.expenseTotal) * 100L / statement.incomeTotal).toInt()
}

/** Extracto de movimientos en PDF A4, en dos diseños. Se genera en el teléfono, sin red. */
class MovementsPdfWriter(private val context: Context, colors: PdfColors) {

    private val kit = PdfKit(context, colors)
    private val c = colors

    private val longFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
    private val shortFormat = DateTimeFormatter.ofPattern("dd/MM", Locale("es"))
    private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale("es"))
    private fun longDate(d: LocalDate) = d.format(longFormat).replace(".", "")

    fun write(
        statement: Statement,
        currency: CurrencyInfo,
        userName: String,
        generatedAt: LocalDateTime,
        style: StatementStyle,
        output: File,
    ) {
        when (style) {
            StatementStyle.CLASSIC -> writeClassic(statement, currency, userName, generatedAt, output)
            StatementStyle.EDITORIAL -> writeEditorial(statement, currency, userName, output)
        }
    }

    private fun rowDescription(row: StatementRow): String {
        val base = if (row.kind == StatementKind.INITIAL) context.getString(R.string.statement_initial_row) else row.description
        return if (row.internal) "$base · ${context.getString(R.string.statement_kind_internal)}" else base
    }

    private fun amountText(row: StatementRow, currency: CurrencyInfo): String = when {
        row.kind == StatementKind.INITIAL -> MoneyFormatter.formatNumber(row.signedAmountMinor, currency)
        row.signedAmountMinor > 0 -> "+" + MoneyFormatter.formatNumber(row.signedAmountMinor, currency)
        else -> MoneyFormatter.formatNumber(row.signedAmountMinor, currency)
    }

    private fun amountColor(row: StatementRow): Int = when {
        row.kind == StatementKind.INITIAL -> c.ink
        row.signedAmountMinor > 0 -> PdfColors.GREEN_INCOME
        else -> PdfColors.RED
    }

    private fun secureBody(): List<String> = context.getString(R.string.statement_secure_body).split("\n")

    // ======================================================================
    // Diseño A: clásico
    // ======================================================================

    private fun writeClassic(
        s: Statement,
        currency: CurrencyInfo,
        userName: String,
        generatedAt: LocalDateTime,
        output: File,
    ) {
        val name = userName.trim().ifEmpty { context.getString(R.string.statement_user_default) }
        val slices = expenseByCategory(s, context.getString(R.string.pdf_mov_other))
        val rows = s.rows
        val left = PdfKit.M
        val right = PdfKit.W - PdfKit.M

        kit.build(output) { session ->
            var index = 0
            var first = true
            while (true) {
                val canvas = session.next(PdfColors.PAGE)
                var y: Float
                if (first) {
                    kit.headerBand(
                        canvas,
                        context.getString(R.string.pdf_mov_title),
                        listOf(name, "${longDate(s.from)} – ${longDate(s.to)}", context.getString(R.string.pdf_issued, longDate(generatedAt.toLocalDate()))),
                    )
                    y = drawClassicSummary(canvas, s, currency, slices)
                } else {
                    kit.compactBand(canvas, context.getString(R.string.pdf_mov_title), "${longDate(s.from)} – ${longDate(s.to)}")
                    y = 96f
                }

                val limit = PdfKit.CONTENT_LIMIT
                val remaining = rows.size - index
                val capacity = ((limit - y - HEAD_H) / ROW_H).toInt().coerceAtLeast(1)
                val last: Boolean
                val count: Int
                if (remaining <= capacity && y + HEAD_H + remaining * ROW_H + CLOSE_H <= limit) {
                    count = remaining
                    last = true
                } else {
                    count = minOf(remaining, if (remaining <= capacity) (capacity - 1).coerceAtLeast(1) else capacity)
                    last = false
                }

                val top = y
                val bottom = top + HEAD_H + count * ROW_H + (if (last) CLOSE_H else 0f)
                val box = RectF(left, top, right, bottom)
                canvas.drawRoundRect(box, 16f, 16f, kit.fill(PdfColors.WHITE))
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(box, 16f, 16f, Path.Direction.CW) })

                canvas.drawRect(left, top, right, top + HEAD_H, kit.fill(c.mint))
                val head = kit.text(10.5f, c.ink, bold = true, spacing = 0.08f)
                val xDate = left + 18f
                val xDesc = left + 88f
                val xBal = right - 18f
                val xAmt = right - 138f
                val xCat = right - 368f
                val hy = top + 23f
                kit.draw(canvas, context.getString(R.string.statement_col_date).uppercase(), xDate, hy, head)
                kit.draw(canvas, context.getString(R.string.statement_col_desc).uppercase(), xDesc, hy, head)
                kit.draw(canvas, context.getString(R.string.statement_col_cat).uppercase(), xCat, hy, head)
                val headRight = kit.text(10.5f, c.ink, bold = true, align = Paint.Align.RIGHT, spacing = 0.08f)
                kit.draw(canvas, context.getString(R.string.statement_col_amount).uppercase(), xAmt, hy, headRight)
                kit.draw(canvas, context.getString(R.string.statement_col_balance).uppercase(), xBal, hy, headRight)

                if (rows.isEmpty()) {
                    kit.draw(canvas, context.getString(R.string.pdf_mov_empty), xDate, top + HEAD_H + 26f, kit.text(12f, PdfColors.GRAY))
                }

                var ry = top + HEAD_H
                for (i in 0 until count) {
                    val row = rows[index + i]
                    if ((index + i) % 2 == 1) canvas.drawRect(left, ry, right, ry + ROW_H, kit.fill(PdfColors.PAGE))
                    canvas.drawLine(left, ry, right, ry, kit.stroke(c.line2, 1f))
                    val base = ry + 23f
                    kit.draw(canvas, row.date.format(shortFormat), xDate, base, kit.text(12f, PdfColors.GRAY))
                    val desc = kit.text(12f, c.ink, bold = true)
                    kit.draw(canvas, kit.fit(rowDescription(row), desc, xCat - xDesc - 12f), xDesc, base, desc)
                    val cat = kit.text(12f, PdfColors.GRAY)
                    kit.draw(canvas, kit.fit(row.categoryName.ifEmpty { "-" }, cat, 118f), xCat, base, cat)
                    kit.draw(canvas, amountText(row, currency), xAmt, base, kit.text(12f, amountColor(row), bold = true, align = Paint.Align.RIGHT))
                    val balColor = if (row.balanceAfter < 0) PdfColors.RED else c.ink
                    kit.draw(canvas, MoneyFormatter.formatNumber(row.balanceAfter, currency), xBal, base, kit.text(12f, balColor, align = Paint.Align.RIGHT))
                    ry += ROW_H
                }

                if (last) {
                    canvas.drawRect(left, ry, right, ry + CLOSE_H, kit.fill(c.ink))
                    kit.draw(
                        canvas, context.getString(R.string.pdf_mov_balance_on, longDate(s.to)), xDate, ry + 29f,
                        kit.text(12.5f, PdfColors.WHITE, bold = true),
                    )
                    kit.draw(
                        canvas, MoneyFormatter.formatNumber(s.closingBalance, currency), xBal, ry + 30f,
                        kit.text(14f, PdfColors.WHITE, bold = true, align = Paint.Align.RIGHT),
                    )
                }
                canvas.restore()
                canvas.drawRoundRect(box, 16f, 16f, kit.stroke(c.line, 1f))

                kit.footer(canvas, PdfKit.M, session.pageNumber, session.totalPages, secureBody())
                index += count
                first = false
                if (last) break
            }
        }
    }

    /** Tarjetas de saldo e ingresos/gastos, más el gráfico por categoría. Devuelve dónde sigue la tabla. */
    private fun drawClassicSummary(canvas: Canvas, s: Statement, currency: CurrencyInfo, slices: List<CategorySlice>): Float {
        val left = PdfKit.M
        val right = PdfKit.W - PdfKit.M
        val gap = 12f
        val unit = (right - left - 2 * gap) / 3.3f
        val w1 = unit * 1.3f
        val top = 142f
        val h = 92f

        // Saldo al cierre
        kit.round(canvas, left, top, left + w1, top + h, 16f, kit.fill(c.ink))
        kit.draw(canvas, context.getString(R.string.pdf_mov_closing).uppercase(), left + 20f, top + 27f, kit.text(10.5f, c.soft, bold = true, spacing = 0.08f))
        val closing = kit.text(25f, PdfColors.WHITE, bold = true, spacing = -0.01f)
        kit.draw(canvas, kit.fit(MoneyFormatter.format(s.closingBalance, currency), closing, w1 - 40f), left + 20f, top + 58f, closing)
        val sub = kit.text(10.5f, c.soft)
        kit.draw(
            canvas,
            kit.fit(context.getString(R.string.pdf_mov_opening_line, MoneyFormatter.format(s.openingBalance, currency)), sub, w1 - 40f),
            left + 20f, top + 78f, sub,
        )

        // Ingresos
        val x2 = left + w1 + gap
        kit.round(canvas, x2, top, x2 + unit, top + h, 16f, kit.fill(c.mint))
        kit.draw(canvas, context.getString(R.string.statement_income).uppercase(), x2 + 20f, top + 27f, kit.text(10.5f, PdfColors.GRAY, bold = true, spacing = 0.08f))
        val inc = kit.text(19f, PdfColors.GREEN_INCOME, bold = true)
        kit.draw(canvas, kit.fit(MoneyFormatter.format(s.incomeTotal, currency, showPlus = true), inc, unit - 40f), x2 + 20f, top + 62f, inc)

        // Gastos
        val x3 = x2 + unit + gap
        kit.round(canvas, x3, top, x3 + unit, top + h, 16f, kit.fill(PdfColors.RED_SOFT))
        kit.draw(canvas, context.getString(R.string.statement_expense).uppercase(), x3 + 20f, top + 27f, kit.text(10.5f, PdfColors.GRAY, bold = true, spacing = 0.08f))
        val exp = kit.text(19f, PdfColors.RED, bold = true)
        val expText = if (s.expenseTotal == 0L) MoneyFormatter.format(0, currency) else "-" + MoneyFormatter.format(s.expenseTotal, currency)
        kit.draw(canvas, kit.fit(expText, exp, unit - 40f), x3 + 20f, top + 62f, exp)

        var y = top + h + 20f
        if (slices.isNotEmpty()) {
            val cardH = 56f + slices.size * 28f
            kit.round(canvas, left, y, right, y + cardH, 16f, kit.fill(PdfColors.WHITE))
            kit.round(canvas, left, y, right, y + cardH, 16f, kit.stroke(c.line, 1f))
            kit.draw(
                canvas, context.getString(R.string.pdf_mov_where).uppercase(), left + 22f, y + 33f,
                kit.text(12f, PdfColors.GRAY, bold = true, spacing = 0.1f),
            )
            val max = slices.maxOf { it.amountMinor }.coerceAtLeast(1L)
            val palette = listOf(c.ink, c.accent, c.mid, c.mid2, c.line)
            val barL = left + 22f + 124f
            val barR = right - 22f - 108f
            slices.forEachIndexed { i, slice ->
                val ry = y + 54f + i * 28f
                val label = kit.text(12.5f, c.ink)
                kit.draw(canvas, kit.fit(slice.name, label, 116f), left + 22f, ry + 11f, label)
                kit.round(canvas, barL, ry, barR, ry + 12f, 6f, kit.fill(c.mint))
                val fillW = ((barR - barL) * slice.amountMinor / max).coerceAtLeast(6f)
                kit.round(canvas, barL, ry, barL + fillW, ry + 12f, 6f, kit.fill(palette[i % palette.size]))
                kit.draw(
                    canvas, MoneyFormatter.formatNumber(slice.amountMinor, currency), right - 22f, ry + 11f,
                    kit.text(12.5f, c.ink, bold = true, align = Paint.Align.RIGHT),
                )
            }
            y += cardH + 20f
        }

        kit.draw(
            canvas, context.getString(R.string.pdf_mov_table).uppercase(), left, y + 8f,
            kit.text(13f, PdfColors.GRAY, bold = true, spacing = 0.1f),
        )
        return y + 22f
    }

    // ======================================================================
    // Diseño B: editorial
    // ======================================================================

    private fun writeEditorial(s: Statement, currency: CurrencyInfo, userName: String, output: File) {
        val m = 52f
        val right = PdfKit.W - m
        val name = userName.trim()
        val slices = expenseByCategory(s, context.getString(R.string.pdf_mov_other))
        val rows = s.rows
        val period = "${longDate(s.from)} – ${longDate(s.to)}"

        kit.build(output) { session ->
            var index = 0
            var first = true
            while (true) {
                val canvas = session.next(PdfColors.WHITE)
                // Fila superior: marca a la izquierda, período a la derecha.
                kit.mark(canvas, m, 44f, 30f)
                kit.wordmark(canvas, m + 40f, 66f, 21f, INK_BLACK, c.accent)
                kit.draw(canvas, period.uppercase(), right, 63f, kit.text(11f, PdfColors.GRAY, bold = true, align = Paint.Align.RIGHT, spacing = 0.1f))

                var y: Float
                if (first) {
                    y = drawEditorialSummary(canvas, s, currency, name, slices, m)
                } else {
                    y = 110f
                }

                kit.draw(
                    canvas, context.getString(R.string.pdf_mov_by_day).uppercase(), m, y,
                    kit.text(11f, PdfColors.GRAY, bold = true, spacing = 0.1f),
                )
                y += 8f

                val capacity = ((PdfKit.CONTENT_LIMIT - y) / E_ROW_H).toInt().coerceAtLeast(1)
                val count = minOf(rows.size - index, capacity)
                if (rows.isEmpty()) {
                    kit.draw(canvas, context.getString(R.string.pdf_mov_empty), m, y + 30f, kit.text(13f, PdfColors.GRAY))
                }
                for (i in 0 until count) {
                    val row = rows[index + i]
                    val top = y + i * E_ROW_H
                    canvas.drawLine(m, top, right, top, kit.stroke(c.line2, 1f))
                    val dayPaint = kit.text(20f, c.ink, bold = true)
                    val dayText = row.date.dayOfMonth.toString().padStart(2, '0')
                    kit.draw(canvas, dayText, m, top + 31f, dayPaint)
                    kit.draw(
                        canvas, " " + row.date.format(monthFormat).replace(".", ""),
                        m + dayPaint.measureText(dayText), top + 31f, kit.text(12f, PdfColors.GRAY),
                    )
                    val desc = kit.text(14f, c.ink, bold = true)
                    kit.draw(canvas, kit.fit(rowDescription(row), desc, 400f), m + 78f, top + 26f, desc)
                    val cat = row.categoryName.ifEmpty { if (row.kind == StatementKind.INITIAL) "" else "-" }
                    if (cat.isNotEmpty()) kit.draw(canvas, kit.fit(cat, kit.text(11.5f, PdfColors.GRAY), 400f), m + 78f, top + 42f, kit.text(11.5f, PdfColors.GRAY))
                    kit.draw(canvas, amountText(row, currency), right, top + 32f, kit.text(14f, amountColor(row), bold = true, align = Paint.Align.RIGHT))
                }
                if (count > 0) {
                    val endY = y + count * E_ROW_H
                    canvas.drawLine(m, endY, right, endY, kit.stroke(c.line2, 1f))
                }

                kit.footer(canvas, m, session.pageNumber, session.totalPages, secureBody())
                index += count
                first = false
                if (index >= rows.size) break
            }
        }
    }

    /** Saldo grande, entró/salió/ahorraste y barra de gastos. Devuelve la altura donde sigue la lista. */
    private fun drawEditorialSummary(
        canvas: Canvas,
        s: Statement,
        currency: CurrencyInfo,
        name: String,
        slices: List<CategorySlice>,
        m: Float,
    ): Float {
        val right = PdfKit.W - m
        val hello = if (name.isEmpty()) context.getString(R.string.pdf_mov_b_hello_anon) else context.getString(R.string.pdf_mov_b_hello, name)
        kit.draw(canvas, kit.fit(hello, kit.text(13f, PdfColors.GRAY), right - m), m, 134f, kit.text(13f, PdfColors.GRAY))

        var size = 60f
        var big = kit.text(size, c.ink, bold = true, spacing = -0.04f)
        val balanceText = MoneyFormatter.format(s.closingBalance, currency)
        while (big.measureText(balanceText) > right - m && size > 24f) {
            size -= 2f
            big = kit.text(size, c.ink, bold = true, spacing = -0.04f)
        }
        kit.draw(canvas, balanceText, m, 196f, big)

        val delta = s.closingBalance - s.openingBalance
        val deltaText = context.getString(
            if (delta >= 0) R.string.pdf_mov_up else R.string.pdf_mov_down,
            MoneyFormatter.format(abs(delta), currency),
        )
        kit.draw(canvas, kit.fit(deltaText, kit.text(14f, PdfColors.GRAY, bold = true), right - m), m, 224f, kit.text(14f, if (delta >= 0) PdfColors.GREEN_INCOME else PdfColors.RED, bold = true))

        // Franja de tres cifras
        val top = 250f
        canvas.drawLine(m, top, right, top, kit.stroke(c.line, 1f))
        canvas.drawLine(m, top + 74f, right, top + 74f, kit.stroke(c.line, 1f))
        val colW = (right - m) / 3f
        val saved = savedPercent(s)
        val cells = listOf(
            Triple(R.string.pdf_mov_in, MoneyFormatter.formatNumber(s.incomeTotal, currency), PdfColors.GREEN_INCOME),
            Triple(R.string.pdf_mov_out, MoneyFormatter.formatNumber(s.expenseTotal, currency), PdfColors.RED),
            Triple(R.string.pdf_mov_saved, saved?.let { "$it%" } ?: "—", c.ink),
        )
        cells.forEachIndexed { i, (label, value, color) ->
            val x = m + i * colW + (if (i == 0) 0f else 20f)
            if (i > 0) canvas.drawLine(m + i * colW, top + 10f, m + i * colW, top + 64f, kit.stroke(c.line, 1f))
            kit.draw(canvas, context.getString(label).uppercase(), x, top + 30f, kit.text(10.5f, PdfColors.GRAY, bold = true, spacing = 0.09f))
            val v = kit.text(20f, color, bold = true)
            kit.draw(canvas, kit.fit(value, v, colW - 28f), x, top + 56f, v)
        }

        var y = top + 74f + 34f
        val total = slices.sumOf { it.amountMinor }
        if (total > 0L) {
            kit.draw(
                canvas, context.getString(R.string.pdf_mov_where_b).uppercase(), m, y,
                kit.text(11f, PdfColors.GRAY, bold = true, spacing = 0.1f),
            )
            val palette = listOf(c.ink, c.accent, c.mid, c.line3())
            val barY = y + 12f
            val barW = right - m
            val gap = 2f
            var x = m
            slices.forEachIndexed { i, slice ->
                val w = (barW - gap * (slices.size - 1)) * slice.amountMinor / total
                val round = if (i == 0 || i == slices.lastIndex) 7f else 0f
                kit.round(canvas, x, barY, x + w.coerceAtLeast(3f), barY + 14f, round, kit.fill(palette[i % palette.size]))
                x += w + gap
            }
            // Leyenda
            var lx = m
            val legend = kit.text(12f, PdfColors.GRAY_DARK)
            slices.forEachIndexed { i, slice ->
                val pct = (slice.amountMinor * 100L / total).toInt()
                val label = "${slice.name} $pct%"
                val cut = kit.fit(label, legend, 210f)
                kit.round(canvas, lx, barY + 26f, lx + 9f, barY + 35f, 3f, kit.fill(palette[i % palette.size]))
                kit.draw(canvas, cut, lx + 15f, barY + 35f, legend)
                lx += 15f + legend.measureText(cut) + 22f
            }
            y = barY + 35f + 34f
        }
        return y
    }

    /** Tono claro para el último tramo de la barra. */
    private fun PdfColors.line3(): Int = mid2

    private companion object {
        const val HEAD_H = 36f
        const val ROW_H = 37f
        const val CLOSE_H = 46f
        const val E_ROW_H = 52f
        const val INK_BLACK = 0xFF111111.toInt()
    }
}
