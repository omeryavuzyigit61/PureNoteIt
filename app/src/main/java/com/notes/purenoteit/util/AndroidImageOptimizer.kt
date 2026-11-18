package com.notes.purenoteit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Downscales large smartphone camera images (> 1600 px) and applies high-efficiency compression
 * cleanly before canonical content-addressed storage to conserve Google Drive quota.
 */
object AndroidImageOptimizer {

    private const val MAX_EDGE_PX = 1600
    private const val JPEG_QUALITY = 82
    private const val SMALL_THRESHOLD_BYTES = 140 * 1024

    fun optimizeStagingImageFile(stagingFile: File) {
        if (!stagingFile.exists() || !stagingFile.isFile) return
        val originalSize = stagingFile.length()
        if (originalSize <= 0L) return

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stagingFile.absolutePath, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return

        val maxDim = max(srcW, srcH)
        if (maxDim <= MAX_EDGE_PX && originalSize <= SMALL_THRESHOLD_BYTES) return

        var sample = 1
        while ((srcW / sample) > MAX_EDGE_PX * 2 || (srcH / sample) > MAX_EDGE_PX * 2) {
            sample *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(stagingFile.absolutePath, decodeOpts) ?: return
        var scaled: Bitmap? = null
        try {
            val w = decoded.width
            val h = decoded.height
            val curMax = max(w, h)
            val targetBitmap = if (curMax > MAX_EDGE_PX) {
                val ratio = MAX_EDGE_PX.toFloat() / curMax.toFloat()
                val dstW = (w * ratio).roundToInt().coerceAtLeast(1)
                val dstH = (h * ratio).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, dstW, dstH, true).also { scaled = it }
            } else {
                decoded
            }

            val format = if (targetBitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (format == Bitmap.CompressFormat.PNG) 92 else JPEG_QUALITY
            val baos = ByteArrayOutputStream()
            if (targetBitmap.compress(format, quality, baos)) {
                val outBytes = baos.toByteArray()
                if (outBytes.isNotEmpty() && outBytes.size < originalSize) {
                    stagingFile.writeBytes(outBytes)
                }
            }
        } catch (_: Exception) {
        } finally {
            scaled?.let { if (it !== decoded && !it.isRecycled) it.recycle() }
            if (!decoded.isRecycled) decoded.recycle()
        }
    }
}
