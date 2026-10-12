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
import com.yuzufin.app.ui.splash.AnimatedSplash
import com.yuzufin.app.ui.theme.YuzuFinTheme

/** Raíz de la app: splash animada y, al terminar, la estructura principal. */
@Composable
fun YuzuFinApp() {
    // rememberSaveable: al rotar la pantalla no se repite la splash.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val darkTheme = isSystemInDarkTheme()

    YuzuFinTheme(darkTheme = darkTheme) {
        // La splash es oscura: iconos claros. Luego, iconos oscuros solo en tema claro.
        SystemBarIcons(darkIcons = !showSplash && !darkTheme)

        Crossfade(
            targetState = showSplash,
            animationSpec = tween(450),
            label = "splash",
        ) { splashVisible ->
            if (splashVisible) {
                AnimatedSplash(onFinished = { showSplash = false })
            } else {
                MainShell()
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
