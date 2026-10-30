package com.yuzufin.app.ui

import com.yuzufin.app.ui.components.MascotPose
import com.yuzufin.app.ui.components.mascotForCurrency
import org.junit.Assert.assertEquals
import org.junit.Test

class MascotTest {
    @Test
    fun guaraniesUseTerereAndPesosUseMate() {
        assertEquals(MascotPose.TERERE, mascotForCurrency("PYG"))
        assertEquals(MascotPose.MATE, mascotForCurrency("ARS"))
    }

    @Test
    fun otherCurrenciesUseCoffee() {
        listOf("USD", "BRL", "EUR").forEach { assertEquals(MascotPose.COFFEE, mascotForCurrency(it)) }
    }
}
