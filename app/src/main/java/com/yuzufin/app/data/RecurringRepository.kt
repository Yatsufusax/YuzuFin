package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.RecurringExpenseEntity
import com.yuzufin.app.data.db.RecurringOccurrenceEntity
import com.yuzufin.app.data.db.TransactionEntity
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.domain.RecurringExpense
import com.yuzufin.app.domain.RecurringOccurrence
import com.yuzufin.app.domain.RecurringScheduler
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Gastos fijos mensuales. Igual que los ingresos programados: el saldo solo cambia al confirmar
 * (se crea el movimiento de gasto) y cada pago (gasto + fecha) existe una sola vez.
 */
class RecurringRepository(private val db: AppDatabase) {

    private val dao = db.recurringDao()
    private val transactionDao = db.transactionDao()
    private val initialBalanceDao = db.initialBalanceDao()

    val expenses: Flow<List<RecurringExpense>> = dao.observeExpenses().map { list -> list.map { it.toDomain() } }

    val occurrences: Flow<List<RecurringOccurrence>> = dao.observeOccurrences().map { list -> list.map { it.toDomain() } }

    suspend fun getExpense(id: Long): RecurringExpense? = dao.getExpense(id)?.toDomain()

    /** Crea (id = 0) o actualiza un gasto fijo. */
    suspend fun save(expense: RecurringExpense) {
        val now = System.currentTimeMillis()
        if (expense.id == 0L) {
            dao.insertExpense(expense.toEntity(createdAt = now))
        } else {
            val existing = dao.getExpense(expense.id)
            dao.updateExpense(expense.toEntity(createdAt = existing?.createdAt ?: now))
        }
    }

    /** Al reactivar un gasto pausado no se generan los pagos de la pausa: se parte de hoy. */
    suspend fun setActive(id: Long, active: Boolean, today: LocalDate) {
        val entity = dao.getExpense(id) ?: return
        dao.updateExpense(
            entity.copy(
                active = active,
                startEpochDay = if (active && !entity.active) today.toEpochDay() else entity.startEpochDay,
            ),
        )
    }

    /** Los movimientos ya registrados se conservan; solo desaparece el gasto fijo y sus pagos. */
    suspend fun delete(id: Long) = dao.deleteExpense(id)

    /** Crea los pagos vencidos hasta [today] que todavía no existen. No duplica nada. */
    suspend fun reconcile(today: LocalDate) {
        val initial = initialBalanceDao.observe().first() ?: return
        val floor = LocalDate.ofEpochDay(initial.startEpochDay)
        db.withTransaction {
            for (entity in dao.allExpenses()) {
                val expense = entity.toDomain()
                val existing = dao.dueDays(expense.id).map(LocalDate::ofEpochDay).toSet()
                for (due in RecurringScheduler.missing(expense, existing, today, floor)) {
                    dao.insertOccurrence(
                        RecurringOccurrenceEntity(
                            expenseId = expense.id,
                            dueEpochDay = due.toEpochDay(),
                            amountMinor = expense.amountMinor,
                            status = OccurrenceStatus.PENDING.name,
                            transactionId = null,
                            notified = false,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    /** "Ya pagué": crea el gasto por [amountMinor] (editable) y confirma el pago. */
    suspend fun confirm(occurrenceId: Long, amountMinor: Long): Boolean {
        if (amountMinor <= 0L) return false
        return db.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction false
            if (occurrence.status != OccurrenceStatus.PENDING.name) return@withTransaction false
            val expense = dao.getExpense(occurrence.expenseId) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amountMinor = amountMinor,
                    categoryId = expense.categoryId,
                    description = expense.name,
                    dateEpochDay = occurrence.dueEpochDay,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.updateOccurrence(
                occurrence.copy(
                    status = OccurrenceStatus.CONFIRMED.name,
                    amountMinor = amountMinor,
                    transactionId = transactionId,
                    notified = true,
                ),
            )
            true
        }
    }

    suspend fun skip(occurrenceId: Long) {
        db.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.status == OccurrenceStatus.PENDING.name) {
                dao.updateOccurrence(occurrence.copy(status = OccurrenceStatus.SKIPPED.name))
            }
        }
    }

    /** Pagos vencidos pendientes de los que todavía no se avisó (solo gastos con recordatorio). */
    suspend fun unnotifiedDue(today: LocalDate): List<DueNotice> {
        val byId = dao.allExpenses().associateBy { it.id }
        return dao.unnotifiedDue(today.toEpochDay()).mapNotNull { occurrence ->
            val expense = byId[occurrence.expenseId] ?: return@mapNotNull null
            DueNotice(occurrence.id, expense.name, occurrence.amountMinor, pending = true, remind = expense.remind)
        }
    }

    /** Pagos previstos para mañana (gastos activos con recordatorio). */
    suspend fun dueTomorrow(today: LocalDate): List<DueNotice> {
        val tomorrow = today.plusDays(1)
        return dao.allExpenses().map { it.toDomain() }
            .filter { it.active && it.remind && RecurringScheduler.dueDates(it.dayOfMonth, tomorrow, tomorrow).isNotEmpty() }
            .map { DueNotice(0, it.name, it.amountMinor, pending = true, remind = true) }
    }

    suspend fun markNotified(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.markNotified(ids)
    }
}

private fun RecurringExpenseEntity.toDomain() = RecurringExpense(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dayOfMonth = dayOfMonth,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    active = active,
    remind = remind,
)

private fun RecurringExpense.toEntity(createdAt: Long) = RecurringExpenseEntity(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dayOfMonth = dayOfMonth,
    startEpochDay = startDate.toEpochDay(),
    active = active,
    remind = remind,
    createdAt = createdAt,
)

private fun RecurringOccurrenceEntity.toDomain() = RecurringOccurrence(
    id = id,
    expenseId = expenseId,
    dueDate = LocalDate.ofEpochDay(dueEpochDay),
    amountMinor = amountMinor,
    status = runCatching { OccurrenceStatus.valueOf(status) }.getOrDefault(OccurrenceStatus.PENDING),
    transactionId = transactionId,
)
