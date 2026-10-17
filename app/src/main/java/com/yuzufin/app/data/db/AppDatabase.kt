package com.yuzufin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local. Al cambiar las tablas (Fases 3 a 6) se sube [version] y se agrega
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
        BillEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        AppNotificationEntity::class,
        BudgetEntity::class,
        AttachmentEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun initialBalanceDao(): InitialBalanceDao
    abstract fun incomeDao(): IncomeDao
    abstract fun savingsDao(): SavingsDao
    abstract fun billDao(): BillDao
    abstract fun loanDao(): LoanDao
    abstract fun notificationDao(): NotificationDao
    abstract fun budgetDao(): BudgetDao
    abstract fun attachmentDao(): AttachmentDao

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

        /** Fase 5: facturas, préstamos y la marca de movimiento interno. Conserva todos los datos. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `internalTransfer` INTEGER NOT NULL DEFAULT 0")
                // La categoría "Préstamos" solo se agrega si ya hay categorías (configuración terminada):
                // si no, la crea la configuración inicial junto con las demás.
                db.execSQL(
                    "INSERT INTO `categories` (`name`, `type`, `iconKey`) " +
                        "SELECT 'Préstamos', 'EXPENSE', 'loan' WHERE EXISTS (SELECT 1 FROM `categories`)",
                )
                db.execSQL(
                    "INSERT INTO `categories` (`name`, `type`, `iconKey`) " +
                        "SELECT 'Préstamos', 'INCOME', 'loan' WHERE EXISTS (SELECT 1 FROM `categories`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bills` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`dueEpochDay` INTEGER NOT NULL, " +
                        "`repeatDay` INTEGER NOT NULL, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`paid` INTEGER NOT NULL, " +
                        "`paidTransactionId` INTEGER, " +
                        "`paidEpochDay` INTEGER, " +
                        "`notified` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_paidTransactionId` ON `bills` (`paidTransactionId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`person` TEXT NOT NULL, " +
                        "`direction` TEXT NOT NULL, " +
                        "`principalMinor` INTEGER NOT NULL, " +
                        "`startEpochDay` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`lastRemindedMonth` INTEGER NOT NULL, " +
                        "`principalTransactionId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `loan_payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`loanId` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`transactionId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`loanId`) REFERENCES `loans`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_loan_payments_loanId` ON `loan_payments` (`loanId`)")
            }
        }

        /** Fase 6: registro de avisos (campanilla). No toca las tablas existentes. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_notifications` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`read` INTEGER NOT NULL, " +
                        "`dedupeKey` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_notifications_dedupeKey` " +
                        "ON `app_notifications` (`dedupeKey`)",
                )
            }
        }

        /** Fase 6: categorías archivables y presupuestos por categoría. Conserva todos los datos. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`limitMinor` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)",
                )
            }
        }

        /** Fotos adjuntas a movimientos. No toca las tablas existentes. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`transactionId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_transactionId` ON `attachments` (`transactionId`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "yuzufin.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
