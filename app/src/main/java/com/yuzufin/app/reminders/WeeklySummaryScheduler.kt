package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yuzufin.app.domain.WeeklySummaryCalculator
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Programa el resumen semanal con WorkManager (el mismo mecanismo que los demás avisos).
 *
 * Antes era un trabajo cada 7 días: si Android lo demoraba pasada la medianoche del domingo, el
 * trabajo no avisaba y el siguiente quedaba 7 días después, es decir, nunca más volvía a caer en
 * domingo. Ahora hay un revisor **diario** alrededor de las 20:00; el trabajo decide si toca el
 * resumen del último domingo ([WeeklySummaryCalculator.dueSunday]) y no lo repite
 * (clave de deduplicación por semana). Así un retraso o un día perdido no pierde la semana.
 */
object WeeklySummaryScheduler {

    const val WORK_NAME = "weekly-summary-check"

    /** Nombre del trabajo semanal de versiones anteriores: se cancela para que no haya dos. */
    const val LEGACY_WORK_NAME = "weekly-summary"

    fun apply(context: Context, enabled: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(
                ReminderScheduler.delayUntilNext(now, WeeklySummaryCalculator.HOUR).toMillis(),
                TimeUnit.MILLISECONDS,
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
