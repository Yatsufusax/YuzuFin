package com.yuzufin.app.core.money

import kotlin.math.abs

/**
 * Moneda con su cantidad de decimales.
 *
 * Todos los importes de la app son [Long] en la unidad mínima de la moneda
 * (guaraníes: 0 decimales, así que 1 = 1 Gs.; dólares: 2 decimales, así que 100 = US$ 1,00).
 * Nunca se usa coma flotante para dinero.
 */
data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val fractionDigits: Int,
) {
    companion object {
        val PYG = CurrencyInfo(code = "PYG", symbol = "Gs.", fractionDigits = 0)
        val USD = CurrencyInfo(code = "USD", symbol = "US$", fractionDigits = 2)
        val EUR = CurrencyInfo(code = "EUR", symbol = "€", fractionDigits = 2)
        val ARS = CurrencyInfo(code = "ARS", symbol = "$", fractionDigits = 2)
    }
}

/** Formato de importes: puntos para miles y coma para decimales (estilo es-PY / es-AR). */
object MoneyFormatter {

    /** Ejemplo: `Gs. 4.250.000`, `-Gs. 1.200`, `US$ 1.234,56`. */
    fun format(
        minorUnits: Long,
        currency: CurrencyInfo = CurrencyInfo.PYG,
        showPlus: Boolean = false,
    ): String {
        val sign = when {
            minorUnits < 0 -> "-"
            showPlus && minorUnits > 0 -> "+"
            else -> ""
        }
        return "$sign${currency.symbol} ${formatMagnitude(minorUnits, currency)}"
    }

    /** Solo el número, sin símbolo de moneda. Ejemplo: `4.250.000`. */
    fun formatNumber(minorUnits: Long, currency: CurrencyInfo = CurrencyInfo.PYG): String {
        val sign = if (minorUnits < 0) "-" else ""
        return sign + formatMagnitude(minorUnits, currency)
    }

    private fun formatMagnitude(minorUnits: Long, currency: CurrencyInfo): String {
        val magnitude = abs(minorUnits)
        var divisor = 1L
        repeat(currency.fractionDigits) { divisor *= 10L }

        val grouped = groupThousands(magnitude / divisor)
        if (currency.fractionDigits == 0) return grouped

        val fraction = (magnitude % divisor).toString().padStart(currency.fractionDigits, '0')
        return "$grouped,$fraction"
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val result = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) result.append('.')
            result.append(char)
        }
        return result.toString()
    }
}
