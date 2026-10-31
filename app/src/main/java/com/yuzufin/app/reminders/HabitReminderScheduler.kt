package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Programa un trabajo diario por cada hora elegida para el aviso "¿Anotaste hoy?". Android puede
 * demorarlos unos minutos; si se demora demasiado, el trabajo no avisa (ver [com.yuzufin.app.domain.HabitReminder.isOnTime]).
 */
object HabitReminderScheduler {

    const val KEY_HOUR = "hour"
    private const val WORK_PREFIX = "habit-reminder-"

    fun apply(context: Context, enabled: Boolean, hours: Set<Int>, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        for (hour in 0..23) {
            val name = WORK_PREFIX + hour
            if (!enabled || hour !in hours) {
                workManager.cancelUniqueWork(name)
                continue
            }
            val request = PeriodicWorkRequestBuilder<HabitReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(ReminderScheduler.delayUntilNext(now, hour).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putInt(KEY_HOUR, hour).build())
                .build()
            workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
