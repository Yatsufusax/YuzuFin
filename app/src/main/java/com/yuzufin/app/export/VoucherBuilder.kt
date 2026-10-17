package com.yuzufin.app.export

import android.content.Context
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.AttachmentRepository
import com.yuzufin.app.data.FinanceRepository
import com.yuzufin.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Arma el comprobante PDF de un movimiento con lo que está guardado (datos y fotos). */
class VoucherBuilder(
    private val context: Context,
    private val finance: FinanceRepository,
    private val attachments: AttachmentRepository,
    private val settings: SettingsRepository,
) {

    /** Devuelve el PDF listo para compartir, o null si el movimiento ya no existe. */
    suspend fun build(transactionId: Long): File? {
        val transaction = finance.getTransaction(transactionId) ?: return null
        val category = finance.categories.first().firstOrNull { it.id == transaction.categoryId }?.name.orEmpty()
        val appSettings = settings.settings.first()
        val photos = attachments.list(transactionId).map { it.fileName }
        return withContext(Dispatchers.IO) {
            // Solo queda el último comprobante en el teléfono.
            val dir = File(context.cacheDir, "vouchers")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(transaction.date)
            val file = File(dir, "Comprobante-YuzuFin-$stamp-$transactionId.pdf")
            VoucherPdfWriter(context, attachments.files).write(
                VoucherData(
                    transactionId = transactionId,
                    type = transaction.type,
                    amountMinor = transaction.amountMinor,
                    categoryName = category,
                    description = transaction.description,
                    date = transaction.date,
                    currency = CurrencyInfo.fromCode(appSettings.currencyCode),
                    userName = appSettings.userName,
                    generatedAt = LocalDateTime.now(),
                    photoNames = photos,
                ),
                file,
            )
            file
        }
    }
}
