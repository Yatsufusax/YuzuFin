package com.yuzufin.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun formatsGuaraniesWithThousandsSeparator() {
        assertEquals("Gs. 4.250.000", MoneyFormatter.format(4_250_000L))
    }

    @Test
    fun formatsZeroAndSmallAmounts() {
        assertEquals("Gs. 0", MoneyFormatter.format(0L))
        assertEquals("Gs. 7", MoneyFormatter.format(7L))
        assertEquals("Gs. 999", MoneyFormatter.format(999L))
        assertEquals("Gs. 1.000", MoneyFormatter.format(1_000L))
    }

    @Test
    fun formatsNegativeAmountsKeepingTheSignBeforeTheSymbol() {
        assertEquals("-Gs. 1.200.000", MoneyFormatter.format(-1_200_000L))
    }

    @Test
    fun showsPlusSignOnlyWhenRequestedAndPositive() {
        assertEquals("+Gs. 300.000", MoneyFormatter.format(300_000L, showPlus = true))
        assertEquals("Gs. 300.000", MoneyFormatter.format(300_000L))
        assertEquals("Gs. 0", MoneyFormatter.format(0L, showPlus = true))
    }

    @Test
    fun formatsCurrenciesWithDecimalsUsingIntegerMath() {
        assertEquals("US$ 1.234,56", MoneyFormatter.format(123_456L, CurrencyInfo.USD))
        assertEquals("US$ 0,05", MoneyFormatter.format(5L, CurrencyInfo.USD))
        assertEquals("-US$ 10,00", MoneyFormatter.format(-1_000L, CurrencyInfo.USD))
    }

    @Test
    fun formatNumberOmitsTheSymbol() {
        assertEquals("4.250.000", MoneyFormatter.formatNumber(4_250_000L))
        assertEquals("-1.500", MoneyFormatter.formatNumber(-1_500L))
    }
}
