package com.tryonwardrobe.app.service.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BackgroundRemover(private val context: Context) {

    data class SegmentationOutput(
        val cutoutBitmap: Bitmap,
        val foregroundDetected: Boolean
    )

    suspend fun removeBackground(uri: Uri): SegmentationOutput = withContext(Dispatchers.Default) {
        val decoded = decodeCorrectedBitmapFromUri(uri, minEdge = 512, maxEdge = 1440)
            ?: throw IllegalArgumentException("無法讀取選取的圖片檔案")
        removeBackground(decoded)
    }

    suspend fun removeBackground(sourceBitmap: Bitmap): SegmentationOutput =
        withContext(Dispatchers.Default) {
            val prepared = ensureMinAndMaxDimension(
                sourceBitmap,
                minEdge = 512,
                maxEdge = 1440,
                recycleOriginal = false
            )
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()

            val segmenter = SubjectSegmentation.getClient(options)
            try {
                ensureModuleInstalled(segmenter)
                val inputImage = InputImage.fromBitmap(prepared, 0)
                val result = segmenter.process(inputImage).await()
                val rawForeground = result.foregroundBitmap

                if (rawForeground != null && hasMeaningfulForeground(rawForeground)) {
                    val trimmed = trimTransparentBounds(rawForeground, paddingPx = 16)
                    SegmentationOutput(cutoutBitmap = trimmed, foregroundDetected = true)
                } else {
                    SegmentationOutput(cutoutBitmap = prepared, foregroundDetected = false)
                }
            } finally {
                runCatching { segmenter.close() }
            }
        }

    suspend fun prepareBasePersonPhoto(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        decodeCorrectedBitmapFromUri(uri, minEdge = 512, maxEdge = 1536)
            ?: throw IllegalArgumentException("無法讀取全身基準照")
    }

    suspend fun prepareBasePersonPhotoFromFile(file: File): Bitmap = withContext(Dispatchers.IO) {
        decodeCorrectedBitmapFromFile(file, minEdge = 512, maxEdge = 1536)
            ?: throw IllegalArgumentException("無法讀取拍攝的全身基準照")
    }

    fun decodeFileBitmap(path: String, maxEdge: Int = 1600): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private suspend fun ensureModuleInstalled(segmenter: SubjectSegmenter) {
        val moduleInstallClient = ModuleInstall.getClient(context)
        val availability = runCatching {
            moduleInstallClient.areModulesAvailable(segmenter).await()
        }.getOrNull()

        if (availability?.areModulesAvailable() == true) return

        val installRequest = ModuleInstallRequest.newBuilder()
            .addApi(segmenter)
            .build()

        runCatching {
            moduleInstallClient.installModules(installRequest).await()
        }
    }

    private fun decodeCorrectedBitmapFromUri(
        uri: Uri,
        minEdge: Int,
        maxEdge: Int
    ): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val upright = applyExifTransform(rawBitmap, orientation)
        return ensureMinAndMaxDimension(upright, minEdge = minEdge, maxEdge = maxEdge)
    }

    private fun decodeCorrectedBitmapFromFile(
        file: File,
        minEdge: Int,
        maxEdge: Int
    ): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val upright = applyExifTransform(rawBitmap, orientation)
        return ensureMinAndMaxDimension(upright, minEdge = minEdge, maxEdge = maxEdge)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        if (longest > maxEdge) {
            while ((longest / inSampleSize) > maxEdge * 2) {
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
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun ensureMinAndMaxDimension(
        bitmap: Bitmap,
        minEdge: Int,
        maxEdge: Int,
        recycleOriginal: Boolean = true
    ): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val shortest = min(bitmap.width, bitmap.height)

        val scale = when {
            longest > maxEdge -> maxEdge.toFloat() / longest.toFloat()
            shortest < minEdge -> minEdge.toFloat() / shortest.toFloat()
            else -> 1f
        }
        if (scale == 1f) return bitmap

        val targetW = max(1, (bitmap.width * scale).roundToInt())
        val targetH = max(1, (bitmap.height * scale).roundToInt())
        val resized = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (recycleOriginal && resized !== bitmap) bitmap.recycle()
        return resized
    }

    private fun hasMeaningfulForeground(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, min(w, h) / 64)
        var totalSamples = 0
        var opaqueSamples = 0

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                totalSamples++
                if (Color.alpha(bitmap.getPixel(x, y)) > 28) {
                    opaqueSamples++
                }
                x += step
            }
            y += step
        }
        return totalSamples > 0 && opaqueSamples.toFloat() / totalSamples.toFloat() > 0.015f
    }

    private fun trimTransparentBounds(bitmap: Bitmap, paddingPx: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 16) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX < minX || maxY < minY) return bitmap

        val cropW = (maxX - minX + 1)
        val cropH = (maxY - minY + 1)
        val cropped = Bitmap.createBitmap(bitmap, minX, minY, cropW, cropH)

        val padded = Bitmap.createBitmap(
            cropW + paddingPx * 2,
            cropH + paddingPx * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(padded)
        canvas.drawBitmap(cropped, paddingPx.toFloat(), paddingPx.toFloat(), null)
        if (cropped !== bitmap) cropped.recycle()
        return padded
    }
}
