package com.yuzufin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.AppSettings
import com.yuzufin.app.domain.TextScale
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Preferencias de la app (moneda elegida y si ya se hizo la configuración inicial). */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            currencyCode = prefs[CURRENCY_CODE] ?: CurrencyInfo.PYG.code,
            userName = prefs[USER_NAME] ?: "",
            remindersEnabled = prefs[REMINDERS_ENABLED] ?: true,
            reminderHour = (prefs[REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR).coerceIn(0, 23),
            showAmountsInNotifications = prefs[SHOW_AMOUNTS] ?: false,
            themePalette = ThemePalette.fromName(prefs[THEME_PALETTE]),
            themeMode = ThemeMode.fromName(prefs[THEME_MODE]),
            dismissedHints = prefs[DISMISSED_HINTS] ?: emptySet(),
            textScale = TextScale.fromName(prefs[TEXT_SCALE]),
            tourPending = prefs[TOUR_PENDING] ?: false,
        )
    }

    suspend fun completeOnboarding(currency: CurrencyInfo, userName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[CURRENCY_CODE] = currency.code
            prefs[USER_NAME] = userName.trim().take(MAX_NAME_LENGTH)
            prefs[ONBOARDING_DONE] = true
            // Solo quien acaba de configurar la app ve el recorrido; quien restaura una copia, no.
            prefs[TOUR_PENDING] = true
        }
    }

    /** El recorrido del primer uso ya se vio (o se salteó): no vuelve a aparecer solo. */
    suspend fun finishTour() {
        context.settingsDataStore.edit { prefs -> prefs[TOUR_PENDING] = false }
    }

    /** Pide que el recorrido se muestre otra vez (botón de Ayuda). */
    suspend fun restartTour() {
        context.settingsDataStore.edit { prefs -> prefs[TOUR_PENDING] = true }
    }

    suspend fun setUserName(name: String) {
        context.settingsDataStore.edit { prefs -> prefs[USER_NAME] = name.trim().take(MAX_NAME_LENGTH) }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[REMINDERS_ENABLED] = enabled }
    }

    suspend fun setReminderHour(hour: Int) {
        context.settingsDataStore.edit { prefs -> prefs[REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun setShowAmountsInNotifications(show: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[SHOW_AMOUNTS] = show }
    }

    suspend fun setCurrencyCode(code: String) {
        context.settingsDataStore.edit { prefs -> prefs[CURRENCY_CODE] = CurrencyInfo.fromCode(code).code }
    }

    /** Todas las preferencias como JSON, para incluirlas en la copia de seguridad. */
    suspend fun exportJson(): String {
        val prefs = context.settingsDataStore.data.first()
        return JSONObject().apply {
            put("onboardingDone", prefs[ONBOARDING_DONE] ?: false)
            put("currencyCode", prefs[CURRENCY_CODE] ?: CurrencyInfo.PYG.code)
            put("userName", prefs[USER_NAME] ?: "")
            put("remindersEnabled", prefs[REMINDERS_ENABLED] ?: true)
            put("reminderHour", prefs[REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR)
            put("showAmounts", prefs[SHOW_AMOUNTS] ?: false)
            put("themePalette", prefs[THEME_PALETTE] ?: ThemePalette.GREEN.name)
            put("themeMode", prefs[THEME_MODE] ?: ThemeMode.SYSTEM.name)
            put("textScale", prefs[TEXT_SCALE] ?: TextScale.NORMAL.name)
        }.toString()
    }

    /** Restaura las preferencias de una copia. Los valores se validan igual que al leerlos normalmente. */
    suspend fun importJson(text: String) {
        val json = JSONObject(text)
        context.settingsDataStore.edit { prefs ->
            // Una copia con datos implica que la configuración inicial ya se hizo.
            prefs[ONBOARDING_DONE] = json.optBoolean("onboardingDone", true)
            prefs[CURRENCY_CODE] = CurrencyInfo.fromCode(json.optString("currencyCode")).code
            prefs[USER_NAME] = json.optString("userName").trim().take(MAX_NAME_LENGTH)
            prefs[REMINDERS_ENABLED] = json.optBoolean("remindersEnabled", true)
            prefs[REMINDER_HOUR] = json.optInt("reminderHour", DEFAULT_REMINDER_HOUR).coerceIn(0, 23)
            prefs[SHOW_AMOUNTS] = json.optBoolean("showAmounts", false)
            prefs[THEME_PALETTE] = ThemePalette.fromName(json.optString("themePalette")).name
            prefs[THEME_MODE] = ThemeMode.fromName(json.optString("themeMode")).name
            prefs[TEXT_SCALE] = TextScale.fromName(json.optString("textScale")).name
        }
    }

    /** Marca un consejo del monito como visto, para que no vuelva a aparecer. */
    suspend fun dismissHint(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[DISMISSED_HINTS] = (prefs[DISMISSED_HINTS] ?: emptySet()) + id }
    }

    /** Vuelve a mostrar todos los consejos. */
    suspend fun resetHints() {
        context.settingsDataStore.edit { prefs -> prefs.remove(DISMISSED_HINTS) }
    }

    /**
     * Guarda el día (epoch day) en que se abrió la app y devuelve el día de la vez anterior,
     * o 0 si es la primera. Sirve para saber cuántos días pasaron sin entrar.
     */
    suspend fun markOpened(epochDay: Long): Long {
        var previous = 0L
        context.settingsDataStore.edit { prefs ->
            previous = prefs[LAST_OPEN_DAY] ?: 0L
            prefs[LAST_OPEN_DAY] = epochDay
        }
        return previous
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        context.settingsDataStore.edit { prefs -> prefs[THEME_PALETTE] = palette.name }
    }

    suspend fun setTextScale(scale: TextScale) {
        context.settingsDataStore.edit { prefs -> prefs[TEXT_SCALE] = scale.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
        const val DEFAULT_REMINDER_HOUR = 9
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val CURRENCY_CODE = stringPreferencesKey("currency_code")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        private val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val SHOW_AMOUNTS = booleanPreferencesKey("show_amounts_in_notifications")
        private val THEME_PALETTE = stringPreferencesKey("theme_palette")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val TEXT_SCALE = stringPreferencesKey("text_scale")
        private val TOUR_PENDING = booleanPreferencesKey("tour_pending")
        private val DISMISSED_HINTS = stringSetPreferencesKey("dismissed_hints")
        private val LAST_OPEN_DAY = longPreferencesKey("last_open_day")
    }
}
