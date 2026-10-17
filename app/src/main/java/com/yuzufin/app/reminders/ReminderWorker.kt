package com.yuzufin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yuzufin.app.R
import com.yuzufin.app.YuzuFinApplication
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import com.yuzufin.app.data.DueNotice
import com.yuzufin.app.domain.BudgetLevel
import com.yuzufin.app.domain.NotificationKind
import com.yuzufin.app.domain.StatsCalculator
import com.yuzufin.app.ui.common.formatShortDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

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
                        post(container, today, NotificationHelper.ID_PENDING,
                            applicationContext.resources.getQuantityString(R.plurals.reminders_pending_title, pending.size, pending.size),
                            describe(pending, settings.showAmountsInNotifications, currency),
                        )
                    }
                    if (auto.isNotEmpty()) {
                        post(container, today, NotificationHelper.ID_AUTO,
                            applicationContext.resources.getQuantityString(R.plurals.reminders_auto_title, auto.size, auto.size),
                            describe(auto, settings.showAmountsInNotifications, currency),
                        )
                    }
                }
                // Se marcan como avisados aunque los avisos estén apagados: al volver a activarlos
                // no llega una avalancha de cobros viejos.
                income.markNotified(due.map { it.occurrenceId })
            }
            remindIncomeSoon(container, today)
            remindSavings(container, today)
            remindBills(container, today)
            remindLoans(container, today)
            remindBudgets(container, today)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin detalles en el log: no se escribe ningún dato financiero.
            Result.retry()
        }
    }

    /** Una vez al mes, por meta: recuerda aportar si todavía no hubo un aporte ese mes. */
    private suspend fun remindSavings(container: com.yuzufin.app.AppContainer, today: LocalDate) {
        val savings = container.savingsRepository
        val due = savings.dueReminders(today)
        if (due.isEmpty()) return
        val settings = container.settingsRepository.settings.first()
        if (settings.remindersEnabled) {
            val currency = CurrencyInfo.fromCode(settings.currencyCode)
            val text = due.joinToString(", ") { goal ->
                if (settings.showAmountsInNotifications) {
                    applicationContext.getString(
                        R.string.reminders_savings_item_amount,
                        goal.name,
                        MoneyFormatter.format(goal.remainingMinor, currency),
                    )
                } else {
                    goal.name
                }
            }
            post(container, today, NotificationHelper.ID_SAVINGS,
                applicationContext.resources.getQuantityString(R.plurals.reminders_savings_title, due.size, due.size),
                text,
            )
        }
        // Se marcan aunque los avisos estén apagados, para no acumular avisos viejos.
        savings.markReminded(due.map { it.goalId }, today)
    }

    /** Facturas que vencen mañana, hoy o ya vencieron: un aviso por vencimiento. */
    private suspend fun remindBills(container: com.yuzufin.app.AppContainer, today: LocalDate) {
        val obligations = container.obligationsRepository
        val due = obligations.dueBillReminders(today)
        if (due.isEmpty()) return
        val settings = container.settingsRepository.settings.first()
        if (settings.remindersEnabled) {
            val currency = CurrencyInfo.fromCode(settings.currencyCode)
            val text = due.joinToString("\n") { bill ->
                val date = formatShortDate(bill.dueDate)
                if (settings.showAmountsInNotifications) {
                    applicationContext.getString(
                        R.string.reminders_bill_item_amount,
                        bill.name,
                        date,
                        MoneyFormatter.format(bill.amountMinor, currency),
                    )
                } else {
                    applicationContext.getString(R.string.reminders_bill_item, bill.name, date)
                }
            }
            post(container, today, NotificationHelper.ID_BILLS,
                applicationContext.resources.getQuantityString(R.plurals.reminders_bills_title, due.size, due.size),
                text,
            )
        }
        obligations.markBillsNotified(due.map { it.id })
    }

    /**
     * Últimos días del mes: quién te debe y a quién le debés, una vez por mes y por préstamo,
     * mientras quede saldo.
     */
    private suspend fun remindLoans(container: com.yuzufin.app.AppContainer, today: LocalDate) {
        val obligations = container.obligationsRepository
        val reminders = obligations.loanReminders(today)
        if (reminders.isEmpty) return
        val settings = container.settingsRepository.settings.first()
        if (settings.remindersEnabled) {
            val currency = CurrencyInfo.fromCode(settings.currencyCode)
            fun describe(items: List<com.yuzufin.app.data.LoanBalance>): String = items.joinToString(", ") { item ->
                if (settings.showAmountsInNotifications) {
                    applicationContext.getString(
                        R.string.reminders_loan_item_amount,
                        item.person,
                        MoneyFormatter.format(item.outstandingMinor, currency),
                    )
                } else {
                    item.person
                }
            }
            if (reminders.owedToMe.isNotEmpty()) {
                post(container, today, NotificationHelper.ID_LOANS_OWED,
                    applicationContext.resources.getQuantityString(
                        R.plurals.reminders_owed_title,
                        reminders.owedToMe.size,
                        reminders.owedToMe.size,
                    ),
                    describe(reminders.owedToMe),
                )
            }
            if (reminders.iOwe.isNotEmpty()) {
                post(container, today, NotificationHelper.ID_LOANS_OWE,
                    applicationContext.resources.getQuantityString(
                        R.plurals.reminders_owe_title,
                        reminders.iOwe.size,
                        reminders.iOwe.size,
                    ),
                    describe(reminders.iOwe),
                )
            }
        }
        obligations.markLoansReminded(reminders.ids, today)
    }

    /**
     * Presupuestos: un aviso al llegar al 80 % y otro al pasarse, una vez por categoría y por mes.
     * Se revisa una vez al día, junto con el resto de los avisos.
     */
    private suspend fun remindBudgets(container: com.yuzufin.app.AppContainer, today: LocalDate) {
        val budgets = container.budgetRepository.all()
        if (budgets.isEmpty()) return
        val settings = container.settingsRepository.settings.first()
        if (!settings.remindersEnabled) return
        val currency = CurrencyInfo.fromCode(settings.currencyCode)
        val month = YearMonth.from(today)
        val spent = StatsCalculator.expenseByCategory(
            container.financeRepository.initialBalance.first(),
            container.financeRepository.transactions.first(),
            month,
        )
        val names = container.financeRepository.categories.first().associate { it.id to it.name }
        for (progress in StatsCalculator.budgetProgress(budgets, spent)) {
            val over = progress.level == BudgetLevel.OVER
            if (!over && progress.level != BudgetLevel.WARNING) continue
            val name = names[progress.categoryId] ?: continue
            val title = if (over) {
                applicationContext.getString(R.string.notif_budget_over_title, name)
            } else {
                applicationContext.getString(R.string.notif_budget_warn_title, name, progress.percent)
            }
            val body = if (settings.showAmountsInNotifications) {
                applicationContext.getString(
                    R.string.notif_budget_body_amount,
                    MoneyFormatter.format(progress.spentMinor, currency),
                    MoneyFormatter.format(progress.limitMinor, currency),
                )
            } else {
                applicationContext.getString(R.string.notif_budget_body)
            }
            val key = "budget:${progress.categoryId}:${month.year * 100 + month.monthValue}:${if (over) "over" else "warn"}"
            val isNew = container.notificationRepository.log(NotificationKind.BUDGET, title, body, key)
            if (isNew) {
                NotificationHelper.show(
                    applicationContext,
                    NotificationHelper.ID_BUDGET_BASE + (progress.categoryId % 1000).toInt(),
                    title,
                    body,
                )
            }
        }
    }

    /** Un día antes: cobros programados para mañana. */
    private suspend fun remindIncomeSoon(container: com.yuzufin.app.AppContainer, today: LocalDate) {
        val soon = container.incomeRepository.dueTomorrow(today)
        if (soon.isEmpty()) return
        val settings = container.settingsRepository.settings.first()
        if (!settings.remindersEnabled) return
        val currency = CurrencyInfo.fromCode(settings.currencyCode)
        post(
            container,
            today,
            NotificationHelper.ID_INCOME_SOON,
            applicationContext.resources.getQuantityString(R.plurals.reminders_soon_title, soon.size, soon.size),
            describe(soon, settings.showAmountsInNotifications, currency),
        )
    }

    /** Guarda el aviso en la campanilla y lo muestra como notificación del sistema. */
    private suspend fun post(
        container: com.yuzufin.app.AppContainer,
        today: LocalDate,
        id: Int,
        title: String,
        text: String,
    ) {
        val kind = when (id) {
            NotificationHelper.ID_PENDING -> NotificationKind.INCOME_PENDING
            NotificationHelper.ID_AUTO -> NotificationKind.INCOME_AUTO
            NotificationHelper.ID_INCOME_SOON -> NotificationKind.INCOME_SOON
            NotificationHelper.ID_SAVINGS -> NotificationKind.SAVINGS
            NotificationHelper.ID_BILLS -> NotificationKind.BILL
            NotificationHelper.ID_LOANS_OWED -> NotificationKind.LOAN_OWED
            NotificationHelper.ID_LOANS_OWE -> NotificationKind.LOAN_OWE
            else -> NotificationKind.OTHER
        }
        // El historial se guarda aunque Android no deje mostrar la notificación (permiso apagado).
        container.notificationRepository.log(kind, title, text, "$id:${today.toEpochDay()}")
        NotificationHelper.show(applicationContext, id, title, text)
    }

    private fun describe(items: List<DueNotice>, showAmounts: Boolean, currency: CurrencyInfo): String =
        items.joinToString(", ") { item ->
            if (showAmounts) "${item.sourceName} · ${MoneyFormatter.format(item.amountMinor, currency)}" else item.sourceName
        }
}
