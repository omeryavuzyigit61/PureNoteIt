package com.notes.purenoteit.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.widget.Toast
import com.notes.purenoteit.domain.model.CodeLanguageCapabilities
import com.notes.purenoteit.domain.model.OfflineExecutionCapability
import com.notes.purenoteit.ui.mvi.BlockExecutionUiState
import com.notes.purenoteit.util.MathCategory
import com.notes.purenoteit.util.MathFormulaRenderer
import com.notes.purenoteit.util.MathFormulaView
import com.notes.purenoteit.util.MathSymbol
import com.notes.purenoteit.util.SyntaxHighlighter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.notes.purenoteit.R
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.SoftYellowLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import com.notes.purenoteit.util.MediaFileUriHelper
import java.io.File
import java.util.Locale

// Markdown Highlighter (==highlight==), Bold (**bold**) & Hashtag (#tag) Visual Transformation
private val NotionMarkdownVisualTransformation = VisualTransformation { text ->
    val raw = text.text
    val builder = AnnotatedString.Builder(raw)

    // Highlighter: ==highlighted text==
    val highlightRegex = Regex("""==([^=\n]+)==""")
    for (match in highlightRegex.findAll(raw)) {
        builder.addStyle(
            SpanStyle(
                background = Color(0xFFFFF59D), // Warm pastel highlighter yellow
                color = Color(0xFF212121),
                fontWeight = FontWeight.SemiBold
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }

    // Bold: **bold text**
    val boldRegex = Regex("""\*\*([^*\n]+)\*\*""")
    for (match in boldRegex.findAll(raw)) {
        builder.addStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }

    // Hashtags: #tag
    val tagRegex = Regex("""#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)""")
    for (match in tagRegex.findAll(raw)) {
        builder.addStyle(
            SpanStyle(
                color = VibrantOrange,
                fontWeight = FontWeight.Bold
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
    }

    TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
}

/**
 * Notion Block Row Component.
 * Supports:
 * - Fluid whole-item finger drag & drop (long press to reorder across the entire block)
 * - Soft spring expansion ("tombikleşme" scale 1.05x and soft elevation shadow)
 * - Notion ⠿ handle + 1-click ▲ / ▼ precision buttons
 * - Drawing block protection (dedicated top-bar reorder to avoid gesture conflict)
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
    onEditDrawing: ((String) -> Unit)? = null,
    onPreviewMedia: ((path: String, isDrawing: Boolean, onEditAction: (() -> Unit)?) -> Unit)? = null,
    onToggleExpand: (() -> Unit)? = null,
    onToggleBodyChange: ((String) -> Unit)? = null,
    onTableChange: ((List<List<String>>) -> Unit)? = null,
    onOpenLinkedNote: ((String) -> Unit)? = null,
    onOpenSubPage: ((NoteBlock) -> Unit)? = null,
    onCodeLanguageChange: ((String) -> Unit)? = null,
    codeExecutionState: BlockExecutionUiState? = null,
    onRunCode: ((blockId: String, language: String, code: String) -> Unit)? = null,
    onClearCodeOutput: ((blockId: String) -> Unit)? = null,
    onOpenExternalPlayground: ((language: String, code: String) -> Unit)? = null,
    allBlocks: List<NoteBlock> = emptyList(),
    onScrollToBlock: ((Int) -> Unit)? = null,
    mediaPathResolver: MediaPathResolver? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDraggingBlock by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }
    var lastMoveTimestamp by remember { mutableLongStateOf(0L) }

    // Tombik Scale & Soft Elevation Shadow with Spring Physics
    val tombikScale by animateFloatAsState(
        targetValue = if (isDraggingBlock) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tombikScale"
    )

    val tombikElevation by animateDpAsState(
        targetValue = if (isDraggingBlock) 14.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "tombikElevation"
    )

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

    // Reorder Controls Bar
    @Composable
    fun BlockReorderControls(
        isDarkContainer: Boolean = false,
        isDrawingBlock: Boolean = false,
        controlModifier: Modifier = Modifier
    ) {
        Row(
            modifier = controlModifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isDraggingBlock) VibrantOrange
                    else if (isDarkContainer) CharcoalGray.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Notion ⠿ Instant Touch-and-Drag Handle (0ms latency, zero text conflict)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .pointerInput(block.id) {
                        detectDragGestures(
                            onDragStart = {
                                isDraggingBlock = true
                                dragAccumulator = 0f
                                lastMoveTimestamp = System.currentTimeMillis()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDraggingBlock = false
                                dragAccumulator = 0f
                            },
                            onDragCancel = {
                                isDraggingBlock = false
                                dragAccumulator = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val now = System.currentTimeMillis()
                                if (now - lastMoveTimestamp < 320L) {
                                    dragAccumulator = 0f
                                    return@detectDragGestures
                                }
                                dragAccumulator += dragAmount.y
                                val threshold = 50f
                                if (dragAccumulator < -threshold && onMoveUp != null) {
                                    onMoveUp()
                                    lastMoveTimestamp = now
                                    dragAccumulator = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (dragAccumulator > threshold && onMoveDown != null) {
                                    onMoveDown()
                                    lastMoveTimestamp = now
                                    dragAccumulator = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⠿",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDraggingBlock || isDarkContainer) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            if (onMoveUp != null) {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text(
                        text = "▲",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkContainer || isDraggingBlock) PureWhite else MaterialTheme.colorScheme.onSurface
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
                        color = if (isDarkContainer || isDraggingBlock) PureWhite else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(
                onClick = onDeleteBlock,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.cancel),
                    contentDescription = stringResource(R.string.delete),
                    tint = if (isDarkContainer || isDraggingBlock) PureWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }

    // Whole-block Long-Press Drag Modifier with Anti-Tremble / Anti-Jitter Cooldown
    val wholeBlockLongPressModifier = if (block.type != BlockType.DRAWING) {
        Modifier.pointerInput(block.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    isDraggingBlock = true
                    dragAccumulator = 0f
                    lastMoveTimestamp = System.currentTimeMillis()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragEnd = {
                    isDraggingBlock = false
                    dragAccumulator = 0f
                },
                onDragCancel = {
                    isDraggingBlock = false
                    dragAccumulator = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val now = System.currentTimeMillis()
                    // 320ms anti-tremble cooldown: prevents the infinite oscillation bug when index changes
                    if (now - lastMoveTimestamp < 320L) {
                        dragAccumulator = 0f
                        return@detectDragGesturesAfterLongPress
                    }

                    dragAccumulator += dragAmount.y
                    val threshold = 55f
                    if (dragAccumulator < -threshold && onMoveUp != null) {
                        onMoveUp()
                        lastMoveTimestamp = now
                        dragAccumulator = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } else if (dragAccumulator > threshold && onMoveDown != null) {
                        onMoveDown()
                        lastMoveTimestamp = now
                        dragAccumulator = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    // Root Tombik Container with Spring Physics (Supports Long Press Anywhere + Handle Drag)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDraggingBlock) 10f else 1f)
            .graphicsLayer {
                scaleX = tombikScale
                scaleY = tombikScale
                shadowElevation = tombikElevation.toPx()
            }
            .then(
                if (isDraggingBlock) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, VibrantOrange.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                } else {
                    Modifier
                }
            )
            .then(wholeBlockLongPressModifier)
    ) {
        when (block.type) {
            BlockType.DIVIDER -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    BlockReorderControls(controlModifier = Modifier.padding(start = 6.dp))
                }
            }

            BlockType.TODO -> {
                Row(
                    modifier = Modifier
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
                        visualTransformation = NotionMarkdownVisualTransformation,
                        cursorBrush = SolidColor(VibrantOrange),
                        decorationBox = { innerTextField ->
                            if (block.content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.block_todo_placeholder),
                                    style = textStyle.copy(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                    )
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                }
            }

            BlockType.BULLET_LIST -> {
                Row(
                    modifier = Modifier
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
                        visualTransformation = NotionMarkdownVisualTransformation,
                        cursorBrush = SolidColor(VibrantOrange),
                        decorationBox = { innerTextField ->
                            if (block.content.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.block_bullet_placeholder),
                                    style = textStyle.copy(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                    )
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                }
            }

            BlockType.CALLOUT -> {
                Surface(
                    modifier = Modifier
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
                        ModernCalloutIconBadge(
                            calloutKey = block.calloutIcon,
                            modifier = Modifier.padding(end = 10.dp)
                        )

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = textStyle,
                            visualTransformation = NotionMarkdownVisualTransformation,
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { innerTextField ->
                                if (block.content.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.block_callout_placeholder),
                                        style = textStyle.copy(
                                            color = CharcoalGray.copy(alpha = 0.45f)
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            BlockType.TOGGLE -> {
                val arrowRotation by animateFloatAsState(
                    targetValue = if (block.isExpanded) 90f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "toggleArrow"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onToggleExpand?.invoke() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(
                                text = "▶",
                                fontSize = 12.sp,
                                color = VibrantOrange,
                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = textStyle.copy(fontWeight = FontWeight.SemiBold),
                            visualTransformation = NotionMarkdownVisualTransformation,
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { innerTextField ->
                                if (block.content.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.block_toggle_placeholder),
                                        style = textStyle.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                    }

                    AnimatedVisibility(
                        visible = block.isExpanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 6.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(VibrantOrange.copy(alpha = 0.35f))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            BasicTextField(
                                value = block.toggleBody,
                                onValueChange = { onToggleBodyChange?.invoke(it) },
                                textStyle = textStyle.copy(fontSize = 13.5.sp),
                                cursorBrush = SolidColor(VibrantOrange),
                                decorationBox = { innerTextField ->
                                    if (block.toggleBody.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.block_toggle_body_placeholder),
                                            style = textStyle.copy(
                                                fontSize = 13.5.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                                            )
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            BlockType.TABLE -> {
                val tableRows = if (block.tableData.isNotEmpty()) block.tableData else listOf(
                    listOf("Öğe", "Tutar (₺)", "Not"),
                    listOf("Örnek 1", "150", "Onaylandı"),
                    listOf("Örnek 2", "350", "Beklemede")
                )

                // Calculate column 1 (numeric) sum if present
                val numericSum = remember(tableRows) {
                    var sum = 0.0
                    var hasNumeric = false
                    for (i in 1..tableRows.lastIndex) {
                        val row = tableRows[i]
                        if (row.size > 1) {
                            val parsed = row[1].trim().replace(",", ".").toDoubleOrNull()
                            if (parsed != null) {
                                sum += parsed
                                hasNumeric = true
                            }
                        }
                    }
                    if (hasNumeric) sum else null
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_table),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.block_dynamic_table),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            BlockReorderControls()
                        }

                        // Table Grid
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            tableRows.forEachIndexed { rIndex, row ->
                                val isHeader = rIndex == 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isHeader) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEachIndexed { cIndex, cellText ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            BasicTextField(
                                                value = cellText,
                                                onValueChange = { newCell ->
                                                    val updated = tableRows.mapIndexed { r, ro ->
                                                        if (r == rIndex) {
                                                            ro.mapIndexed { c, v -> if (c == cIndex) newCell else v }
                                                        } else ro
                                                    }
                                                    onTableChange?.invoke(updated)
                                                },
                                                textStyle = textStyle.copy(
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isHeader) VibrantOrange else MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(VibrantOrange),
                                                decorationBox = { inner ->
                                                    if (cellText.isEmpty()) {
                                                        Text(
                                                            text = if (isHeader) stringResource(R.string.block_table_column, cIndex + 1) else "...",
                                                            style = textStyle.copy(
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                            )
                                                        )
                                                    }
                                                    inner()
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                if (rIndex < tableRows.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                    )
                                }
                            }
                        }

                        // Footer: Add Row + Numeric Sum
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SoftOrangeLight)
                                    .clickable {
                                        val colCount = if (tableRows.isNotEmpty()) tableRows[0].size else 3
                                        val newRow = List(colCount) { "" }
                                        val updated = tableRows + listOf(newRow)
                                        onTableChange?.invoke(updated)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                Text(
                                    text = stringResource(R.string.block_table_add_row),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }

                            if (numericSum != null) {
                                Text(
                                    text = stringResource(R.string.block_table_sum) + " " + if (numericSum % 1.0 == 0.0) "${numericSum.toLong()} ₺" else "${String.format(Locale.US, "%.2f", numericSum)} ₺",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }
                        }
                    }
                }
            }

            BlockType.CODE -> {
                var showLangMenu by remember { mutableStateOf(false) }
                var isCopied by remember { mutableStateOf(false) }
                val context = LocalContext.current
                var currentLang by remember(block.id, block.codeLanguage) {
                    mutableStateOf(block.codeLanguage.ifBlank { "kotlin" })
                }
                val capability = CodeLanguageCapabilities.capabilityFor(currentLang)
                val isRunning = codeExecutionState?.isRunning == true

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E24),
                    border = BorderStroke(1.dp, Color(0xFF33333D)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF2C2C35),
                                        border = BorderStroke(0.5.dp, Color(0xFF454552)),
                                        modifier = Modifier.clickable { showLangMenu = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val langLabel = SyntaxHighlighter.SUPPORTED_LANGUAGES.find { it.first.equals(currentLang, true) }?.second ?: currentLang.replaceFirstChar { it.uppercase() }
                                            Text(
                                                text = "$langLabel ▾",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFB74D)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showLangMenu,
                                        onDismissRequest = { showLangMenu = false },
                                        modifier = Modifier
                                            .background(Color(0xFF2C2C35))
                                            .border(0.5.dp, Color(0xFF454552), RoundedCornerShape(12.dp))
                                    ) {
                                        SyntaxHighlighter.SUPPORTED_LANGUAGES.forEach { (key, label) ->
                                            val isSelected = currentLang.equals(key, ignoreCase = true)
                                            val isOfflineExecutable = CodeLanguageCapabilities.isFullyExecutableOffline(key)
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = (if (isSelected) "✓  " else "    ") + label,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) Color(0xFFFFB74D) else PureWhite,
                                                            fontSize = 13.sp
                                                        )
                                                        if (isOfflineExecutable) {
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                text = stringResource(R.string.code_offline_badge),
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF50FA7B),
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Color(0xFF50FA7B).copy(alpha = 0.14f))
                                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    showLangMenu = false
                                                    currentLang = key
                                                    block.codeLanguage = key
                                                    onCodeLanguageChange?.invoke(key)
                                                    onClearCodeOutput?.invoke(block.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onRunCode != null) {
                                    val runLabel = when (capability) {
                                        OfflineExecutionCapability.SQL_IN_MEMORY,
                                        OfflineExecutionCapability.JAVASCRIPT_SANDBOX -> "▶ " + stringResource(R.string.code_run)
                                        OfflineExecutionCapability.HTML_PREVIEW -> "▶ " + stringResource(R.string.code_preview)
                                        OfflineExecutionCapability.JSON_FORMAT_VALIDATE -> "⚡ " + stringResource(R.string.code_validate)
                                        OfflineExecutionCapability.SYNTAX_CHECK_ONLY -> "✓ " + stringResource(R.string.code_check_syntax)
                                    }
                                    val isFullRunner = capability != OfflineExecutionCapability.SYNTAX_CHECK_ONLY
                                    val runBg = if (isFullRunner) Color(0xFF1F4E32) else Color(0xFF282834)
                                    val runBorder = if (isFullRunner) Color(0xFF3E8E5A) else Color(0xFF454555)
                                    val runTint = if (isFullRunner) Color(0xFF80FFAC) else Color(0xFF8BE9FD)

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = runBg,
                                        border = BorderStroke(0.5.dp, runBorder),
                                        modifier = Modifier
                                            .padding(end = 5.dp)
                                            .clickable(enabled = !isRunning) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onRunCode(block.id, currentLang, block.content)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isRunning) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(10.dp),
                                                    strokeWidth = 1.5.dp,
                                                    color = runTint
                                                )
                                            } else {
                                                Text(
                                                    text = runLabel,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = runTint
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCopied) Color(0xFF2E7D32) else Color(0xFF2C2C35),
                                    border = BorderStroke(0.5.dp, Color(0xFF454552)),
                                    modifier = Modifier.clickable {
                                        val clipManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        clipManager?.setPrimaryClip(android.content.ClipData.newPlainText("Code", block.content))
                                        isCopied = true
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ isCopied = false }, 2000L)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = if (isCopied) R.drawable.ic_checklist else R.drawable.ic_subpage_doc),
                                            contentDescription = if (isCopied) stringResource(R.string.code_copied) else stringResource(R.string.code_copy),
                                            tint = if (isCopied) PureWhite else Color(0xFFE0E0E0),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(5.dp))
                                BlockReorderControls(isDarkContainer = true)
                            }
                        }

                        val codeVisualTransformation = remember(currentLang) {
                            VisualTransformation { text ->
                                TransformedText(
                                    SyntaxHighlighter.highlight(text.text, currentLang, isDark = true),
                                    OffsetMapping.Identity
                                )
                            }
                        }

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = PureWhite,
                                lineHeight = 19.sp
                            ),
                            visualTransformation = codeVisualTransformation,
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { innerTextField ->
                                if (block.content.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.code_input_placeholder),
                                        style = TextStyle(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            color = Color(0xFF757585),
                                            fontStyle = FontStyle.Italic
                                        )
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        if (codeExecutionState != null && (codeExecutionState.output != null || codeExecutionState.error != null)) {
                            val formatToastMsg = stringResource(R.string.code_json_applied_toast)
                            CodeBlockExecutionPanel(
                                executionState = codeExecutionState,
                                hasExternalPlayground = CodeLanguageCapabilities.externalPlaygroundUrl(currentLang) != null,
                                onApplyFormattedJson = { formatted ->
                                    onContentChange(formatted)
                                    Toast.makeText(context, formatToastMsg, Toast.LENGTH_SHORT).show()
                                },
                                onOpenExternalPlayground = {
                                    onOpenExternalPlayground?.invoke(currentLang, block.content)
                                },
                                onClearOutput = {
                                    onClearCodeOutput?.invoke(block.id)
                                }
                            )
                        }
                    }
                }
            }

            BlockType.MATH -> {
                var isEditingMath by rememberSaveable { mutableStateOf(block.content.isBlank()) }
                var selectedCategory by rememberSaveable { mutableStateOf(MathCategory.TEMEL) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isEditingMath) VibrantOrange.copy(alpha = 0.6f) else Color(0xFFFFB74D).copy(alpha = 0.4f)
                    ),
                    shadowElevation = if (isEditingMath) 3.dp else 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "∑",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                                Text(
                                    text = if (isEditingMath) stringResource(R.string.slash_math_title) else "Matematik Formülü",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (isEditingMath) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VibrantOrange,
                                        modifier = Modifier.clickable {
                                            isEditingMath = false
                                        }
                                    ) {
                                        Text(
                                            text = "Tamam ✓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PureWhite,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = VibrantOrange.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable {
                                            isEditingMath = true
                                        }
                                    ) {
                                        Text(
                                            text = "Düzenle ✎",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VibrantOrange,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            BlockReorderControls()
                        }

                        if (!isEditingMath && block.content.isNotBlank()) {
                            // PREVIEW ONLY MODE: Clean equation display with zero text-box redundancy
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { isEditingMath = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFE082).copy(alpha = 0.6f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp, horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MathFormulaView(
                                        latex = block.content,
                                        fontSize = 19.sp,
                                        isDark = isSystemInDarkTheme()
                                    )
                                }
                            }
                        } else {
                            // EDITING MODE: Category tabs, extended symbols palette, input field, and live preview
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Category Tabs
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MathCategory.values().forEach { category ->
                                        val isSelected = category == selectedCategory
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.clickable { selectedCategory = category }
                                        ) {
                                            Text(
                                                text = category.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // 2. Palette Content
                                if (selectedCategory == MathCategory.SABLONLAR) {
                                    // Template formulas list
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val templates = MathFormulaRenderer.CATEGORIZED_SYMBOLS[MathCategory.SABLONLAR] ?: emptyList()
                                        templates.forEach { tmpl ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SoftOrangeLight,
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, VibrantOrange.copy(alpha = 0.4f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onContentChange(tmpl.insertText)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = tmpl.label,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = VibrantOrange
                                                    )
                                                    Text(
                                                        text = tmpl.insertText,
                                                        fontSize = 10.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Grid of symbol buttons (5 per row)
                                    val currentSymbols = MathFormulaRenderer.CATEGORIZED_SYMBOLS[selectedCategory] ?: emptyList()
                                    val chunked = remember(currentSymbols) {
                                        val perRow = 5
                                        currentSymbols.chunked(perRow)
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        chunked.forEach { rowSymbols ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                rowSymbols.forEach { sym ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = SoftOrangeLight,
                                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, VibrantOrange.copy(alpha = 0.4f)),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                onContentChange(block.content + sym.insertText)
                                                            }
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(vertical = 5.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = sym.label,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = VibrantOrange,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                                repeat(5 - rowSymbols.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. LaTeX Monospace Text Editor
                                BasicTextField(
                                    value = block.content,
                                    onValueChange = onContentChange,
                                    textStyle = textStyle.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 13.5.sp
                                    ),
                                    cursorBrush = SolidColor(VibrantOrange),
                                    decorationBox = { inner ->
                                        if (block.content.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.math_input_placeholder),
                                                style = textStyle.copy(
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            )
                                        }
                                        inner()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                )

                                // 4. Live Rendered Preview (during edit mode)
                                if (block.content.isNotBlank()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFFE082).copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.math_preview_title),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF57F17)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            MathFormulaView(
                                                latex = block.content,
                                                fontSize = 17.sp,
                                                isDark = isSystemInDarkTheme()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BlockType.TABLE_OF_CONTENTS -> {
                val headings = remember(allBlocks) {
                    allBlocks.mapIndexedNotNull { idx, b ->
                        if ((b.type == BlockType.HEADING_1 || b.type == BlockType.HEADING_2) && b.content.isNotBlank()) {
                            Triple(idx, b.type, b.content)
                        } else null
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📑", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.toc_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            BlockReorderControls()
                        }

                        if (headings.isEmpty()) {
                            Text(
                                text = stringResource(R.string.toc_no_headings),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            headings.forEach { (index, type, text) ->
                                val indent = if (type == BlockType.HEADING_2) 16.dp else 0.dp
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = indent, top = 3.dp, bottom = 3.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onScrollToBlock?.invoke(index) }
                                        .padding(vertical = 3.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (type == BlockType.HEADING_1) "•" else "◦",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = text,
                                        fontSize = if (type == BlockType.HEADING_1) 13.sp else 12.sp,
                                        fontWeight = if (type == BlockType.HEADING_1) FontWeight.Bold else FontWeight.Medium,
                                        color = VibrantOrange,
                                        textDecoration = TextDecoration.Underline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BlockType.IMAGE -> {
                val parsedWidth = remember(block.content) {
                    try {
                        if (block.content.startsWith("{") && block.content.contains("w")) {
                            org.json.JSONObject(block.content).optInt("w", 100).coerceIn(30, 100)
                        } else 100
                    } catch (e: Exception) { 100 }
                }

                val parsedHeight = remember(block.content) {
                    try {
                        if (block.content.startsWith("{") && block.content.contains("h")) {
                            org.json.JSONObject(block.content).optDouble("h", 260.0).toFloat().coerceIn(120f, 900f)
                        } else 260f
                    } catch (e: Exception) { 260f }
                }

                val parsedLock = remember(block.content) {
                    try {
                        if (block.content.startsWith("{") && block.content.contains("lock")) {
                            org.json.JSONObject(block.content).optBoolean("lock", true)
                        } else true
                    } catch (e: Exception) { true }
                }

                var currentHeightDp by remember(block.id) { mutableFloatStateOf(parsedHeight) }
                var currentWidthPercent by remember(block.id) { mutableFloatStateOf(parsedWidth.toFloat()) }
                var isRatioLocked by remember(block.id) { mutableStateOf(parsedLock) }
                var isDraggingResize by remember { mutableStateOf(false) }
                var initialRatio by remember(block.id) { mutableFloatStateOf(2.6f) }

                LaunchedEffect(parsedHeight) {
                    if (!isDraggingResize) {
                        currentHeightDp = parsedHeight
                    }
                }
                LaunchedEffect(parsedWidth) {
                    if (!isDraggingResize) {
                        currentWidthPercent = parsedWidth.toFloat()
                    }
                }
                LaunchedEffect(parsedLock) {
                    isRatioLocked = parsedLock
                }

                val currentWidthPercentState by rememberUpdatedState(currentWidthPercent)
                val currentHeightDpState by rememberUpdatedState(currentHeightDp)
                val isRatioLockedState by rememberUpdatedState(isRatioLocked)

                fun persistImageConfig(w: Int, h: Float, lock: Boolean) {
                    try {
                        val json = org.json.JSONObject().apply {
                            put("w", w)
                            put("h", h.toInt())
                            put("lock", lock)
                        }.toString()
                        onContentChange(json)
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // Full-width Header: Controls and presets on Left, Block controls & Delete 'X' on Right
                    // NEVER squished or clipped because it fills the full width of the editor!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isRatioLocked) SoftOrangeLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isRatioLocked) VibrantOrange.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.clickable {
                                    isRatioLocked = !isRatioLocked
                                    persistImageConfig(currentWidthPercent.roundToInt(), currentHeightDp, isRatioLocked)
                                }
                            ) {
                                Text(
                                    text = if (isRatioLocked) "🔒 Oran Kilitli" else "🔓 Serbest Oran",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRatioLocked) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }

                            listOf(100, 75, 50).forEach { pct ->
                                val isSelected = currentWidthPercent.roundToInt() == pct
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        currentWidthPercent = pct.toFloat()
                                        persistImageConfig(pct, currentHeightDp, isRatioLocked)
                                    }
                                ) {
                                    Text(
                                        text = "%$pct",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            val imageMediaPath = block.mediaPath
                            if (!imageMediaPath.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SoftOrangeLight,
                                    border = BorderStroke(0.8.dp, VibrantOrange.copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable {
                                        onPreviewMedia?.invoke(imageMediaPath, false, null)
                                    }
                                ) {
                                    Text(
                                        text = "🔍 Büyüt",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        BlockReorderControls(
                            controlModifier = Modifier.padding(start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Centered Resizable Image Surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(currentWidthPercent / 100f)
                                .height(currentHeightDp.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isDraggingResize) 2.dp else 1.dp,
                                if (isDraggingResize) VibrantOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            shadowElevation = if (isDraggingResize) 6.dp else 1.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val mediaContext = androidx.compose.ui.platform.LocalContext.current
                                val resolvedImageFile = MediaFileUriHelper.resolveReadableFile(
                                    context = mediaContext,
                                    rawPath = block.mediaPath,
                                    mediaPathResolver = mediaPathResolver
                                )
                                AsyncImage(
                                    model = resolvedImageFile ?: File(block.mediaPath ?: ""),
                                    contentDescription = stringResource(R.string.block_image_desc),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val imgPath = block.mediaPath
                                            if (!imgPath.isNullOrBlank()) {
                                                onPreviewMedia?.invoke(imgPath, false, null)
                                            }
                                        },
                                    contentScale = if (isRatioLocked) ContentScale.Fit else ContentScale.Crop
                                )

                                // Modern Corner Resize Handle Badge with Sleek Vector Icon
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (isDraggingResize) VibrantOrange else Color(0xCC1F1F24)
                                        )
                                        .border(
                                            1.dp,
                                            if (isDraggingResize) PureWhite else PureWhite.copy(alpha = 0.5f),
                                            RoundedCornerShape(9.dp)
                                        )
                                        .pointerInput(block.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    isDraggingResize = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val w = currentWidthPercentState.coerceAtLeast(10f)
                                                    initialRatio = (currentHeightDpState / w).coerceIn(0.5f, 15f)
                                                },
                                                onDragEnd = {
                                                    isDraggingResize = false
                                                    persistImageConfig(currentWidthPercentState.roundToInt(), currentHeightDpState, isRatioLockedState)
                                                },
                                                onDragCancel = {
                                                    isDraggingResize = false
                                                    persistImageConfig(currentWidthPercentState.roundToInt(), currentHeightDpState, isRatioLockedState)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val dy = dragAmount.y
                                                    val dx = dragAmount.x
                                                    if (isRatioLockedState) {
                                                        // Diagonally locked scaling: Both width and height scale in exact lockstep
                                                        val delta = (dy + (dx * 1.5f)) / 2f
                                                        val newHeight = (currentHeightDp + delta).coerceIn(120f, 900f)
                                                        currentHeightDp = newHeight
                                                        currentWidthPercent = (newHeight / initialRatio).coerceIn(30f, 100f)
                                                    } else {
                                                        // Free independent scaling: Smooth like butter in both X and Y
                                                        currentHeightDp = (currentHeightDp + dy).coerceIn(120f, 900f)
                                                        currentWidthPercent = (currentWidthPercent + (dx / 6f)).coerceIn(30f, 100f)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_resize_handle),
                                        contentDescription = "Resize",
                                        tint = PureWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            BlockType.DRAWING -> {
                val drawingPath = block.mediaPath
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, BorderLight.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                        .background(PureWhite)
                ) {
                    val drawingContext = androidx.compose.ui.platform.LocalContext.current
                    val resolvedDrawingFile = MediaFileUriHelper.resolveReadableFile(
                        context = drawingContext,
                        rawPath = drawingPath,
                        mediaPathResolver = mediaPathResolver
                    )
                    AsyncImage(
                        model = resolvedDrawingFile ?: File(drawingPath ?: ""),
                        contentDescription = stringResource(R.string.block_drawing_desc),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (onEditDrawing != null && !drawingPath.isNullOrBlank()) {
                                    onEditDrawing(drawingPath)
                                }
                            }
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )

                    // Top Action Bar for Drawing: Read-Only Zoom Button + Edit Button + Reorder Controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!drawingPath.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CharcoalGray.copy(alpha = 0.88f))
                                    .clickable {
                                        onPreviewMedia?.invoke(
                                            drawingPath,
                                            true,
                                            if (onEditDrawing != null) ({ onEditDrawing(drawingPath) }) else null
                                        )
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "🔍", fontSize = 11.sp)
                                    Text(
                                        text = "Büyüt",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite
                                    )
                                }
                            }
                        }

                        if (onEditDrawing != null && !drawingPath.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VibrantOrange)
                                    .clickable { onEditDrawing(drawingPath) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "✏️", fontSize = 11.sp)
                                    Text(
                                        text = stringResource(R.string.edit),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite
                                    )
                                }
                            }
                        }

                        BlockReorderControls(
                            isDarkContainer = true,
                            isDrawingBlock = true
                        )
                    }
                }
            }

            BlockType.AUDIO -> {
                Column(
                    modifier = Modifier
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

            BlockType.WEB_BOOKMARK -> {
                val context = LocalContext.current
                val fallbackDomain = stringResource(R.string.block_web_bookmark)
                val domain = remember(block.content) {
                    try {
                        Uri.parse(block.content).host ?: fallbackDomain
                    } catch (e: Exception) {
                        fallbackDomain
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tpl_globe),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = block.content,
                                onValueChange = onContentChange,
                                textStyle = textStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                cursorBrush = SolidColor(VibrantOrange),
                                decorationBox = { inner ->
                                    if (block.content.isEmpty()) {
                                        Text(stringResource(R.string.block_web_bookmark_placeholder), style = textStyle.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                                    }
                                    inner()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (block.content.isNotBlank()) {
                                Text(
                                    text = domain,
                                    fontSize = 11.sp,
                                    color = VibrantOrange,
                                    modifier = Modifier
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(if (block.content.startsWith("http")) block.content else "https://${block.content}"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Invalid url
                                            }
                                        }
                                        .padding(top = 2.dp)
                                )
                            }
                        }

                        BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            BlockType.SUB_PAGE -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenSubPage?.invoke(block) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f)),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_subpage_doc),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { inner ->
                                if (block.content.isEmpty()) {
                                    Text(stringResource(R.string.block_subpage_placeholder), style = textStyle.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                                }
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = stringResource(R.string.block_subpage_open),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange,
                            modifier = Modifier
                                .clickable { onOpenSubPage?.invoke(block) }
                                .padding(horizontal = 6.dp)
                        )

                        BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            else -> { // PARAGRAPH, HEADING_1, HEADING_2
                val detectedLinks = remember(block.content) {
                    Regex("""\[\[(.*?)\]\]""").findAll(block.content)
                        .map { it.groupValues[1].trim() }
                        .filter { it.isNotBlank() }
                        .toList()
                }

                val detectedHashtags = remember(block.content) {
                    Regex("""#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)""").findAll(block.content)
                        .map { "#" + it.groupValues[1] }
                        .distinct()
                        .toList()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (block.type == BlockType.HEADING_1 || block.type == BlockType.HEADING_2) {
                            ModernHeadingIconBadge(
                                headingType = block.type,
                                content = block.content,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = textStyle,
                            visualTransformation = NotionMarkdownVisualTransformation,
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { innerTextField ->
                                if (block.content.isEmpty()) {
                                    val placeholderText = when (block.type) {
                                        BlockType.HEADING_1 -> stringResource(R.string.block_h1_placeholder)
                                        BlockType.HEADING_2 -> stringResource(R.string.block_h2_placeholder)
                                        else -> stringResource(R.string.block_paragraph_placeholder)
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

                        BlockReorderControls(controlModifier = Modifier.padding(start = 4.dp))
                    }

                    // Clickable pills for [[Links]] inside paragraph
                    if (detectedLinks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (link in detectedLinks) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenLinkedNote?.invoke(link) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftOrangeLight,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_link_hub),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = link,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VibrantOrange
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Interactive pills for #Hashtags inside paragraph (Feature 3)
                    if (detectedHashtags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (tag in detectedHashtags) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftOrangeLight,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
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
