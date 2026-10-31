package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuzufin.app.MoneaApplication
import com.yuzufin.app.R
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.domain.NotificationKind
import com.yuzufin.app.domain.WeeklySummaryCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Resumen del domingo: lo gastado en los últimos 7 días y la categoría que más pesó. Si el ajuste
 * "mostrar importes en los avisos" está apagado, el texto no lleva importes.
 */
class WeeklySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MoneaApplication).container
        return try {
            val settings = container.settingsRepository.settings.first()
            if (!WeeklySummaryCalculator.shouldSend(settings.weeklySummaryEnabled, LocalDateTime.now())) {
                return Result.success()
            }
            val today = LocalDate.now()
            val summary = WeeklySummaryCalculator.compute(container.financeRepository.transactions.first(), today)
            val names = container.financeRepository.categories.first().associate { it.id to it.name }
            val topName = summary.topCategoryId?.let { names[it] }
            val currency = CurrencyInfo.fromCode(settings.currencyCode)
            val showAmounts = settings.showAmountsInNotifications

            val title = applicationContext.getString(R.string.weekly_title)
            val body = when {
                summary.expenseCount == 0 || topName == null ->
                    applicationContext.getString(R.string.weekly_body_empty)
                showAmounts -> applicationContext.getString(
                    R.string.weekly_body_amount,
                    MoneyFormatter.format(summary.totalExpenseMinor, currency),
                    topName,
                    MoneyFormatter.format(summary.topCategoryMinor, currency),
                )
                else -> applicationContext.getString(R.string.weekly_body, topName)
            }
            // Un resumen por semana: si el trabajo se repite, no se muestra dos veces.
            val isNew = container.notificationRepository.log(NotificationKind.SUMMARY, title, body, "weekly:${today.toEpochDay()}")
            if (isNew) NotificationHelper.show(applicationContext, ID_WEEKLY, title, body)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin detalles en el log: no se escribe ningún dato financiero.
            Result.retry()
        }
    }

    private companion object {
        /** No choca con los avisos de hábito (4000-4023) ni con los de presupuestos (3000-3999). */
        const val ID_WEEKLY = 4100
    }
}
