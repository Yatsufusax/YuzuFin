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

    /**
     * Número de la última Release, leído de la redirección de `github.com/<repo>/releases/latest`.
     * Es una consulta liviana que no cuenta contra el límite de la API (60 por hora), así que
     * se puede repetir cada minuto. `null` si todavía no hay ninguna Release.
     */
    suspend fun latestBuildNumber(): Long? = withContext(Dispatchers.IO) {
        val connection = URL("https://github.com/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "YuzuFin-Android")
            when (val code = connection.responseCode) {
                301, 302, 303, 307, 308 ->
                    connection.getHeaderField("Location")?.let(::parseBuildFromLocation)
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub respondió $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Datos completos (novedades y APK) de la Release [build]. Si la API no responde (por ejemplo
     * por el límite de consultas), arma la dirección del APK con el nombre que publica el workflow.
     */
    suspend fun detailsFor(build: Long, currentVersionName: String): UpdateInfo {
        val fromApi = try {
            fetchLatest()
        } catch (e: IOException) {
            null
        }
        return fromApi?.takeIf { it.buildNumber >= build } ?: fallbackInfo(repo, currentVersionName, build)
    }

    companion object {
        private val BUILD_TAG = Regex("^build-(\\d+)$")

        /** Las etiquetas de las Releases son `build-<número>`; devuelve ese número. */
        fun parseBuildNumber(tag: String): Long? =
            BUILD_TAG.matchEntire(tag.trim())?.groupValues?.get(1)?.toLongOrNull()

        /** `https://github.com/<repo>/releases/tag/build-12` → 12. */
        fun parseBuildFromLocation(location: String): Long? {
            if (!location.contains("/releases/tag/")) return null
            return parseBuildNumber(location.trimEnd('/').substringAfterLast('/'))
        }

        /** Versión `0.2.7` a partir de la instalada (`0.2.3`) y el número de compilación. */
        fun fallbackInfo(repo: String, currentVersionName: String, build: Long): UpdateInfo {
            val name = currentVersionName.substringBeforeLast('.', "0.2") + ".$build"
            return UpdateInfo(
                versionName = name,
                buildNumber = build,
                notes = "",
                downloadUrl = "https://github.com/$repo/releases/download/build-$build/YuzuFin-$name.apk",
                sizeBytes = 0L,
            )
        }

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
