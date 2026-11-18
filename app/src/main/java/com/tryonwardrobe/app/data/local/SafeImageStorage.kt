package com.tryonwardrobe.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Offline-first image ingestion and sandbox persistence helper.
 * Copies content:// URIs safely in a single stream pass, performs memory-safe
 * inJustDecodeBounds + inSampleSize downsampling (<= 1280px), applies EXIF
 * rotation correction, and stores permanent files inside App private filesDir.
 */
class SafeImageStorage(private val context: Context) {

    companion object {
        const val MAX_IMAGE_EDGE_PX = 1280
    }

    private val wardrobeDir: File by lazy {
        File(context.filesDir, "wardrobe").apply { mkdirs() }
    }

    private val fullBodyDir: File by lazy {
        File(context.filesDir, "full_body").apply { mkdirs() }
    }

    private val tryOnCacheDir: File by lazy {
        File(context.filesDir, "try_on_cache").apply { mkdirs() }
    }

    private val tempIngestDir: File by lazy {
        File(context.cacheDir, "ingest_tmp").apply { mkdirs() }
    }

    suspend fun loadSafeBitmapFromUri(
        uri: Uri,
        maxEdge: Int = MAX_IMAGE_EDGE_PX
    ): Bitmap = withContext(Dispatchers.IO) {
        val tempFile = File(tempIngestDir, "uri_${UUID.randomUUID()}.img")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("無法讀取選取的圖片串流，請重新挑選圖片")
            input.use { src ->
                FileOutputStream(tempFile).use { dst ->
                    src.copyTo(dst)
                }
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalArgumentException("圖片內容為空或讀取失敗")
            }
            decodeAndOrientFile(tempFile, maxEdge)
                ?: throw IllegalArgumentException("無法解碼此圖片格式")
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun loadSafeBitmapFromFile(
        file: File,
        maxEdge: Int = MAX_IMAGE_EDGE_PX
    ): Bitmap = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("找不到拍攝的暫存影像檔案")
        }
        decodeAndOrientFile(file, maxEdge)
            ?: throw IllegalArgumentException("無法解碼拍攝的照片")
    }

    suspend fun saveFullBodyBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val normalized = scaleDownIfNeeded(bitmap, MAX_IMAGE_EDGE_PX, recycleOriginal = false)
        val outFile = File(fullBodyDir, "person_${UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use { out ->
            if (!normalized.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                error("全身照儲存壓縮失敗")
            }
        }
        if (normalized !== bitmap) {
            normalized.recycle()
        }
        outFile.absolutePath
    }

    suspend fun saveWardrobeBitmap(bitmap: Bitmap, preferPng: Boolean): String =
        withContext(Dispatchers.IO) {
            val normalized = scaleDownIfNeeded(bitmap, MAX_IMAGE_EDGE_PX, recycleOriginal = false)
            val usePng = preferPng && normalized.hasAlpha()
            val ext = if (usePng) "png" else "jpg"
            val outFile = File(wardrobeDir, "garment_${UUID.randomUUID()}.$ext")
            FileOutputStream(outFile).use { out ->
                val ok = if (usePng) {
                    normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    normalized.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                if (!ok) error("單品圖片儲存失敗")
            }
            if (normalized !== bitmap) {
                normalized.recycle()
            }
            outFile.absolutePath
        }

    suspend fun saveTryOnResultBitmap(cacheKey: String, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val outFile = File(
                tryOnCacheDir,
                "tryon_${cacheKey.take(16)}_${System.currentTimeMillis()}.jpg"
            )
            FileOutputStream(outFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    error("實穿成果存檔失敗")
                }
            }
            outFile.absolutePath
        }

    fun decodeStoredBitmap(path: String, maxEdge: Int = MAX_IMAGE_EDGE_PX): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return scaleDownIfNeeded(decoded, maxEdge, recycleOriginal = true)
    }

    private fun decodeAndOrientFile(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val upright = applyExifTransform(sampled, orientation)
        return scaleDownIfNeeded(upright, maxEdge, recycleOriginal = true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxEdge) {
            while ((longest / (inSampleSize * 2)) >= maxEdge) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }

    private fun applyExifTransform(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun scaleDownIfNeeded(
        bitmap: Bitmap,
        maxEdge: Int,
        recycleOriginal: Boolean
    ): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val targetW = max(1, (bitmap.width * ratio).roundToInt())
        val targetH = max(1, (bitmap.height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (recycleOriginal && scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
}
