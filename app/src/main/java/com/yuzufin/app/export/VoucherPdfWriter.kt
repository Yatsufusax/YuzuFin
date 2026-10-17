package com.yuzufin.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.data.AttachmentFileStore
import com.yuzufin.app.domain.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

/** Datos de un comprobante de movimiento. [photoNames] son archivos de [AttachmentFileStore]. */
data class VoucherData(
    val transactionId: Long,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryName: String,
    val description: String,
    val date: LocalDate,
    val currency: CurrencyInfo,
    val userName: String,
    val generatedAt: LocalDateTime,
    val photoNames: List<String>,
)

/**
 * Comprobante de un solo movimiento en PDF A4: datos del registro y las fotos adjuntas.
 * Es un documento independiente del extracto general. Reutiliza los dibujos de marca del extracto.
 */
class VoucherPdfWriter(private val context: Context, private val files: AttachmentFileStore) {

    private val base = StatementPdfWriter(context)
    private val longFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))
    private val stampFormat = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es"))

    private class Placed(val bitmap: Bitmap, val rect: RectF)

    fun write(data: VoucherData, output: File) {
        val bitmaps = data.photoNames.mapNotNull { name -> files.decodeBlocking(name, PDF_PHOTO_SIDE) }
        try {
            val pages = layoutPhotos(bitmaps)
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, placed ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), index + 1).create())
                    val canvas = page.canvas
                    canvas.drawRect(0f, 0f, PAGE_W, PAGE_H, base.fill(base.pageBg))
                    if (index == 0) {
                        drawFirstPage(canvas, data, bitmaps.size)
                    } else {
                        drawCompactHeader(canvas, data)
                    }
                    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    placed.forEach { canvas.drawBitmap(it.bitmap, null, it.rect, photoPaint) }
                    drawFooter(canvas, index + 1, pages.size)
                    document.finishPage(page)
                }
                output.parentFile?.mkdirs()
                FileOutputStream(output).use { document.writeTo(it) }
            } finally {
                document.close()
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    /** Reparte las fotos en páginas: una foto no se achica demasiado, si no entra pasa a la página siguiente. */
    private fun layoutPhotos(bitmaps: List<Bitmap>): List<List<Placed>> {
        val pages = mutableListOf(mutableListOf<Placed>())
        val contentW = PAGE_W - 2 * MARGIN
        val bottom = FOOTER_TOP - 14f
        var startY = PHOTOS_TOP
        var y = startY
        for (bitmap in bitmaps) {
            var scale = min(contentW / bitmap.width, (bottom - y) / bitmap.height)
            if (bitmap.height * scale < MIN_PHOTO_H && y != startY) {
                pages += mutableListOf<Placed>()
                startY = NEXT_PAGE_TOP
                y = startY
                scale = min(contentW / bitmap.width, (bottom - y) / bitmap.height)
            }
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val x = MARGIN + (contentW - w) / 2f
            pages.last() += Placed(bitmap, RectF(x, y, x + w, y + h))
            y += h + 14f
        }
        return pages
    }

    // ---------- Páginas ----------

    private fun drawFirstPage(canvas: Canvas, d: VoucherData, photoCount: Int) {
        base.drawLogo(canvas, MARGIN, 28f, 50f)
        base.drawWordmark(canvas, MARGIN + 60f, 62f, 30f)
        base.drawText(canvas, context.getString(R.string.statement_tagline), MARGIN + 61f, 82f, base.paint(11f, base.gray))
        val right = PAGE_W - MARGIN
        base.drawText(canvas, context.getString(R.string.voucher_title), right, 52f, base.paint(22f, base.ink, bold = true, align = Paint.Align.RIGHT))
        base.drawText(canvas, context.getString(R.string.voucher_number, d.transactionId), right, 71f, base.paint(10.5f, base.gray, align = Paint.Align.RIGHT))
        base.drawText(
            canvas,
            context.getString(R.string.statement_generated, d.generatedAt.format(stampFormat).replace(".", "")),
            right, 88f, base.paint(8.5f, base.gray, align = Paint.Align.RIGHT),
        )
        canvas.drawLine(MARGIN, 108f, right, 108f, base.stroke(base.line, 1f))

        // Importe destacado
        val isIncome = d.type == TransactionType.INCOME
        val amountColor = if (isIncome) base.green else base.red
        val box = RectF(MARGIN, 124f, right, 214f)
        canvas.drawRoundRect(box, 14f, 14f, base.fill(base.mint))
        val typeLabel = context.getString(if (isIncome) R.string.voucher_type_income else R.string.voucher_type_expense)
        base.drawText(canvas, typeLabel.uppercase(Locale.forLanguageTag("es")), box.left + 18f, box.top + 28f, base.paint(10.5f, base.gray, bold = true))
        val amountText = if (isIncome) {
            MoneyFormatter.format(d.amountMinor, d.currency, showPlus = true)
        } else {
            MoneyFormatter.format(-d.amountMinor, d.currency)
        }
        base.drawText(canvas, amountText, box.left + 18f, box.top + 66f, base.paint(30f, amountColor, bold = true))
        base.drawText(
            canvas, d.date.format(longFormat).replace(".", ""), box.right - 18f, box.top + 66f,
            base.paint(13f, base.ink, bold = true, align = Paint.Align.RIGHT),
        )

        // Filas de detalle
        val rows = listOf(
            context.getString(R.string.voucher_row_category) to d.categoryName,
            context.getString(R.string.voucher_row_description) to d.description.ifBlank { "—" },
            context.getString(R.string.voucher_row_date) to d.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            context.getString(R.string.voucher_row_user) to d.userName.trim().ifEmpty { context.getString(R.string.statement_user_default) },
        )
        var y = 244f
        val valuePaint = base.paint(12f, base.ink, bold = true)
        rows.forEach { (label, value) ->
            base.drawText(canvas, label, MARGIN + 4f, y, base.paint(10.5f, base.gray))
            base.drawText(canvas, fit(value, valuePaint, right - (MARGIN + 150f)), MARGIN + 150f, y, valuePaint)
            canvas.drawLine(MARGIN, y + 9f, right, y + 9f, base.stroke(base.line, 0.6f))
            y += 28f
        }

        val title = if (photoCount > 0) {
            context.getString(R.string.voucher_attachments, photoCount)
        } else {
            context.getString(R.string.voucher_no_attachments)
        }
        base.drawText(canvas, title, MARGIN, PHOTOS_TOP - 14f, base.paint(14f, base.ink, bold = true))
    }

    private fun drawCompactHeader(canvas: Canvas, d: VoucherData) {
        base.drawLogo(canvas, MARGIN, 26f, 26f)
        base.drawWordmark(canvas, MARGIN + 33f, 47f, 17f)
        val right = PAGE_W - MARGIN
        base.drawText(canvas, context.getString(R.string.voucher_title), right, 38f, base.paint(12f, base.ink, bold = true, align = Paint.Align.RIGHT))
        base.drawText(canvas, context.getString(R.string.voucher_number, d.transactionId), right, 52f, base.paint(9f, base.gray, align = Paint.Align.RIGHT))
        canvas.drawLine(MARGIN, 62f, right, 62f, base.stroke(base.line, 1f))
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, pageCount: Int) {
        canvas.drawLine(MARGIN, FOOTER_TOP, PAGE_W - MARGIN, FOOTER_TOP, base.stroke(base.line, 1f))
        base.drawLogo(canvas, MARGIN, FOOTER_TOP + 10f, 26f)
        base.drawWordmark(canvas, MARGIN + 33f, FOOTER_TOP + 30f, 16f)
        val note = base.paint(7.5f, base.gray, italic = true)
        context.getString(R.string.voucher_footer_note).split("\n").forEachIndexed { i, line ->
            base.drawText(canvas, line, 170f, FOOTER_TOP + 22f + i * 10f, note)
        }
        base.drawText(
            canvas, context.getString(R.string.statement_page, pageNumber, pageCount),
            PAGE_W - MARGIN, PAGE_H - 12f, base.paint(7.5f, base.gray, align = Paint.Align.RIGHT),
        )
    }

    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val room = maxWidth - paint.measureText("…")
        return text.take(paint.breakText(text, true, room, null)).trimEnd() + "…"
    }

    private companion object {
        const val PAGE_W = 595f
        const val PAGE_H = 842f
        const val MARGIN = 36f
        const val FOOTER_TOP = PAGE_H - 62f
        const val PHOTOS_TOP = 396f
        const val NEXT_PAGE_TOP = 84f
        const val MIN_PHOTO_H = 380f
        const val PDF_PHOTO_SIDE = 1200
    }
}
