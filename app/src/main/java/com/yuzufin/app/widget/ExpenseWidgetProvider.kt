package com.yuzufin.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yuzufin.app.MainActivity
import com.yuzufin.app.R
import com.yuzufin.app.ui.navigation.QuickLaunch

/**
 * Widget "+ Gasto" de la pantalla de inicio del teléfono: un toque abre directo el formulario
 * para anotar un gasto. No guarda ni muestra ningún dato de la app.
 */
class ExpenseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(QuickLaunch.EXTRA_OPEN, QuickLaunch.OPEN_EXPENSE)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_expense).apply {
            setOnClickPendingIntent(R.id.widget_expense_root, pending)
        }
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    private companion object {
        const val REQUEST_CODE = 5001
    }
}
