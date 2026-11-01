package com.yuzufin.app.assistant

/**
 * Región con la que habla y se ve Monki, según la moneda configurada:
 * guaraníes → Paraguay, pesos argentinos → Argentina, el resto → neutral (sin camiseta ni modismos).
 */
enum class MonkiRegion {
    PARAGUAY, ARGENTINA, NEUTRAL;

    companion object {
        fun fromCurrency(code: String): MonkiRegion = when (code) {
            "PYG" -> PARAGUAY
            "ARS" -> ARGENTINA
            else -> NEUTRAL
        }
    }
}
