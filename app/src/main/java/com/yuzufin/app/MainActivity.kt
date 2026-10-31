package com.yuzufin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yuzufin.app.ui.MoneaApp
import com.yuzufin.app.ui.navigation.QuickLaunch

class MainActivity : ComponentActivity() {

    /** El widget "+ Gasto" pidió abrir directo el formulario de gasto. */
    private var openExpense by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash del sistema (solo fondo verde); la animación real es AnimatedSplash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Al rotar o restaurar la app no se vuelve a abrir el formulario.
        if (savedInstanceState == null) readLaunchRequest(intent)
        setContent {
            MoneaApp(openExpense = openExpense, onOpenExpenseHandled = { openExpense = false })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunchRequest(intent)
    }

    private fun readLaunchRequest(intent: Intent?) {
        if (intent?.getStringExtra(QuickLaunch.EXTRA_OPEN) == QuickLaunch.OPEN_EXPENSE) {
            openExpense = true
            // Se borra para que no se repita si la actividad se recrea.
            intent.removeExtra(QuickLaunch.EXTRA_OPEN)
        }
    }
}
