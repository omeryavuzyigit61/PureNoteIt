package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange

@Composable
fun NotionToolbar(
    onAddBlock: (BlockType) -> Unit,
    onPickImage: () -> Unit,
    onRecordAudio: () -> Unit,
    onOpenDrawing: () -> Unit,
    onOpenSlashMenu: () -> Unit,
    onLinkNote: (() -> Unit)? = null,
    onScanOcr: (() -> Unit)? = null,
    onVoiceDictation: (() -> Unit)? = null,
    isRecordingAudio: Boolean = false,
    isVoiceDictating: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notion Slash / [+] Quick Action Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(VibrantOrange)
                    .clickable { onOpenSlashMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.plus_small),
                    contentDescription = "Blok Ekle",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            // H1 Başlık (Heading 1) with clear icon & label
            ToolbarItem(
                iconRes = R.drawable.ic_format_h1,
                label = "Başlık 1",
                onClick = { onAddBlock(BlockType.HEADING_1) }
            )

            // H2 Alt Başlık (Heading 2) with clear icon & label
            ToolbarItem(
                iconRes = R.drawable.ic_format_h2,
                label = "Başlık 2",
                onClick = { onAddBlock(BlockType.HEADING_2) }
            )

            // Görev / Checklist (To-Do)
            ToolbarItem(
                iconRes = R.drawable.ic_checklist,
                label = "Görev",
                onClick = { onAddBlock(BlockType.TODO) },
                iconTint = VibrantOrange
            )

            // Madde İmleri (Bullet list)
            ToolbarItem(
                iconRes = R.drawable.ic_format_bullet,
                label = "Liste",
                onClick = { onAddBlock(BlockType.BULLET_LIST) }
            )

            // Açılır Liste (Toggle)
            ToolbarItem(
                iconRes = R.drawable.category,
                label = "Açılır Liste",
                onClick = { onAddBlock(BlockType.TOGGLE) },
                iconTint = VibrantOrange
            )

            // Dinamik Tablo (Table)
            ToolbarItem(
                iconRes = R.drawable.ic_table,
                label = "Tablo",
                onClick = { onAddBlock(BlockType.TABLE) },
                iconTint = VibrantOrange
            )

            // Not Bağla (Bi-directional Link [[Note]])
            ToolbarItem(
                iconRes = R.drawable.ic_network_graph,
                label = "Not Bağla",
                onClick = { onLinkNote?.invoke() },
                iconTint = VibrantOrange
            )

            // Alt Sayfa (Nested Sub-Page)
            ToolbarItem(
                iconRes = R.drawable.ic_subpage,
                label = "Alt Sayfa",
                onClick = { onAddBlock(BlockType.SUB_PAGE) },
                iconTint = VibrantOrange
            )

            // Vurgu / Alıntı Kutusu (Callout / Quote)
            ToolbarItem(
                iconRes = R.drawable.ic_format_quote,
                label = "Alıntı",
                onClick = { onAddBlock(BlockType.CALLOUT) }
            )

            // Fotoğraf Ekleme
            ToolbarItem(
                iconRes = R.drawable.picture,
                label = "Fotoğraf",
                onClick = onPickImage
            )

            // OCR Metin Tarama (Scan to Text)
            ToolbarItem(
                iconRes = R.drawable.ic_ocr_scan,
                label = "Metin Tara",
                onClick = { onScanOcr?.invoke() },
                iconTint = VibrantOrange
            )

            // El Çizimi Ekleme (Drawing Canvas)
            ToolbarItem(
                iconRes = R.drawable.ic_brush,
                label = "Çizim",
                onClick = onOpenDrawing
            )

            // Ses Kaydı (changes visual style when recording)
            ToolbarItem(
                iconRes = R.drawable.microphone_lines,
                label = if (isRecordingAudio) "Kaydediliyor..." else "Ses Kaydı",
                onClick = onRecordAudio,
                iconTint = if (isRecordingAudio) Color(0xFFE53935) else VibrantOrange,
                containerColor = if (isRecordingAudio) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
            )

            // Sesli Dikte (Voice-to-Text Transcription via SpeechRecognizer)
            ToolbarItem(
                iconRes = R.drawable.microphone_lines,
                label = if (isVoiceDictating) "Dinleniyor..." else "Sesli Dikte",
                onClick = { onVoiceDictation?.invoke() },
                iconTint = if (isVoiceDictating) Color(0xFF6200EE) else Color(0xFF2E7D32),
                containerColor = if (isVoiceDictating) Color(0xFFEDE7F6) else MaterialTheme.colorScheme.surfaceVariant
            )

            // Ayırıcı Çizgi (Divider)
            ToolbarItem(
                iconRes = R.drawable.category,
                label = "Ayırıcı",
                onClick = { onAddBlock(BlockType.DIVIDER) }
            )
        }
    }
}

@Composable
private fun ToolbarItem(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
) {
    Surface(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
