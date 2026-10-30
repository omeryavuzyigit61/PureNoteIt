package com.notes.purenoteit.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
     * Generates a sleek, formatted PDF document from note blocks and triggers the Android Share Sheet.
     */
    fun shareAsPdf(context: Context, title: String, blocks: List<NoteBlock>) {
        val safeTitle = if (title.isBlank()) "PureNoteIt_Not" else title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val pdfFile = File(exportDir, "${safeTitle}.pdf")

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (72 dpi)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#E65100") // PureNoteIt VibrantOrange
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val datePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        val h2Paint = Paint().apply {
            color = Color.parseColor("#212121")
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val calloutPaint = Paint().apply {
            color = Color.parseColor("#E65100")
            textSize = 11.5f
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 50f
        val marginX = 40f
        val maxPageY = 780f

        // Document Title & Date Header
        canvas.drawText("PureNoteIt • $title", marginX, y, titlePaint)
        y += 20f
        val todayStr = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr")).format(Date())
        canvas.drawText("Oluşturulma Tarihi: $todayStr", marginX, y, datePaint)
        y += 12f
        canvas.drawLine(marginX, y, 555f, y, dividerPaint)
        y += 24f

        for (block in blocks) {
            if (y > maxPageY) break

            when (block.type) {
                BlockType.HEADING_1 -> {
                    y += 10f
                    canvas.drawText(block.content, marginX, y, titlePaint)
                    y += 24f
                }
                BlockType.HEADING_2 -> {
                    y += 8f
                    canvas.drawText(block.content, marginX, y, h2Paint)
                    y += 20f
                }
                BlockType.TODO -> {
                    val mark = if (block.isChecked) "[✓]" else "[ ]"
                    canvas.drawText("$mark ${block.content}", marginX, y, bodyPaint)
                    y += 18f
                }
                BlockType.BULLET_LIST -> {
                    canvas.drawText("•  ${block.content}", marginX + 6f, y, bodyPaint)
                    y += 18f
                }
                BlockType.CALLOUT -> {
                    canvas.drawText("${block.calloutIcon}  ${block.content}", marginX, y, calloutPaint)
                    y += 20f
                }
                BlockType.TOGGLE -> {
                    canvas.drawText("▶ ${block.content}", marginX, y, h2Paint)
                    y += 18f
                    if (block.toggleBody.isNotBlank()) {
                        canvas.drawText("    ${block.toggleBody}", marginX, y, bodyPaint)
                        y += 18f
                    }
                }
                BlockType.TABLE -> {
                    if (block.tableData.isNotEmpty()) {
                        y += 6f
                        block.tableData.forEachIndexed { rIdx, row ->
                            val rowText = row.joinToString("   |   ")
                            val paint = if (rIdx == 0) h2Paint else bodyPaint
                            canvas.drawText(rowText, marginX, y, paint)
                            y += 16f
                        }
                        y += 8f
                    }
                }
                BlockType.DIVIDER -> {
                    y += 6f
                    canvas.drawLine(marginX, y, 555f, y, dividerPaint)
                    y += 16f
                }
                else -> {
                    if (block.content.isNotBlank()) {
                        canvas.drawText(block.content, marginX, y, bodyPaint)
                        y += 18f
                    }
                }
            }
        }

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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "PDF Olarak Paylaş"))
        } catch (e: Exception) {
            pdfDocument.close()
            context.showToastShort("PDF oluşturulamadı: ${e.message}")
        }
    }
}
