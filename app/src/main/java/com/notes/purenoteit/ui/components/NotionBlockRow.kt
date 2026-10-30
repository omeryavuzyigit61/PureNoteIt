package com.notes.purenoteit.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import java.util.Locale

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
    onToggleExpand: (() -> Unit)? = null,
    onToggleBodyChange: ((String) -> Unit)? = null,
    onTableChange: ((List<List<String>>) -> Unit)? = null,
    onOpenLinkedNote: ((String) -> Unit)? = null,
    onOpenSubPage: ((String) -> Unit)? = null,
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
                    contentDescription = "Sil",
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
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { innerTextField ->
                                if (block.content.isEmpty()) {
                                    Text(
                                        text = "Açılır başlık (Toggle)...",
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
                                            text = "Gizlenen içerik detayları...",
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
                                Text(text = "📊", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dinamik Tablo",
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
                                                            text = if (isHeader) "Sütun ${cIndex + 1}" else "...",
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
                                    text = "+ Satır Ekle",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }

                            if (numericSum != null) {
                                Text(
                                    text = "∑ Toplam: " + if (numericSum % 1.0 == 0.0) "${numericSum.toLong()} ₺" else "${String.format(Locale.US, "%.2f", numericSum)} ₺",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }
                        }
                    }
                }
            }

            BlockType.IMAGE -> {
                Box(
                    modifier = Modifier
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
                        controlModifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
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
                    AsyncImage(
                        model = File(drawingPath ?: ""),
                        contentDescription = "El Çizimi",
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

                    // Top Action Bar for Drawing: Edit Button + Reorder Controls
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                        text = "Düzenle",
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
                val domain = remember(block.content) {
                    try {
                        Uri.parse(block.content).host ?: "Web Bağlantısı"
                    } catch (e: Exception) {
                        "Web Bağlantısı"
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
                            Text(text = "🌐", fontSize = 18.sp)
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
                                        Text("https://... bağlantı yapıştırın", style = textStyle.copy(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
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
                        .clickable { onOpenSubPage?.invoke(block.content) },
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
                        Text(text = "📄", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))

                        BasicTextField(
                            value = block.content,
                            onValueChange = onContentChange,
                            textStyle = textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(VibrantOrange),
                            decorationBox = { inner ->
                                if (block.content.isEmpty()) {
                                    Text("Alt sayfa başlığı...", style = textStyle.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                                }
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "Aç ↗",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange,
                            modifier = Modifier
                                .clickable { onOpenSubPage?.invoke(block.content) }
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                        Text(text = "🔗", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "$link ↗",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VibrantOrange
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
}
