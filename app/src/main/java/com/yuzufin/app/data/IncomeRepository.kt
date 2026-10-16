package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.IncomeOccurrenceEntity
import com.yuzufin.app.data.db.IncomeSourceEntity
import com.yuzufin.app.data.db.SavingsEntryEntity
import com.yuzufin.app.data.db.TransactionEntity
import com.yuzufin.app.domain.IncomeOccurrence
import com.yuzufin.app.domain.IncomeScheduler
import com.yuzufin.app.domain.IncomeSchedule
import com.yuzufin.app.domain.IncomeSource
import com.yuzufin.app.domain.OccurrenceStatus
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Un cobro que se registró solo (fuente sin confirmación manual). Sirve para avisar a la persona. */
data class AutoPosted(val sourceName: String, val amountMinor: Long, val dueDate: LocalDate)

/**
 * Ingresos programados. Coherencia: el saldo solo cambia cuando existe un movimiento de ingreso,
 * y cada cobro (fuente + fecha) existe una sola vez gracias al índice único de la base de datos.
 * Crear el movimiento y marcar el cobro como confirmado ocurre en una sola transacción.
 */
class IncomeRepository(private val db: AppDatabase) {

    private val dao = db.incomeDao()
    private val transactionDao = db.transactionDao()
    private val initialBalanceDao = db.initialBalanceDao()
    private val savingsDao = db.savingsDao()

    val sources: Flow<List<IncomeSource>> =
        dao.observeSources().map { list -> list.map { it.toDomain() } }

    val occurrences: Flow<List<IncomeOccurrence>> =
        dao.observeOccurrences().map { list -> list.map { it.toDomain() } }

    suspend fun getSource(id: Long): IncomeSource? = dao.getSource(id)?.toDomain()

    /** Crea (id = 0) o actualiza una fuente. Devuelve su id. */
    suspend fun saveSource(source: IncomeSource): Long {
        val now = System.currentTimeMillis()
        return if (source.id == 0L) {
            dao.insertSource(source.toEntity(createdAt = now))
        } else {
            val existing = dao.getSource(source.id)
            dao.updateSource(source.toEntity(createdAt = existing?.createdAt ?: now))
            source.id
        }
    }

    /** Al reactivar una fuente pausada no se generan los cobros de la pausa: se parte de hoy. */
    suspend fun setActive(id: Long, active: Boolean, today: LocalDate) {
        val entity = dao.getSource(id) ?: return
        dao.updateSource(
            entity.copy(
                active = active,
                startEpochDay = if (active && !entity.active) today.toEpochDay() else entity.startEpochDay,
            ),
        )
    }

    /** Los movimientos ya registrados se conservan; solo desaparece la fuente y sus cobros. */
    suspend fun deleteSource(id: Long) = dao.deleteSource(id)

    /**
     * Crea los cobros vencidos hasta [today] que todavía no existen. Los pendientes esperan la
     * confirmación; los de fuentes automáticas se registran de inmediato. Se puede llamar las
     * veces que haga falta: no duplica nada.
     */
    suspend fun reconcile(today: LocalDate): List<AutoPosted> {
        val initial = initialBalanceDao.observe().first() ?: return emptyList()
        val floor = LocalDate.ofEpochDay(initial.startEpochDay)
        val posted = ArrayList<AutoPosted>()

        db.withTransaction {
            for (entity in dao.allSources()) {
                val source = entity.toDomain()
                val existing = dao.dueDays(source.id).map(LocalDate::ofEpochDay).toSet()
                for (due in IncomeScheduler.missingOccurrences(source, existing, today, floor)) {
                    val id = dao.insertOccurrence(
                        IncomeOccurrenceEntity(
                            sourceId = source.id,
                            dueEpochDay = due.toEpochDay(),
                            amountMinor = source.amountMinor,
                            status = OccurrenceStatus.PENDING.name,
                            transactionId = null,
                            notified = false,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                    if (id == -1L) continue // ya existía
                    if (!source.requiresConfirmation) {
                        post(id, source.amountMinor, alreadyNotified = false)
                        posted += AutoPosted(source.name, source.amountMinor, due)
                    }
                }
            }
        }
        return posted
    }

    /** "Ya cobré": crea el ingreso por [amountMinor] (editable) y confirma el cobro. */
    suspend fun confirm(occurrenceId: Long, amountMinor: Long): Boolean {
        if (amountMinor <= 0L) return false
        // La persona lo está confirmando ahora mismo: no hace falta avisarle después.
        return db.withTransaction { post(occurrenceId, amountMinor, alreadyNotified = true) }
    }

    suspend fun skip(occurrenceId: Long) {
        db.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.status == OccurrenceStatus.PENDING.name) {
                dao.updateOccurrence(occurrence.copy(status = OccurrenceStatus.SKIPPED.name))
            }
        }
    }

    /** Devuelve un cobro omitido a pendiente para poder confirmarlo. */
    suspend fun reopen(occurrenceId: Long) {
        db.withTransaction {
            val occurrence = dao.getOccurrence(occurrenceId) ?: return@withTransaction
            if (occurrence.status == OccurrenceStatus.SKIPPED.name) {
                dao.updateOccurrence(occurrence.copy(status = OccurrenceStatus.PENDING.name))
            }
        }
    }

    /** Cobros vencidos de los que todavía no se avisó, con el nombre y si la fuente pide recordatorio. */
    suspend fun unnotifiedDue(today: LocalDate): List<DueNotice> {
        val sourcesById = dao.allSources().associateBy { it.id }
        return dao.unnotifiedDue(today.toEpochDay()).mapNotNull { occurrence ->
            val source = sourcesById[occurrence.sourceId] ?: return@mapNotNull null
            DueNotice(
                occurrenceId = occurrence.id,
                sourceName = source.name,
                amountMinor = occurrence.amountMinor,
                pending = occurrence.status == OccurrenceStatus.PENDING.name,
                remind = source.remind,
            )
        }
    }

    /** Cobros programados para mañana (fuentes activas con recordatorio): aviso "un día antes". */
    suspend fun dueTomorrow(today: LocalDate): List<DueNotice> {
        val tomorrow = today.plusDays(1)
        return dao.allSources().map { it.toDomain() }
            .filter { it.active && it.remind && IncomeScheduler.dueDates(it, tomorrow, tomorrow).isNotEmpty() }
            .map { DueNotice(occurrenceId = 0, sourceName = it.name, amountMinor = it.amountMinor, pending = true, remind = true) }
    }

    suspend fun markNotified(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.markNotified(ids)
    }

    /** Debe llamarse dentro de una transacción. Devuelve false si el cobro ya no está pendiente. */
    private suspend fun post(occurrenceId: Long, amountMinor: Long, alreadyNotified: Boolean): Boolean {
        val occurrence = dao.getOccurrence(occurrenceId) ?: return false
        if (occurrence.status != OccurrenceStatus.PENDING.name) return false
        val source = dao.getSource(occurrence.sourceId) ?: return false
        val now = System.currentTimeMillis()
        val transactionId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.INCOME,
                amountMinor = amountMinor,
                categoryId = source.categoryId,
                description = source.name,
                dateEpochDay = occurrence.dueEpochDay,
                createdAt = now,
                updatedAt = now,
            ),
        )
        setAsideForGoals(transactionId, amountMinor, occurrence.dueEpochDay, now)
        dao.updateOccurrence(
            occurrence.copy(
                status = OccurrenceStatus.CONFIRMED.name,
                amountMinor = amountMinor,
                transactionId = transactionId,
                notified = occurrence.notified || alreadyNotified,
            ),
        )
        return true
    }

    /**
     * Aporte automático a metas: cada meta con porcentaje recibe esa parte del cobro (sin pasar de lo
     * que le falta ni de lo que queda del cobro). Va en la misma transacción que el ingreso, enlazado
     * a él: si el ingreso se borra, el aporte se deshace.
     */
    private suspend fun setAsideForGoals(transactionId: Long, incomeMinor: Long, dueEpochDay: Long, now: Long) {
        var left = incomeMinor
        for (goal in savingsDao.allGoals()) {
            if (goal.autoPercent <= 0) continue
            val remaining = SavingsMath.remaining(goal.targetMinor, savingsDao.savedFor(goal.id))
            val part = SavingsMath.autoContribution(incomeMinor, goal.autoPercent, remaining, left)
            if (part <= 0L) continue
            savingsDao.insertEntry(
                SavingsEntryEntity(
                    goalId = goal.id,
                    amountMinor = part,
                    dateEpochDay = dueEpochDay,
                    note = "",
                    sourceTransactionId = transactionId,
                    createdAt = now,
                ),
            )
            left -= part
        }
    }
}

data class DueNotice(
    val occurrenceId: Long,
    val sourceName: String,
    val amountMinor: Long,
    /** true: falta confirmar. false: se registró solo. */
    val pending: Boolean,
    val remind: Boolean,
)

private fun IncomeSourceEntity.toDomain() = IncomeSource(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    schedule = runCatching { IncomeSchedule.valueOf(schedule) }.getOrDefault(IncomeSchedule.MONTHLY),
    daysOfMonth = daysOfMonth.split(',').mapNotNull { it.trim().toIntOrNull() },
    customDates = customDates.split(',').mapNotNull { it.trim().toLongOrNull()?.let(LocalDate::ofEpochDay) },
    startDate = LocalDate.ofEpochDay(startEpochDay),
    requiresConfirmation = requiresConfirmation,
    active = active,
    remind = remind,
)

private fun IncomeSource.toEntity(createdAt: Long) = IncomeSourceEntity(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    schedule = schedule.name,
    daysOfMonth = daysOfMonth.joinToString(","),
    customDates = customDates.joinToString(",") { it.toEpochDay().toString() },
    startEpochDay = startDate.toEpochDay(),
    requiresConfirmation = requiresConfirmation,
    active = active,
    remind = remind,
    createdAt = createdAt,
)

private fun IncomeOccurrenceEntity.toDomain() = IncomeOccurrence(
    id = id,
    sourceId = sourceId,
    dueDate = LocalDate.ofEpochDay(dueEpochDay),
    amountMinor = amountMinor,
    status = runCatching { OccurrenceStatus.valueOf(status) }.getOrDefault(OccurrenceStatus.PENDING),
    transactionId = transactionId,
)
