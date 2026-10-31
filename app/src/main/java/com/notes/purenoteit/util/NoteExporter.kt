package com.notes.purenoteit.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import androidx.core.content.FileProvider
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfExportConfig(
    val includeText: Boolean = true,
    val includeImages: Boolean = true,
    val includeDrawings: Boolean = true,
    val includeTasks: Boolean = true,
    val includeAudioBadge: Boolean = true,
    val includeHeader: Boolean = true
)

object NoteExporter {

    /**
     * Converts note blocks into GitHub-Flavored Markdown and triggers the Android Share Sheet.
     */
    fun shareAsMarkdown(context: Context, title: String, blocks: List<NoteBlock>) {
        val sb = StringBuilder()
        sb.append("# ").append(if (title.isBlank()) "Başlıksız Not" else title).append("\n\n")

        for (block in blocks) {
            when (block.type) {
                BlockType.HEADING_1 -> sb.append("# ").append(block.content).append("\n\n")
                BlockType.HEADING_2 -> sb.append("## ").append(block.content).append("\n\n")
                BlockType.TODO -> {
                    val check = if (block.isChecked) "[x]" else "[ ]"
                    sb.append("- ").append(check).append(" ").append(block.content).append("\n")
                }
                BlockType.BULLET_LIST -> sb.append("- ").append(block.content).append("\n")
                BlockType.CALLOUT -> sb.append("> ").append(block.calloutIcon).append(" ").append(block.content).append("\n\n")
                BlockType.TOGGLE -> {
                    sb.append("<details>\n<summary>").append(block.content).append("</summary>\n")
                    sb.append(block.toggleBody).append("\n</details>\n\n")
                }
                BlockType.TABLE -> {
                    if (block.tableData.isNotEmpty()) {
                        val header = block.tableData[0]
                        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
                        sb.append("| ").append(header.map { "---" }.joinToString(" | ")).append(" |\n")
                        for (i in 1..block.tableData.lastIndex) {
                            val row = block.tableData[i]
                            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                        }
                        sb.append("\n")
                    }
                }
                BlockType.IMAGE -> {
                    sb.append("![Görsel](${block.mediaPath ?: ""})\n\n")
                }
                BlockType.DRAWING -> {
                    sb.append("![Çizim](${block.mediaPath ?: ""})\n\n")
                }
                BlockType.AUDIO -> {
                    sb.append("🎙️ [Ses Kaydı Eki: ${block.mediaPath?.substringAfterLast("/") ?: "ses.mp4"}]\n\n")
                }
                BlockType.DIVIDER -> sb.append("\n---\n\n")
                else -> {
                    if (block.content.isNotBlank()) {
                        sb.append(block.content).append("\n\n")
                    }
                }
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(shareIntent, "Markdown Notunu Paylaş"))
    }

    /**
     * Inspects EXIF orientation metadata and rotates the bitmap so it is upright.
     */
    private fun rotateBitmapIfRequired(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Safely decodes a bitmap from disk with appropriate sampling and EXIF rotation to prevent OOM and orientation bugs.
     */
    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var isSwapped = false
            try {
                val exif = ExifInterface(path)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                isSwapped = (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270)
            } catch (ignored: Exception) {}

            val rawWidth = if (isSwapped) options.outHeight else options.outWidth
            val rawHeight = if (isSwapped) options.outWidth else options.outHeight

            var inSampleSize = 1
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
            rotateBitmapIfRequired(decoded, path)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a multi-page, publication-quality A4 PDF with real images, drawings, audio badges,
     * tables and checklists, filtered according to the user's PdfExportConfig.
     */
    fun shareAsPdf(
        context: Context,
        title: String,
        blocks: List<NoteBlock>,
        categoryName: String = "",
        config: PdfExportConfig = PdfExportConfig()
    ) {
        val safeTitle = if (title.isBlank()) "PureNoteIt_Not" else title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(exportDir, "${safeTitle}.pdf")

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginX = 40f
        val contentWidth = 515f
        val maxPageY = 780f

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Paints
        val brandPaint = Paint().apply {
            color = Color.parseColor("#E65100") // PureNoteIt VibrantOrange
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val datePaint = Paint().apply {
            color = Color.parseColor("#757575")
            textSize = 10f
            isAntiAlias = true
        }

        val categoryTagPaint = Paint().apply {
            color = Color.parseColor("#E65100")
            textSize = 9.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val categoryTagBgPaint = Paint().apply {
            color = Color.parseColor("#FFF3E0")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 12f
            isAntiAlias = true
        }

        val todoDonePaint = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 12f
            isStrikeThruText = true
            isAntiAlias = true
        }

        val h1Paint = Paint().apply {
            color = Color.parseColor("#1C1C1E")
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val h2Paint = Paint().apply {
            color = Color.parseColor("#2C2C2E")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val calloutPaint = Paint().apply {
            color = Color.parseColor("#BF360C")
            textSize = 11.5f
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }

        val pageNumberPaint = Paint().apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 9.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val audioBgPaint = Paint().apply {
            color = Color.parseColor("#FFF8E1")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val audioBorderPaint = Paint().apply {
            color = Color.parseColor("#FFE082")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        val audioTitlePaint = Paint().apply {
            color = Color.parseColor("#F57F17")
            textSize = 11.5f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val audioSubPaint = Paint().apply {
            color = Color.parseColor("#795548")
            textSize = 9.5f
            isAntiAlias = true
        }

        val imageBorderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        var y = 50f

        fun ensurePageSpace(neededHeight: Float) {
            if (y + neededHeight > maxPageY) {
                // Finish current page
                canvas.drawText("Sayfa $pageNumber", pageWidth / 2f, 815f, pageNumberPaint)
                pdfDocument.finishPage(page)

                // Start next page
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 45f

                // Small running header
                canvas.drawText("PureNoteIt • $title", marginX, y, datePaint)
                y += 8f
                canvas.drawLine(marginX, y, marginX + contentWidth, y, dividerPaint)
                y += 22f
            }
        }

        // 1. Document Header (if enabled)
        if (config.includeHeader) {
            val displayTitle = if (title.isBlank()) "Başlıksız Not" else title
            canvas.drawText(displayTitle, marginX, y, brandPaint)
            y += 18f

            val todayStr = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr")).format(Date())
            canvas.drawText("Oluşturulma: $todayStr", marginX, y, datePaint)

            if (categoryName.isNotBlank() && categoryName != "Tümü") {
                val tagText = " $categoryName "
                val tagWidth = categoryTagPaint.measureText(tagText) + 8f
                val tagRect = RectF(marginX + 200f, y - 10f, marginX + 200f + tagWidth, y + 3f)
                canvas.drawRoundRect(tagRect, 4f, 4f, categoryTagBgPaint)
                canvas.drawText(tagText, marginX + 204f, y - 1f, categoryTagPaint)
            }

            y += 12f
            canvas.drawLine(marginX, y, marginX + contentWidth, y, dividerPaint)
            y += 24f
        }

        // 2. Iterate and draw filtered blocks
        for (block in blocks) {
            when (block.type) {
                BlockType.HEADING_1 -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        ensurePageSpace(32f)
                        y += 6f
                        canvas.drawText(block.content, marginX, y, h1Paint)
                        y += 24f
                    }
                }

                BlockType.HEADING_2 -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        ensurePageSpace(28f)
                        y += 4f
                        canvas.drawText(block.content, marginX, y, h2Paint)
                        y += 20f
                    }
                }

                BlockType.TODO -> {
                    if (config.includeTasks && block.content.isNotBlank()) {
                        ensurePageSpace(22f)
                        val mark = if (block.isChecked) "[✓]" else "[  ]"
                        val paint = if (block.isChecked) todoDonePaint else bodyPaint
                        canvas.drawText("$mark  ${block.content}", marginX, y, paint)
                        y += 18f
                    }
                }

                BlockType.BULLET_LIST -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        ensurePageSpace(20f)
                        canvas.drawText("•   ${block.content}", marginX + 6f, y, bodyPaint)
                        y += 18f
                    }
                }

                BlockType.CALLOUT -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        ensurePageSpace(26f)
                        canvas.drawText("${block.calloutIcon}   ${block.content}", marginX, y, calloutPaint)
                        y += 20f
                    }
                }

                BlockType.TOGGLE -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        ensurePageSpace(24f)
                        canvas.drawText("▸  ${block.content}", marginX, y, h2Paint)
                        y += 18f
                        if (block.toggleBody.isNotBlank()) {
                            ensurePageSpace(20f)
                            canvas.drawText("    ${block.toggleBody}", marginX, y, bodyPaint)
                            y += 18f
                        }
                    }
                }

                BlockType.IMAGE -> {
                    val imgPath = block.mediaPath
                    if (config.includeImages && !imgPath.isNullOrBlank()) {
                        val bitmap = decodeSampledBitmap(imgPath, 515, 420)
                        if (bitmap != null) {
                            val bitmapW = bitmap.width.toFloat()
                            val bitmapH = bitmap.height.toFloat()
                            val aspect = bitmapH / bitmapW

                            val maxAllowedW = contentWidth // 515f
                            val maxAllowedH = 380f // Max height on standard A4 page

                            val (drawWidth, drawHeight) = if (aspect > 1f) {
                                // Dikey (Portrait): Yükseklik 380'i geçmeyecek, genişlik oranlı küçülecek
                                val h = maxAllowedH.coerceAtMost(maxAllowedW * aspect)
                                val w = h / aspect
                                Pair(w, h)
                            } else {
                                // Yatay (Landscape) veya Kare: Genişlik sayfaya sığacak, yükseklik oranlı hesaplanacak
                                val w = maxAllowedW.coerceAtMost(bitmapW)
                                val h = w * aspect
                                val finalH = h.coerceAtMost(maxAllowedH)
                                val finalW = if (h > maxAllowedH) maxAllowedH / aspect else w
                                Pair(finalW, finalH)
                            }

                            ensurePageSpace(drawHeight + 18f)

                            // Sayfada yatay olarak tam ortala
                            val drawX = marginX + (contentWidth - drawWidth) / 2f
                            val dstRect = RectF(drawX, y, drawX + drawWidth, y + drawHeight)
                            canvas.drawRoundRect(dstRect, 8f, 8f, imageBorderPaint)
                            canvas.drawBitmap(bitmap, null, dstRect, null)
                            y += drawHeight + 16f
                        }
                    }
                }

                BlockType.DRAWING -> {
                    val drawingPath = block.mediaPath
                    if (config.includeDrawings && !drawingPath.isNullOrBlank()) {
                        val bitmap = decodeSampledBitmap(drawingPath, 515, 340)
                        if (bitmap != null) {
                            val bitmapW = bitmap.width.toFloat()
                            val bitmapH = bitmap.height.toFloat()
                            val aspect = bitmapH / bitmapW

                            val maxAllowedW = contentWidth
                            val maxAllowedH = 320f

                            val (drawWidth, drawHeight) = if (aspect > 1f) {
                                val h = maxAllowedH.coerceAtMost(maxAllowedW * aspect)
                                val w = h / aspect
                                Pair(w, h)
                            } else {
                                val w = maxAllowedW.coerceAtMost(bitmapW)
                                val h = w * aspect
                                val finalH = h.coerceAtMost(maxAllowedH)
                                val finalW = if (h > maxAllowedH) maxAllowedH / aspect else w
                                Pair(finalW, finalH)
                            }

                            ensurePageSpace(drawHeight + 18f)

                            val drawX = marginX + (contentWidth - drawWidth) / 2f
                            val dstRect = RectF(drawX, y, drawX + drawWidth, y + drawHeight)
                            canvas.drawRoundRect(dstRect, 8f, 8f, imageBorderPaint)
                            canvas.drawBitmap(bitmap, null, dstRect, null)
                            y += drawHeight + 16f
                        }
                    }
                }

                BlockType.AUDIO -> {
                    if (config.includeAudioBadge) {
                        val badgeHeight = 44f
                        ensurePageSpace(badgeHeight + 14f)

                        val badgeRect = RectF(marginX, y, marginX + contentWidth, y + badgeHeight)
                        canvas.drawRoundRect(badgeRect, 8f, 8f, audioBgPaint)
                        canvas.drawRoundRect(badgeRect, 8f, 8f, audioBorderPaint)

                        canvas.drawText("🎙️  Ses Kaydı Eki (Audio Attachment)", marginX + 14f, y + 18f, audioTitlePaint)
                        val fileName = block.mediaPath?.substringAfterLast("/") ?: "Ses_Kaydi.mp4"
                        canvas.drawText("Dosya: $fileName  •  PureNoteIt Yerleşik Ses Dosyası", marginX + 14f, y + 33f, audioSubPaint)
                        y += badgeHeight + 14f
                    }
                }

                BlockType.TABLE -> {
                    if (config.includeText && block.tableData.isNotEmpty()) {
                        val tableHeight = block.tableData.size * 18f + 10f
                        ensurePageSpace(tableHeight)
                        block.tableData.forEachIndexed { rIdx, row ->
                            val rowText = row.joinToString("   |   ")
                            val paint = if (rIdx == 0) h2Paint else bodyPaint
                            canvas.drawText(rowText, marginX, y, paint)
                            y += 17f
                        }
                        y += 8f
                    }
                }

                BlockType.DIVIDER -> {
                    ensurePageSpace(14f)
                    canvas.drawLine(marginX, y, marginX + contentWidth, y, dividerPaint)
                    y += 14f
                }

                else -> {
                    if (config.includeText && block.content.isNotBlank()) {
                        // Word-wrap long paragraphs if needed
                        val words = block.content.split(" ")
                        var currentLine = ""
                        for (word in words) {
                            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (bodyPaint.measureText(testLine) > contentWidth) {
                                ensurePageSpace(18f)
                                canvas.drawText(currentLine, marginX, y, bodyPaint)
                                y += 17f
                                currentLine = word
                            } else {
                                currentLine = testLine
                            }
                        }
                        if (currentLine.isNotEmpty()) {
                            ensurePageSpace(18f)
                            canvas.drawText(currentLine, marginX, y, bodyPaint)
                            y += 18f
                        }
                    }
                }
            }
        }

        // Draw last page footer
        canvas.drawText("Sayfa $pageNumber", pageWidth / 2f, 815f, pageNumberPaint)
        pdfDocument.finishPage(page)

        try {
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "PDF Olarak Paylaş"))
        } catch (e: Exception) {
            pdfDocument.close()
            context.showToastShort("PDF oluşturulamadı: ${e.message}")
        }
    }
}
