package com.yuzufin.app.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yuzufin.app.MainActivity
import com.yuzufin.app.domain.WeeklySummaryCalculator
import com.yuzufin.app.ui.navigation.QuickLaunch
import com.yuzufin.app.ui.navigation.SummaryLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Pruebas en dispositivo/emulador (connectedAndroidTest) del Intent que lleva la notificación del
 * resumen semanal. La lógica de navegación y la persistencia están en pruebas unitarias comunes.
 */
@RunWith(AndroidJUnit4::class)
class WeeklySummaryLaunchInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sunday = LocalDate.of(2026, 11, 1)
    private val key = WeeklySummaryCalculator.dedupeKey(sunday)

    @Test fun el_intent_apunta_a_la_actividad_principal_con_los_extras_del_resumen() {
        val intent = WeeklySummaryIntents.open(context, key)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(SummaryLaunch.OPEN_WEEKLY_SUMMARY, intent.getStringExtra(QuickLaunch.EXTRA_OPEN))
        assertEquals(key, intent.getStringExtra(SummaryLaunch.EXTRA_SUMMARY_KEY))
        assertEquals(SummaryLaunch.uriString(key), intent.dataString)
    }

    @Test fun el_intent_trae_a_primer_plano_la_actividad_que_ya_esta_abierta() {
        val flags = WeeklySummaryIntents.open(context, key).flags
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun semanas_distintas_generan_PendingIntent_distintos() {
        val nextKey = WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(1))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val first = PendingIntent.getActivity(context, WeeklySummaryWorker.ID_WEEKLY, WeeklySummaryIntents.open(context, key), flags)
        val second = PendingIntent.getActivity(context, WeeklySummaryWorker.ID_WEEKLY, WeeklySummaryIntents.open(context, nextKey), flags)
        assertNotNull(first)
        assertNotNull(second)
        // Con el mismo código de pedido, solo la dirección (data) las distingue.
        assertNotEquals(first, second)
    }
}
