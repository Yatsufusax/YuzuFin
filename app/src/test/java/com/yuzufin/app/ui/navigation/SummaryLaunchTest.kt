package com.yuzufin.app.ui.navigation

import com.yuzufin.app.domain.WeeklySummaryCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Navegación de la notificación del resumen semanal: qué lleva la notificación, cómo se reconoce y
 * a dónde va la app según esté cerrada, en segundo plano o ya abierta.
 */
class SummaryLaunchTest {

    private val sunday = LocalDate.of(2026, 11, 1)
    private val key = WeeklySummaryCalculator.dedupeKey(sunday)

    // ----- Extras / identificadores -----------------------------------------------------------------

    @Test fun la_clave_que_genera_el_calculador_es_valida() {
        assertTrue(SummaryLaunch.isValidKey(key))
    }

    @Test fun los_extras_de_la_notificacion_se_reconocen_como_pedido_de_resumen() {
        val extras = SummaryLaunch.extras(key)
        val request = SummaryLaunch.requestFrom(extras[QuickLaunch.EXTRA_OPEN], extras[SummaryLaunch.EXTRA_SUMMARY_KEY])
        assertEquals(key, request)
    }

    @Test fun una_notificacion_comun_no_es_un_pedido_de_resumen() {
        assertNull(SummaryLaunch.requestFrom(null, null))
        assertNull(SummaryLaunch.requestFrom(null, key))
        assertNull(SummaryLaunch.requestFrom(SummaryLaunch.OPEN_WEEKLY_SUMMARY, null))
    }

    @Test fun el_pedido_del_widget_no_se_confunde_con_el_del_resumen() {
        assertNull(SummaryLaunch.requestFrom(QuickLaunch.OPEN_EXPENSE, key))
    }

    @Test fun claves_con_formato_raro_se_rechazan() {
        assertFalse(SummaryLaunch.isValidKey(""))
        assertFalse(SummaryLaunch.isValidKey("weekly:"))
        assertFalse(SummaryLaunch.isValidKey("weekly:abc"))
        assertFalse(SummaryLaunch.isValidKey("weekly:12;DROP"))
        assertFalse(SummaryLaunch.isValidKey("habit:20000"))
        assertFalse(SummaryLaunch.isValidKey("weekly:123456789"))
        assertNull(SummaryLaunch.requestFrom(SummaryLaunch.OPEN_WEEKLY_SUMMARY, "weekly:abc"))
    }

    @Test fun cada_semana_tiene_su_propia_direccion_para_no_mezclar_los_PendingIntent() {
        val next = WeeklySummaryCalculator.dedupeKey(sunday.plusWeeks(1))
        assertNotEquals(SummaryLaunch.uriString(key), SummaryLaunch.uriString(next))
        assertEquals("monea://weekly-summary/${sunday.toEpochDay()}", SummaryLaunch.uriString(key))
    }

    @Test fun la_semana_del_resumen_sale_de_la_clave() {
        assertEquals(sunday.minusDays(6) to sunday, SummaryLaunch.weekRange(key))
        assertNull(SummaryLaunch.weekRange("weekly:abc"))
    }

    // ----- Navegación según el estado de la app -------------------------------------------------------

    @Test fun sin_pedido_no_se_hace_nada() {
        assertEquals(SummaryLaunch.Action.NONE, SummaryLaunch.decide(null, true, "home"))
    }

    @Test fun app_cerrada_espera_a_que_carguen_los_ajustes_y_la_navegacion() {
        // Recién arrancada: los ajustes todavía no se leyeron.
        assertEquals(SummaryLaunch.Action.WAIT, SummaryLaunch.decide(key, null, null))
        // Ajustes listos pero el NavHost todavía no tiene pantalla.
        assertEquals(SummaryLaunch.Action.WAIT, SummaryLaunch.decide(key, true, null))
    }

    @Test fun app_cerrada_con_la_navegacion_lista_abre_la_campanilla() {
        assertEquals(SummaryLaunch.Action.OPEN_NOTIFICATIONS, SummaryLaunch.decide(key, true, Destination.Home.route))
    }

    @Test fun app_en_segundo_plano_abre_la_campanilla_desde_la_pantalla_donde_estaba() {
        listOf(
            Destination.Home.route,
            Destination.Movements.route,
            Destination.Settings.route,
            Destination.Stats.route,
            TransactionRoute.PATTERN,
        ).forEach { route ->
            assertEquals(route, SummaryLaunch.Action.OPEN_NOTIFICATIONS, SummaryLaunch.decide(key, true, route))
        }
    }

    @Test fun app_ya_abierta_en_la_campanilla_no_navega_otra_vez_y_deja_que_la_pantalla_lo_abra() {
        assertEquals(
            SummaryLaunch.Action.SHOW_HERE,
            SummaryLaunch.decide(key, true, Destination.Notifications.route),
        )
    }

    @Test fun sin_configuracion_inicial_el_pedido_se_descarta() {
        assertEquals(SummaryLaunch.Action.DISCARD, SummaryLaunch.decide(key, false, "home"))
        assertEquals(SummaryLaunch.Action.DISCARD, SummaryLaunch.decide(key, false, null))
        assertTrue(SummaryLaunch.shouldDiscard(onboardingDone = false))
        assertFalse(SummaryLaunch.shouldDiscard(onboardingDone = true))
    }

    @Test fun el_destino_del_resumen_es_una_pantalla_secundaria_que_vuelve_con_atras() {
        // Se apila sobre Inicio: "atrás" desde el resumen regresa a donde estaba la persona.
        assertFalse(Destination.Notifications.isTopLevel)
    }
}
