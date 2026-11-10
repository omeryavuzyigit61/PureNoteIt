package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.desktop.ui.contract.CodeBlockRunState
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

private val SUPPORTED_LANGUAGES = listOf(
    "python", "kotlin", "java", "javascript", "sql", "cpp", "json", "html", "bash"
)

@Composable
fun DesktopBlockRow(
    block: NoteBlock,
    index: Int,
    totalCount: Int,
    mediaPathResolver: MediaPathResolver,
    readMediaBytes: suspend (String) -> ByteArray?,
    runState: CodeBlockRunState?,
    onUpdate: (NoteBlock) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRequestSlashMenu: () -> Unit,
    onOpenSubPageSplit: (String) -> Unit,
    onImportImage: (blockId: String, bytes: ByteArray, ext: String) -> Unit,
    onOpenDrawing: (blockId: String) -> Unit,
    onRunCode: (blockId: String, language: String, code: String) -> Unit,
    onApproveBash: (blockId: String) -> Unit,
    onDismissRunOutput: (blockId: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Block micro controls
        Column(modifier = Modifier.padding(top = 4.dp, end = 6.dp)) {
            Row {
                if (index > 0) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(22.dp)) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = "Yukarı",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                if (index < totalCount - 1) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(22.dp)) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = "Aşağı",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Bloğu Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            when (block.type) {
                BlockType.HEADING_1 -> EditorTextField(
                    value = block.content,
                    placeholder = "Büyük Başlık",
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onValueChange = { handleSlashOrUpdate(it, block, onUpdate, onRequestSlashMenu) }
                )

                BlockType.HEADING_2 -> EditorTextField(
                    value = block.content,
                    placeholder = "Alt Başlık",
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onValueChange = { handleSlashOrUpdate(it, block, onUpdate, onRequestSlashMenu) }
                )

                BlockType.TODO -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = block.isChecked,
                        onCheckedChange = { onUpdate(block.copy(isChecked = it)) },
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    EditorTextField(
                        value = block.content,
                        placeholder = "Yapılacak görev...",
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        onValueChange = { onUpdate(block.copy(content = it)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                BlockType.BULLET_LIST -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "•",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DesktopColors.VibrantOrange,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                    )
                    EditorTextField(
                        value = block.content,
                        placeholder = "Liste maddesi...",
                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                        onValueChange = { onUpdate(block.copy(content = it)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                BlockType.CALLOUT -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(block.calloutIcon.ifBlank { "💡" }, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        EditorTextField(
                            value = block.content,
                            placeholder = "Önemli not veya çağrı kutusu...",
                            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                            onValueChange = { onUpdate(block.copy(content = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BlockType.TOGGLE -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (block.isExpanded) "▼" else "▶",
                                fontSize = 13.sp,
                                color = DesktopColors.VibrantOrange,
                                modifier = Modifier
                                    .clickable { onUpdate(block.copy(isExpanded = !block.isExpanded)) }
                                    .padding(end = 8.dp)
                            )
                            EditorTextField(
                                value = block.content,
                                placeholder = "Açılır başlık...",
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                onValueChange = { onUpdate(block.copy(content = it)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (block.isExpanded) {
                            Spacer(Modifier.height(6.dp))
                            EditorTextField(
                                value = block.toggleBody,
                                placeholder = "Gizli içerik...",
                                textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                onValueChange = { onUpdate(block.copy(toggleBody = it)) }
                            )
                        }
                    }
                }

                BlockType.TABLE -> DesktopEditableTableBlock(
                    block = block,
                    onUpdate = onUpdate
                )

                BlockType.DIVIDER -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline
                )

                BlockType.MATH -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Text("∑", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DesktopColors.VibrantOrange)
                        Spacer(Modifier.width(10.dp))
                        EditorTextField(
                            value = block.content,
                            placeholder = "LaTeX / Formül (ör. E = mc^2)...",
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            onValueChange = { onUpdate(block.copy(content = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BlockType.WEB_BOOKMARK -> Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = DesktopColors.AccentBlue)
                        Spacer(Modifier.width(8.dp))
                        EditorTextField(
                            value = block.content,
                            placeholder = "https://...",
                            textStyle = TextStyle(fontSize = 13.sp, color = DesktopColors.AccentBlue),
                            onValueChange = { onUpdate(block.copy(content = it)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BlockType.SUB_PAGE -> Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable {
                            block.subPageSyncUuid?.let(onOpenSubPageSplit)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = DesktopColors.VibrantOrange)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = block.content.ifBlank { "Alt Sayfa" },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Yan bölmede açmak için tıklayın",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                BlockType.IMAGE -> DesktopImageOrDrawingBlock(
                    block = block,
                    isDrawing = false,
                    readMediaBytes = readMediaBytes,
                    onPickImage = {
                        val picked = chooseLocalImageFile()
                        if (picked != null && picked.isFile) {
                            val ext = picked.extension.ifBlank { "png" }
                            onImportImage(block.id, picked.readBytes(), ext)
                        }
                    },
                    onOpenDrawing = { onOpenDrawing(block.id) }
                )

                BlockType.DRAWING -> DesktopImageOrDrawingBlock(
                    block = block,
                    isDrawing = true,
                    readMediaBytes = readMediaBytes,
                    onPickImage = {},
                    onOpenDrawing = { onOpenDrawing(block.id) }
                )

                BlockType.CODE -> DesktopCodeEditorBlock(
                    block = block,
                    runState = runState,
                    onUpdate = onUpdate,
                    onRunCode = { onRunCode(block.id, block.codeLanguage, block.content) },
                    onApproveBash = { onApproveBash(block.id) },
                    onDismissOutput = { onDismissRunOutput(block.id) }
                )

                else -> EditorTextField(
                    value = block.content,
                    placeholder = "Yazmaya başla... (Komutlar için '/' yazın veya [[Sayfa]] bağlayın)",
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    onValueChange = { handleSlashOrUpdate(it, block, onUpdate, onRequestSlashMenu) }
                )
            }
        }
    }
}

private fun handleSlashOrUpdate(
    newValue: String,
    block: NoteBlock,
    onUpdate: (NoteBlock) -> Unit,
    onRequestSlashMenu: () -> Unit
) {
    if (newValue == "/") {
        onRequestSlashMenu()
    }
    onUpdate(block.copy(content = newValue))
}

@Composable
private fun EditorTextField(
    value: String,
    placeholder: String,
    textStyle: TextStyle,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            cursorBrush = SolidColor(DesktopColors.VibrantOrange),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DesktopEditableTableBlock(
    block: NoteBlock,
    onUpdate: (NoteBlock) -> Unit
) {
    val rows = block.tableData.ifEmpty { listOf(listOf("Sütun 1", "Sütun 2"), listOf("", "")) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            rows.forEachIndexed { rIdx, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { cIdx, cell ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .padding(6.dp)
                        ) {
                            BasicTextField(
                                value = cell,
                                onValueChange = { nextText ->
                                    val updatedTable = rows.mapIndexed { ri, r ->
                                        if (ri != rIdx) r else r.mapIndexed { ci, old -> if (ci == cIdx) nextText else old }
                                    }
                                    onUpdate(block.copy(tableData = updatedTable))
                                },
                                textStyle = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cols = rows.firstOrNull()?.size ?: 2
                        onUpdate(block.copy(tableData = rows + listOf(List(cols) { "" })))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("+ Satır", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedButton(
                    onClick = {
                        val expanded = rows.mapIndexed { i, r -> r + if (i == 0) "Yeni" else "" }
                        onUpdate(block.copy(tableData = expanded))
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("+ Sütun", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DesktopImageOrDrawingBlock(
    block: NoteBlock,
    isDrawing: Boolean,
    readMediaBytes: suspend (String) -> ByteArray?,
    onPickImage: () -> Unit,
    onOpenDrawing: () -> Unit
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, block.mediaPath) {
        value = withContext(Dispatchers.IO) {
            val path = block.mediaPath ?: return@withContext null
            val bytes = readMediaBytes(path) ?: return@withContext null
            runCatching { ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap() }.getOrNull()
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = if (isDrawing) "Çizim" else "Görsel",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isDrawing) Icons.Rounded.Brush else Icons.Rounded.Image,
                    contentDescription = null,
                    tint = DesktopColors.VibrantOrange
                )
                Text(
                    text = block.mediaPath ?: if (isDrawing) "Henüz çizim yok" else "Görsel seçilmedi",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = if (isDrawing) onOpenDrawing else onPickImage) {
                    Text(if (isDrawing) "Tuvalde Çiz / Düzenle" else "Dosyadan Resim Seç")
                }
            }
        }
    }
}

@Composable
private fun DesktopCodeEditorBlock(
    block: NoteBlock,
    runState: CodeBlockRunState?,
    onUpdate: (NoteBlock) -> Unit,
    onRunCode: () -> Unit,
    onApproveBash: () -> Unit,
    onDismissOutput: () -> Unit
) {
    var langMenuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1B20),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF333540), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Code,
                    contentDescription = null,
                    tint = DesktopColors.VibrantOrange,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { langMenuOpen = true },
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            block.codeLanguage.uppercase(),
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = langMenuOpen,
                        onDismissRequest = { langMenuOpen = false }
                    ) {
                        SUPPORTED_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.uppercase()) },
                                onClick = {
                                    langMenuOpen = false
                                    onUpdate(block.copy(codeLanguage = lang))
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onRunCode,
                    enabled = runState?.isRunning != true,
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (runState?.isRunning == true) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Çalıştır", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            BasicTextField(
                value = block.content,
                onValueChange = { onUpdate(block.copy(content = it)) },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE6E6EC)
                ),
                cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111216), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            )

            // Bash Explicit Security Confirmation Gate
            if (runState?.awaitingBashApproval == true) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF3E2414),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = DesktopColors.VibrantOrange)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bash betiği sisteminizde izole dizinde yürütülecek. Onaylıyor musunuz?",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = onApproveBash, modifier = Modifier.height(30.dp)) {
                            Text("Onayla ve Çalıştır", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Live Terminal / SQL Table / JSON Output Panel
            val out = runState?.output
            val err = runState?.errorMessage
            if (out != null || err != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0E1014),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out?.let { "Çıktı (${it.durationMs} ms)" } ?: "Çalıştırma Hatası",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (err != null) Color(0xFFF87171) else DesktopColors.EmeraldGreen
                            )
                            IconButton(onClick = onDismissOutput, modifier = Modifier.size(20.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Çıktıyı Kapat",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (err != null) {
                            Text(err, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFFCA5A5))
                        } else if (out != null) {
                            when (out) {
                                is CodeExecutionOutput.Console -> {
                                    out.lines.forEach { line ->
                                        val c = when (line.level) {
                                            ConsoleLevel.ERROR -> Color(0xFFF87171)
                                            ConsoleLevel.WARN -> Color(0xFFFBBF24)
                                            ConsoleLevel.INFO -> Color(0xFF60A5FA)
                                            ConsoleLevel.OUTPUT -> Color(0xFFD1FAE5)
                                        }
                                        Text(
                                            text = line.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = c
                                        )
                                    }
                                }
                                is CodeExecutionOutput.SqlTable -> {
                                    if (out.columns.isNotEmpty()) {
                                        Text(
                                            text = out.columns.joinToString(" | "),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF93C5FD)
                                        )
                                        out.rows.forEach { row ->
                                            Text(
                                                text = row.joinToString(" | "),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = Color(0xFFE5E7EB)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "${out.statementsExecuted} SQL sorgusu çalıştırıldı (${out.rowsAffected} satır etkilendi).",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFD1FAE5)
                                        )
                                    }
                                }
                                is CodeExecutionOutput.JsonResult -> {
                                    Text(
                                        text = out.formattedJson,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFBAE6FD)
                                    )
                                }
                                is CodeExecutionOutput.HtmlPreview -> {
                                    Text(
                                        text = "HTML Önizleme Hazır (${out.htmlContent.length} karakter):\n${out.htmlContent.take(300)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFFDE68A)
                                    )
                                }
                                is CodeExecutionOutput.SyntaxCheckPassed -> {
                                    Text(
                                        text = "Sözdizimi denetimi başarılı (${out.lineCount} satır).",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFD1FAE5)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun chooseLocalImageFile(): File? {
    val dialog = FileDialog(null as Frame?, "Görsel Seç", FileDialog.LOAD).apply {
        setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
        }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return File(dir, file)
}
