package com.yuzufin.app.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yuzufin.app.MainActivity
import com.yuzufin.app.ui.navigation.SummaryLaunch

/** El `Intent` que abre el resumen semanal al tocar la notificación. */
object WeeklySummaryIntents {

    /**
     * Intent explícito a [MainActivity] con los extras del resumen de la semana [key]. Con la app
     * cerrada arranca la actividad; con la app abierta o en segundo plano llega a `onNewIntent`
     * (la actividad es `singleTop`). La dirección propia de cada semana evita que Android reutilice
     * el `PendingIntent` de otra semana.
     */
    fun open(context: Context, key: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse(SummaryLaunch.uriString(key))
            SummaryLaunch.extras(key).forEach { (name, value) -> putExtra(name, value) }
        }
}
