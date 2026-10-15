package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.BillEntity
import com.yuzufin.app.data.db.CategoryEntity
import com.yuzufin.app.data.db.LoanEntity
import com.yuzufin.app.data.db.LoanPaymentEntity
import com.yuzufin.app.data.db.TransactionEntity
import com.yuzufin.app.domain.Bill
import com.yuzufin.app.domain.LOAN_ICON_KEY
import com.yuzufin.app.domain.Loan
import com.yuzufin.app.domain.LoanDirection
import com.yuzufin.app.domain.LoanPayment
import com.yuzufin.app.domain.LoanResult
import com.yuzufin.app.domain.ObligationMath
import com.yuzufin.app.domain.SavingsMath
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Saldo pendiente de un préstamo, para el aviso de fin de mes. */
data class LoanBalance(val loanId: Long, val person: String, val outstandingMinor: Long)

/** Préstamos con saldo a recordar este mes: lo que te deben y lo que debés. */
data class LoanReminders(val owedToMe: List<LoanBalance>, val iOwe: List<LoanBalance>) {
    val isEmpty: Boolean get() = owedToMe.isEmpty() && iOwe.isEmpty()
    val ids: List<Long> get() = (owedToMe + iOwe).map { it.loanId }
}

/**
 * Facturas y préstamos. Coherencia:
 *  - Una factura pendiente no toca el saldo. Pagarla crea el gasto real y marca la factura, en una transacción.
 *  - Un préstamo mueve el saldo con un movimiento interno enlazado (no cuenta como ingreso ni gasto del mes).
 *    Las devoluciones también, y no pueden superar lo que falta devolver.
 */
class ObligationsRepository(private val db: AppDatabase) {

    private val billDao = db.billDao()
    private val loanDao = db.loanDao()
    private val transactionDao = db.transactionDao()
    private val categoryDao = db.categoryDao()

    val bills: Flow<List<Bill>> = billDao.observeAll().map { list -> list.map { it.toDomain() } }
    val loans: Flow<List<Loan>> = loanDao.observeLoans().map { list -> list.map { it.toDomain() } }
    val payments: Flow<List<LoanPayment>> = loanDao.observePayments().map { list -> list.map { it.toDomain() } }

    // ---------------------------------------------------------------- Facturas

    suspend fun getBill(id: Long): Bill? = billDao.getById(id)?.toDomain()

    /** Crea (id = 0) o actualiza una factura. Si cambia el vencimiento, el aviso vuelve a quedar pendiente. */
    suspend fun saveBill(bill: Bill): Long {
        val now = System.currentTimeMillis()
        if (bill.id == 0L) return billDao.insert(bill.toEntity(notified = false, paidTransactionId = null, createdAt = now))
        val existing = billDao.getById(bill.id) ?: return 0L
        val dueChanged = existing.dueEpochDay != bill.dueDate.toEpochDay()
        billDao.update(
            bill.toEntity(
                notified = if (dueChanged) false else existing.notified,
                paidTransactionId = existing.paidTransactionId,
                createdAt = existing.createdAt,
            ),
        )
        return bill.id
    }

    /** Borrar la factura no borra el gasto que ya se registró al pagarla. */
    suspend fun deleteBill(id: Long) = billDao.deleteById(id)

    /**
     * Paga una factura: crea el gasto por [amountMinor] (editable) en [date] y la marca como pagada.
     * Si es mensual, deja lista la siguiente (mismo día del mes). Devuelve false si ya estaba pagada.
     */
    suspend fun payBill(id: Long, amountMinor: Long, date: LocalDate): Boolean {
        if (amountMinor <= 0L) return false
        return db.withTransaction {
            val bill = billDao.getById(id) ?: return@withTransaction false
            if (bill.paid) return@withTransaction false
            val now = System.currentTimeMillis()
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amountMinor = amountMinor,
                    categoryId = bill.categoryId,
                    description = bill.name,
                    dateEpochDay = date.toEpochDay(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            billDao.update(
                bill.copy(paid = true, paidTransactionId = transactionId, paidEpochDay = date.toEpochDay(), notified = true),
            )
            if (bill.repeatDay > 0) {
                val next = ObligationMath.nextDueDate(LocalDate.ofEpochDay(bill.dueEpochDay), bill.repeatDay)
                billDao.insert(
                    bill.copy(
                        id = 0,
                        dueEpochDay = next.toEpochDay(),
                        paid = false,
                        paidTransactionId = null,
                        paidEpochDay = null,
                        notified = false,
                        createdAt = now,
                    ),
                )
            }
            true
        }
    }

    /** Facturas pendientes con aviso por hacer (vencen mañana, hoy o ya vencieron). */
    suspend fun dueBillReminders(today: LocalDate): List<Bill> =
        billDao.allUnpaid()
            .filter { ObligationMath.billNeedsReminder(it.toDomain(), it.notified, today) }
            .map { it.toDomain() }
            .sortedBy { it.dueDate }

    suspend fun markBillsNotified(ids: List<Long>) {
        if (ids.isNotEmpty()) billDao.markNotified(ids)
    }

    // --------------------------------------------------------------- Préstamos

    suspend fun getLoan(id: Long): Loan? = loanDao.getLoan(id)?.toDomain()

    /** Registra el préstamo y mueve el saldo con un movimiento interno. Devuelve el id del préstamo. */
    suspend fun createLoan(loan: Loan, today: LocalDate): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val person = loan.person.trim()
        val type = if (loan.direction == LoanDirection.LENT) TransactionType.EXPENSE else TransactionType.INCOME
        val description = if (loan.direction == LoanDirection.LENT) "Préstamo a $person" else "Préstamo de $person"
        val transactionId = insertInternal(type, loan.principalMinor, description, loan.startDate, now)
        loanDao.insertLoan(
            LoanEntity(
                person = person,
                direction = loan.direction.name,
                principalMinor = loan.principalMinor,
                startEpochDay = loan.startDate.toEpochDay(),
                note = loan.note.trim().take(60),
                remind = loan.remind,
                lastRemindedMonth = SavingsMath.monthKey(today),
                principalTransactionId = transactionId,
                createdAt = now,
            ),
        )
    }

    /** Solo se editan nombre, nota y aviso: el monto y la fecha mueven el saldo y no se cambian. */
    suspend fun updateLoan(id: Long, person: String, note: String, remind: Boolean) {
        db.withTransaction {
            val loan = loanDao.getLoan(id) ?: return@withTransaction
            val name = person.trim()
            loanDao.updateLoan(
                loan.copy(
                    person = name,
                    note = note.trim().take(60),
                    remind = remind,
                    lastRemindedMonth = if (remind && !loan.remind) SavingsMath.monthKey(LocalDate.now()) else loan.lastRemindedMonth,
                ),
            )
            val lent = loan.direction == LoanDirection.LENT.name
            loan.principalTransactionId?.let { renameTransaction(it, if (lent) "Préstamo a $name" else "Préstamo de $name") }
            for (payment in loanDao.paymentsFor(id)) {
                payment.transactionId?.let { renameTransaction(it, if (lent) "Devolución de $name" else "Pago a $name") }
            }
        }
    }

    /** Borra el préstamo con sus devoluciones y sus movimientos: el saldo vuelve a como estaba. */
    suspend fun deleteLoan(id: Long) {
        db.withTransaction {
            val loan = loanDao.getLoan(id) ?: return@withTransaction
            val transactionIds = listOfNotNull(loan.principalTransactionId) +
                loanDao.paymentsFor(id).mapNotNull { it.transactionId }
            loanDao.deleteLoan(id) // las devoluciones se borran en cascada
            transactionIds.forEach { transactionDao.deleteById(it) }
        }
    }

    /** Registra una devolución (parcial o total). No puede superar lo que falta. */
    suspend fun repay(loanId: Long, amountMinor: Long, date: LocalDate, note: String): LoanResult {
        if (amountMinor <= 0L) return LoanResult.INVALID
        return db.withTransaction {
            val loan = loanDao.getLoan(loanId) ?: return@withTransaction LoanResult.NOT_FOUND
            val outstanding = (loan.principalMinor - loanDao.paidFor(loanId)).coerceAtLeast(0L)
            if (!ObligationMath.canRepay(amountMinor, outstanding)) return@withTransaction LoanResult.TOO_MUCH
            val lent = loan.direction == LoanDirection.LENT.name
            val now = System.currentTimeMillis()
            val transactionId = insertInternal(
                type = if (lent) TransactionType.INCOME else TransactionType.EXPENSE,
                amountMinor = amountMinor,
                description = if (lent) "Devolución de ${loan.person}" else "Pago a ${loan.person}",
                date = date,
                now = now,
            )
            loanDao.insertPayment(
                LoanPaymentEntity(
                    loanId = loanId,
                    amountMinor = amountMinor,
                    dateEpochDay = date.toEpochDay(),
                    note = note.trim().take(60),
                    transactionId = transactionId,
                    createdAt = now,
                ),
            )
            LoanResult.OK
        }
    }

    suspend fun deletePayment(id: Long) {
        db.withTransaction {
            val payment = loanDao.getPayment(id) ?: return@withTransaction
            loanDao.deletePayment(id)
            payment.transactionId?.let { transactionDao.deleteById(it) }
        }
    }

    /**
     * En los últimos días del mes: préstamos con aviso y saldo pendiente, a los que todavía no se
     * avisó este mes. Separa lo que te deben de lo que debés.
     */
    suspend fun loanReminders(today: LocalDate): LoanReminders {
        if (!ObligationMath.isMonthEndWindow(today)) return LoanReminders(emptyList(), emptyList())
        val key = SavingsMath.monthKey(today)
        val owed = ArrayList<LoanBalance>()
        val iOwe = ArrayList<LoanBalance>()
        for (loan in loanDao.allLoans()) {
            if (!loan.remind || loan.lastRemindedMonth >= key) continue
            val outstanding = (loan.principalMinor - loanDao.paidFor(loan.id)).coerceAtLeast(0L)
            if (outstanding <= 0L) continue
            val item = LoanBalance(loan.id, loan.person, outstanding)
            if (loan.direction == LoanDirection.LENT.name) owed += item else iOwe += item
        }
        return LoanReminders(owed, iOwe)
    }

    suspend fun markLoansReminded(ids: List<Long>, today: LocalDate) {
        val key = SavingsMath.monthKey(today)
        for (id in ids) loanDao.getLoan(id)?.let { loanDao.updateLoan(it.copy(lastRemindedMonth = key)) }
    }

    // ---------------------------------------------------------------- Internos

    private suspend fun insertInternal(
        type: TransactionType,
        amountMinor: Long,
        description: String,
        date: LocalDate,
        now: Long,
    ): Long {
        val category = categoryDao.findByIcon(LOAN_ICON_KEY, type.name)
            ?: CategoryEntity(name = "Préstamos", type = type, iconKey = LOAN_ICON_KEY)
                .let { it.copy(id = categoryDao.insert(it)) }
        return transactionDao.insert(
            TransactionEntity(
                type = type,
                amountMinor = amountMinor,
                categoryId = category.id,
                description = description,
                dateEpochDay = date.toEpochDay(),
                createdAt = now,
                updatedAt = now,
                internalTransfer = true,
            ),
        )
    }

    private suspend fun renameTransaction(id: Long, description: String) {
        transactionDao.getById(id)?.let { transactionDao.update(it.copy(description = description)) }
    }
}

private fun BillEntity.toDomain() = Bill(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dueDate = LocalDate.ofEpochDay(dueEpochDay),
    repeatDay = repeatDay,
    remind = remind,
    paid = paid,
    paidDate = paidEpochDay?.let(LocalDate::ofEpochDay),
)

private fun Bill.toEntity(notified: Boolean, paidTransactionId: Long?, createdAt: Long) = BillEntity(
    id = id,
    name = name,
    amountMinor = amountMinor,
    categoryId = categoryId,
    dueEpochDay = dueDate.toEpochDay(),
    repeatDay = repeatDay,
    remind = remind,
    paid = paid,
    paidTransactionId = paidTransactionId,
    paidEpochDay = paidDate?.toEpochDay(),
    notified = notified,
    createdAt = createdAt,
)

private fun LoanEntity.toDomain() = Loan(
    id = id,
    person = person,
    direction = runCatching { LoanDirection.valueOf(direction) }.getOrDefault(LoanDirection.LENT),
    principalMinor = principalMinor,
    startDate = LocalDate.ofEpochDay(startEpochDay),
    note = note,
    remind = remind,
)

private fun LoanPaymentEntity.toDomain() = LoanPayment(
    id = id,
    loanId = loanId,
    amountMinor = amountMinor,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
)
