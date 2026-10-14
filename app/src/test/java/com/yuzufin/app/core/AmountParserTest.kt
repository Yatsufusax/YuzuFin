package com.yuzufin.app.core

import com.yuzufin.app.core.money.AmountParser
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.core.money.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {

    @Test fun guaranies_ignoran_puntos_y_comas() {
        assertEquals(1_200_000L, AmountParser.parseToMinorUnits("1.200.000", CurrencyInfo.PYG))
        assertEquals(15_000L, AmountParser.parseToMinorUnits("15000", CurrencyInfo.PYG))
    }

    @Test fun vacio_devuelve_nulo() {
        assertNull(AmountParser.parseToMinorUnits("", CurrencyInfo.PYG))
        assertNull(AmountParser.parseToMinorUnits("abc", CurrencyInfo.USD))
    }

    @Test fun dolares_con_decimales_sin_errores_de_coma_flotante() {
        assertEquals(1_050L, AmountParser.parseToMinorUnits("10,5", CurrencyInfo.USD))
        assertEquals(1_005L, AmountParser.parseToMinorUnits("10,05", CurrencyInfo.USD))
        assertEquals(1_999L, AmountParser.parseToMinorUnits("19.99", CurrencyInfo.USD))
        assertEquals(10L, AmountParser.parseToMinorUnits(",1", CurrencyInfo.USD))
    }

    @Test fun se_recortan_los_decimales_sobrantes() {
        assertEquals(1_234L, AmountParser.parseToMinorUnits("12,345", CurrencyInfo.USD))
    }

    @Test fun ida_y_vuelta_al_editar() {
        val text = AmountParser.toInputText(123_456L, CurrencyInfo.USD)
        assertEquals("1234,56", text)
        assertEquals(123_456L, AmountParser.parseToMinorUnits(text, CurrencyInfo.USD))
        assertEquals("5", AmountParser.toInputText(5L, CurrencyInfo.PYG))
        assertEquals("0,05", AmountParser.toInputText(5L, CurrencyInfo.USD))
    }

    @Test fun el_formato_agrupa_miles() {
        assertEquals("1.234.567", MoneyFormatter.formatNumber(1_234_567L, CurrencyInfo.PYG))
    }

    @Test fun moneda_desconocida_cae_en_guaranies() {
        assertEquals(CurrencyInfo.PYG, CurrencyInfo.fromCode("XXX"))
        assertEquals(CurrencyInfo.PYG, CurrencyInfo.fromCode(null))
    }
}
