package com.yuzufin.app.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import com.yuzufin.app.domain.TextScale
import com.yuzufin.app.domain.ThemeMode
import com.yuzufin.app.domain.ThemePalette
import com.yuzufin.app.rememberAppContainer
import com.yuzufin.app.ui.navigation.QuickLaunch
import com.yuzufin.app.ui.onboarding.OnboardingScreen
import com.yuzufin.app.ui.common.ProvideTextScale
import com.yuzufin.app.ui.splash.VideoSplash
import com.yuzufin.app.ui.theme.MoneaTheme

private enum class Stage { Splash, Onboarding, Main }

/**
 * Raíz de la app: splash animada → configuración inicial (solo la primera vez) →
 * estructura principal.
 */
@Composable
fun MoneaApp(openExpense: Boolean = false, onOpenExpenseHandled: () -> Unit = {}) {
    // rememberSaveable: al rotar la pantalla no se repite la splash.
    // Si se abre desde el widget "+ Gasto" no hay intro: la persona quiere anotar ya.
    var showSplash by rememberSaveable { mutableStateOf(!openExpense) }
    val systemDark = isSystemInDarkTheme()
    val container = rememberAppContainer()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)

    // Cuántos días pasaron desde la última vez que se abrió la app (para el monito de Inicio).
    // Se calcula una sola vez por proceso: al rotar la pantalla no se pisa.
    LaunchedEffect(Unit) {
        if (!container.sessionInfo.checked) {
            container.sessionInfo.checked = true
            val today = LocalDate.now().toEpochDay()
            val previous = container.settingsRepository.markOpened(today)
            container.sessionInfo.daysAway = if (previous > 0L) (today - previous).toInt().coerceAtLeast(0) else 0
        }
    }
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

    // Sin configuración inicial, el widget solo abre la app normal: el pedido se descarta.
    LaunchedEffect(openExpense, loadedSettings?.onboardingDone) {
        val done = loadedSettings?.onboardingDone
        if (openExpense && done != null && QuickLaunch.shouldConsumeWithoutOpening(done)) onOpenExpenseHandled()
    }

    MoneaTheme(darkTheme = darkTheme, palette = settings?.themePalette ?: ThemePalette.GREEN) {
        // La splash es oscura: iconos claros. En las demás pantallas, oscuros solo con tema claro.
        SystemBarIcons(darkIcons = stage != Stage.Splash && !darkTheme)

        ProvideTextScale(settings?.textScale ?: TextScale.NORMAL) {
            Crossfade(
                targetState = stage,
                animationSpec = tween(450),
                label = "stage",
            ) { current ->
                when (current) {
                    Stage.Splash -> VideoSplash(onFinished = { showSplash = false })
                    Stage.Onboarding -> OnboardingScreen()
                    Stage.Main -> MainShell(openExpense = openExpense, onOpenExpenseHandled = onOpenExpenseHandled)
                }
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
