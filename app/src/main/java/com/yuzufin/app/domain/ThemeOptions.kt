package com.yuzufin.app.domain

/** Modo de la interfaz: sigue al teléfono, siempre claro o siempre oscuro. */
enum class ThemeMode { SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Paletas de color disponibles. Cada una tiene su versión clara y su versión oscura. */
enum class ThemePalette { GREEN, VIOLET, NAVY, RED, ORANGE, PINK, TEAL;

    companion object {
        fun fromName(name: String?): ThemePalette = entries.firstOrNull { it.name == name } ?: GREEN
    }
}
