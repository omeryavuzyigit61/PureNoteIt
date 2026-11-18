package com.notes.purenoteit.desktop.data.storage

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes images across PNG, JPEG, WebP, GIF, and BMP safely using Skiko with ImageIO fallback,
 * and compresses large photos cleanly before encrypted storage & cloud sync to conserve Drive quota.
 */
object DesktopImageOptimizer {

    private const val MAX_EDGE_PX = 1600
    private const val JPEG_QUALITY = 82
    private const val SMALL_BYTES_THRESHOLD = 140 * 1024

    fun decodeComposeBitmap(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrElse {
            runCatching {
                ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
            }.getOrNull()
        }
    }

    fun optimizeForSync(
        rawBytes: ByteArray,
        extensionHint: String,
        isDrawing: Boolean = false
    ): Pair<ByteArray, String> {
        val cleanExt = extensionHint.trim().lowercase().trimStart('.').ifBlank { "png" }
        val normalizedExt = when (cleanExt) {
            "jpeg" -> "jpg"
            else -> cleanExt
        }
        if (rawBytes.isEmpty()) return rawBytes to normalizedExt

        val skiaImage = runCatching { Image.makeFromEncoded(rawBytes) }.getOrNull()
            ?: return rawBytes to normalizedExt

        val srcW = skiaImage.width
        val srcH = skiaImage.height
        if (srcW <= 0 || srcH <= 0) {
            skiaImage.close()
            return rawBytes to normalizedExt
        }

        val maxDim = max(srcW, srcH)
        if (maxDim <= MAX_EDGE_PX && rawBytes.size <= SMALL_BYTES_THRESHOLD) {
            skiaImage.close()
            return rawBytes to normalizedExt
        }

        return try {
            val scale = if (maxDim > MAX_EDGE_PX) MAX_EDGE_PX.toFloat() / maxDim.toFloat() else 1f
            val dstW = (srcW * scale).roundToInt().coerceAtLeast(1)
            val dstH = (srcH * scale).roundToInt().coerceAtLeast(1)

            val workingImage = if (scale < 0.999f) {
                val surface = Surface.makeRasterN32Premul(dstW, dstH)
                try {
                    val paint = Paint().apply { isAntiAlias = true }
                    surface.canvas.drawImageRect(
                        image = skiaImage,
                        src = Rect.makeWH(srcW.toFloat(), srcH.toFloat()),
                        dst = Rect.makeWH(dstW.toFloat(), dstH.toFloat()),
                        samplingMode = SamplingMode.MITCHELL,
                        paint = paint,
                        strict = true
                    )
                    paint.close()
                    surface.makeImageSnapshot()
                } finally {
                    surface.close()
                }
            } else {
                skiaImage
            }

            try {
                val keepPng = isDrawing || !workingImage.isOpaque
                val targetFormat = if (keepPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
                val targetQuality = if (keepPng) 92 else JPEG_QUALITY
                val encoded = workingImage.encodeToData(targetFormat, targetQuality)?.bytes
                if (encoded != null && encoded.isNotEmpty() && encoded.size < rawBytes.size) {
                    val finalExt = if (keepPng) "png" else "jpg"
                    encoded to finalExt
                } else {
                    rawBytes to normalizedExt
                }
            } finally {
                if (workingImage !== skiaImage) {
                    workingImage.close()
                }
            }
        } catch (_: Exception) {
            rawBytes to normalizedExt
        } finally {
            skiaImage.close()
        }
    }
}
