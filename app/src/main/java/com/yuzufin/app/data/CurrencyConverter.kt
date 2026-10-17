package com.yuzufin.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.domain.CurrencyConversion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Cambia la moneda de toda la app. Convierte cada importe guardado en una sola transacción
 * (o se convierte todo o no se toca nada) y recién después guarda la moneda nueva en los ajustes.
 */
class CurrencyConverter(private val database: AppDatabase, private val settings: SettingsRepository) {

    /** [newPerOld]: unidades de la moneda nueva por cada unidad de la vieja (1 = mantener los números). */
    suspend fun change(to: CurrencyInfo, newPerOld: BigDecimal) {
        val from = CurrencyInfo.fromCode(settings.settings.first().currencyCode)
        if (from.code == to.code) return
        val factor = CurrencyConversion.factor(from, to, newPerOld)
        withContext(Dispatchers.IO) {
            database.runInTransaction(
                Runnable {
                    val db = database.openHelper.writableDatabase
                    for ((table, column) in MONEY_COLUMNS) convertColumn(db, table, column, factor)
                },
            )
        }
        settings.setCurrencyCode(to.code)
    }

    private fun convertColumn(db: SupportSQLiteDatabase, table: String, column: String, factor: BigDecimal) {
        val rows = ArrayList<Pair<Long, Long>>()
        db.query("SELECT `id`, `$column` FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getLong(0) to cursor.getLong(1)
        }
        val statement = db.compileStatement("UPDATE `$table` SET `$column` = ? WHERE `id` = ?")
        try {
            for ((id, value) in rows) {
                statement.bindLong(1, CurrencyConversion.convert(value, factor))
                statement.bindLong(2, id)
                statement.executeUpdateDelete()
            }
        } finally {
            statement.close()
        }
    }

    private companion object {
        /** Todas las columnas que guardan dinero. Si se agrega una tabla con importes, va acá. */
        val MONEY_COLUMNS = listOf(
            "transactions" to "amountMinor",
            "initial_balance" to "amountMinor",
            "income_sources" to "amountMinor",
            "income_occurrences" to "amountMinor",
            "savings_goals" to "targetMinor",
            "savings_entries" to "amountMinor",
            "bills" to "amountMinor",
            "loans" to "principalMinor",
            "loan_payments" to "amountMinor",
            "budgets" to "limitMinor",
        )
    }
}
