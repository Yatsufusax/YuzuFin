package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.SavingsEntryEntity
import com.yuzufin.app.data.db.SavingsGoalEntity
import com.yuzufin.app.domain.BalanceCalculator
import com.yuzufin.app.domain.SavingsEntry
import com.yuzufin.app.domain.SavingsGoal
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.SavingsResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Una meta a la que conviene recordarle un aporte este mes. */
data class GoalReminder(val goalId: Long, val name: String, val remainingMinor: Long)

/**
 * Metas de ahorro. Apartar dinero no crea movimientos: el saldo total no cambia y solo baja lo
 * "disponible". Los aportes se validan contra lo disponible y los retiros contra lo ahorrado en la meta.
 */
class SavingsRepository(
    private val db: AppDatabase,
    private val finance: FinanceRepository,
) {
    private val dao = db.savingsDao()

    val goals: Flow<List<SavingsGoal>> = dao.observeGoals().map { list -> list.map { it.toDomain() } }

    val entries: Flow<List<SavingsEntry>> = dao.observeEntries().map { list -> list.map { it.toDomain() } }

    suspend fun getGoal(id: Long): SavingsGoal? = dao.getGoal(id)?.toDomain()

    suspend fun savedFor(goalId: Long): Long = dao.savedFor(goalId)

    /** Crea (id = 0) o actualiza una meta. Devuelve su id. */
    suspend fun saveGoal(goal: SavingsGoal, today: LocalDate): Long {
        val now = System.currentTimeMillis()
        val month = SavingsMath.monthKey(today)
        if (goal.id == 0L) {
            return dao.insertGoal(goal.toEntity(lastRemindedMonth = month, createdAt = now))
        }
        val existing = dao.getGoal(goal.id) ?: return 0L
        // Al activar el recordatorio no llega un aviso el mismo mes: el primero es el mes siguiente.
        val last = if (goal.remind && !existing.remind) month else existing.lastRemindedMonth
        dao.updateGoal(goal.toEntity(lastRemindedMonth = last, createdAt = existing.createdAt))
        return goal.id
    }

    /** Al borrar una meta se borran sus aportes: ese dinero vuelve a estar disponible. */
    suspend fun deleteGoal(id: Long) = dao.deleteGoal(id)

    /**
     * Aporta (importe positivo) o retira (negativo) dinero de una meta.
     * Aportar no puede superar lo disponible; retirar no puede superar lo ahorrado en la meta.
     */
    suspend fun addEntry(goalId: Long, amountMinor: Long, date: LocalDate, note: String): SavingsResult {
        if (amountMinor == 0L) return SavingsResult.INVALID
        // El saldo se calcula antes de abrir la transacción (no se observan flujos dentro de una).
        val initial = finance.initialBalance.first() ?: return SavingsResult.INVALID
        val balance = BalanceCalculator.availableBalance(initial, finance.transactions.first())

        return db.withTransaction {
            if (dao.getGoal(goalId) == null) return@withTransaction SavingsResult.INVALID
            if (amountMinor > 0L) {
                if (amountMinor > SavingsMath.spendable(balance, dao.totalSaved())) {
                    return@withTransaction SavingsResult.NOT_ENOUGH_AVAILABLE
                }
            } else if (-amountMinor > dao.savedFor(goalId)) {
                return@withTransaction SavingsResult.NOT_ENOUGH_SAVED
            }
            dao.insertEntry(
                SavingsEntryEntity(
                    goalId = goalId,
                    amountMinor = amountMinor,
                    dateEpochDay = date.toEpochDay(),
                    note = note.trim().take(60),
                    sourceTransactionId = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            SavingsResult.OK
        }
    }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    /**
     * Metas con recordatorio, sin cumplir y sin aporte este mes, a las que todavía no se avisó este mes.
     * Las que ya recibieron un aporte este mes se marcan como evaluadas sin avisar.
     */
    suspend fun dueReminders(today: LocalDate): List<GoalReminder> {
        val key = SavingsMath.monthKey(today)
        val monthStart = today.withDayOfMonth(1).toEpochDay()
        val due = ArrayList<GoalReminder>()
        for (goal in dao.allGoals()) {
            if (!goal.remind || goal.lastRemindedMonth >= key) continue
            val saved = dao.savedFor(goal.id)
            if (SavingsMath.isComplete(goal.targetMinor, saved)) continue
            if (dao.contributionsBetween(goal.id, monthStart, today.toEpochDay()) > 0) {
                dao.updateGoal(goal.copy(lastRemindedMonth = key))
                continue
            }
            due += GoalReminder(goal.id, goal.name, SavingsMath.remaining(goal.targetMinor, saved))
        }
        return due
    }

    suspend fun markReminded(ids: List<Long>, today: LocalDate) {
        val key = SavingsMath.monthKey(today)
        for (id in ids) dao.getGoal(id)?.let { dao.updateGoal(it.copy(lastRemindedMonth = key)) }
    }
}

private fun SavingsGoalEntity.toDomain() = SavingsGoal(
    id = id,
    name = name,
    targetMinor = targetMinor,
    deadline = deadlineEpochDay?.let(LocalDate::ofEpochDay),
    autoPercent = autoPercent,
    remind = remind,
)

private fun SavingsGoal.toEntity(lastRemindedMonth: Int, createdAt: Long) = SavingsGoalEntity(
    id = id,
    name = name,
    targetMinor = targetMinor,
    deadlineEpochDay = deadline?.toEpochDay(),
    autoPercent = autoPercent,
    remind = remind,
    lastRemindedMonth = lastRemindedMonth,
    createdAt = createdAt,
)

private fun SavingsEntryEntity.toDomain() = SavingsEntry(
    id = id,
    goalId = goalId,
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    sourceTransactionId = sourceTransactionId,
)
