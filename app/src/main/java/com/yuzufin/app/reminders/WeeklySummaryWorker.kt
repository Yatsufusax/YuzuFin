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
            // Revisor diario: solo actúa si toca el resumen del último domingo (con hasta 2 días de margen).
            val sunday = WeeklySummaryCalculator.dueSunday(settings.weeklySummaryEnabled, nowProvider())
                ?: return Result.success()
            val summary = WeeklySummaryCalculator.compute(container.financeRepository.transactions.first(), sunday)
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
            // Un resumen por semana: si el trabajo se repite, no se muestra dos veces. Siempre queda en el
            // buzón de la app; la notificación del sistema solo sale si Android lo permite (ver Ajustes).
            // Al tocar la notificación se abre el resumen de ESA semana (la clave lo identifica).
            val key = WeeklySummaryCalculator.dedupeKey(sunday)
            val isNew = container.notificationRepository.log(NotificationKind.SUMMARY, title, body, key)
            if (isNew) NotificationHelper.show(applicationContext, ID_WEEKLY, title, body, WeeklySummaryIntents.open(applicationContext, key))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin detalles en el log: no se escribe ningún dato financiero.
            Result.retry()
        }
    }

    companion object {
        /** Reloj del trabajo; las pruebas instrumentadas lo reemplazan para simular el domingo. */
        @Volatile
        var nowProvider: () -> LocalDateTime = { LocalDateTime.now() }

        /** No choca con los avisos de hábito (4000-4023) ni con los de presupuestos (3000-3999). */
        const val ID_WEEKLY = 4100
    }
}
