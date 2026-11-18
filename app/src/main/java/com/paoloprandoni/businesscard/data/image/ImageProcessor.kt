package com.paoloprandoni.businesscard.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Pipeline immagini: import con correzione EXIF, decode campionato (memoria controllata),
 * ritaglio del logo e compressione per la rubrica.
 */
class ImageProcessor {

    /**
     * Copia l'immagine scelta (fotocamera o galleria) nella cache dell'app, ne corregge
     * l'orientamento EXIF e la salva come JPEG "dritto": da qui in poi tutti i consumatori
     * (OCR, crop, anteprima) lavorano su coordinate coerenti.
     */
    suspend fun importImage(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bitmap = decodeSampled(resolver, uri, MAX_DECODE_DIMENSION) ?: return@withContext null
        val upright = applyExifOrientation(resolver, uri, bitmap)
        val file = File(context.cacheDir, "card_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(file).use { output ->
                upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_IMPORT, output)
            }
            true
        }.getOrDefault(false)
        if (upright !== bitmap) upright.recycle()
        bitmap.recycle()
        if (saved && file.length() > 0L) file else null
    }

    suspend fun decode(file: File, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }

    /** Ritaglia usando un rettangolo normalizzato (0..1) sull'immagine. */
    fun cropNormalized(bitmap: Bitmap, rect: RectF): Bitmap? {
        val left = (rect.left.coerceIn(0f, 1f) * bitmap.width).toInt()
        val top = (rect.top.coerceIn(0f, 1f) * bitmap.height).toInt()
        val right = (rect.right.coerceIn(0f, 1f) * bitmap.width).toInt()
        val bottom = (rect.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        val safeLeft = left.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
        val safeTop = top.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
        val safeWidth = width.coerceAtMost(bitmap.width - safeLeft)
        val safeHeight = height.coerceAtMost(bitmap.height - safeTop)
        if (safeWidth <= 0 || safeHeight <= 0) return null
        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
    }

    /** Salva il logo ritagliato ad alta risoluzione (la compressione avviene solo al salvataggio). */
    suspend fun saveLogo(context: Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "logo_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY_LOGO, output)
            }
            true
        }.getOrDefault(false)
        if (saved && file.length() > 0L) file else null
    }

    /**
     * Comprime la foto per il BLOB della rubrica: max 512×512 px, JPEG qualità 90,
     * con riduzione adattiva della qualità per restare sotto ~900 KB
     * (limite prudente per evitare TransactionTooLargeException).
     */
    fun compressForContact(bitmap: Bitmap): ByteArray {
        val scaled = scaleDown(bitmap, MAX_CONTACT_PHOTO_SIDE)
        var quality = CONTACT_PHOTO_QUALITY
        var bytes: ByteArray
        do {
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            bytes = output.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_CONTACT_PHOTO_BYTES && quality >= 40)
        if (scaled !== bitmap) scaled.recycle()
        return bytes
    }

    private fun scaleDown(source: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val ratio = maxSide.toFloat() / longest.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun decodeSampled(
        resolver: ContentResolver,
        uri: Uri,
        maxDimension: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }.getOrNull() ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    private fun applyExifOrientation(
        resolver: ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        const val MAX_DECODE_DIMENSION = 2400
        const val MAX_CONTACT_PHOTO_SIDE = 512
        const val CONTACT_PHOTO_QUALITY = 90
        const val MAX_CONTACT_PHOTO_BYTES = 900_000
        private const val JPEG_QUALITY_IMPORT = 95
        private const val JPEG_QUALITY_LOGO = 95
    }
}
