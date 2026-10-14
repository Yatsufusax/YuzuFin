package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuzufin.app.R
import com.yuzufin.app.YuzuFinApplication
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.data.DueNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Una vez al día: crea los cobros vencidos (y registra los automáticos) y avisa de los que la
 * persona todavía no vio. Cada cobro se avisa una sola vez.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as YuzuFinApplication).container
        return try {
            val income = container.incomeRepository
            val today = LocalDate.now()
            income.reconcile(today)

            val due = income.unnotifiedDue(today)
            if (due.isNotEmpty()) {
                val settings = container.settingsRepository.settings.first()
                if (settings.remindersEnabled) {
                    val currency = CurrencyInfo.fromCode(settings.currencyCode)
                    val pending = due.filter { it.remind && it.pending }
                    val auto = due.filter { it.remind && !it.pending }
                    if (pending.isNotEmpty()) {
                        NotificationHelper.show(
                            applicationContext,
                            NotificationHelper.ID_PENDING,
                            applicationContext.resources.getQuantityString(R.plurals.reminders_pending_title, pending.size, pending.size),
                            describe(pending, settings.showAmountsInNotifications, currency),
                        )
                    }
                    if (auto.isNotEmpty()) {
                        NotificationHelper.show(
                            applicationContext,
                            NotificationHelper.ID_AUTO,
                            applicationContext.resources.getQuantityString(R.plurals.reminders_auto_title, auto.size, auto.size),
                            describe(auto, settings.showAmountsInNotifications, currency),
                        )
                    }
                }
                // Se marcan como avisados aunque los avisos estén apagados: al volver a activarlos
                // no llega una avalancha de cobros viejos.
                income.markNotified(due.map { it.occurrenceId })
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin detalles en el log: no se escribe ningún dato financiero.
            Result.retry()
        }
    }

    private fun describe(items: List<DueNotice>, showAmounts: Boolean, currency: CurrencyInfo): String =
        items.joinToString(", ") { item ->
            if (showAmounts) "${item.sourceName} · ${MoneyFormatter.format(item.amountMinor, currency)}" else item.sourceName
        }
}
