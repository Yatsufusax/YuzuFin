package com.yuzufin.app.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.onboarding.OnboardingScreen
import com.yuzufin.app.ui.splash.AnimatedSplash
import com.yuzufin.app.ui.theme.YuzuFinTheme

private enum class Stage { Splash, Onboarding, Main }

/**
 * Raíz de la app: splash animada → configuración inicial (solo la primera vez) →
 * estructura principal.
 */
@Composable
fun YuzuFinApp() {
    // rememberSaveable: al rotar la pantalla no se repite la splash.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val systemDark = isSystemInDarkTheme()
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val darkTheme = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Mientras las preferencias no se leyeron se sigue mostrando la splash (dura milisegundos).
    val loadedSettings = settings
    val stage = when {
        showSplash || loadedSettings == null -> Stage.Splash
        !loadedSettings.onboardingDone -> Stage.Onboarding
        else -> Stage.Main
    }

    YuzuFinTheme(darkTheme = darkTheme, palette = settings?.themePalette ?: ThemePalette.GREEN) {
        // La splash es oscura: iconos claros. En las demás pantallas, oscuros solo con tema claro.
        SystemBarIcons(darkIcons = stage != Stage.Splash && !darkTheme)

        Crossfade(
            targetState = stage,
            animationSpec = tween(450),
            label = "stage",
        ) { current ->
            when (current) {
                Stage.Splash -> AnimatedSplash(onFinished = { showSplash = false })
                Stage.Onboarding -> OnboardingScreen()
                Stage.Main -> MainShell()
            }
        }
    }
}

@Composable
private fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        controller.isAppearanceLightNavigationBars = darkIcons
    }
}
