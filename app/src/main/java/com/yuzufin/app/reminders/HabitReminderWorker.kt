package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuzufin.app.MoneaApplication
import com.yuzufin.app.R
import com.yuzufin.app.domain.HabitReminder
import com.yuzufin.app.domain.HabitSlot
import com.yuzufin.app.domain.NotificationKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Aviso "¿Anotaste hoy?": solo si hoy no hay ningún gasto ni ingreso anotado. Nunca incluye
 * importes (la pantalla bloqueada se ve). Cada hora avisa una sola vez por día.
 */
class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MoneaApplication).container
        return try {
            val slotHour = inputData.getInt(HabitReminderScheduler.KEY_HOUR, -1)
            if (slotHour !in 0..23) return Result.success()
            val now = LocalDateTime.now()
            if (!HabitReminder.isOnTime(now.hour, slotHour)) return Result.success()

            val settings = container.settingsRepository.settings.first()
            val today = LocalDate.now()
            val hasEntry = HabitReminder.hasEntryOn(container.financeRepository.transactions.first(), today)
            if (HabitReminder.shouldRemind(settings.habitRemindersEnabled, slotHour, settings.habitHours, hasEntry)) {
                val (title, body) = texts(HabitReminder.slotFor(slotHour))
                // Un aviso por hora y por día: si el trabajo se repite, no se muestra dos veces.
                val isNew = container.notificationRepository.log(
                    NotificationKind.HABIT, title, body, "habit:${today.toEpochDay()}:$slotHour",
                )
                if (isNew) NotificationHelper.show(applicationContext, ID_HABIT_BASE + slotHour, title, body)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun texts(slot: HabitSlot): Pair<String, String> {
        val (title, body) = when (slot) {
            HabitSlot.MORNING -> R.string.habit_morning_title to R.string.habit_morning_body
            HabitSlot.MIDDAY -> R.string.habit_midday_title to R.string.habit_midday_body
            HabitSlot.AFTERNOON -> R.string.habit_afternoon_title to R.string.habit_afternoon_body
            HabitSlot.EVENING -> R.string.habit_evening_title to R.string.habit_evening_body
            HabitSlot.NIGHT -> R.string.habit_night_title to R.string.habit_night_body
        }
        return applicationContext.getString(title) to applicationContext.getString(body)
    }

    private companion object {
        /** 4000 + hora: un id de notificación por hora (no choca con presupuestos, que usan 3000-3999). */
        const val ID_HABIT_BASE = 4000
    }
}
