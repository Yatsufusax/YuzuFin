package com.yuzufin.app.data

import androidx.room.withTransaction
import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.AttachmentEntity
import com.yuzufin.app.domain.Attachment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Fotos adjuntas a movimientos: filas en la base y archivos en [AttachmentFileStore]. */
class AttachmentRepository(private val db: AppDatabase, val files: AttachmentFileStore) {

    private val dao = db.attachmentDao()

    /** Ids de los movimientos que tienen al menos una foto (para mostrar el clip en la lista). */
    val transactionIdsWithPhotos: Flow<Set<Long>> = dao.observeTransactionIds().map { it.toSet() }

    suspend fun list(transactionId: Long): List<Attachment> =
        dao.forTransaction(transactionId).map { Attachment(it.id, it.transactionId, it.fileName) }

    /**
     * Guarda los cambios de fotos de un movimiento: enlaza las nuevas ([addNames], ya importadas) y
     * quita las indicadas ([removeIds]), borrando también sus archivos.
     */
    suspend fun apply(transactionId: Long, addNames: List<String>, removeIds: List<Long>) {
        val removedNames = db.withTransaction {
            val names = if (removeIds.isEmpty()) emptyList() else dao.namesByIds(removeIds)
            if (removeIds.isNotEmpty()) dao.deleteByIds(removeIds)
            if (addNames.isNotEmpty()) {
                val now = System.currentTimeMillis()
                dao.insertAll(addNames.map { AttachmentEntity(transactionId = transactionId, fileName = it, createdAt = now) })
            }
            names
        }
        files.delete(removedNames)
    }
}
