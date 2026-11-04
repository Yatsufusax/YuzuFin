package com.yuzufin.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceParserTest {
    private val categories = listOf(
        Category(1, "Alimentación", TransactionType.EXPENSE, "food"),
        Category(2, "Transporte", TransactionType.EXPENSE, "transport"),
        Category(4, "Servicios", TransactionType.EXPENSE, "utilities"),
        Category(9, "Otros gastos", TransactionType.EXPENSE, "other"),
        Category(10, "Salario", TransactionType.INCOME, "salary"),
        Category(11, "Ingreso extra", TransactionType.INCOME, "extra"),
    )

    @Test fun amountInWords() {
        assertEquals(25_000L, VoiceParser.extractAmount("gasté veinticinco mil en comida"))
        assertEquals(2_500L, VoiceParser.extractAmount("dos mil quinientos"))
        assertEquals(35_000L, VoiceParser.extractAmount("treinta y cinco mil"))
        assertEquals(120_000L, VoiceParser.extractAmount("ciento veinte mil"))
        assertEquals(2_000_000L, VoiceParser.extractAmount("cobré dos millones"))
        assertEquals(1_500_000L, VoiceParser.extractAmount("un millón y medio"))
        assertEquals(1_000L, VoiceParser.extractAmount("mil guaraníes"))
        assertEquals(500L, VoiceParser.extractAmount("quinientos"))
    }

    @Test fun amountInDigits() {
        assertEquals(25_000L, VoiceParser.extractAmount("gasté 25000 en nafta"))
        assertEquals(25_000L, VoiceParser.extractAmount("gasté 25.000 en nafta"))
        assertEquals(25_000L, VoiceParser.extractAmount("gasté 25 mil en nafta"))
        assertEquals(1_500_000L, VoiceParser.extractAmount("cobré 1,5 millones"))
        assertEquals(2_000_000L, VoiceParser.extractAmount("2 millones"))
    }

    @Test fun thousandsSeparatorsFromRecognizer() {
        assertEquals(20_000L, VoiceParser.extractAmount("gasté 20,000 en el súper"))
        assertEquals(20_000L, VoiceParser.extractAmount("gasté 20,000 mil en el súper"))
        assertEquals(20_000L, VoiceParser.extractAmount("gasté 20.000 mil en el súper"))
        assertEquals(1_234_567L, VoiceParser.extractAmount("cobré 1,234,567"))
        assertEquals(1_500_000L, VoiceParser.extractAmount("cobré 1,5 millones"))
    }

    @Test fun noAmount() {
        assertNull(VoiceParser.extractAmount("gasté en el súper"))
        assertNull(VoiceParser.extractAmount(""))
    }

    @Test fun typeDetection() {
        assertEquals(TransactionType.INCOME, VoiceParser.detectType("cobré el sueldo"))
        assertEquals(TransactionType.INCOME, VoiceParser.detectType("me pagaron 300 mil"))
        assertEquals(TransactionType.EXPENSE, VoiceParser.detectType("pagué la luz"))
        assertEquals(TransactionType.EXPENSE, VoiceParser.detectType("gasté 10 mil"))
    }

    @Test fun categoryAndDescription() {
        val r = VoiceParser.parse("gasté veinticinco mil en el súper", categories)
        assertEquals(TransactionType.EXPENSE, r.type)
        assertEquals(25_000L, r.amount)
        assertEquals(1L, r.categoryId)
        assertEquals("Súper".lowercase(), r.description.lowercase())
    }

    @Test fun categoryByNameAndKeyword() {
        assertEquals(2L, VoiceParser.parse("15 mil de nafta", categories).categoryId)
        assertEquals(4L, VoiceParser.parse("pagué 80 mil de luz", categories).categoryId)
        assertEquals(1L, VoiceParser.parse("20 mil en alimentación", categories).categoryId)
        assertEquals(10L, VoiceParser.parse("cobré el sueldo 3 millones", categories).categoryId)
    }

    @Test fun incomeUsesOnlyIncomeCategories() {
        val r = VoiceParser.parse("cobré 500 mil de una venta", categories)
        assertEquals(TransactionType.INCOME, r.type)
        assertEquals(11L, r.categoryId)
    }
}
