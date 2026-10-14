package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Programa el revisor diario de cobros con WorkManager. Android decide el momento exacto y puede
 * retrasarlo unos minutos (o más con el ahorro de batería): el aviso llega "alrededor" de la hora.
 * WorkManager lo vuelve a programar solo después de reiniciar el teléfono.
 */
object ReminderScheduler {

    private const val WORK_NAME = "income-reminders"

    fun apply(context: Context, enabled: Boolean, hour: Int, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(now, hour).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Tiempo hasta la próxima vez que el reloj marque [hour]:00 (hoy si todavía no pasó, si no mañana). */
    fun delayUntilNext(now: LocalDateTime, hour: Int): Duration {
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
