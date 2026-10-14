package com.yuzufin.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Descarga el APK de una actualización a la carpeta de caché de la app. */
class ApkDownloader(private val context: Context) {

    /** [onProgress] recibe valores de 0 a 1. Devuelve el archivo descargado. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "YuzuFin-${info.buildNumber}.apk")

            val connection = URL(info.downloadUrl).openConnection() as HttpURLConnection
            var completed = false
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "YuzuFin-Android")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Descarga rechazada (${connection.responseCode})")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                var downloaded = 0L
                var lastReported = -1

                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
                completed = true
                target
            } finally {
                connection.disconnect()
                if (!completed) target.delete()
            }
        }
}

/** Abre el instalador del sistema con el APK descargado. */
object ApkInstaller {

    /** Android exige que el usuario autorice a la app a instalar paquetes (una sola vez). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Instala si hay permiso; si no, lleva a la pantalla donde se concede. */
    fun installOrRequestPermission(context: Context, apk: File) {
        if (canInstall(context)) install(context, apk) else openInstallPermissionSettings(context)
    }
}
