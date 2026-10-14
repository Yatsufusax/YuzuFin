package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.CategoryEntity
import com.yuzufin.app.data.db.InitialBalanceEntity
import com.yuzufin.app.data.db.TransactionEntity
import com.yuzufin.app.domain.Category
import com.yuzufin.app.domain.FinanceTransaction
import com.yuzufin.app.domain.InitialBalance
import com.yuzufin.app.domain.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Acceso a movimientos, categorías y saldo inicial. La UI nunca toca la base de datos directo. */
class FinanceRepository(private val db: AppDatabase) {

    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val initialBalanceDao = db.initialBalanceDao()

    val categories: Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    val transactions: Flow<List<FinanceTransaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    val initialBalance: Flow<InitialBalance?> =
        initialBalanceDao.observe().map { it?.toDomain() }

    /** Guarda el punto de partida y crea las categorías iniciales, todo en una transacción. */
    suspend fun completeSetup(amountMinor: Long, startDate: LocalDate) {
        db.withTransaction {
            if (categoryDao.count() == 0) categoryDao.insertAll(DEFAULT_CATEGORIES)
            initialBalanceDao.upsert(
                InitialBalanceEntity(amountMinor = amountMinor, startEpochDay = startDate.toEpochDay()),
            )
        }
    }

    suspend fun getTransaction(id: Long): FinanceTransaction? =
        transactionDao.getById(id)?.toDomain()

    /** Crea (id = 0) o actualiza un movimiento. Devuelve su id. */
    suspend fun saveTransaction(transaction: FinanceTransaction): Long {
        val now = System.currentTimeMillis()
        return if (transaction.id == 0L) {
            transactionDao.insert(transaction.toEntity(createdAt = now, updatedAt = now))
        } else {
            val existing = transactionDao.getById(transaction.id)
            transactionDao.update(
                transaction.toEntity(createdAt = existing?.createdAt ?: now, updatedAt = now),
            )
            transaction.id
        }
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteById(id)
    }

    private companion object {
        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Alimentación", type = TransactionType.EXPENSE, iconKey = "food"),
            CategoryEntity(name = "Transporte", type = TransactionType.EXPENSE, iconKey = "transport"),
            CategoryEntity(name = "Hogar", type = TransactionType.EXPENSE, iconKey = "home"),
            CategoryEntity(name = "Servicios", type = TransactionType.EXPENSE, iconKey = "utilities"),
            CategoryEntity(name = "Salud", type = TransactionType.EXPENSE, iconKey = "health"),
            CategoryEntity(name = "Educación", type = TransactionType.EXPENSE, iconKey = "education"),
            CategoryEntity(name = "Ocio", type = TransactionType.EXPENSE, iconKey = "leisure"),
            CategoryEntity(name = "Compras", type = TransactionType.EXPENSE, iconKey = "shopping"),
            CategoryEntity(name = "Otros gastos", type = TransactionType.EXPENSE, iconKey = "other"),
            CategoryEntity(name = "Salario", type = TransactionType.INCOME, iconKey = "salary"),
            CategoryEntity(name = "Ingreso extra", type = TransactionType.INCOME, iconKey = "extra"),
            CategoryEntity(name = "Otros ingresos", type = TransactionType.INCOME, iconKey = "other"),
        )
    }
}

private fun CategoryEntity.toDomain() = Category(id = id, name = name, type = type, iconKey = iconKey)

private fun TransactionEntity.toDomain() = FinanceTransaction(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    description = description,
    date = LocalDate.ofEpochDay(dateEpochDay),
)

private fun FinanceTransaction.toEntity(createdAt: Long, updatedAt: Long) = TransactionEntity(
    id = id,
    type = type,
    amountMinor = amountMinor,
    categoryId = categoryId,
    description = description,
    dateEpochDay = date.toEpochDay(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun InitialBalanceEntity.toDomain() = InitialBalance(
    amountMinor = amountMinor,
    startDate = LocalDate.ofEpochDay(startEpochDay),
)
