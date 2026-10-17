package com.yuzufin.app.domain

import com.yuzufin.app.core.money.CurrencyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CurrencyConversionTest {
    private val pyg = CurrencyInfo.PYG
    private val usd = CurrencyInfo.USD
    private val eur = CurrencyInfo.EUR

    @Test
    fun guaraniToDollarsUsesOneDollarEqualsRate() {
        val newPerOld = CurrencyConversion.newPerOld(pyg, usd, BigDecimal(7300)) // 1 USD = 7.300 Gs.
        val factor = CurrencyConversion.factor(pyg, usd, newPerOld)
        // Gs. 1.000.000 → US$ 136,99 (13699 centavos)
        assertEquals(13_699L, CurrencyConversion.convert(1_000_000L, factor))
    }

    @Test
    fun dollarsToGuaraniUsesTheSameQuestion() {
        val newPerOld = CurrencyConversion.newPerOld(usd, pyg, BigDecimal(7300))
        val factor = CurrencyConversion.factor(usd, pyg, newPerOld)
        // US$ 100,00 (10000 centavos) → Gs. 730.000
        assertEquals(730_000L, CurrencyConversion.convert(10_000L, factor))
    }

    @Test
    fun keepingNumbersOnlyShiftsDecimals() {
        val factor = CurrencyConversion.factor(pyg, usd, BigDecimal.ONE)
        assertEquals(100_000_000L, CurrencyConversion.convert(1_000_000L, factor))
        val back = CurrencyConversion.factor(usd, pyg, BigDecimal.ONE)
        assertEquals(1_000_000L, CurrencyConversion.convert(100_000_000L, back))
    }

    @Test
    fun smallAmountsNeverBecomeZero() {
        val factor = CurrencyConversion.factor(pyg, usd, CurrencyConversion.newPerOld(pyg, usd, BigDecimal(7300)))
        assertEquals(1L, CurrencyConversion.convert(10L, factor))
        assertEquals(-1L, CurrencyConversion.convert(-10L, factor))
        assertEquals(0L, CurrencyConversion.convert(0L, factor))
    }

    @Test
    fun otherPairsAskInTermsOfCurrentCurrency() {
        // 1 USD = 0,92 EUR
        val newPerOld = CurrencyConversion.newPerOld(usd, eur, BigDecimal("0.92"))
        assertEquals(BigDecimal("0.92"), newPerOld)
        val factor = CurrencyConversion.factor(usd, eur, newPerOld)
        assertEquals(9_200L, CurrencyConversion.convert(10_000L, factor))
    }

    @Test
    fun parsesRateText() {
        assertEquals(BigDecimal("7300"), CurrencyConversion.parseRate("7300"))
        assertEquals(BigDecimal("0.92"), CurrencyConversion.parseRate("0,92"))
        assertNull(CurrencyConversion.parseRate(""))
        assertNull(CurrencyConversion.parseRate("0"))
        assertNull(CurrencyConversion.parseRate("1,2,3"))
        assertNull(CurrencyConversion.parseRate("abc"))
    }
}
