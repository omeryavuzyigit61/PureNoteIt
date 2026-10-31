package com.notes.purenoteit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun NotionWhiteboardView(
    notes: List<NoteData>,
    links: List<NoteLinkData>,
    onNoteClick: (NoteData) -> Unit,
    onAddNote: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidthDp = 220.dp
    val cardHeightDp = 140.dp
    val cardWidthPx = with(density) { cardWidthDp.toPx() }
    val cardHeightPx = with(density) { cardHeightDp.toPx() }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset(200f, 300f)) }

    // Positions of draggable note cards in whiteboard coordinate space
    val cardPositions = remember { mutableStateMapOf<Int, Offset>() }

    // Initialize layout positions in a structured 2D grid layout
    LaunchedEffect(notes) {
        notes.forEachIndexed { index, note ->
            if (!cardPositions.containsKey(note.id)) {
                val col = index % 3
                val row = index / 3
                cardPositions[note.id] = Offset(
                    x = 80f + col * 320f + (row % 2) * 40f,
                    y = 100f + row * 240f
                )
            }
        }
    }

    val dotGridColor = if (isDark) Color(0xFF333333) else Color(0xFFDDDDDD)
    val canvasBg = if (isDark) Color(0xFF141414) else Color(0xFFF9F9FB)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.4f, 2.5f)
                    panOffset += pan
                }
            }
    ) {
        // 1. Dotted infinite background grid & mathematically locked connecting arrows
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 32f * scale
            val startX = (panOffset.x % step + step) % step
            val startY = (panOffset.y % step + step) % step

            // Draw infinite background grid dots
            var x = startX
            while (x < size.width) {
                var y = startY
                while (y < size.height) {
                    drawCircle(
                        color = dotGridColor,
                        radius = 1.5f * scale,
                        center = Offset(x, y)
                    )
                    y += step
                }
                x += step
            }

            // Function to find exact intersection with card rectangular border
            fun getCardBorderIntersection(center: Offset, target: Offset, halfW: Float, halfH: Float): Offset {
                val dx = target.x - center.x
                val dy = target.y - center.y
                if (dx == 0f && dy == 0f) return center
                val factorX = if (dx != 0f) halfW / kotlin.math.abs(dx) else Float.MAX_VALUE
                val factorY = if (dy != 0f) halfH / kotlin.math.abs(dy) else Float.MAX_VALUE
                val factor = min(factorX, factorY)
                return Offset(center.x + dx * factor, center.y + dy * factor)
            }

            // 2. Draw curved neon beams with arrowheads locked to card boundaries
            links.forEach { link ->
                val sourcePos = cardPositions[link.sourceNoteId]
                val targetPos = cardPositions[link.targetNoteId]
                if (sourcePos != null && targetPos != null) {
                    val sLeft = sourcePos.x * scale + panOffset.x
                    val sTop = sourcePos.y * scale + panOffset.y
                    val sCenter = Offset(sLeft + (cardWidthPx * scale) / 2f, sTop + (cardHeightPx * scale) / 2f)

                    val tLeft = targetPos.x * scale + panOffset.x
                    val tTop = targetPos.y * scale + panOffset.y
                    val tCenter = Offset(tLeft + (cardWidthPx * scale) / 2f, tTop + (cardHeightPx * scale) / 2f)

                    val halfW = (cardWidthPx * scale) / 2f
                    val halfH = (cardHeightPx * scale) / 2f

                    // Calculate border contact points
                    val pStart = getCardBorderIntersection(sCenter, tCenter, halfW, halfH)
                    val pEnd = getCardBorderIntersection(tCenter, sCenter, halfW, halfH)

                    val dx = pEnd.x - pStart.x
                    val dy = pEnd.y - pStart.y

                    // Directional curve with control points
                    val cp1 = Offset(pStart.x + dx * 0.45f, pStart.y + dy * 0.05f)
                    val cp2 = Offset(pStart.x + dx * 0.55f, pStart.y + dy * 0.95f)

                    val path = Path().apply {
                        moveTo(pStart.x, pStart.y)
                        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, pEnd.x, pEnd.y)
                    }

                    // Outer neon glow beam
                    drawPath(
                        path = path,
                        color = Color(0xFFFF9800).copy(alpha = 0.35f),
                        style = Stroke(
                            width = 6f * scale.coerceIn(0.7f, 1.8f),
                            cap = StrokeCap.Round
                        )
                    )

                    // Inner bright beam
                    drawPath(
                        path = path,
                        color = Color(0xFFFF6D00),
                        style = Stroke(
                            width = 2.5f * scale.coerceIn(0.7f, 1.8f),
                            cap = StrokeCap.Round
                        )
                    )

                    // Arrowhead (Ok Başı) pointing into target card
                    val arrowAngle = atan2(pEnd.y - cp2.y, pEnd.x - cp2.x)
                    val arrowLen = 13f * scale.coerceIn(0.7f, 1.5f)
                    val arrowWid = 8f * scale.coerceIn(0.7f, 1.5f)
                    val cosA = cos(arrowAngle)
                    val sinA = sin(arrowAngle)

                    val wing1 = Offset(
                        pEnd.x - arrowLen * cosA + arrowWid * sinA,
                        pEnd.y - arrowLen * sinA - arrowWid * cosA
                    )
                    val wing2 = Offset(
                        pEnd.x - arrowLen * cosA - arrowWid * sinA,
                        pEnd.y - arrowLen * sinA + arrowWid * cosA
                    )

                    val arrowPath = Path().apply {
                        moveTo(pEnd.x, pEnd.y)
                        lineTo(wing1.x, wing1.y)
                        lineTo(wing2.x, wing2.y)
                        close()
                    }

                    // Draw solid neon arrowhead
                    drawPath(arrowPath, color = Color(0xFFFF9800).copy(alpha = 0.4f))
                    drawPath(arrowPath, color = Color(0xFFFF6D00))
                }
            }
        }

        // 3. Draggable Note Cards
        notes.forEach { note ->
            val pos = cardPositions[note.id] ?: Offset(100f, 100f)
            val screenX = pos.x * scale + panOffset.x
            val screenY = pos.y * scale + panOffset.y

            WhiteboardCard(
                note = note,
                cardWidthDp = cardWidthDp,
                cardHeightDp = cardHeightDp,
                modifier = Modifier
                    .offset {
                        IntOffset(screenX.roundToInt(), screenY.roundToInt())
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .pointerInput(note.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val current = cardPositions[note.id] ?: Offset(100f, 100f)
                            cardPositions[note.id] = Offset(
                                current.x + dragAmount.x / scale,
                                current.y + dragAmount.y / scale
                            )
                        }
                    },
                onClick = { onNoteClick(note) },
                isDark = isDark
            )
        }

        // 4. Whiteboard Floating Controls (Top Right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom Out
                IconButton(
                    onClick = { scale = (scale / 1.2f).coerceAtLeast(0.4f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }

                // Scale indicator / Reset to 100%
                Text(
                    text = "${(scale * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable {
                            scale = 1f
                            panOffset = Offset(200f, 300f)
                        }
                        .padding(horizontal = 6.dp)
                )

                // Zoom In
                IconButton(
                    onClick = { scale = (scale * 1.2f).coerceAtMost(2.5f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Add Note FAB
        FloatingActionButton(
            onClick = onAddNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 85.dp),
            containerColor = VibrantOrange,
            contentColor = PureWhite,
            shape = CircleShape
        ) {
            Icon(
                painter = painterResource(id = R.drawable.curveplus),
                contentDescription = "Yeni Not Ekle",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun WhiteboardCard(
    note: NoteData,
    cardWidthDp: androidx.compose.ui.unit.Dp,
    cardHeightDp: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val cardColor = if (isDark) Color(0xFF222225) else PureWhite
    val parsedBlocks = remember(note.description) {
        try {
            BlockParser.parse(note.description ?: "")
        } catch (e: Exception) {
            emptyList()
        }
    }
    val previewText = remember(parsedBlocks) {
        parsedBlocks.firstOrNull { it.content.isNotBlank() }?.content ?: "İçerik yok"
    }

    Surface(
        modifier = modifier
            .width(cardWidthDp)
            .height(cardHeightDp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (note.isPinned) VibrantOrange else if (isDark) Color(0xFF3A3A3E) else Color(0xFFE5E5EA)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = note.icon ?: "📝",
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (note.title.isNullOrBlank()) "Başlıksız Not" else note.title!!,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (note.isPinned) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = "Sabitlendi",
                            tint = VibrantOrange,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = previewText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SoftOrangeLight
                ) {
                    Text(
                        text = note.status ?: "TODO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = VibrantOrange,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "Açmak için dokunun",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
