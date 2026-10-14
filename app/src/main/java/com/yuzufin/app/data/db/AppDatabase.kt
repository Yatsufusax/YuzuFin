package com.yuzufin.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local. Al cambiar las tablas (Fases 3 a 5) se sube [version] y se agrega
 * una migración explícita: nunca se borra la base de datos del usuario.
 */
@Database(
    entities = [CategoryEntity::class, TransactionEntity::class, InitialBalanceEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun initialBalanceDao(): InitialBalanceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "yuzufin.db")
                .build()
    }
}
