package com.yuzufin.app.data

import com.yuzufin.app.data.db.AppNotificationEntity
import com.yuzufin.app.data.db.NotificationDao
import com.yuzufin.app.domain.NotificationKind
import com.yuzufin.app.domain.WeeklySummaryCalculator
import com.yuzufin.app.ui.navigation.SummaryLaunch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Persistencia del resumen semanal en la campanilla. El DAO es un doble en memoria que respeta las
 * reglas de Room: clave de deduplicación única (insertar otra igual devuelve -1) y el orden y la poda
 * de las consultas reales.
 */
class NotificationRepositoryTest {

    private class FakeNotificationDao : NotificationDao {
        private val rows = MutableStateFlow<List<AppNotificationEntity>>(emptyList())
        private var nextId = 1L

        private fun ordered(list: List<AppNotificationEntity>) =
            list.sortedWith(compareByDescending<AppNotificationEntity> { it.createdAt }.thenByDescending { it.id })

        override fun observeAll(): Flow<List<AppNotificationEntity>> = rows.map { ordered(it) }

        override fun observeUnreadCount(): Flow<Int> = rows.map { list -> list.count { !it.read } }

        override suspend fun findByKey(key: String): AppNotificationEntity? = rows.value.firstOrNull { it.dedupeKey == key }

        override suspend fun insert(entity: AppNotificationEntity): Long {
            if (rows.value.any { it.dedupeKey == entity.dedupeKey }) return -1L
            val saved = entity.copy(id = nextId++)
            rows.value = rows.value + saved
            return saved.id
        }

        override suspend fun markAllRead() {
            rows.value = rows.value.map { it.copy(read = true) }
        }

        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }

        override suspend fun prune(keep: Int) {
            val keepIds = ordered(rows.value).take(keep).map { it.id }.toSet()
            rows.value = rows.value.filter { it.id in keepIds }
        }
    }

    private val repository = NotificationRepository(FakeNotificationDao())
    private val title = "Tu semana en Monea"
    private val sunday = LocalDate.of(2026, 11, 1)
    private val key = WeeklySummaryCalculator.dedupeKey(sunday)
    private val body = "Gastaste Gs. 368.000 en 7 días. Lo que más pesó: Cuotas (Gs. 305.000)."

    @Test fun el_resumen_se_guarda_con_el_mismo_titulo_y_texto_y_se_encuentra_por_su_semana() = runBlocking {
        assertTrue(repository.log(NotificationKind.SUMMARY, title, body, key))

        val saved = repository.findByKey(key)
        assertNotNull(saved)
        assertEquals(title, saved!!.title)
        assertEquals(body, saved.body)
        assertEquals(NotificationKind.SUMMARY, saved.kind)
        assertEquals(key, saved.dedupeKey)
        assertFalse(saved.read)
    }

    @Test fun la_campanilla_lista_el_resumen_con_su_clave() = runBlocking {
        repository.log(NotificationKind.SUMMARY, title, body, key)

        val list = repository.notifications.first()
        assertEquals(1, list.size)
        assertEquals(key, list.single().dedupeKey)
        assertEquals(body, list.single().body)
    }

    @Test fun no_se_duplica_el_resumen_de_una_misma_semana_y_se_conserva_el_primero() = runBlocking {
        assertTrue(repository.log(NotificationKind.SUMMARY, title, body, key))
        // El trabajo se repite (reintento, revisor diario) y esta vez el cálculo daría otro texto.
        assertFalse(repository.log(NotificationKind.SUMMARY, title, "Gastaste Gs. 1 en 7 días.", key))

        assertEquals(1, repository.notifications.first().size)
        assertEquals(body, repository.findByKey(key)!!.body)
    }

    @Test fun cada_semana_es_un_registro_propio_y_cada_clave_devuelve_el_suyo() = runBlocking {
        val nextKey = WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(1))
        repository.log(NotificationKind.SUMMARY, title, body, key)
        repository.log(NotificationKind.SUMMARY, title, "Otra semana distinta.", nextKey)

        assertEquals(2, repository.notifications.first().size)
        assertEquals(body, repository.findByKey(key)!!.body)
        assertEquals("Otra semana distinta.", repository.findByKey(nextKey)!!.body)
    }

    @Test fun una_clave_que_no_existe_no_devuelve_nada() = runBlocking {
        repository.log(NotificationKind.SUMMARY, title, body, key)
        assertNull(repository.findByKey(WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(5))))
    }

    @Test fun abrir_la_campanilla_marca_como_leido_sin_cambiar_el_contenido() = runBlocking {
        repository.log(NotificationKind.SUMMARY, title, body, key)
        assertEquals(1, repository.unreadCount.first())

        repository.markAllRead()

        assertEquals(0, repository.unreadCount.first())
        val saved = repository.findByKey(key)!!
        assertTrue(saved.read)
        assertEquals(body, saved.body)
        assertEquals(title, saved.title)
    }

    @Test fun borrar_un_aviso_no_toca_el_resumen() = runBlocking {
        repository.log(NotificationKind.SUMMARY, title, body, key)
        repository.log(NotificationKind.UPDATE, "Actualización", "Novedades", "update:1")
        val update = repository.findByKey("update:1")!!

        repository.delete(update.id)

        assertNull(repository.findByKey("update:1"))
        assertEquals(body, repository.findByKey(key)!!.body)
    }

    @Test fun el_resumen_mas_reciente_aparece_primero() = runBlocking {
        val nextKey = WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(1))
        repository.log(NotificationKind.SUMMARY, title, "Semana 1", key)
        repository.log(NotificationKind.SUMMARY, title, "Semana 2", nextKey)

        assertEquals(listOf("Semana 2", "Semana 1"), repository.notifications.first().map { it.body })
    }

    @Test fun la_poda_conserva_los_avisos_mas_recientes() = runBlocking {
        repeat(NotificationRepository.MAX_KEPT + 5) { index ->
            repository.log(NotificationKind.SUMMARY, title, "Semana $index", "weekly:$index")
        }

        assertEquals(NotificationRepository.MAX_KEPT, repository.notifications.first().size)
        assertNull(repository.findByKey("weekly:0"))
        assertNotNull(repository.findByKey("weekly:${NotificationRepository.MAX_KEPT + 4}"))
    }

    @Test fun la_clave_guardada_alcanza_para_pedir_la_semana_desde_la_notificacion() = runBlocking {
        repository.log(NotificationKind.SUMMARY, title, body, key)

        // La notificación lleva la clave; con ella se reconoce el pedido y se encuentra lo guardado.
        val extras = SummaryLaunch.extras(key)
        val requested = SummaryLaunch.requestFrom(extras.getValue("open"), extras.getValue(SummaryLaunch.EXTRA_SUMMARY_KEY))
        assertEquals(key, requested)
        assertEquals(body, repository.findByKey(requested!!)!!.body)
        assertEquals(sunday.minusDays(6) to sunday, SummaryLaunch.weekRange(requested))
    }
}
