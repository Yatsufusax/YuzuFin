package com.yuzufin.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuzufin.app.core.money.CurrencyInfo
import com.yuzufin.app.domain.AppSettings
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

    companion object {
        const val MAX_NAME_LENGTH = 40
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val CURRENCY_CODE = stringPreferencesKey("currency_code")
        private val USER_NAME = stringPreferencesKey("user_name")
    }
}
