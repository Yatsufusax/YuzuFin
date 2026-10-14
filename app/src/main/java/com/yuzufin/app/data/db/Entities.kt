package com.yuzufin.app.data.db

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
)

/** Una sola fila (id = 1) con el punto de partida financiero. */
@Entity(tableName = "initial_balance")
data class InitialBalanceEntity(
    @PrimaryKey val id: Int = 1,
    val amountMinor: Long,
    val startEpochDay: Long,
)
