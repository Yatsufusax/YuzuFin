package com.yuzufin.app.assistant

/**
 * Región con la que habla Monki: la preferencia de la persona si eligió una, si no la de la moneda.
 * Hoy siempre cae en la moneda (`null`); para activar el selector en Ajustes, guardar la elección en
 * DataStore (clave sugerida: `monki_voice` = AUTO / PY / AR / NEUTRAL, incluida en la copia de
 * seguridad) y pasarla acá. Un solo punto de resolución: el chat y los mensajes proactivos siempre
 * hablan con la misma voz.
 */
object MonkiRegionResolver {
    fun resolve(userRegion: MonkiRegion?, currencyCode: String): MonkiRegion =
        userRegion ?: MonkiRegion.fromCurrency(currencyCode)
}
