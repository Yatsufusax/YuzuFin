package com.yuzufin.app.ui.transaction

import com.yuzufin.app.core.money.CurrencyInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiCurrencyEntryTest {
    @Test
    fun dollarsAreConvertedToTheMainCurrency() {
        val state = TransactionFormState(
            currency = CurrencyInfo.PYG,
            entryCurrency = CurrencyInfo.USD,
            amountText = "50",
            rateText = "7.600",
        )
        assertEquals(5_000L, state.enteredMinor)
        assertEquals(380_000L, state.amountMinor)
    }

    @Test
    fun withoutRateThereIsNoAmount() {
        val state = TransactionFormState(
            currency = CurrencyInfo.PYG,
            entryCurrency = CurrencyInfo.USD,
            amountText = "50",
        )
        assertNull(state.amountMinor)
    }

    @Test
    fun mainCurrencyWorksAsBefore() {
        val state = TransactionFormState(currency = CurrencyInfo.PYG, amountText = "20000")
        assertEquals(20_000L, state.amountMinor)
        assertEquals(CurrencyInfo.PYG, state.inputCurrency)
    }
}
