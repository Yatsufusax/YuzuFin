package com.yuzufin.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.InstallmentLine
import com.yuzufin.app.domain.InstallmentPlan
import com.yuzufin.app.domain.InstallmentState
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Comprobante de cuotas en PDF A4: el de un solo plan y el resumen de todos.
 * Documento personal (no es una factura): se arma en el teléfono con lo que cargó la persona.
 */
class InstallmentsPdfWriter(private val context: Context, colors: PdfColors) {

    private val kit = PdfKit(context, colors)
    private val c = colors

    private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
    private fun date(d: LocalDate) = d.format(shortDate).replace(".", "")

    private val cardBg = 0xFFF6F8F7.toInt()

    // ======================================================================
    // Un plan
    // ======================================================================

    fun writePlan(
        plan: InstallmentPlan,
        planId: Long,
        currency: CurrencyInfo,
        userName: String,
        generatedAt: LocalDateTime,
        output: File,
    ) {
        val today = generatedAt.toLocalDate()
        val holder = userName.trim().ifEmpty { context.getString(R.string.statement_user_default) }
        val number = "CU-${today.year}-${planId.toString().padStart(4, '0')}"
        val lines = plan.lines

        kit.build(output) { session ->
            var index = 0
            var first = true
            while (true) {
                val canvas = session.next(PdfColors.PAGE)
                var y: Float
                if (first) {
                    kit.headerBand(
                        canvas,
                        context.getString(R.string.pdf_inst_title),
                        listOf(context.getString(R.string.pdf_inst_number, number), context.getString(R.string.pdf_issued, date(today))),
                    )
                    y = drawPlanIntro(canvas, plan, currency, holder)
                } else {
                    kit.compactBand(canvas, context.getString(R.string.pdf_inst_title), plan.name)
                    y = 96f
                }

                val limit = PdfKit.CONTENT_LIMIT - NOTE_LINE_H
                val remaining = lines.size - index
                val capacity = ((limit - y - HEAD_H) / ROW_H).toInt().coerceAtLeast(1)

                val last: Boolean
                val count: Int
                if (remaining <= capacity && y + HEAD_H + remaining * ROW_H + TOTAL_H <= limit) {
                    count = remaining
                    last = true
                } else {
                    // Si el total no entra, se deja al menos una fila para la página siguiente.
                    count = minOf(remaining, if (remaining <= capacity) (capacity - 1).coerceAtLeast(1) else capacity)
                    last = false
                }

                val tableBottom = drawPlanTable(canvas, plan, currency, lines.subList(index, index + count), y, last)
                if (last) drawPlanNote(canvas, plan, today, tableBottom + 18f)
                drawDisclaimer(canvas)
                kit.footer(canvas, PdfKit.M, session.pageNumber, session.totalPages, secureBody(R.string.pdf_secure_body_voucher))

                index += count
                first = false
                if (last) break
            }
        }
    }

    /** Dibuja título, tarjeta de progreso y devuelve la altura donde empieza la tabla. */
    private fun drawPlanIntro(canvas: Canvas, plan: InstallmentPlan, currency: CurrencyInfo, holder: String): Float {
        val right = PdfKit.W - PdfKit.M
        kit.draw(canvas, context.getString(R.string.pdf_inst_kicker), PdfKit.M, 158f, kit.text(12f, PdfColors.GRAY, bold = true))
        val title = kit.text(40f, c.ink, bold = true, spacing = -0.02f)
        kit.draw(canvas, kit.fit(plan.name, title, 470f), PdfKit.M, 200f, title)
        val sub = kit.text(13f, PdfColors.GRAY)
        kit.draw(
            canvas,
            kit.fit(
                context.getString(
                    R.string.pdf_inst_subtitle, plan.total, MoneyFormatter.format(plan.installmentMinor, currency),
                    plan.dayOfMonth, plan.categoryName.ifEmpty { "-" },
                ),
                sub, 690f,
            ),
            PdfKit.M, 224f, sub,
        )
        kit.draw(canvas, context.getString(R.string.pdf_holder), right, 158f, kit.text(12f, PdfColors.GRAY, align = Paint.Align.RIGHT))
        val holderPaint = kit.text(14f, c.ink, bold = true, align = Paint.Align.RIGHT)
        kit.draw(canvas, kit.fit(holder, holderPaint, 200f), right, 180f, holderPaint)

        // Tarjeta de progreso
        val top = 252f
        val left = PdfKit.M
        val width = PdfKit.W - 2 * PdfKit.M
        val bottom = top + 204f
        kit.round(canvas, left, top, left + width, bottom, 18f, kit.fill(PdfColors.WHITE))
        kit.round(canvas, left, top, left + width, bottom, 18f, kit.stroke(c.line, 1f))

        val innerL = left + 26f
        val innerR = left + width - 26f
        val big = kit.text(34f, c.accent, bold = true, spacing = -0.02f)
        val paidText = plan.paidCount.toString()
        kit.draw(canvas, paidText, innerL, top + 58f, big)
        kit.draw(
            canvas, context.getString(R.string.pdf_inst_progress_tail, plan.total),
            innerL + big.measureText(paidText) + 8f, top + 58f, kit.text(15f, c.ink, bold = true),
        )
        val leftText = if (plan.openCount == 0) {
            context.getString(R.string.pdf_inst_complete)
        } else {
            context.resources.getQuantityString(R.plurals.pdf_inst_left, plan.openCount, plan.openCount)
        }
        kit.draw(canvas, leftText, innerR, top + 58f, kit.text(12f, PdfColors.GRAY, align = Paint.Align.RIGHT))

        kit.segments(canvas, innerL, top + 80f, innerR - innerL, 12f, plan.total, plan.paidCount)

        val gap = 12f
        val boxW = (innerR - innerL - 2 * gap) / 3f
        val values = listOf(
            Triple(R.string.pdf_paid, plan.paidMinor, true),
            Triple(R.string.pdf_to_pay, plan.openMinor, false),
            Triple(R.string.pdf_plan_total, plan.totalMinor, false),
        )
        values.forEachIndexed { i, (label, amount, highlight) ->
            val x = innerL + i * (boxW + gap)
            kit.round(canvas, x, top + 110f, x + boxW, top + 178f, 12f, kit.fill(if (highlight) c.mint else cardBg))
            kit.draw(canvas, context.getString(label).uppercase(), x + 16f, top + 131f, kit.text(10.5f, PdfColors.GRAY, bold = true, spacing = 0.08f))
            val v = kit.text(19f, if (highlight) c.accent else c.ink, bold = true)
            kit.draw(canvas, kit.fit(MoneyFormatter.format(amount, currency), v, boxW - 28f), x + 16f, top + 160f, v)
        }

        kit.draw(
            canvas, context.getString(R.string.pdf_detail).uppercase(), PdfKit.M, bottom + 32f,
            kit.text(13f, PdfColors.GRAY, bold = true, spacing = 0.1f),
        )
        return bottom + 46f
    }

    /** Dibuja la tabla desde [top]; devuelve el borde de abajo. [withTotal] agrega la fila final. */
    private fun drawPlanTable(
        canvas: Canvas,
        plan: InstallmentPlan,
        currency: CurrencyInfo,
        rows: List<InstallmentLine>,
        top: Float,
        withTotal: Boolean,
    ): Float {
        val left = PdfKit.M
        val right = PdfKit.W - PdfKit.M
        val bottom = top + HEAD_H + rows.size * ROW_H + (if (withTotal) TOTAL_H else 0f)
        val box = RectF(left, top, right, bottom)

        canvas.drawRoundRect(box, 16f, 16f, kit.fill(PdfColors.WHITE))
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(box, 16f, 16f, Path.Direction.CW) })

        canvas.drawRect(left, top, right, top + HEAD_H, kit.fill(c.mint))
        val head = kit.text(10.5f, c.ink, bold = true, spacing = 0.08f)
        val hy = top + 23f
        val xNum = left + 20f
        val xDue = left + 76f
        val xPaid = left + 247f
        val xState = left + 418f
        val xAmount = right - 20f
        kit.draw(canvas, context.getString(R.string.pdf_col_installment).uppercase(), xNum, hy, head)
        kit.draw(canvas, context.getString(R.string.pdf_col_due).uppercase(), xDue, hy, head)
        kit.draw(canvas, context.getString(R.string.pdf_col_paid_on).uppercase(), xPaid, hy, head)
        kit.draw(canvas, context.getString(R.string.pdf_col_state).uppercase(), xState, hy, head)
        kit.draw(
            canvas, context.getString(R.string.pdf_col_amount).uppercase(), xAmount, hy,
            kit.text(10.5f, c.ink, bold = true, align = Paint.Align.RIGHT, spacing = 0.08f),
        )

        var y = top + HEAD_H
        for (row in rows) {
            canvas.drawLine(left, y, right, y, kit.stroke(c.line2, 1f))
            val base = y + 25f
            val open = row.isOpen
            val color = if (open) PdfColors.GRAY else c.ink
            kit.draw(canvas, "${row.number}/${plan.total}", xNum, base, kit.text(13f, color, bold = true))
            val normal = kit.text(13f, color)
            kit.draw(canvas, row.dueDate?.let { date(it) } ?: "—", xDue, base, normal)
            kit.draw(canvas, row.paidDate?.let { date(it) } ?: "—", xPaid, base, normal)
            drawState(canvas, row.state, xState, base)
            val amount = if (row.state == InstallmentState.SKIPPED) "—" else MoneyFormatter.formatNumber(row.amountMinor, currency)
            kit.draw(canvas, amount, xAmount, base, kit.text(13f, color, bold = true, align = Paint.Align.RIGHT))
            y += ROW_H
        }

        if (withTotal) {
            canvas.drawRect(left, y, right, y + TOTAL_H, kit.fill(c.ink))
            kit.draw(canvas, context.getString(R.string.pdf_total_paid), xNum, y + 31f, kit.text(13f, PdfColors.WHITE, bold = true))
            kit.draw(
                canvas, MoneyFormatter.format(plan.paidMinor, currency), xAmount, y + 32f,
                kit.text(16f, PdfColors.WHITE, bold = true, align = Paint.Align.RIGHT),
            )
        }
        canvas.restore()
        canvas.drawRoundRect(box, 16f, 16f, kit.stroke(c.line, 1f))
        return bottom
    }

    private fun drawState(canvas: Canvas, state: InstallmentState, x: Float, baseline: Float) {
        val (label, bg, fg) = when (state) {
            InstallmentState.PAID -> Triple(R.string.pdf_state_paid, c.pillBg, c.pillFg)
            InstallmentState.PAID_BEFORE -> Triple(R.string.pdf_state_before, c.pillBg, c.pillFg)
            InstallmentState.ADVANCED -> Triple(R.string.pdf_state_advanced, PdfColors.BLUE_BG, PdfColors.BLUE_FG)
            InstallmentState.SKIPPED -> Triple(R.string.pdf_state_skipped, PdfColors.GRAY_BG, PdfColors.GRAY_DARK)
            InstallmentState.DUE, InstallmentState.UPCOMING ->
                Triple(R.string.pdf_state_pending, PdfColors.GRAY_BG, PdfColors.GRAY_DARK)
        }
        kit.pill(canvas, context.getString(label), x, baseline, bg, fg)
    }

    private fun drawPlanNote(canvas: Canvas, plan: InstallmentPlan, today: LocalDate, top: Float) {
        val advanced = plan.lines.count { it.state == InstallmentState.ADVANCED }
        val parts = ArrayList<String>()
        if (advanced > 0) parts += context.resources.getQuantityString(R.plurals.pdf_inst_note_advanced, advanced, advanced)
        if (plan.finished) {
            plan.lastPaidDate?.let { parts += context.getString(R.string.pdf_inst_note_done, date(it)) }
        } else {
            plan.nextDue?.let { parts += context.getString(R.string.pdf_inst_note_next, date(it)) }
        }
        if (parts.isEmpty() || top + 44f > PdfKit.CONTENT_LIMIT - NOTE_LINE_H) return
        val box = RectF(PdfKit.M, top, PdfKit.W - PdfKit.M, top + 44f)
        val dash = kit.stroke(c.line, 1f).apply { pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 4f), 0f) }
        canvas.drawRoundRect(box, 12f, 12f, kit.fill(PdfColors.WHITE))
        canvas.drawRoundRect(box, 12f, 12f, dash)
        val p = kit.text(12f, PdfColors.GRAY)
        kit.draw(canvas, kit.fit(parts.joinToString(" "), p, box.width() - 32f), PdfKit.M + 16f, top + 27f, p)
    }

    // ======================================================================
    // Resumen de todos los planes
    // ======================================================================

    fun writeSummary(
        plans: List<InstallmentPlan>,
        currency: CurrencyInfo,
        userName: String,
        generatedAt: LocalDateTime,
        output: File,
    ) {
        val today = generatedAt.toLocalDate()
        val holder = userName.trim().ifEmpty { context.getString(R.string.statement_user_default) }
        val active = plans.filter { !it.finished }.sortedBy { it.nextDue ?: LocalDate.MAX }
        val done = plans.filter { it.finished }
        val ordered = active + done

        val paid = plans.sumOf { it.paidMinor }
        val open = plans.sumOf { it.openMinor }
        val month = plans.sumOf { it.dueInMonth(today) }

        // Próximas cuotas de todos los planes activos, para "Lo que viene".
        val upcoming = active.flatMap { plan ->
            plan.lines.filter { it.isOpen && it.dueDate != null }.map { plan to it }
        }.sortedBy { it.second.dueDate }.take(3)

        kit.build(output) { session ->
            var canvas = session.next(PdfColors.PAGE)
            kit.headerBand(
                canvas,
                context.getString(R.string.pdf_sum_title),
                listOf(context.getString(R.string.pdf_sum_asof, date(today)), holder),
            )
            kit.draw(canvas, context.getString(R.string.pdf_sum_headline), PdfKit.M, 178f, kit.text(34f, c.ink, bold = true, spacing = -0.02f))
            kit.draw(
                canvas, context.getString(R.string.pdf_sum_counts, active.size, done.size),
                PdfKit.M, 202f, kit.text(13f, PdfColors.GRAY),
            )

            // Tres cifras
            val gap = 12f
            val boxW = (PdfKit.W - 2 * PdfKit.M - 2 * gap) / 3f
            val kpis = listOf(
                Triple(R.string.pdf_sum_paid, paid, true),
                Triple(R.string.pdf_sum_left, open, false),
                Triple(R.string.pdf_sum_month, month, false),
            )
            kpis.forEachIndexed { i, (label, amount, highlight) ->
                val x = PdfKit.M + i * (boxW + gap)
                if (highlight) {
                    kit.round(canvas, x, 226f, x + boxW, 308f, 14f, kit.fill(c.mint))
                } else {
                    kit.round(canvas, x, 226f, x + boxW, 308f, 14f, kit.fill(PdfColors.WHITE))
                    kit.round(canvas, x, 226f, x + boxW, 308f, 14f, kit.stroke(c.line, 1f))
                }
                kit.draw(canvas, context.getString(label).uppercase(), x + 18f, 250f, kit.text(10.5f, PdfColors.GRAY, bold = true, spacing = 0.08f))
                val v = kit.text(21f, if (highlight) c.accent else c.ink, bold = true)
                kit.draw(canvas, kit.fit(MoneyFormatter.format(amount, currency), v, boxW - 36f), x + 18f, 286f, v)
            }

            kit.draw(
                canvas, context.getString(R.string.pdf_sum_plans).uppercase(), PdfKit.M, 342f,
                kit.text(13f, PdfColors.GRAY, bold = true, spacing = 0.1f),
            )
            var y = 356f

            fun newPage() {
                kit.footer(canvas, PdfKit.M, session.pageNumber, session.totalPages, secureBody(R.string.pdf_secure_body_summary))
                drawDisclaimer(canvas)
                canvas = session.next(PdfColors.PAGE)
                kit.compactBand(canvas, context.getString(R.string.pdf_sum_title), context.getString(R.string.pdf_sum_asof, date(today)))
                y = 96f
            }

            for (plan in ordered) {
                val h = if (plan.finished) DONE_CARD_H else PLAN_CARD_H
                if (y + h > PdfKit.CONTENT_LIMIT - NOTE_LINE_H) newPage()
                if (plan.finished) drawDoneCard(canvas, plan, currency, y) else drawPlanCard(canvas, plan, currency, y)
                y += h + 12f
            }

            if (upcoming.isNotEmpty()) {
                val h = 24f + upcoming.size * 40f
                if (y + 14f + h > PdfKit.CONTENT_LIMIT - NOTE_LINE_H) newPage()
                kit.draw(
                    canvas, context.getString(R.string.pdf_sum_upcoming).uppercase(), PdfKit.M, y + 22f,
                    kit.text(13f, PdfColors.GRAY, bold = true, spacing = 0.1f),
                )
                val top = y + 34f
                val box = RectF(PdfKit.M, top, PdfKit.W - PdfKit.M, top + upcoming.size * 40f)
                canvas.drawRoundRect(box, 16f, 16f, kit.fill(PdfColors.WHITE))
                canvas.drawRoundRect(box, 16f, 16f, kit.stroke(c.line, 1f))
                upcoming.forEachIndexed { i, (plan, line) ->
                    val ry = top + i * 40f
                    if (i > 0) canvas.drawLine(PdfKit.M, ry, PdfKit.W - PdfKit.M, ry, kit.stroke(c.line2, 1f))
                    val base = ry + 25f
                    kit.draw(canvas, line.dueDate?.let { date(it) }.orEmpty(), PdfKit.M + 20f, base, kit.text(13f, PdfColors.GRAY))
                    val label = context.getString(
                        if (line.number == plan.total) R.string.pdf_sum_upcoming_last else R.string.pdf_sum_upcoming_row,
                        plan.name, line.number, plan.total,
                    )
                    kit.draw(canvas, kit.fit(label, kit.text(13f, c.ink, bold = true), 380f), PdfKit.M + 150f, base, kit.text(13f, c.ink, bold = true))
                    kit.draw(
                        canvas, MoneyFormatter.formatNumber(line.amountMinor, currency), PdfKit.W - PdfKit.M - 20f, base,
                        kit.text(13f, c.ink, bold = true, align = Paint.Align.RIGHT),
                    )
                }
            }

            drawDisclaimer(canvas)
            kit.footer(canvas, PdfKit.M, session.pageNumber, session.totalPages, secureBody(R.string.pdf_secure_body_summary))
        }
    }

    private fun drawPlanCard(canvas: Canvas, plan: InstallmentPlan, currency: CurrencyInfo, top: Float) {
        val left = PdfKit.M
        val right = PdfKit.W - PdfKit.M
        kit.round(canvas, left, top, right, top + PLAN_CARD_H, 16f, kit.fill(PdfColors.WHITE))
        kit.round(canvas, left, top, right, top + PLAN_CARD_H, 16f, kit.stroke(c.line, 1f))

        val name = kit.text(17f, c.ink, bold = true)
        kit.draw(canvas, kit.fit(plan.name, name, 300f), left + 22f, top + 34f, name)
        val used = (plan.lines.count { it.isPaid || it.state == InstallmentState.SKIPPED }).coerceAtLeast(1)
        val progress = kit.text(13f, PdfColors.GRAY, align = Paint.Align.RIGHT)
        kit.draw(
            canvas,
            kit.fit(
                context.getString(R.string.pdf_sum_progress, used, plan.total, MoneyFormatter.format(plan.installmentMinor, currency)),
                progress, 340f,
            ),
            right - 22f, top + 34f, progress,
        )
        kit.segments(canvas, left + 22f, top + 50f, right - left - 44f, 9f, plan.total, plan.paidCount)

        val small = kit.text(12f, PdfColors.GRAY)
        kit.draw(
            canvas,
            kit.fit(
                context.getString(
                    R.string.pdf_sum_paid_left,
                    MoneyFormatter.format(plan.paidMinor, currency), MoneyFormatter.format(plan.openMinor, currency),
                ),
                small, 430f,
            ),
            left + 22f, top + 86f, small,
        )
        plan.nextDue?.let {
            kit.draw(
                canvas, context.getString(R.string.pdf_sum_next, date(it)), right - 22f, top + 86f,
                kit.text(12f, PdfColors.GRAY, align = Paint.Align.RIGHT),
            )
        }
    }

    private fun drawDoneCard(canvas: Canvas, plan: InstallmentPlan, currency: CurrencyInfo, top: Float) {
        val left = PdfKit.M
        val right = PdfKit.W - PdfKit.M
        kit.round(canvas, left, top, right, top + DONE_CARD_H, 16f, kit.fill(0xFFF3F7F5.toInt()))
        kit.round(canvas, left, top, right, top + DONE_CARD_H, 16f, kit.stroke(c.line, 1f))
        val name = kit.text(15f, PdfColors.GRAY_DARK, bold = true)
        kit.draw(canvas, kit.fit(plan.name, name, 420f), left + 22f, top + 29f, name)
        val finished = plan.lastPaidDate
        val line = if (finished != null) {
            context.getString(R.string.pdf_sum_done_line, plan.total, MoneyFormatter.format(plan.installmentMinor, currency), date(finished))
        } else {
            context.getString(R.string.pdf_sum_done_line_nodate, plan.total, MoneyFormatter.format(plan.installmentMinor, currency))
        }
        val small = kit.text(12f, PdfColors.GRAY)
        kit.draw(canvas, kit.fit(line, small, 460f), left + 22f, top + 50f, small)
        val label = context.getString(R.string.pdf_sum_done)
        val w = kit.text(11f, c.pillFg, bold = true).measureText(label) + 24f
        kit.pill(canvas, label, right - 22f - w + 0f, top + 40f, c.pillBg, c.pillFg)
    }

    // ======================================================================

    private fun drawDisclaimer(canvas: Canvas) {
        val p = kit.text(9.5f, PdfColors.GRAY)
        kit.draw(canvas, kit.fit(context.getString(R.string.pdf_disclaimer), p, PdfKit.W - 2 * PdfKit.M), PdfKit.M, PdfKit.FOOTER_TOP - 10f, p)
    }

    private fun secureBody(res: Int): List<String> = context.getString(res).split("\n")

    private companion object {
        const val HEAD_H = 38f
        const val ROW_H = 40f
        const val TOTAL_H = 52f
        const val NOTE_LINE_H = 12f
        const val PLAN_CARD_H = 104f
        const val DONE_CARD_H = 70f
    }
}
