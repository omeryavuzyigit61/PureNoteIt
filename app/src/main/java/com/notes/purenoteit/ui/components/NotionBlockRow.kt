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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun NotionBlockRow(
    block: NoteBlock,
    onContentChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteBlock: () -> Unit,
    audioRecorder: AudioRecorder,
    modifier: Modifier = Modifier
) {
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
                IconButton(
                    onClick = onDeleteBlock,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cancel),
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        BlockType.TODO -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Checkbox
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (block.isChecked) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .background(if (block.isChecked) VibrantOrange else Color.Transparent)
                        .clickable { onCheckedChange(!block.isChecked) },
                    contentAlignment = Alignment.Center
                ) {
                    if (block.isChecked) {
                        Icon(
                            painter = painterResource(id = R.drawable.confirm_button_background),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(10.dp)
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

                if (block.content.isBlank()) {
                    IconButton(
                        onClick = onDeleteBlock,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        BlockType.BULLET_LIST -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, start = 4.dp, end = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onBackground)
                )

                BasicTextField(
                    value = block.content,
                    onValueChange = onContentChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(VibrantOrange),
                    decorationBox = { innerTextField ->
                        if (block.content.isEmpty()) {
                            Text(
                                text = "Madde...",
                                style = textStyle.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )

                if (block.content.isBlank()) {
                    IconButton(
                        onClick = onDeleteBlock,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        BlockType.CALLOUT -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SoftOrangeLight.copy(alpha = 0.5f))
                    .border(1.dp, VibrantOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = block.calloutIcon,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 8.dp)
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

                    IconButton(
                        onClick = onDeleteBlock,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = "Sil",
                            tint = CharcoalGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
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

                IconButton(
                    onClick = onDeleteBlock,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CharcoalGray.copy(alpha = 0.7f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cancel),
                        contentDescription = "Sil",
                        tint = PureWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        BlockType.AUDIO -> {
            SpotifyAudioPlayer(
                filePath = block.mediaPath ?: "",
                audioRecorder = audioRecorder,
                onDelete = onDeleteBlock,
                modifier = modifier
            )
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

                if (block.content.isBlank()) {
                    IconButton(
                        onClick = onDeleteBlock,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = "Sil",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
