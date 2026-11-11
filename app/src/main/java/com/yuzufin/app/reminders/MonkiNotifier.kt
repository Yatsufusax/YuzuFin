package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yuzufin.app.AppContainer
import com.yuzufin.app.MoneaApplication
import com.yuzufin.app.R
import com.yuzufin.app.assistant.ProactiveKind
import com.yuzufin.app.assistant.ProactiveMonki
import com.yuzufin.app.assistant.ProactivePrefs
import com.yuzufin.app.assistant.RepositoryMonkiDataSource
import com.yuzufin.app.domain.MonkiProactiveMode
import com.yuzufin.app.domain.NotificationKind
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Mensajes de Monki: los guarda en la campanilla y, si los avisos están activos, los muestra como
 * notificación del sistema. Cada mensaje se publica una sola vez (clave de deduplicación).
 */
object MonkiNotifier {

    /** Lo que merece avisar en el momento: irregularidades y logros. Las rachas y el «¿todo bien?» esperan al chequeo diario. */
    val INSTANT_KINDS = setOf(
        ProactiveKind.OVERSPEND,
        ProactiveKind.LOW_FUNDS,
        ProactiveKind.MILESTONE,
        ProactiveKind.INCOME,
    )

    /** [kinds] null = todos los tipos (chequeo diario). Devuelve cuántos mensajes nuevos publicó. */
    suspend fun run(context: Context, container: AppContainer, today: LocalDate, kinds: Set<ProactiveKind>? = null): Int {
        val settings = container.settingsRepository.settings.first()
        if (settings.monkiProactive == MonkiProactiveMode.OFF) return 0
        val snapshot = RepositoryMonkiDataSource(
            container.financeRepository,
            container.budgetRepository,
            container.savingsRepository,
            container.obligationsRepository,
            container.settingsRepository,
        ).load()
        val prefs = ProactivePrefs(
            mode = settings.monkiProactive,
            showAmounts = settings.showAmountsInNotifications,
            lastOpenEpochDay = container.settingsRepository.lastOpenDay(),
        )
        val title = context.getString(R.string.monki_proactive_title)
        var published = 0
        for (note in ProactiveMonki.notes(snapshot, today, prefs, limit = Int.MAX_VALUE)) {
            if (published >= ProactiveMonki.MAX_NOTES_PER_DAY) break
            if (kinds != null && note.kind !in kinds) continue
            if (container.notificationRepository.log(NotificationKind.MONKI, title, note.text, note.dedupeKey)) {
                published++
                // Un id por mensaje, para que dos avisos seguidos no se pisen.
                if (settings.remindersEnabled) {
                    NotificationHelper.show(
                        context,
                        NotificationHelper.ID_MONKI_BASE + (note.dedupeKey.hashCode() and 0x3FF),
                        title,
                        note.text,
                    )
                }
            }
        }
        return published
    }

    /** Pide revisar ahora (después de anotar un movimiento o un aporte). Junta varios pedidos seguidos en uno. */
    fun checkSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<InstantWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("monki_instant", ExistingWorkPolicy.REPLACE, request)
    }

    class InstantWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val container = (applicationContext as MoneaApplication).container
            return try {
                run(applicationContext, container, LocalDate.now(), INSTANT_KINDS)
                Result.success()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.success() // Es un extra: si falla, el chequeo diario lo recupera.
            }
        }
    }
}
