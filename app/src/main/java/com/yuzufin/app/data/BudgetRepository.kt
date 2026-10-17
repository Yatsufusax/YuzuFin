package com.yuzufin.app.data

import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.BudgetEntity
import com.yuzufin.app.domain.Budget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Topes mensuales por categoría de gasto. Un límite de 0 o menos quita el presupuesto. */
class BudgetRepository(db: AppDatabase) {

    private val dao = db.budgetDao()

    val budgets: Flow<List<Budget>> = dao.observeAll().map { list -> list.map { Budget(it.id, it.categoryId, it.limitMinor) } }

    suspend fun all(): List<Budget> = dao.all().map { Budget(it.id, it.categoryId, it.limitMinor) }

    suspend fun setLimit(categoryId: Long, limitMinor: Long) {
        if (limitMinor <= 0L) {
            dao.deleteByCategory(categoryId)
        } else {
            // El índice único por categoría hace que REPLACE sustituya el tope anterior.
            dao.upsert(BudgetEntity(categoryId = categoryId, limitMinor = limitMinor))
        }
    }
}
