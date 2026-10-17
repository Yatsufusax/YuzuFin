package com.yuzufin.app.domain

import com.yuzufin.app.core.money.CurrencyInfo
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Conversión de importes al cambiar de moneda. Todo con BigDecimal: nunca coma flotante.
 *
 * "newPerOld" = cuántas unidades de la moneda nueva equivalen a 1 unidad de la vieja.
 * Con newPerOld = 1 los números se mantienen tal cual (solo cambia la moneda y sus decimales).
 */
object CurrencyConversion {
    private val mc = MathContext(20)

    /**
     * Cómo se le pregunta el tipo de cambio a la persona: "1 [A] = X [B]". Si una de las dos monedas
     * es el guaraní, B es el guaraní (1 USD = 7.300 Gs.); si no, A es la moneda actual.
     */
    fun anchor(from: CurrencyInfo, to: CurrencyInfo): Pair<CurrencyInfo, CurrencyInfo> = when {
        to.code == CurrencyInfo.PYG.code -> from to to
        from.code == CurrencyInfo.PYG.code -> to to from
        else -> from to to
    }

    /** Pasa el valor escrito en "1 [A] = X [B]" a unidades de la moneda nueva por cada una de la vieja. */
    fun newPerOld(from: CurrencyInfo, to: CurrencyInfo, input: BigDecimal): BigDecimal {
        require(input.signum() > 0) { "El tipo de cambio debe ser mayor que cero" }
        val (a, _) = anchor(from, to)
        return if (a.code == from.code) input else BigDecimal.ONE.divide(input, mc)
    }

    /** Factor para multiplicar un importe en unidades mínimas de la moneda vieja. */
    fun factor(from: CurrencyInfo, to: CurrencyInfo, newPerOld: BigDecimal): BigDecimal =
        newPerOld.multiply(BigDecimal.TEN.pow(to.fractionDigits)).divide(BigDecimal.TEN.pow(from.fractionDigits), mc)

    /**
     * Convierte un importe. Redondea al más cercano; un importe distinto de cero nunca queda en cero
     * (si no, un gasto chico desaparecería).
     */
    fun convert(minor: Long, factor: BigDecimal): Long {
        if (minor == 0L) return 0L
        val result = BigDecimal(minor).multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong()
        return when {
            minor > 0L && result < 1L -> 1L
            minor < 0L && result > -1L -> -1L
            else -> result
        }
    }

    /** Interpreta lo escrito por la persona: dígitos y una sola coma decimal. null si no es válido o es cero. */
    fun parseRate(text: String): BigDecimal? {
        val normalized = text.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
        return normalized.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }
}
