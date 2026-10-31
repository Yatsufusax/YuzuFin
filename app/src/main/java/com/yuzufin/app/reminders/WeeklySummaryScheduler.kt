package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yuzufin.app.domain.WeeklySummaryCalculator
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Programa el resumen semanal: un trabajo cada 7 días, alrededor del domingo a las 20:00. */
object WeeklySummaryScheduler {

    private const val WORK_NAME = "weekly-summary"

    fun apply(context: Context, enabled: Boolean, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(WeeklySummaryCalculator.delayUntilNext(now).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
