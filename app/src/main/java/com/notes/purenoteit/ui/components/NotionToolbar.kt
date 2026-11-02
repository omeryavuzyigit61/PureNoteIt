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
import androidx.compose.ui.res.stringResource
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
    onInsertHighlight: (() -> Unit)? = null,
    onInsertTag: (() -> Unit)? = null,
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
                    contentDescription = stringResource(R.string.tb_add_block),
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 1. Sesli Dikte (Voice-to-Text Transcription)
            ToolbarItem(
                iconRes = R.drawable.microphone_lines,
                label = if (isVoiceDictating) stringResource(R.string.tb_listening) else stringResource(R.string.tb_voice_dictation),
                onClick = { onVoiceDictation?.invoke() },
                iconTint = if (isVoiceDictating) Color(0xFF8E24AA) else Color(0xFF6A1B9A),
                containerColor = if (isVoiceDictating) Color(0xFFEDE7F6) else Color(0xFFF3E5F5)
            )

            // 2. OCR Metin Tarama (Scan to Text)
            ToolbarItem(
                iconRes = R.drawable.ic_ocr_scan,
                label = stringResource(R.string.tb_scan_ocr),
                onClick = { onScanOcr?.invoke() },
                iconTint = VibrantOrange
            )

            // 3. El Çizimi (Drawing Canvas)
            ToolbarItem(
                iconRes = R.drawable.ic_brush,
                label = stringResource(R.string.tb_drawing),
                onClick = onOpenDrawing
            )

            // 4. Görev / Checklist (To-Do)
            ToolbarItem(
                iconRes = R.drawable.ic_checklist,
                label = stringResource(R.string.tb_todo),
                onClick = { onAddBlock(BlockType.TODO) },
                iconTint = VibrantOrange
            )

            // 5. Not Bağla (Bi-directional Link [[Note]])
            ToolbarItem(
                iconRes = R.drawable.ic_network_graph,
                label = stringResource(R.string.tb_link_note),
                onClick = { onLinkNote?.invoke() },
                iconTint = VibrantOrange
            )

            // 6. Fosforlu Vurgu (Highlighter ==metin==)
            ToolbarItem(
                iconRes = R.drawable.ic_brush,
                label = stringResource(R.string.tb_highlight),
                onClick = { onInsertHighlight?.invoke() },
                iconTint = Color(0xFFF57F17),
                containerColor = Color(0xFFFFF9C4)
            )

            // 7. Akıllı #Etiket
            ToolbarItem(
                iconRes = R.drawable.category,
                label = stringResource(R.string.tb_tag),
                onClick = { onInsertTag?.invoke() },
                iconTint = VibrantOrange
            )

            // H1 Başlık (Heading 1)
            ToolbarItem(
                iconRes = R.drawable.ic_format_h1,
                label = stringResource(R.string.tb_heading_1),
                onClick = { onAddBlock(BlockType.HEADING_1) }
            )

            // H2 Alt Başlık (Heading 2)
            ToolbarItem(
                iconRes = R.drawable.ic_format_h2,
                label = stringResource(R.string.tb_heading_2),
                onClick = { onAddBlock(BlockType.HEADING_2) }
            )

            // Madde İmleri (Bullet list)
            ToolbarItem(
                iconRes = R.drawable.ic_format_bullet,
                label = stringResource(R.string.tb_bullet_list),
                onClick = { onAddBlock(BlockType.BULLET_LIST) }
            )

            // Açılır Liste (Toggle)
            ToolbarItem(
                iconRes = R.drawable.category,
                label = stringResource(R.string.tb_toggle),
                onClick = { onAddBlock(BlockType.TOGGLE) },
                iconTint = VibrantOrange
            )

            // Dinamik Tablo (Table)
            ToolbarItem(
                iconRes = R.drawable.ic_table,
                label = stringResource(R.string.tb_table),
                onClick = { onAddBlock(BlockType.TABLE) },
                iconTint = VibrantOrange
            )

            // Kod Bloğu (Code)
            ToolbarItem(
                iconRes = R.drawable.file_edit,
                label = stringResource(R.string.tb_code),
                onClick = { onAddBlock(BlockType.CODE) },
                iconTint = Color(0xFF673AB7)
            )

            // Matematik Formülü (Math)
            ToolbarItem(
                iconRes = R.drawable.other,
                label = stringResource(R.string.tb_math),
                onClick = { onAddBlock(BlockType.MATH) },
                iconTint = Color(0xFF00897B)
            )

            // İçindekiler Tablosu (Table of Contents)
            ToolbarItem(
                iconRes = R.drawable.ic_format_bullet,
                label = stringResource(R.string.slash_toc_title),
                onClick = { onAddBlock(BlockType.TABLE_OF_CONTENTS) },
                iconTint = VibrantOrange
            )

            // Alt Sayfa (Nested Sub-Page)
            ToolbarItem(
                iconRes = R.drawable.ic_subpage,
                label = stringResource(R.string.tb_subpage),
                onClick = { onAddBlock(BlockType.SUB_PAGE) },
                iconTint = VibrantOrange
            )

            // Vurgu / Alıntı Kutusu (Callout / Quote)
            ToolbarItem(
                iconRes = R.drawable.ic_format_quote,
                label = stringResource(R.string.tb_callout),
                onClick = { onAddBlock(BlockType.CALLOUT) }
            )

            // Fotoğraf Ekleme
            ToolbarItem(
                iconRes = R.drawable.picture,
                label = stringResource(R.string.tb_photo),
                onClick = onPickImage
            )

            // Ses Kaydı
            ToolbarItem(
                iconRes = R.drawable.microphone_lines,
                label = if (isRecordingAudio) stringResource(R.string.tb_recording) else stringResource(R.string.tb_audio_record),
                onClick = onRecordAudio,
                iconTint = if (isRecordingAudio) Color(0xFFE53935) else VibrantOrange,
                containerColor = if (isRecordingAudio) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant
            )

            // Ayırıcı Çizgi (Divider)
            ToolbarItem(
                iconRes = R.drawable.category,
                label = stringResource(R.string.tb_divider),
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
