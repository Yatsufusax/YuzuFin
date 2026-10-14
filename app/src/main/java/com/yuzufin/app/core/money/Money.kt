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
        val ARS = CurrencyInfo(code = "ARS", symbol = "$", fractionDigits = 2)
        val BRL = CurrencyInfo(code = "BRL", symbol = "R$", fractionDigits = 2)
        val EUR = CurrencyInfo(code = "EUR", symbol = "€", fractionDigits = 2)

        /** Monedas que se pueden elegir en la configuración inicial. */
        val supported: List<CurrencyInfo> = listOf(PYG, USD, ARS, BRL, EUR)

        /** Busca por código; si no existe (o es nulo) devuelve guaraníes. */
        fun fromCode(code: String?): CurrencyInfo =
            supported.firstOrNull { it.code == code } ?: PYG
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

/** Convierte lo que escribe la persona en un importe entero, sin pasar por decimales binarios. */
object AmountParser {

    private const val MAX_INTEGER_DIGITS = 12

    /**
     * Deja solo lo que tiene sentido en un campo de importe: dígitos y, si la moneda
     * tiene decimales, un único separador decimal (se muestra siempre como coma).
     * En monedas sin decimales (guaraníes) se ignoran los puntos y comas, así que
     * pegar `1.200.000` funciona.
     */
    fun sanitize(raw: String, currency: CurrencyInfo): String {
        val allowed = raw.filter { it in '0'..'9' || it == ',' || it == '.' }
        if (currency.fractionDigits == 0) {
            return allowed.filter { it in '0'..'9' }.take(MAX_INTEGER_DIGITS)
        }
        val separatorIndex = allowed.indexOfLast { it == ',' || it == '.' }
        if (separatorIndex < 0) return allowed.take(MAX_INTEGER_DIGITS)

        val whole = allowed.substring(0, separatorIndex).filter { it in '0'..'9' }.take(MAX_INTEGER_DIGITS)
        val fraction = allowed.substring(separatorIndex + 1).filter { it in '0'..'9' }
            .take(currency.fractionDigits)
        return "$whole,$fraction"
    }

    /** Importe en unidades mínimas, o `null` si el texto está vacío o no es válido. */
    fun parseToMinorUnits(text: String, currency: CurrencyInfo): Long? {
        val clean = sanitize(text, currency)
        if (clean.isEmpty()) return null

        val parts = clean.split(',')
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null

        var multiplier = 1L
        repeat(currency.fractionDigits) { multiplier *= 10L }
        if (currency.fractionDigits == 0) return whole

        val fractionText = (parts.getOrNull(1) ?: "").padEnd(currency.fractionDigits, '0')
        val fraction = fractionText.toLongOrNull() ?: return null
        return whole * multiplier + fraction
    }

    /** Texto para volver a mostrar un importe guardado dentro del campo al editar. */
    fun toInputText(minorUnits: Long, currency: CurrencyInfo): String {
        var divisor = 1L
        repeat(currency.fractionDigits) { divisor *= 10L }
        val whole = minorUnits / divisor
        if (currency.fractionDigits == 0) return whole.toString()
        val fraction = (minorUnits % divisor).toString().padStart(currency.fractionDigits, '0')
        return "$whole,$fraction"
    }
}
