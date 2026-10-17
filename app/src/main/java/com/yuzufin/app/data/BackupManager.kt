package com.yuzufin.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.yuzufin.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad: un .zip con la base de datos, los ajustes y un manifiesto.
 * No sale de la app por su cuenta: la persona elige dónde guardarla (Android le muestra su selector).
 * El archivo NO está cifrado: contiene todos tus datos, guardalo en un lugar de confianza.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val attachmentFiles: AttachmentFileStore,
) {

    enum class RestoreResult { OK, NOT_A_BACKUP, TOO_NEW, CORRUPT, FAILED }

    /** Devuelve el tamaño del archivo en bytes, o null si no se pudo guardar. */
    suspend fun export(target: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            val db = database.openHelper.writableDatabase
            // Pasa lo que está en el registro temporal (WAL) al archivo principal antes de copiarlo.
            db.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val manifest = JSONObject()
                .put("app", APP_ID)
                .put("format", FORMAT)
                .put("dbVersion", db.version)
                .put("createdAt", System.currentTimeMillis())
                .toString()
            val settingsJson = settings.exportJson()
            val photoNames = ArrayList<String>()
            db.query("SELECT fileName FROM attachments").use { cursor ->
                while (cursor.moveToNext()) photoNames += cursor.getString(0)
            }
            val output = context.contentResolver.openOutputStream(target, "w") ?: return@withContext null
            val counter = CountingOutputStream(output.buffered())
            counter.use { raw ->
                ZipOutputStream(raw).use { zip ->
                    zip.putText(MANIFEST, manifest)
                    zip.putText(SETTINGS, settingsJson)
                    zip.putNextEntry(ZipEntry(DATABASE))
                    FileInputStream(context.getDatabasePath(DB_NAME)).use { it.copyTo(zip) }
                    zip.closeEntry()
                    for (name in photoNames.distinct()) {
                        val photo = attachmentFiles.file(name) ?: continue
                        zip.putNextEntry(ZipEntry(PHOTOS_PREFIX + name))
                        FileInputStream(photo).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            counter.count
        } catch (e: Exception) {
            // Sin detalles en el log: no se escribe ningún dato financiero.
            null
        }
    }

    /**
     * Valida la copia y, si está bien, reemplaza la base y los ajustes actuales.
     * Después hay que reiniciar la app (la base abierta queda cerrada).
     */
    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "restore")
        try {
            work.deleteRecursively()
            work.mkdirs()
            val dbTemp = File(work, "restored.db")
            val photosTemp = File(work, "photos").apply { mkdirs() }
            var photoBytes = 0L
            var manifest: JSONObject? = null
            var settingsJson: String? = null
            var haveDb = false

            val input = context.contentResolver.openInputStream(source) ?: return@withContext RestoreResult.FAILED
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            MANIFEST -> manifest = runCatching { JSONObject(zip.readLimited(SMALL_LIMIT)) }.getOrNull()
                            SETTINGS -> settingsJson = zip.readLimited(SMALL_LIMIT)
                            DATABASE -> {
                                FileOutputStream(dbTemp).use { out -> zip.copyLimited(out, DB_LIMIT) }
                                haveDb = true
                            }
                            else -> if (entry.name.startsWith(PHOTOS_PREFIX)) {
                                val photoName = entry.name.removePrefix(PHOTOS_PREFIX)
                                if (attachmentFiles.isValidName(photoName)) {
                                    val target = File(photosTemp, photoName)
                                    FileOutputStream(target).use { out -> zip.copyLimited(out, PHOTO_LIMIT) }
                                    photoBytes += target.length()
                                    if (photoBytes > PHOTOS_TOTAL_LIMIT) throw java.io.IOException("Fotos demasiado grandes")
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val meta = manifest
            val prefsText = settingsJson
            if (meta == null || meta.optString("app") != APP_ID || !haveDb || prefsText == null) {
                return@withContext RestoreResult.NOT_A_BACKUP
            }
            runCatching { JSONObject(prefsText) }.getOrNull() ?: return@withContext RestoreResult.NOT_A_BACKUP
            val backupVersion = meta.optInt("dbVersion", -1)
            if (backupVersion < 1) return@withContext RestoreResult.NOT_A_BACKUP
            val currentVersion = database.openHelper.readableDatabase.version
            if (backupVersion > currentVersion) return@withContext RestoreResult.TOO_NEW
            if (!looksHealthy(dbTemp, backupVersion)) return@withContext RestoreResult.CORRUPT

            // Reemplazo: la base actual se guarda aparte hasta saber que la copia quedó bien puesta.
            database.close()
            val dbFile = context.getDatabasePath(DB_NAME)
            val previous = File(dbFile.path + ".pre-restore")
            previous.delete()
            if (dbFile.exists() && !dbFile.renameTo(previous)) return@withContext RestoreResult.FAILED
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            val photoDir = attachmentFiles.directory
            val restoredPhotos = photosTemp.listFiles()?.toList().orEmpty()
            val newlyCopied = ArrayList<File>()
            try {
                dbTemp.copyTo(dbFile, overwrite = true)
                // Las fotos restauradas se suman primero; las que sobran se limpian solo si todo salió bien.
                for (photo in restoredPhotos) {
                    val destination = File(photoDir, photo.name)
                    if (!destination.exists()) newlyCopied += destination
                    photo.copyTo(destination, overwrite = true)
                }
                settings.importJson(prefsText)
            } catch (e: Exception) {
                newlyCopied.forEach { it.delete() }
                dbFile.delete()
                previous.renameTo(dbFile)
                return@withContext RestoreResult.FAILED
            }
            val keep = restoredPhotos.map { it.name }.toSet()
            photoDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
            RestoreResult.OK
        } catch (e: Exception) {
            RestoreResult.FAILED
        } finally {
            work.deleteRecursively()
        }
    }

    /** Abre la copia solo para lectura y comprueba integridad, versión y las tablas principales. */
    private fun looksHealthy(file: File, expectedVersion: Int): Boolean = try {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { if (it.moveToFirst()) it.getString(0) else "" }
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('categories','transactions','initial_balance')",
                null,
            ).use { it.count }
            integrity == "ok" && tables == 3 && db.version == expectedVersion
        } finally {
            db.close()
        }
    } catch (e: Exception) {
        false
    }

    /** Cuenta los bytes escritos, para mostrar el tamaño de la copia. */
    private class CountingOutputStream(out: java.io.OutputStream) : java.io.FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun InputStream.readLimited(limit: Int): String {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            if (buffer.size() + read > limit) throw java.io.IOException("Entrada demasiado grande")
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.copyLimited(out: FileOutputStream, limit: Long) {
        val chunk = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) throw java.io.IOException("Archivo demasiado grande")
            out.write(chunk, 0, read)
        }
    }

    companion object {
        const val DB_NAME = "yuzufin.db"
        private const val APP_ID = "YuzuFin"
        private const val FORMAT = 1
        private const val MANIFEST = "manifest.json"
        private const val SETTINGS = "settings.json"
        private const val DATABASE = "yuzufin.db"
        private const val SMALL_LIMIT = 64 * 1024
        private const val DB_LIMIT = 512L * 1024 * 1024
        private const val PHOTOS_PREFIX = "attachments/"
        private const val PHOTO_LIMIT = 30L * 1024 * 1024
        private const val PHOTOS_TOTAL_LIMIT = 2L * 1024 * 1024 * 1024
    }
}
