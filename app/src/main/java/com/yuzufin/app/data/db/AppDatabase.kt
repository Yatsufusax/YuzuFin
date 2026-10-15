package com.yuzufin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local. Al cambiar las tablas (Fases 3 a 5) se sube [version] y se agrega
 * una migración explícita: nunca se borra la base de datos del usuario.
 */
@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        InitialBalanceEntity::class,
        IncomeSourceEntity::class,
        IncomeOccurrenceEntity::class,
        SavingsGoalEntity::class,
        SavingsEntryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun initialBalanceDao(): InitialBalanceDao
    abstract fun incomeDao(): IncomeDao
    abstract fun savingsDao(): SavingsDao

    companion object {
        /** Fase 3: agrega las fuentes de ingreso y sus cobros. No toca las tablas existentes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `income_sources` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`schedule` TEXT NOT NULL, " +
                        "`daysOfMonth` TEXT NOT NULL, " +
                        "`customDates` TEXT NOT NULL, " +
                        "`startEpochDay` INTEGER NOT NULL, " +
                        "`requiresConfirmation` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `income_occurrences` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceId` INTEGER NOT NULL, " +
                        "`dueEpochDay` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`transactionId` INTEGER, " +
                        "`notified` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sourceId`) REFERENCES `income_sources`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_income_occurrences_sourceId_dueEpochDay` " +
                        "ON `income_occurrences` (`sourceId`, `dueEpochDay`)",
                )
            }
        }

        /** Fase 4: agrega las metas de ahorro y sus aportes. No toca las tablas existentes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_goals` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`targetMinor` INTEGER NOT NULL, " +
                        "`deadlineEpochDay` INTEGER, " +
                        "`autoPercent` INTEGER NOT NULL, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`lastRemindedMonth` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`sourceTransactionId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_entries_goalId` ON `savings_entries` (`goalId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_savings_entries_sourceTransactionId` " +
                        "ON `savings_entries` (`sourceTransactionId`)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "yuzufin.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
