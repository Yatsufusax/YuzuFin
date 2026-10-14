package com.yuzufin.app.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.yuzufin.app.reminders.NotificationHelper

/** Estado del permiso de notificaciones y cómo pedirlo. */
class NotificationPermissionState(
    val granted: Boolean,
    /** true si ya se pidió y Android no lo concedió: ahí solo queda abrir los ajustes del sistema. */
    val needsSettings: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = NotificationHelper.canNotify(context)
        denied = !granted
    }
    // Al volver de los ajustes del sistema se vuelve a comprobar.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = NotificationHelper.canNotify(context)
        if (granted) denied = false
    }

    return NotificationPermissionState(
        granted = granted,
        needsSettings = denied && !granted,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        openSettings = { openNotificationSettings(context) },
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
