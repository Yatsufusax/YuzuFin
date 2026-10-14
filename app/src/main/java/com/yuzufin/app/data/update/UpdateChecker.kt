package com.yuzufin.app.data.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Repositorio de GitHub donde el workflow publica las versiones. Cambiá esto si lo renombrás. */
object UpdateConfig {
    const val GITHUB_REPO = "Yatsufusax/YuzuFin"
}

/** Versión instalada en este teléfono. */
data class AppVersion(val code: Long, val name: String) {
    companion object {
        @Suppress("DEPRECATION")
        fun read(context: Context): AppVersion {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return AppVersion(
                code = PackageInfoCompat.getLongVersionCode(info),
                name = info.versionName.orEmpty(),
            )
        }
    }
}

/** Versión publicada en GitHub. [buildNumber] es lo que se compara con el `versionCode`. */
data class UpdateInfo(
    val versionName: String,
    val buildNumber: Long,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

/**
 * Consulta la última Release del repositorio. Es el único servicio externo de la app y no
 * envía ningún dato tuyo: es un GET público a api.github.com.
 */
class UpdateChecker(private val repo: String = UpdateConfig.GITHUB_REPO) {

    /** La última versión publicada, `null` si todavía no hay ninguna. Lanza [IOException] si falla la red. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "YuzuFin-Android")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK ->
                    parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub respondió $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val BUILD_TAG = Regex("^build-(\\d+)$")

        /** Las etiquetas de las Releases son `build-<número>`; devuelve ese número. */
        fun parseBuildNumber(tag: String): Long? =
            BUILD_TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toLongOrNull()

        /** Lee el JSON de una Release de GitHub. `null` si no tiene APK o no es una versión de la app. */
        fun parseRelease(json: String): UpdateInfo? {
            val root = JSONObject(json)
            val tag = root.optString("tag_name")
            val build = parseBuildNumber(tag) ?: return null
            val assets = root.optJSONArray("assets") ?: return null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    return UpdateInfo(
                        versionName = root.optString("name").ifBlank { tag },
                        buildNumber = build,
                        notes = root.optString("body").trim(),
                        downloadUrl = asset.getString("browser_download_url"),
                        sizeBytes = asset.optLong("size"),
                    )
                }
            }
            return null
        }
    }
}
