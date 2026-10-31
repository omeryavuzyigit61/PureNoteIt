package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notes.purenoteit.R
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.PdfExportConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportStudioSheet(
    title: String,
    categoryName: String,
    blocks: List<NoteBlock>,
    onDismiss: () -> Unit,
    onExportPdf: (PdfExportConfig) -> Unit,
    onExportMarkdown: () -> Unit
) {
    var includeText by remember { mutableStateOf(true) }
    var includeImages by remember { mutableStateOf(true) }
    var includeDrawings by remember { mutableStateOf(true) }
    var includeTasks by remember { mutableStateOf(true) }
    var includeAudioBadge by remember { mutableStateOf(true) }
    var includeHeader by remember { mutableStateOf(true) }

    val imageCount = remember(blocks) { blocks.count { it.type == BlockType.IMAGE && !it.mediaPath.isNullOrBlank() } }
    val drawingCount = remember(blocks) { blocks.count { it.type == BlockType.DRAWING && !it.mediaPath.isNullOrBlank() } }
    val taskCount = remember(blocks) { blocks.count { it.type == BlockType.TODO } }
    val audioCount = remember(blocks) { blocks.count { it.type == BlockType.AUDIO } }

    val todayDateStr = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr")).format(Date()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFEBEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📄", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PDF Dışa Aktarma Stüdyosu",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Canlı A4 önizlemesi ve içerik filtreleri",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cancel),
                        contentDescription = "Kapat",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips Bar (Horizontal Scroll)
            Text(
                text = "PDF'e Dahil Edilecek Ögeler:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterToggleChip(
                    label = "Metinler",
                    icon = "✍️",
                    isSelected = includeText,
                    onClick = { includeText = !includeText }
                )

                if (imageCount > 0) {
                    FilterToggleChip(
                        label = "Fotoğraflar ($imageCount)",
                        icon = "🖼️",
                        isSelected = includeImages,
                        onClick = { includeImages = !includeImages }
                    )
                }

                if (drawingCount > 0) {
                    FilterToggleChip(
                        label = "Çizimler ($drawingCount)",
                        icon = "🎨",
                        isSelected = includeDrawings,
                        onClick = { includeDrawings = !includeDrawings }
                    )
                }

                if (taskCount > 0) {
                    FilterToggleChip(
                        label = "Görevler ($taskCount)",
                        icon = "✅",
                        isSelected = includeTasks,
                        onClick = { includeTasks = !includeTasks }
                    )
                }

                if (audioCount > 0) {
                    FilterToggleChip(
                        label = "Ses Rozeti ($audioCount)",
                        icon = "🎙️",
                        isSelected = includeAudioBadge,
                        onClick = { includeAudioBadge = !includeAudioBadge }
                    )
                }

                FilterToggleChip(
                    label = "Başlık & Tarih",
                    icon = "📅",
                    isSelected = includeHeader,
                    onClick = { includeHeader = !includeHeader }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live A4 Paper Sheet Preview Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(min = 220.dp, max = 340.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CANLI A4 SAYFA ÖNİZLEMESİ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VibrantOrange,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "A4 (72 DPI)",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Miniature White Paper Sheet
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        color = PureWhite,
                        shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                        ) {
                            // Header Section in Preview
                            if (includeHeader) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (title.isBlank()) "Başlıksız Not" else title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (categoryName.isNotBlank() && categoryName != "Tümü") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFF3E0)
                                        ) {
                                            Text(
                                                text = categoryName,
                                                fontSize = 9.sp,
                                                color = Color(0xFFE65100),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Oluşturulma: $todayDateStr",
                                    fontSize = 9.sp,
                                    color = Color(0xFF757575)
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Dynamic Filtered Content Blocks in Preview
                            for (block in blocks) {
                                when (block.type) {
                                    BlockType.HEADING_1 -> {
                                        if (includeText && block.content.isNotBlank()) {
                                            Text(
                                                text = block.content,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1C1C1E),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    BlockType.HEADING_2 -> {
                                        if (includeText && block.content.isNotBlank()) {
                                            Text(
                                                text = block.content,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF2C2C2E),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    BlockType.TODO -> {
                                        if (includeTasks && block.content.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (block.isChecked) "✓ " else "◻ ",
                                                    color = if (block.isChecked) Color(0xFF4CAF50) else Color(0xFF757575),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = block.content,
                                                    fontSize = 11.sp,
                                                    color = if (block.isChecked) Color(0xFF9E9E9E) else Color(0xFF212121),
                                                    textDecoration = if (block.isChecked) TextDecoration.LineThrough else null
                                                )
                                            }
                                        }
                                    }

                                    BlockType.BULLET_LIST -> {
                                        if (includeText && block.content.isNotBlank()) {
                                            Text(
                                                text = "•  ${block.content}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF212121),
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                        }
                                    }

                                    BlockType.IMAGE -> {
                                        if (includeImages && !block.mediaPath.isNullOrBlank()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(90.dp)
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFF5F5F5),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                                            ) {
                                                AsyncImage(
                                                    model = File(block.mediaPath ?: ""),
                                                    contentDescription = "Görsel Önizleme",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    BlockType.DRAWING -> {
                                        if (includeDrawings && !block.mediaPath.isNullOrBlank()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                shape = RoundedCornerShape(6.dp),
                                                color = PureWhite,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                                            ) {
                                                AsyncImage(
                                                    model = File(block.mediaPath ?: ""),
                                                    contentDescription = "Çizim Önizleme",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    BlockType.AUDIO -> {
                                        if (includeAudioBadge) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFFFF8E1),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = "🎙️", fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(
                                                            text = "Ses Kaydı Eki",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFF57F17)
                                                        )
                                                        Text(
                                                            text = block.mediaPath?.substringAfterLast("/") ?: "ses_kaydi.mp4",
                                                            fontSize = 8.sp,
                                                            color = Color(0xFF795548),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    BlockType.CALLOUT -> {
                                        if (includeText && block.content.isNotBlank()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFFF3E0)
                                            ) {
                                                Text(
                                                    text = "${block.calloutIcon} ${block.content}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFBF360C),
                                                    modifier = Modifier.padding(6.dp)
                                                )
                                            }
                                        }
                                    }

                                    BlockType.DIVIDER -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color(0xFFE0E0E0))
                                                .padding(vertical = 4.dp)
                                        )
                                    }

                                    else -> {
                                        if (includeText && block.content.isNotBlank()) {
                                            Text(
                                                text = block.content,
                                                fontSize = 11.sp,
                                                color = Color(0xFF212121),
                                                lineHeight = 15.sp,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary: Generate & Share PDF
                Button(
                    onClick = {
                        val config = PdfExportConfig(
                            includeText = includeText,
                            includeImages = includeImages,
                            includeDrawings = includeDrawings,
                            includeTasks = includeTasks,
                            includeAudioBadge = includeAudioBadge,
                            includeHeader = includeHeader
                        )
                        onExportPdf(config)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantOrange,
                        contentColor = PureWhite
                    )
                ) {
                    Text(
                        text = "📄 PDF Oluştur & Paylaş",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Secondary: Share as Markdown (.md)
                OutlinedButton(
                    onClick = onExportMarkdown,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "📝 Markdown (.md) Olarak Paylaş",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterToggleChip(
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
