package com.yuzufin.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuzufin.app.domain.TransactionType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
)

/**
 * Ingreso o gasto confirmado. La fecha se guarda como día (epochDay) para no depender
 * de zonas horarias; [createdAt] y [updatedAt] sirven para auditar.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("dateEpochDay")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val description: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** Movimiento interno de un préstamo: mueve el saldo pero no cuenta como ingreso ni gasto. */
    @ColumnInfo(defaultValue = "0") val internalTransfer: Boolean = false,
)

/** Una sola fila (id = 1) con el punto de partida financiero. */
@Entity(tableName = "initial_balance")
data class InitialBalanceEntity(
    @PrimaryKey val id: Int = 1,
    val amountMinor: Long,
    val startEpochDay: Long,
)

/**
 * Fuente de ingreso programado (Fase 3). Las listas se guardan como texto separado por comas
 * (días del mes, o días-época de las fechas sueltas) para no necesitar conversores.
 */
@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long,
    /** Nombre de [com.yuzufin.app.domain.IncomeSchedule]. */
    val schedule: String,
    val daysOfMonth: String,
    val customDates: String,
    val startEpochDay: Long,
    val requiresConfirmation: Boolean,
    val active: Boolean,
    val remind: Boolean,
    val createdAt: Long,
)

/**
 * Un cobro concreto. El índice único (fuente, fecha) impide registrar dos veces el mismo cobro
 * aunque la reconciliación corra varias veces o a la vez.
 */
@Entity(
    tableName = "income_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = IncomeSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceId", "dueEpochDay"], unique = true)],
)
data class IncomeOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val dueEpochDay: Long,
    val amountMinor: Long,
    /** Nombre de [com.yuzufin.app.domain.OccurrenceStatus]. */
    val status: String,
    /** Movimiento de ingreso creado al confirmar; null si está pendiente u omitido. */
    val transactionId: Long?,
    /** Si ya se mostró el aviso de este cobro. */
    val notified: Boolean,
    val createdAt: Long,
)

/** Meta de ahorro (Fase 4). La fecha límite es un día-época o null. */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val deadlineEpochDay: Long?,
    val autoPercent: Int,
    val remind: Boolean,
    /** Último mes (aaaamm) en el que se evaluó el recordatorio de esta meta. */
    val lastRemindedMonth: Int,
    val createdAt: Long,
)

/**
 * Aporte (+) o retiro (-) de una meta. No es un movimiento: no entra en el saldo ni en el extracto.
 * [sourceTransactionId] enlaza los aportes automáticos con el ingreso que los originó.
 */
@Entity(
    tableName = "savings_entries",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId"), Index("sourceTransactionId")],
)
data class SavingsEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val note: String,
    val sourceTransactionId: Long?,
    val createdAt: Long,
)

/** Factura o vencimiento (Fase 5). Pendiente: no toca el saldo. Pagada: enlaza el gasto creado. */
@Entity(tableName = "bills", indices = [Index("paidTransactionId")])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountMinor: Long,
    val categoryId: Long,
    val dueEpochDay: Long,
    /** Día del mes en que se repite (31 = último día); 0 si no se repite. */
    val repeatDay: Int,
    val remind: Boolean,
    val paid: Boolean,
    val paidTransactionId: Long?,
    val paidEpochDay: Long?,
    /** Si ya se mostró el aviso de este vencimiento. */
    val notified: Boolean,
    val createdAt: Long,
)

/** Préstamo hecho (LENT) o recibido (BORROWED). El dinero se movió con un movimiento interno enlazado. */
@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    /** Nombre de [com.yuzufin.app.domain.LoanDirection]. */
    val direction: String,
    val principalMinor: Long,
    val startEpochDay: Long,
    val note: String,
    val remind: Boolean,
    /** Último mes (aaaamm) en que se avisó el saldo de este préstamo. */
    val lastRemindedMonth: Int,
    val principalTransactionId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("loanId")],
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val note: String,
    val transactionId: Long?,
    val createdAt: Long,
)

/**
 * Registro de avisos de la app (campanilla). [dedupeKey] es único: si el revisor diario se
 * ejecuta dos veces el mismo día, el aviso queda una sola vez.
 */
@Entity(
    tableName = "app_notifications",
    indices = [Index(value = ["dedupeKey"], unique = true)],
)
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean,
    val dedupeKey: String,
)
