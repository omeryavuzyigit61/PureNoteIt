package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.notes.purenoteit.ui.theme.SoftYellowLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import java.io.File

/**
 * Notion Block Row Component.
 * Supports rich blocks: Headings, Paragraphs, Checklists, Bullet Lists, Callouts,
 * Dividers, Spotify Audio Players, Images, and Hand Drawings.
 * Features 1-click Move Up / Move Down block reordering and deletion.
 */
@Composable
fun NotionBlockRow(
    block: NoteBlock,
    onContentChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteBlock: () -> Unit,
    audioRecorder: AudioRecorder,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showBlockMenu by remember { mutableStateOf(false) }

    val textStyle = when (block.type) {
        BlockType.HEADING_1 -> TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        BlockType.HEADING_2 -> TextStyle(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        BlockType.TODO -> TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
            textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None
        )
        BlockType.CALLOUT -> TextStyle(
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            color = CharcoalGray
        )
        else -> TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground
        )
    }

    // Mini Reorder Bar (Up / Down / Delete)
    @Composable
    fun BlockReorderControls(
        isDarkContainer: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkContainer) CharcoalGray.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onMoveUp != null) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        text = "▲",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkContainer) PureWhite else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (onMoveDown != null) {
                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        text = "▼",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkContainer) PureWhite else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(
                onClick = onDeleteBlock,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cancel),
                    contentDescription = "Sil",
                    tint = if (isDarkContainer) PureWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }

    when (block.type) {
        BlockType.DIVIDER -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                BlockReorderControls(modifier = Modifier.padding(start = 6.dp))
            }
        }

        BlockType.TODO -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (block.isChecked) VibrantOrange else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(5.dp)
                        )
                        .background(if (block.isChecked) VibrantOrange else Color.Transparent)
                        .clickable { onCheckedChange(!block.isChecked) },
                    contentAlignment = Alignment.Center
                ) {
                    if (block.isChecked) {
                        Text(
                            text = "✓",
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = block.content,
                    onValueChange = onContentChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(VibrantOrange),
                    decorationBox = { innerTextField ->
                        if (block.content.isEmpty()) {
                            Text(
                                text = "Yapılacak görev...",
                                style = textStyle.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )

                BlockReorderControls(modifier = Modifier.padding(start = 4.dp))
            }
        }

        BlockType.BULLET_LIST -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp, end = 12.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VibrantOrange)
                )

                BasicTextField(
                    value = block.content,
                    onValueChange = onContentChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(VibrantOrange),
                    decorationBox = { innerTextField ->
                        if (block.content.isEmpty()) {
                            Text(
                                text = "Liste maddesi...",
                                style = textStyle.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )

                BlockReorderControls(modifier = Modifier.padding(start = 4.dp))
            }
        }

        BlockType.CALLOUT -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = SoftYellowLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = block.calloutIcon,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )

                    BasicTextField(
                        value = block.content,
                        onValueChange = onContentChange,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(VibrantOrange),
                        decorationBox = { innerTextField ->
                            if (block.content.isEmpty()) {
                                Text(
                                    text = "Vurgulanmış not veya alıntı...",
                                    style = textStyle.copy(
                                        color = CharcoalGray.copy(alpha = 0.45f)
                                    )
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    BlockReorderControls(modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        BlockType.IMAGE -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = File(block.mediaPath ?: ""),
                    contentDescription = "Not Görseli",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )

                BlockReorderControls(
                    isDarkContainer = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }

        BlockType.DRAWING -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, BorderLight.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .background(PureWhite)
            ) {
                AsyncImage(
                    model = File(block.mediaPath ?: ""),
                    contentDescription = "El Çizimi",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                BlockReorderControls(
                    isDarkContainer = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }

        BlockType.AUDIO -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    BlockReorderControls()
                }

                SpotifyAudioPlayer(
                    filePath = block.mediaPath ?: "",
                    audioRecorder = audioRecorder,
                    onDelete = onDeleteBlock,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        else -> { // PARAGRAPH, HEADING_1, HEADING_2
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = block.content,
                    onValueChange = onContentChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(VibrantOrange),
                    decorationBox = { innerTextField ->
                        if (block.content.isEmpty()) {
                            val placeholderText = when (block.type) {
                                BlockType.HEADING_1 -> "Başlık 1 (Büyük Başlık)..."
                                BlockType.HEADING_2 -> "Başlık 2 (Alt Başlık)..."
                                else -> "Not yazın veya '/' ile komutları açın..."
                            }
                            Text(
                                text = placeholderText,
                                style = textStyle.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(
                                        alpha = if (block.type == BlockType.PARAGRAPH) 0.35f else 0.45f
                                    )
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )

                BlockReorderControls(modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
