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
import com.yuzufin.app.ui.navigation.SummaryLaunch

class MainActivity : ComponentActivity() {

    /** El widget "+ Gasto" pidió abrir directo el formulario de gasto. */
    private var openExpense by mutableStateOf(false)

    /** La notificación del resumen semanal pidió abrir el resumen de esta semana (clave de deduplicación). */
    private var openSummaryKey by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash del sistema (solo fondo verde); la animación real es AnimatedSplash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Al rotar o restaurar la app no se vuelve a abrir el formulario.
        if (savedInstanceState == null) readLaunchRequest(intent)
        setContent {
            MoneaApp(
                openExpense = openExpense,
                onOpenExpenseHandled = { openExpense = false },
                openSummaryKey = openSummaryKey,
                onSummaryHandled = { openSummaryKey = null },
            )
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
        // Notificación del resumen semanal: llega por onCreate (app cerrada) o por onNewIntent (abierta o en segundo plano).
        val summaryKey = SummaryLaunch.requestFrom(
            intent?.getStringExtra(QuickLaunch.EXTRA_OPEN),
            intent?.getStringExtra(SummaryLaunch.EXTRA_SUMMARY_KEY),
        )
        if (summaryKey != null) {
            openSummaryKey = summaryKey
            // Se borran para que no se repita si la actividad se recrea (rotación).
            intent?.removeExtra(QuickLaunch.EXTRA_OPEN)
            intent?.removeExtra(SummaryLaunch.EXTRA_SUMMARY_KEY)
        }
    }
}
