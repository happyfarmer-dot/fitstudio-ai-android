package com.tryonwardrobe.app.service.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.tryonwardrobe.app.data.local.SafeImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offline ML Kit Subject Segmentation wrapper.
 * Accepts already-sanitized Bitmaps from SafeImageStorage and wraps segmentation in runCatching
 * so local garment saving never fails if ML Kit is offline or detects no foreground.
 */
class BackgroundRemover(private val context: Context) {

    data class SegmentationOutput(
        val cutoutBitmap: Bitmap,
        val foregroundDetected: Boolean
    )

    suspend fun removeBackgroundWithFallback(sourceBitmap: Bitmap): SegmentationOutput =
        withContext(Dispatchers.Default) {
            runCatching {
                segmentForeground(sourceBitmap)
            }.getOrElse {
                SegmentationOutput(
                    cutoutBitmap = sourceBitmap,
                    foregroundDetected = false
                )
            }
        }

    private suspend fun segmentForeground(sourceBitmap: Bitmap): SegmentationOutput {
        // ML Kit Subject Segmentation works best when shortest side is >= 512px
        val mlInput = ensureMinDimensionForInference(
            sourceBitmap,
            minEdge = 512,
            maxEdge = SafeImageStorage.MAX_IMAGE_EDGE_PX
        )
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()

        val segmenter = SubjectSegmentation.getClient(options)
        try {
            ensureModuleInstalled(segmenter)
            val inputImage = InputImage.fromBitmap(mlInput, 0)
            val result = segmenter.process(inputImage).await()
            val rawForeground = result.foregroundBitmap

            return if (rawForeground != null && hasMeaningfulForeground(rawForeground)) {
                val trimmed = trimTransparentBounds(rawForeground, paddingPx = 16)
                val bounded = capMaxEdge(trimmed, SafeImageStorage.MAX_IMAGE_EDGE_PX)
                SegmentationOutput(cutoutBitmap = bounded, foregroundDetected = true)
            } else {
                SegmentationOutput(cutoutBitmap = sourceBitmap, foregroundDetected = false)
            }
        } finally {
            runCatching { segmenter.close() }
        }
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

    private fun ensureMinDimensionForInference(
        bitmap: Bitmap,
        minEdge: Int,
        maxEdge: Int
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
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun capMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val targetW = max(1, (bitmap.width * ratio).roundToInt())
        val targetH = max(1, (bitmap.height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
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
