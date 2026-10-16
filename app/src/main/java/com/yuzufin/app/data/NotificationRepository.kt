package com.yuzufin.app.data

import com.yuzufin.app.data.db.AppDatabase
import com.yuzufin.app.data.db.AppNotificationEntity
import com.yuzufin.app.domain.AppNotification
import com.yuzufin.app.domain.NotificationKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Historial de avisos de la app (campanilla). Vive solo en el teléfono. El texto guardado es el
 * mismo que se muestra en la notificación, así que respeta el ajuste de mostrar o no importes.
 */
class NotificationRepository(db: AppDatabase) {

    private val dao = db.notificationDao()

    val notifications: Flow<List<AppNotification>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    val unreadCount: Flow<Int> = dao.observeUnreadCount()

    /** Guarda un aviso. Devuelve false si ya existía otro con la misma [dedupeKey]. */
    suspend fun log(kind: NotificationKind, title: String, body: String, dedupeKey: String): Boolean {
        val id = dao.insert(
            AppNotificationEntity(
                kind = kind.name,
                title = title,
                body = body,
                createdAt = System.currentTimeMillis(),
                read = false,
                dedupeKey = dedupeKey,
            ),
        )
        if (id == -1L) return false
        dao.prune(MAX_KEPT)
        return true
    }

    suspend fun markAllRead() = dao.markAllRead()

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_KEPT = 200
    }
}

private fun AppNotificationEntity.toDomain() = AppNotification(
    id = id,
    kind = NotificationKind.fromName(kind),
    title = title,
    body = body,
    createdAt = createdAt,
    read = read,
)
