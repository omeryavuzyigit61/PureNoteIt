package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.data.storage.DesktopImageOptimizer
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

@Composable
fun DesktopZenReadingView(
    title: String,
    icon: String,
    blocks: List<NoteBlock>,
    readMediaBytes: suspend (String) -> ByteArray?,
    onPlayAudio: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wordCount = remember(title, blocks) {
        val raw = buildString {
            append(title).append(' ')
            blocks.forEach {
                append(it.content).append(' ')
                append(it.toggleBody).append(' ')
            }
        }
        raw.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val readMinutes = maxOf(1, (wordCount + 179) / 180)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Minimal Zen Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = DesktopColors.VibrantOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Zen Okuma Modu",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = DesktopColors.VibrantOrange.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "$wordCount kelime · ~$readMinutes dk okuma",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DesktopColors.VibrantOrange,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Okumayı Kapat", fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                ) {
                    item(key = "zen_header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 34.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = title.ifBlank { "Başlıksız Not" },
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 36.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    }

                    items(blocks, key = { "zen_${it.id}" }) { block ->
                        ZenBlockCard(
                            block = block,
                            readMediaBytes = readMediaBytes,
                            onPlayAudio = onPlayAudio
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZenBlockCard(
    block: NoteBlock,
    readMediaBytes: suspend (String) -> ByteArray?,
    onPlayAudio: (String) -> Unit
) {
    when (block.type) {
        BlockType.HEADING_1 -> Text(
            text = block.content,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
        BlockType.HEADING_2 -> Text(
            text = block.content,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 26.sp,
            color = DesktopColors.VibrantOrange,
            modifier = Modifier.padding(top = 6.dp)
        )
        BlockType.TODO -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (block.isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (block.isChecked) DesktopColors.EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = block.content,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textDecoration = if (block.isChecked) TextDecoration.LineThrough else null,
                color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
            )
        }
        BlockType.BULLET_LIST -> Row(verticalAlignment = Alignment.Top) {
            Text("•", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesktopColors.VibrantOrange)
            Spacer(Modifier.width(10.dp))
            Text(block.content, fontSize = 15.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        BlockType.CALLOUT -> Surface(
            shape = RoundedCornerShape(14.dp),
            color = DesktopColors.VibrantOrange.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, DesktopColors.VibrantOrange.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.calloutIcon.ifBlank { "💡" }, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text(block.content, fontSize = 14.5.sp, lineHeight = 22.sp)
            }
        }
        BlockType.CODE -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = DesktopColors.VibrantOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(block.codeLanguage.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DesktopColors.VibrantOrange)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = block.content,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
        BlockType.MATH -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                DesktopMathFormulaView(
                    latex = block.content,
                    isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                    fontSize = 18.sp
                )
            }
        }
        BlockType.TABLE -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.tableData.forEachIndexed { rIdx, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (rIdx == 0) DesktopColors.VibrantOrange.copy(alpha = 0.12f)
                                else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                fontSize = 12.5.sp,
                                fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        BlockType.AUDIO -> Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = DesktopColors.VibrantOrange)
                Spacer(Modifier.width(10.dp))
                Text(block.content.ifBlank { "Sesli Not" }, modifier = Modifier.weight(1f), fontSize = 14.sp)
                if (!block.mediaPath.isNullOrBlank()) {
                    OutlinedButton(onClick = { block.mediaPath?.let(onPlayAudio) }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sesi Dinle", fontSize = 12.sp)
                    }
                }
            }
        }
        BlockType.IMAGE, BlockType.DRAWING -> {
            val bitmap by produceState<ImageBitmap?>(initialValue = null, block.mediaPath) {
                val p = block.mediaPath ?: return@produceState
                val bytes = readMediaBytes(p) ?: return@produceState
                value = DesktopImageOptimizer.decodeComposeBitmap(bytes)
            }
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = block.content,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
            }
        }
        BlockType.DIVIDER -> HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        else -> if (block.content.isNotBlank()) {
            Text(
                text = block.content,
                fontSize = 15.5.sp,
                lineHeight = 25.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun DesktopExportStudioDialog(
    title: String,
    categoryName: String,
    blocks: List<NoteBlock>,
    readMediaBytes: suspend (String) -> ByteArray?,
    onExportFeedback: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var includeHeader by remember { mutableStateOf(true) }
    var includeTasks by remember { mutableStateOf(true) }
    var includeCodeAndTables by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 480.dp,
        title = "PDF & Markdown Dışa Aktar",
        subtitle = "Notunuzu yazdırılabilir şık PDF veya taşınabilir Markdown (.md) olarak kaydedin.",
        badgeIcon = Icons.Rounded.PictureAsPdf,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        val options = listOf(
            Triple("Başlık, Kategori & Tarih Bilgisi", includeHeader) { v: Boolean -> includeHeader = v },
            Triple("Görev / Kontrol Maddeleri", includeTasks) { v: Boolean -> includeTasks = v },
            Triple("Kod Blokları & Tablolar", includeCodeAndTables) { v: Boolean -> includeCodeAndTables = v },
            Triple("Görseller & Çizimler (PDF)", includeImages) { v: Boolean -> includeImages = v }
        )

        options.forEach { (label, checked, onChange) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onChange,
                    colors = CheckboxDefaults.colors(checkedColor = DesktopColors.VibrantOrange)
                )
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val chosen = chooseSaveFile("${sanitizeExportFilename(title)}.md")
                    if (chosen != null) {
                        val md = DesktopNoteExporter.buildMarkdown(
                            title = title,
                            categoryName = categoryName,
                            blocks = blocks,
                            includeHeader = includeHeader,
                            includeTasks = includeTasks,
                            includeCodeAndTables = includeCodeAndTables
                        )
                        val ok = runCatching {
                            chosen.writeText(md, Charsets.UTF_8)
                            true
                        }.getOrDefault(false)
                        if (ok) {
                            onDismiss()
                            onExportFeedback("Markdown dosyası kaydedildi: ${chosen.name}", false)
                        } else {
                            onExportFeedback("Markdown kaydedilemedi.", true)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Markdown (.md)", fontSize = 12.5.sp)
            }

            Button(
                onClick = {
                    val chosen = chooseSaveFile("${sanitizeExportFilename(title)}.pdf")
                    if (chosen != null) {
                        scope.launch {
                            val pdfOk = runCatching {
                                val loadedImages = if (includeImages) {
                                    blocks.mapNotNull { b ->
                                        val path = b.mediaPath
                                        if ((b.type == BlockType.IMAGE || b.type == BlockType.DRAWING) && !path.isNullOrBlank()) {
                                            readMediaBytes(path)?.let { bytes ->
                                                path to ImageIO.read(ByteArrayInputStream(bytes))
                                            }
                                        } else {
                                            null
                                        }
                                    }.toMap()
                                } else {
                                    emptyMap()
                                }
                                withContext(Dispatchers.IO) {
                                    val pdfBytes = DesktopNoteExporter.renderToPdfBytes(
                                        title = title,
                                        categoryName = categoryName,
                                        blocks = blocks,
                                        includeHeader = includeHeader,
                                        includeTasks = includeTasks,
                                        includeCodeAndTables = includeCodeAndTables,
                                        imagesByPath = loadedImages
                                    )
                                    chosen.writeBytes(pdfBytes)
                                }
                                true
                            }.getOrDefault(false)
                            if (pdfOk) {
                                onDismiss()
                                onExportFeedback("PDF dosyası kaydedildi: ${chosen.name}", false)
                            } else {
                                onExportFeedback("PDF dosyası oluşturulamadı.", true)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("PDF Kaydet", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun sanitizeExportFilename(title: String): String {
    val clean = title.trim().ifBlank { "PureNoteIt_Not" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    return clean.take(60)
}

private fun chooseSaveFile(defaultFileName: String): File? {
    return try {
        val dialog = FileDialog(null as Frame?, "Dışa Aktar", FileDialog.SAVE)
        dialog.file = defaultFileName
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file) else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Pure JVM zero-dependency Markdown & multi-page PDF 1.4 exporter preserving full Turkish Unicode glyphs.
 */
object DesktopNoteExporter {

    fun buildMarkdown(
        title: String,
        categoryName: String,
        blocks: List<NoteBlock>,
        includeHeader: Boolean = true,
        includeTasks: Boolean = true,
        includeCodeAndTables: Boolean = true
    ): String {
        val sb = StringBuilder()
        if (includeHeader) {
            sb.append("# ").append(title.ifBlank { "Başlıksız Not" }).append("\n")
            if (categoryName.isNotBlank()) {
                sb.append("*Kategori: ").append(categoryName).append("*\n\n")
            } else {
                sb.append("\n")
            }
        }
        for (b in blocks) {
            when (b.type) {
                BlockType.HEADING_1 -> sb.append("# ").append(b.content).append("\n\n")
                BlockType.HEADING_2 -> sb.append("## ").append(b.content).append("\n\n")
                BlockType.TODO -> if (includeTasks) {
                    sb.append(if (b.isChecked) "- [x] " else "- [ ] ").append(b.content).append("\n")
                }
                BlockType.BULLET_LIST -> sb.append("- ").append(b.content).append("\n")
                BlockType.CALLOUT -> sb.append("> ").append(b.calloutIcon).append(" ").append(b.content).append("\n\n")
                BlockType.CODE -> if (includeCodeAndTables) {
                    sb.append("```").append(b.codeLanguage).append("\n")
                        .append(b.content).append("\n```\n\n")
                }
                BlockType.TABLE -> if (includeCodeAndTables && b.tableData.isNotEmpty()) {
                    b.tableData.forEachIndexed { idx, row ->
                        sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
                        if (idx == 0) {
                            sb.append("| ").append(row.joinToString(" | ") { "---" }).append(" |\n")
                        }
                    }
                    sb.append("\n")
                }
                BlockType.DIVIDER -> sb.append("---\n\n")
                else -> if (b.content.isNotBlank()) sb.append(b.content).append("\n\n")
            }
        }
        return sb.toString()
    }

    fun renderToPdfBytes(
        title: String,
        categoryName: String,
        blocks: List<NoteBlock>,
        includeHeader: Boolean = true,
        includeTasks: Boolean = true,
        includeCodeAndTables: Boolean = true,
        imagesByPath: Map<String, BufferedImage?> = emptyMap()
    ): ByteArray {
        val pageW = 1190
        val pageH = 1684
        val marginX = 88
        val contentW = pageW - marginX * 2

        val pages = mutableListOf<BufferedImage>()
        var currentImg = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
        var g = createPageGraphics(currentImg, pageW, pageH)
        var y = 100

        fun newPage() {
            g.dispose()
            pages += currentImg
            currentImg = BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB)
            g = createPageGraphics(currentImg, pageW, pageH)
            y = 100
        }

        fun ensureSpace(neededHeight: Int) {
            if (y + neededHeight > pageH - 100) newPage()
        }

        fun drawWrappedText(text: String, font: Font, color: java.awt.Color, indent: Int = 0, lineHeight: Int = 34) {
            g.font = font
            g.color = color
            val fm = g.fontMetrics
            val maxW = contentW - indent
            val paragraphs = text.split('\n')
            for (para in paragraphs) {
                if (para.isBlank()) {
                    y += lineHeight / 2
                    continue
                }
                val words = para.split(' ')
                val line = StringBuilder()
                for (w in words) {
                    val candidate = if (line.isEmpty()) w else "$line $w"
                    if (fm.stringWidth(candidate) <= maxW) {
                        if (line.isNotEmpty()) line.append(' ')
                        line.append(w)
                    } else {
                        ensureSpace(lineHeight)
                        g.drawString(line.toString(), marginX + indent, y)
                        y += lineHeight
                        line.clear()
                        line.append(w)
                    }
                }
                if (line.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    g.drawString(line.toString(), marginX + indent, y)
                    y += lineHeight
                }
            }
        }

        if (includeHeader) {
            val headerTitle = title.ifBlank { "Başlıksız Not" }
            drawWrappedText(headerTitle, Font("SansSerif", Font.BOLD, 34), java.awt.Color(0x1C, 0x1C, 0x1E), lineHeight = 44)
            val dateStr = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR")).format(Date())
            val meta = if (categoryName.isNotBlank()) "$categoryName  ·  $dateStr" else dateStr
            drawWrappedText(meta, Font("SansSerif", Font.PLAIN, 18), java.awt.Color(0x8E, 0x8E, 0x93), lineHeight = 28)
            g.color = java.awt.Color(0xFF, 0x90, 0x0F)
            g.fillRect(marginX, y - 8, 96, 4)
            y += 26
        }

        for (b in blocks) {
            when (b.type) {
                BlockType.HEADING_1 -> {
                    y += 10
                    drawWrappedText(b.content, Font("SansSerif", Font.BOLD, 28), java.awt.Color(0x1C, 0x1C, 0x1E), lineHeight = 38)
                }
                BlockType.HEADING_2 -> {
                    y += 8
                    drawWrappedText(b.content, Font("SansSerif", Font.BOLD, 22), java.awt.Color(0xD9, 0x6B, 0x00), lineHeight = 32)
                }
                BlockType.TODO -> if (includeTasks) {
                    val mark = if (b.isChecked) "[✓]" else "[  ]"
                    drawWrappedText("$mark  ${b.content}", Font("SansSerif", Font.PLAIN, 21), java.awt.Color(0x2C, 0x2C, 0x2E), indent = 12, lineHeight = 30)
                }
                BlockType.BULLET_LIST -> {
                    drawWrappedText("•  ${b.content}", Font("SansSerif", Font.PLAIN, 21), java.awt.Color(0x2C, 0x2C, 0x2E), indent = 12, lineHeight = 30)
                }
                BlockType.CALLOUT -> {
                    y += 6
                    drawWrappedText("┃  ${b.content}", Font("SansSerif", Font.ITALIC, 21), java.awt.Color(0x5A, 0x3D, 0x1A), indent = 14, lineHeight = 30)
                    y += 6
                }
                BlockType.CODE -> if (includeCodeAndTables) {
                    y += 6
                    drawWrappedText(b.content, Font("Monospaced", Font.PLAIN, 18), java.awt.Color(0x1E, 0x29, 0x3B), indent = 18, lineHeight = 26)
                    y += 8
                }
                BlockType.TABLE -> if (includeCodeAndTables && b.tableData.isNotEmpty()) {
                    y += 6
                    b.tableData.forEachIndexed { idx, row ->
                        val f = if (idx == 0) Font("SansSerif", Font.BOLD, 19) else Font("SansSerif", Font.PLAIN, 19)
                        drawWrappedText(row.joinToString("   |   "), f, java.awt.Color(0x1C, 0x1C, 0x1E), indent = 12, lineHeight = 28)
                    }
                    y += 6
                }
                BlockType.IMAGE, BlockType.DRAWING -> {
                    val bmp = b.mediaPath?.let { imagesByPath[it] }
                    if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                        val targetW = minOf(contentW, 760)
                        val targetH = ((bmp.height.toDouble() / bmp.width.toDouble()) * targetW).toInt().coerceIn(80, 560)
                        ensureSpace(targetH + 24)
                        g.drawImage(bmp, marginX, y, targetW, targetH, null)
                        y += targetH + 20
                    }
                }
                BlockType.DIVIDER -> {
                    ensureSpace(24)
                    g.color = java.awt.Color(0xDD, 0xDD, 0xE5)
                    g.stroke = BasicStroke(2f)
                    g.drawLine(marginX, y, pageW - marginX, y)
                    y += 22
                }
                else -> if (b.content.isNotBlank()) {
                    drawWrappedText(b.content, Font("SansSerif", Font.PLAIN, 21), java.awt.Color(0x2C, 0x2C, 0x2E), lineHeight = 32)
                }
            }
        }

        g.dispose()
        pages += currentImg

        return assembleJpegPagesPdf(pages)
    }

    private fun createPageGraphics(img: BufferedImage, w: Int, h: Int) = img.createGraphics().apply {
        color = java.awt.Color.WHITE
        fillRect(0, 0, w, h)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    }

    private fun assembleJpegPagesPdf(pages: List<BufferedImage>): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))

        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()

        // Object layout:
        // 1: Catalog
        // 2: Pages
        // For each page i in 0 until N:
        //   pageObj = 3 + i * 3
        //   contentObj = 4 + i * 3
        //   imageObj = 5 + i * 3
        val pageCount = pages.size
        val pageObjIds = (0 until pageCount).map { 3 + it * 3 }

        // Obj 1: Catalog
        offsets += out.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2: Pages
        offsets += out.size()
        val kids = pageObjIds.joinToString(" ") { "$it 0 R" }
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $pageCount >>\nendobj\n")

        for (i in pages.indices) {
            val img = pages[i]
            val jpgBaos = ByteArrayOutputStream()
            ImageIO.write(img, "jpg", jpgBaos)
            val jpgBytes = jpgBaos.toByteArray()

            val pageId = 3 + i * 3
            val contentId = 4 + i * 3
            val imageId = 5 + i * 3
            val contentStream = "q\n595 0 0 842 0 0 cm\n/Im$i Do\nQ\n".toByteArray(Charsets.ISO_8859_1)

            // Page object
            offsets += out.size()
            writeAscii(
                "$pageId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /XObject << /Im$i $imageId 0 R >> >> /Contents $contentId 0 R >>\nendobj\n"
            )

            // Content stream object
            offsets += out.size()
            writeAscii("$contentId 0 obj\n<< /Length ${contentStream.size} >>\nstream\n")
            out.write(contentStream)
            writeAscii("\nendstream\nendobj\n")

            // JPEG Image XObject
            offsets += out.size()
            writeAscii(
                "$imageId 0 obj\n<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpgBytes.size} >>\nstream\n"
            )
            out.write(jpgBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val xrefOffset = out.size()
        val totalObjs = offsets.size
        writeAscii("xref\n0 ${totalObjs + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (off in offsets) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${totalObjs + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }
}
