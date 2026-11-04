package com.yuzufin.app.ui.navigation

import java.time.LocalDate

/**
 * Pedido de abrir el resumen semanal que llega desde fuera de la app: la notificación del domingo.
 *
 * La notificación lleva dos extras: [QuickLaunch.EXTRA_OPEN] = [OPEN_WEEKLY_SUMMARY] y
 * [EXTRA_SUMMARY_KEY] = la clave de deduplicación de esa semana (`weekly:<día>`). Con la clave se
 * busca en la campanilla el resumen que se guardó cuando se generó: no se recalcula nada.
 * Todo lo de acá es puro (sin Android) para poder probarlo.
 */
object SummaryLaunch {
    const val OPEN_WEEKLY_SUMMARY = "weekly_summary"
    const val EXTRA_SUMMARY_KEY = "summary_key"

    private const val KEY_PREFIX = "weekly:"
    private val KEY_FORMAT = Regex("^weekly:-?[0-9]{1,7}$")

    /** Solo se aceptan claves con el formato que genera `WeeklySummaryCalculator.dedupeKey`. */
    fun isValidKey(key: String?): Boolean = key != null && KEY_FORMAT.matches(key)

    /** Los extras que lleva la notificación; el mismo código los arma y los lee. */
    fun extras(key: String): Map<String, String> =
        mapOf(QuickLaunch.EXTRA_OPEN to OPEN_WEEKLY_SUMMARY, EXTRA_SUMMARY_KEY to key)

    /** La clave del resumen a abrir, o null si los extras no son un pedido válido de resumen. */
    fun requestFrom(open: String?, key: String?): String? =
        if (open == OPEN_WEEKLY_SUMMARY && isValidKey(key)) key else null

    /**
     * Dirección distinta por semana. Android compara los `PendingIntent` sin mirar los extras: sin
     * esto, la notificación de la semana anterior terminaría abriendo la semana nueva.
     */
    fun uriString(key: String): String = "monea://weekly-summary/" + key.removePrefix(KEY_PREFIX)

    /** Los 7 días que cubre el resumen (lunes a domingo), sacados de la clave. */
    fun weekRange(key: String): Pair<LocalDate, LocalDate>? {
        if (!isValidKey(key)) return null
        val day = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return null
        val sunday = LocalDate.ofEpochDay(day)
        return sunday.minusDays(6) to sunday
    }

    /** Si todavía no terminó la configuración inicial no hay resumen que mostrar: el pedido se descarta. */
    fun shouldDiscard(onboardingDone: Boolean): Boolean = !onboardingDone

    enum class Action {
        /** No hay pedido pendiente. */
        NONE,

        /** Hay pedido, pero la app todavía no está lista (ajustes o navegación sin cargar). */
        WAIT,

        /** Hay pedido pero no corresponde mostrarlo (falta la configuración inicial). */
        DISCARD,

        /** Hay que navegar a la campanilla, donde se muestra el resumen. */
        OPEN_NOTIFICATIONS,

        /** Ya está en la campanilla: la pantalla toma el pedido y abre el resumen. */
        SHOW_HERE,
    }

    /**
     * Qué hacer con un pedido pendiente. Sirve igual con la app cerrada (llega al arrancar), en
     * segundo plano o ya abierta (llega con la app andando, en cualquier pantalla).
     */
    fun decide(request: String?, onboardingDone: Boolean?, currentRoute: String?): Action = when {
        request == null -> Action.NONE
        onboardingDone == null -> Action.WAIT
        shouldDiscard(onboardingDone) -> Action.DISCARD
        currentRoute == null -> Action.WAIT
        currentRoute == Destination.Notifications.route -> Action.SHOW_HERE
        else -> Action.OPEN_NOTIFICATIONS
    }
}
