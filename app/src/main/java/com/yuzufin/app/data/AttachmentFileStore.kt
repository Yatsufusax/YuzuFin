package com.yuzufin.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Archivos de las fotos adjuntas. Viven solo en la memoria privada de la app (ninguna otra app los
 * ve). Al importar se corrige la rotación, se reduce a [MAX_SIDE] px y se guarda como JPEG.
 */
class AttachmentFileStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    val directory: File get() = File(appContext.filesDir, DIR).apply { mkdirs() }

    /** Importa una foto desde [uri]. Devuelve el nombre del archivo guardado o null si no se pudo leer. */
    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            val longest = max(decoded.width, decoded.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                matrix.postScale(scale, scale)
            }
            val finalBitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val name = "${UUID.randomUUID()}.jpg"
            FileOutputStream(File(directory, name)).use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (finalBitmap !== decoded) decoded.recycle()
            finalBitmap.recycle()
            name
        } catch (e: Exception) {
            // Sin detalles en el log: la foto puede ser un comprobante con datos personales.
            null
        }
    }

    /** Archivo de una foto, solo si el nombre tiene el formato propio de la app (evita rutas raras). */
    fun file(name: String): File? = if (isValidName(name)) File(directory, name).takeIf { it.isFile } else null

    fun delete(names: List<String>) {
        names.forEach { name -> if (isValidName(name)) File(directory, name).delete() }
    }

    /** Foto reducida para miniaturas o pantalla completa (lado más largo aproximado [maxSide]). */
    suspend fun decode(name: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        val source = file(name) ?: return@withContext null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /** Igual que [decode], sin corrutinas: para el generador de PDF, que ya corre en un hilo de fondo. */
    fun decodeBlocking(name: String, maxSide: Int): Bitmap? {
        val source = file(name) ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

    companion object {
        const val DIR = "attachments"
        const val MAX_SIDE = 1600
        private const val QUALITY = 85
        private val NAME_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}
