package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notes.purenoteit.R
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import java.io.File

@Composable
fun ZenReadingModeView(
    title: String,
    icon: String?,
    blocks: List<NoteBlock>,
    wordCount: Int,
    readingTimeMinutes: Int,
    audioRecorder: AudioRecorder,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zenPreviewPath by remember { mutableStateOf<String?>(null) }
    var zenPreviewIsDrawing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(64.dp)) // Space for floating Zen bar

                // Page Icon
                if (!icon.isNullOrBlank()) {
                    Text(
                        text = icon,
                        fontSize = 38.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Zen Title
                Text(
                    text = if (title.isBlank()) stringResource(R.string.untitled_note) else title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stats row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📖 " + stringResource(R.string.zen_mode_badge),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VibrantOrange
                    )
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.zen_words, wordCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.zen_reading_time, readingTimeMinutes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))
            }

            items(blocks) { block ->
                ZenBlockContent(
                    block = block,
                    audioRecorder = audioRecorder,
                    onPreviewMedia = { path, isDrawing ->
                        zenPreviewPath = path
                        zenPreviewIsDrawing = isDrawing
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Zen Control Pill at the Top
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.8f)),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🧘", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.zen_mode_badge),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Exit Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onExit() },
                    shape = RoundedCornerShape(12.dp),
                    color = VibrantOrange
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✕ " + stringResource(R.string.zen_return_edit),
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        zenPreviewPath?.let { path ->
            MediaLightboxDialog(
                imagePath = path,
                isDrawing = zenPreviewIsDrawing,
                onDismiss = { zenPreviewPath = null }
            )
        }
    }
}

@Composable
private fun ZenBlockContent(
    block: NoteBlock,
    audioRecorder: AudioRecorder,
    onPreviewMedia: (path: String, isDrawing: Boolean) -> Unit
) {
    when (block.type) {
        BlockType.HEADING_1 -> {
            Text(
                text = renderAnnotatedMarkdown(block.content),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 28.sp
            )
        }

        BlockType.HEADING_2 -> {
            Text(
                text = renderAnnotatedMarkdown(block.content),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 24.sp
            )
        }

        BlockType.PARAGRAPH -> {
            if (block.content.isNotBlank()) {
                Text(
                    text = renderAnnotatedMarkdown(block.content),
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        BlockType.TODO -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (block.isChecked) VibrantOrange else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (block.isChecked) VibrantOrange else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (block.isChecked) {
                        Text(text = "✓", color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = renderAnnotatedMarkdown(block.content),
                    fontSize = 15.sp,
                    color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                    textDecoration = if (block.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 22.sp
                )
            }
        }

        BlockType.BULLET_LIST -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp, end = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(VibrantOrange)
                )
                Text(
                    text = renderAnnotatedMarkdown(block.content),
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        BlockType.CALLOUT -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = block.calloutIcon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = renderAnnotatedMarkdown(block.content),
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        BlockType.DIVIDER -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        BlockType.IMAGE -> {
            val imgPath = block.mediaPath
            if (!imgPath.isNullOrBlank()) {
                val file = File(imgPath)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .clickable { onPreviewMedia(imgPath, false) }
                    )
                }
            }
        }

        BlockType.DRAWING -> {
            val drawingPath = block.mediaPath
            if (!drawingPath.isNullOrBlank()) {
                val file = File(drawingPath)
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = stringResource(R.string.tb_drawing),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .clickable { onPreviewMedia(drawingPath, true) }
                    )
                }
            }
        }

        BlockType.AUDIO -> {
            if (!block.mediaPath.isNullOrBlank()) {
                SpotifyAudioPlayer(
                    filePath = block.mediaPath ?: "",
                    audioRecorder = audioRecorder,
                    onDelete = {},
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        else -> {
            if (block.content.isNotBlank()) {
                Text(
                    text = renderAnnotatedMarkdown(block.content),
                    fontSize = 15.5.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * Parses ==highlighted text== into yellow background, **bold** into Bold font,
 * and #tags into vibrant orange text.
 */
private fun renderAnnotatedMarkdown(raw: String): AnnotatedString {
    return buildAnnotatedString {
        append(raw)

        // Highlights ==...==
        val highlightRegex = Regex("""==([^=\n]+)==""")
        for (match in highlightRegex.findAll(raw)) {
            addStyle(
                SpanStyle(
                    background = Color(0xFFFFF59D),
                    color = Color(0xFF212121),
                    fontWeight = FontWeight.SemiBold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // Bold **...**
        val boldRegex = Regex("""\*\*([^*\n]+)\*\*""")
        for (match in boldRegex.findAll(raw)) {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                start = match.range.first,
                end = match.range.last + 1
            )
        }

        // Hashtags #...
        val tagRegex = Regex("""#([a-zA-Z0-9ığüşöçİĞÜŞÖÇ_-]+)""")
        for (match in tagRegex.findAll(raw)) {
            addStyle(
                SpanStyle(
                    color = Color(0xFFFF6D00),
                    fontWeight = FontWeight.Bold
                ),
                start = match.range.first,
                end = match.range.last + 1
            )
        }
    }
}
