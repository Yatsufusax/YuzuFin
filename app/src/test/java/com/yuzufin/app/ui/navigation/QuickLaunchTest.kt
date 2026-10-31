package com.yuzufin.app.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLaunchTest {

    @Test fun sin_configuracion_inicial_el_pedido_se_descarta() {
        assertTrue(QuickLaunch.shouldConsumeWithoutOpening(onboardingDone = false))
        assertFalse(QuickLaunch.shouldConsumeWithoutOpening(onboardingDone = true))
    }

    @Test fun no_abre_hasta_que_la_navegacion_esta_lista() {
        assertFalse(QuickLaunch.shouldOpenExpense(null))
    }

    @Test fun abre_desde_cualquier_pantalla_que_no_sea_el_formulario() {
        assertTrue(QuickLaunch.shouldOpenExpense("home"))
        assertTrue(QuickLaunch.shouldOpenExpense("movements"))
    }

    @Test fun no_abre_otro_formulario_si_ya_hay_uno() {
        assertFalse(QuickLaunch.shouldOpenExpense(TransactionRoute.PATTERN))
    }
}
