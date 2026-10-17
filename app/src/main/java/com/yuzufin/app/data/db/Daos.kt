package com.yuzufin.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<CategoryEntity>)

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE iconKey = :iconKey AND type = :type LIMIT 1")
    suspend fun findByIcon(iconKey: String, type: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Update
    suspend fun update(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    /** Cuántos movimientos, facturas y fuentes de ingreso usan la categoría. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM bills WHERE categoryId = :id) + " +
            "(SELECT COUNT(*) FROM income_sources WHERE categoryId = :id)",
    )
    suspend fun usageCount(id: Long): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, createdAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface InitialBalanceDao {
    @Query("SELECT * FROM initial_balance WHERE id = 1")
    fun observe(): Flow<InitialBalanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InitialBalanceEntity)
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income_sources ORDER BY active DESC, name COLLATE NOCASE")
    fun observeSources(): Flow<List<IncomeSourceEntity>>

    @Query("SELECT * FROM income_sources WHERE id = :id")
    suspend fun getSource(id: Long): IncomeSourceEntity?

    @Query("SELECT * FROM income_sources")
    suspend fun allSources(): List<IncomeSourceEntity>

    @Insert
    suspend fun insertSource(entity: IncomeSourceEntity): Long

    @Update
    suspend fun updateSource(entity: IncomeSourceEntity)

    @Query("DELETE FROM income_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT * FROM income_occurrences ORDER BY dueEpochDay DESC, id DESC")
    fun observeOccurrences(): Flow<List<IncomeOccurrenceEntity>>

    @Query("SELECT * FROM income_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: Long): IncomeOccurrenceEntity?

    @Query("SELECT * FROM income_occurrences WHERE transactionId = :transactionId")
    suspend fun getOccurrenceByTransaction(transactionId: Long): IncomeOccurrenceEntity?

    @Query("SELECT dueEpochDay FROM income_occurrences WHERE sourceId = :sourceId")
    suspend fun dueDays(sourceId: Long): List<Long>

    /** Devuelve -1 si ya existía un cobro de esa fuente en esa fecha (no se duplica). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(entity: IncomeOccurrenceEntity): Long

    @Update
    suspend fun updateOccurrence(entity: IncomeOccurrenceEntity)

    @Query(
        "SELECT * FROM income_occurrences " +
            "WHERE notified = 0 AND status IN ('PENDING', 'CONFIRMED') AND dueEpochDay <= :todayEpochDay",
    )
    suspend fun unnotifiedDue(todayEpochDay: Long): List<IncomeOccurrenceEntity>

    @Query("UPDATE income_occurrences SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<Long>)
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_goals ORDER BY createdAt, id")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY id")
    suspend fun allGoals(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoal(id: Long): SavingsGoalEntity?

    @Insert
    suspend fun insertGoal(entity: SavingsGoalEntity): Long

    @Update
    suspend fun updateGoal(entity: SavingsGoalEntity)

    /** Los aportes de la meta se borran con ella (FK en cascada): ese dinero vuelve a estar disponible. */
    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    @Query("SELECT * FROM savings_entries ORDER BY dateEpochDay DESC, createdAt DESC, id DESC")
    fun observeEntries(): Flow<List<SavingsEntryEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM savings_entries")
    suspend fun totalSaved(): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM savings_entries WHERE goalId = :goalId")
    suspend fun savedFor(goalId: Long): Long

    @Query(
        "SELECT COUNT(*) FROM savings_entries " +
            "WHERE goalId = :goalId AND amountMinor > 0 AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay",
    )
    suspend fun contributionsBetween(goalId: Long, fromEpochDay: Long, toEpochDay: Long): Int

    @Insert
    suspend fun insertEntry(entity: SavingsEntryEntity): Long

    @Query("DELETE FROM savings_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    /** Si se borra un ingreso, también se deshace lo que se apartó solo a partir de él. */
    @Query("DELETE FROM savings_entries WHERE sourceTransactionId = :transactionId")
    suspend fun deleteBySourceTransaction(transactionId: Long)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY paid, dueEpochDay, id")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE paid = 0")
    suspend fun allUnpaid(): List<BillEntity>

    @Insert
    suspend fun insert(entity: BillEntity): Long

    @Update
    suspend fun update(entity: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Si se borra el gasto de un pago, la factura sigue pagada pero sin enlace. */
    @Query("UPDATE bills SET paidTransactionId = NULL WHERE paidTransactionId = :transactionId")
    suspend fun unlinkTransaction(transactionId: Long)

    @Query("UPDATE bills SET notified = 1 WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<Long>)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY startEpochDay DESC, id DESC")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loan_payments ORDER BY dateEpochDay DESC, id DESC")
    fun observePayments(): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getLoan(id: Long): LoanEntity?

    @Query("SELECT * FROM loans")
    suspend fun allLoans(): List<LoanEntity>

    @Insert
    suspend fun insertLoan(entity: LoanEntity): Long

    @Update
    suspend fun updateLoan(entity: LoanEntity)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId")
    suspend fun paymentsFor(loanId: Long): List<LoanPaymentEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM loan_payments WHERE loanId = :loanId")
    suspend fun paidFor(loanId: Long): Long

    @Query("SELECT * FROM loan_payments WHERE id = :id")
    suspend fun getPayment(id: Long): LoanPaymentEntity?

    @Insert
    suspend fun insertPayment(entity: LoanPaymentEntity): Long

    @Query("DELETE FROM loan_payments WHERE id = :id")
    suspend fun deletePayment(id: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE `read` = 0")
    fun observeUnreadCount(): Flow<Int>

    /** Devuelve -1 si ya existía un aviso con la misma clave. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AppNotificationEntity): Long

    @Query("UPDATE app_notifications SET `read` = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun clear()

    /** Conserva solo los [keep] avisos más recientes. */
    @Query(
        "DELETE FROM app_notifications WHERE id NOT IN " +
            "(SELECT id FROM app_notifications ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)
}
