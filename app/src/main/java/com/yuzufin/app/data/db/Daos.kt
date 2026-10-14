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
