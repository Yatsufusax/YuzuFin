package com.yuzufin.app.ui.recurring

import android.content.Context
import com.yuzufin.app.AppContainer
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.InstallmentPlan
import com.yuzufin.app.domain.InstallmentReportBuilder
import com.yuzufin.app.export.InstallmentsPdfWriter
import com.yuzufin.app.export.PdfColors
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDateTime

/** Un plan en cuotas con el id de su gasto fijo (sirve para numerar el comprobante). */
data class PlanWithId(val id: Long, val plan: InstallmentPlan)

/**
 * Arma y guarda en la caché el PDF del comprobante de cuotas. Se llama desde un hilo de fondo.
 * Los gastos en cuotas terminados siguen en la lista hasta que la persona los borre, así que su
 * comprobante se puede descargar siempre.
 */
object InstallmentExport {

    /** Todos los gastos en cuotas, con su detalle. */
    suspend fun plans(container: AppContainer): List<PlanWithId> {
        val expenses = container.recurringRepository.expenses.first().filter { it.isInstallments }
        if (expenses.isEmpty()) return emptyList()
        val occurrences = container.recurringRepository.occurrences.first()
        val categories = container.financeRepository.categories.first().associate { it.id to it.name }
        val transactionDates = container.financeRepository.transactions.first().associate { it.id to it.date }
        return expenses.map { expense ->
            PlanWithId(
                expense.id,
                InstallmentReportBuilder.build(
                    expense = expense,
                    categoryName = categories[expense.categoryId].orEmpty(),
                    occurrences = occurrences,
                    transactionDates = transactionDates,
                ),
            )
        }
    }

    /** PDF de un plan ([expenseId]) o, con null, el resumen de todos. Devuelve null si no hay nada que mostrar. */
    suspend fun generate(context: Context, container: AppContainer, expenseId: Long?): File? {
        val all = plans(container)
        if (all.isEmpty()) return null
        val settings = container.settingsRepository.settings.first()
        val currency = CurrencyInfo.fromCode(settings.currencyCode)
        val writer = InstallmentsPdfWriter(context, PdfColors.of(settings.themePalette))
        val now = LocalDateTime.now()

        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        return if (expenseId == null) {
            val file = File(dir, "Resumen-cuotas-Monea.pdf")
            writer.writeSummary(all.map { it.plan }, currency, settings.userName, now, file)
            file
        } else {
            val item = all.firstOrNull { it.id == expenseId } ?: return null
            val safeName = item.plan.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "plan" }
            val file = File(dir, "Comprobante-cuotas-$safeName.pdf")
            writer.writePlan(item.plan, item.id, currency, settings.userName, now, file)
            file
        }
    }
}
