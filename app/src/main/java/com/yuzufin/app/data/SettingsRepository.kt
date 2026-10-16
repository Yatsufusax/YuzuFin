package com.yuzufin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.AppSettings
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        )
    }

    suspend fun completeOnboarding(currency: CurrencyInfo, userName: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[CURRENCY_CODE] = currency.code
            prefs[USER_NAME] = userName.trim().take(MAX_NAME_LENGTH)
            prefs[ONBOARDING_DONE] = true
        }
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

    suspend fun setThemePalette(palette: ThemePalette) {
        context.settingsDataStore.edit { prefs -> prefs[THEME_PALETTE] = palette.name }
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
    }
}
