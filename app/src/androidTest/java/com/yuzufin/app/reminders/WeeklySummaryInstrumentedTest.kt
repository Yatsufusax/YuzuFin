package com.yuzufin.app.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Pruebas en dispositivo/emulador del resumen semanal (hay que ejecutarlas con connectedAndroidTest).
 * El reinicio del teléfono no se puede simular desde acá: ver los pasos manuales en el informe.
 */
@RunWith(AndroidJUnit4::class)
class WeeklySummaryInstrumentedTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    // Cada prueba usa su propio domingo: los avisos ya enviados quedan guardados entre pruebas.
    private val sunday = LocalDateTime.of(2026, 11, 1, 20, 5)
    private fun week(n: Long) = sunday.plusWeeks(n)

    @Before fun setUp() {
        manager.cancelAll()
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    @After fun tearDown() {
        WeeklySummaryWorker.nowProvider = { LocalDateTime.now() }
        manager.cancelAll()
    }

    private fun weeklyNotifications() = manager.activeNotifications.filter { it.id == WeeklySummaryWorker.ID_WEEKLY }

    private fun runWorker(now: LocalDateTime): ListenableWorker.Result {
        WeeklySummaryWorker.nowProvider = { now }
        val worker = TestListenableWorkerBuilder<WeeklySummaryWorker>(context).build()
        return runBlocking { worker.doWork() }
    }

    @Test fun la_tarea_se_registra_una_sola_vez() {
        WeeklySummaryScheduler.apply(context, enabled = true)
        WeeklySummaryScheduler.apply(context, enabled = true) // abrir la app varias veces no duplica
        val work = WorkManager.getInstance(context)
        val infos = work.getWorkInfosForUniqueWork(WeeklySummaryScheduler.WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        // El trabajo semanal viejo no queda registrado.
        assertTrue(work.getWorkInfosForUniqueWork(WeeklySummaryScheduler.LEGACY_WORK_NAME).get().none { !it.state.isFinished })
        WeeklySummaryScheduler.apply(context, enabled = false)
        assertTrue(work.getWorkInfosForUniqueWork(WeeklySummaryScheduler.WORK_NAME).get().all { it.state.isFinished })
    }

    @Test fun el_domingo_a_las_20_se_genera_y_muestra_la_notificacion() {
        assertEquals(ListenableWorker.Result.success(), runWorker(week(10)))
        assertEquals(1, weeklyNotifications().size)
    }

    @Test fun repetir_el_trabajo_no_duplica_la_notificacion() {
        runWorker(week(11))
        manager.cancel(WeeklySummaryWorker.ID_WEEKLY)
        // Android lo vuelve a ejecutar el lunes y el martes: ya se avisó esa semana.
        runWorker(week(11).plusDays(1))
        runWorker(week(11).plusDays(2).withHour(19))
        assertEquals(0, weeklyNotifications().size)
    }

    @Test fun un_retraso_hasta_el_lunes_igual_entrega_el_resumen() {
        runWorker(week(12).plusDays(1).withHour(1))
        assertEquals(1, weeklyNotifications().size)
    }

    @Test fun fuera_de_la_ventana_no_avisa() {
        runWorker(week(13).plusDays(4)) // jueves
        assertEquals(0, weeklyNotifications().size)
    }
}
