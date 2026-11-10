package com.notes.purenoteit.desktop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun categoryNeonColor(categorySyncUuid: String?, categories: List<Category>): Color {
    val palette = listOf(
        DesktopColors.VibrantOrange,
        Color(0xFF10B981),
        Color(0xFF06B6D4),
        Color(0xFF8B5CF6),
        Color(0xFFF59E0B),
        Color(0xFFF43F5E),
        Color(0xFF3B82F6)
    )
    if (categorySyncUuid.isNullOrBlank()) return palette[0]
    val idx = categories.indexOfFirst { it.syncUuid == categorySyncUuid }
    return if (idx < 0) palette[0] else palette[idx % palette.size]
}

@Composable
fun DesktopGraphView(
    notes: List<Note>,
    links: List<NoteLink>,
    categories: List<Category>,
    selectedNoteSyncUuid: String?,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var focusedCategoryUuid by remember { mutableStateOf<String?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val labelColor = MaterialTheme.colorScheme.onSurface
    val subGridColor = if (isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.05f)

    val positions = remember(notes, links) {
        computeGraphLayout(notes, links)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // HUD Header with Category filter chips & zoom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Bilgi Haritası (${notes.size} not · ${links.size} bağlantı)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { zoom = (zoom * 1.2f).coerceIn(0.4f, 3.0f) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Yakınlaş",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = { zoom = (zoom / 1.2f).coerceIn(0.4f, 3.0f) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Remove,
                        contentDescription = "Uzaklaş",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        panOffset = Offset.Zero
                        zoom = 1f
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.CenterFocusStrong,
                        contentDescription = "Ortala",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                val selected = focusedCategoryUuid == null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { focusedCategoryUuid = null }
                ) {
                    Text(
                        text = "Tümü",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            items(categories, key = { it.syncUuid }) { cat ->
                val selected = focusedCategoryUuid == cat.syncUuid
                val dot = categoryNeonColor(cat.syncUuid, categories)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) dot.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable {
                        focusedCategoryUuid = if (selected) null else cat.syncUuid
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = cat.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffset += dragAmount
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (deltaY != 0f) {
                                    val factor = if (deltaY < 0) 1.1f else 0.9f
                                    zoom = (zoom * factor).coerceIn(0.4f, 3.0f)
                                }
                            }
                        }
                    }
                }
                .pointerInput(notes, positions, zoom, panOffset) {
                    detectTapGestures { tapPos ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val worldTap = (tapPos - center - panOffset) / zoom
                        val clicked = notes.firstOrNull { n ->
                            val pos = positions[n.syncUuid] ?: return@firstOrNull false
                            val dx = worldTap.x - pos.x
                            val dy = worldTap.y - pos.y
                            sqrt(dx * dx + dy * dy) <= 28f
                        }
                        if (clicked != null) {
                            onNodeClick(clicked.syncUuid)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // Background dot matrix grid
                val gridSpacing = 36f * zoom.coerceAtLeast(0.6f)
                if (gridSpacing >= 14f) {
                    val offsetX = ((panOffset.x % gridSpacing) + gridSpacing) % gridSpacing
                    val offsetY = ((panOffset.y % gridSpacing) + gridSpacing) % gridSpacing
                    var gx = offsetX
                    while (gx < size.width) {
                        var gy = offsetY
                        while (gy < size.height) {
                            drawCircle(color = subGridColor, radius = 1.6f, center = Offset(gx, gy))
                            gy += gridSpacing
                        }
                        gx += gridSpacing
                    }
                }

                // Draw edges + moving pulse photons
                for (link in links) {
                    val pA = positions[link.sourceSyncUuid] ?: continue
                    val pB = positions[link.targetSyncUuid] ?: continue
                    val screenA = center + panOffset + pA * zoom
                    val screenB = center + panOffset + pB * zoom
                    val srcNote = notes.firstOrNull { it.syncUuid == link.sourceSyncUuid }
                    val beamColor = categoryNeonColor(srcNote?.categorySyncUuid, categories)
                    val dim = focusedCategoryUuid != null && srcNote?.categorySyncUuid != focusedCategoryUuid

                    drawLine(
                        brush = Brush.linearGradient(
                            listOf(
                                beamColor.copy(alpha = if (dim) 0.12f else 0.55f),
                                beamColor.copy(alpha = if (dim) 0.10f else 0.28f)
                            ),
                            start = screenA,
                            end = screenB
                        ),
                        start = screenA,
                        end = screenB,
                        strokeWidth = (2.2f * zoom).coerceIn(1f, 4f),
                        cap = StrokeCap.Round
                    )

                    if (!dim) {
                        val photonPos = Offset(
                            x = screenA.x + (screenB.x - screenA.x) * pulse,
                            y = screenA.y + (screenB.y - screenA.y) * pulse
                        )
                        drawCircle(
                            color = beamColor.copy(alpha = 0.85f),
                            radius = (3.4f * zoom).coerceIn(2f, 6f),
                            center = photonPos
                        )
                    }
                }

                // Draw nodes + labels
                val linkCounts = HashMap<String, Int>()
                links.forEach { l ->
                    linkCounts[l.sourceSyncUuid] = (linkCounts[l.sourceSyncUuid] ?: 0) + 1
                    linkCounts[l.targetSyncUuid] = (linkCounts[l.targetSyncUuid] ?: 0) + 1
                }

                for (note in notes) {
                    val worldPos = positions[note.syncUuid] ?: continue
                    val screenPos = center + panOffset + worldPos * zoom
                    val neon = categoryNeonColor(note.categorySyncUuid, categories)
                    val degree = linkCounts[note.syncUuid] ?: 0
                    val isSelected = note.syncUuid == selectedNoteSyncUuid
                    val isDimmed = focusedCategoryUuid != null && note.categorySyncUuid != focusedCategoryUuid
                    val baseRadius = (13f + degree * 2.2f).coerceAtMost(24f) * zoom.coerceIn(0.6f, 1.8f)

                    if (isSelected) {
                        drawCircle(
                            color = neon.copy(alpha = 0.28f),
                            radius = baseRadius * 1.75f,
                            center = screenPos
                        )
                    }
                    drawCircle(
                        color = if (isDimmed) neon.copy(alpha = 0.22f) else neon,
                        radius = baseRadius,
                        center = screenPos
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = if (isDimmed) 0.2f else 0.9f),
                        radius = baseRadius * 0.42f,
                        center = screenPos
                    )

                    val rawTitle = note.title?.takeIf { it.isNotBlank() } ?: "Başlıksız"
                    val shortTitle = if (rawTitle.length > 18) rawTitle.take(16) + "…" else rawTitle
                    val measured = textMeasurer.measure(
                        text = shortTitle,
                        style = TextStyle(
                            fontSize = (11f * zoom.coerceIn(0.75f, 1.25f)).sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor.copy(alpha = if (isDimmed) 0.3f else 0.92f)
                        )
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = screenPos.x - measured.size.width / 2f,
                            y = screenPos.y + baseRadius + 4f
                        )
                    )
                }
            }
        }
    }
}

private fun computeGraphLayout(notes: List<Note>, links: List<NoteLink>): Map<String, Offset> {
    if (notes.isEmpty()) return emptyMap()
    val pos = LinkedHashMap<String, Offset>()
    val count = notes.size
    val goldenAngle = PI * (3.0 - sqrt(5.0))
    notes.forEachIndexed { idx, note ->
        val angle = idx * goldenAngle
        val radius = if (count == 1) 0f else 55f + 42f * sqrt(idx + 1f)
        pos[note.syncUuid] = Offset(
            x = (radius * cos(angle)).toFloat(),
            y = (radius * sin(angle)).toFloat()
        )
    }

    // Light 18-iteration force-directed relaxation for cleaner connected clusters
    repeat(18) {
        val forces = HashMap<String, Offset>()
        for (i in notes.indices) {
            val a = notes[i].syncUuid
            val pa = pos[a]!!
            var fx = 0f
            var fy = 0f
            for (j in notes.indices) {
                if (i == j) continue
                val pb = pos[notes[j].syncUuid]!!
                val dx = pa.x - pb.x
                val dy = pa.y - pb.y
                val distSq = (dx * dx + dy * dy).coerceAtLeast(250f)
                val rep = 2200f / distSq
                fx += dx * rep
                fy += dy * rep
            }
            forces[a] = Offset(fx - pa.x * 0.02f, fy - pa.y * 0.02f)
        }
        for (link in links) {
            val pa = pos[link.sourceSyncUuid] ?: continue
            val pb = pos[link.targetSyncUuid] ?: continue
            val dx = pb.x - pa.x
            val dy = pb.y - pa.y
            forces[link.sourceSyncUuid] = (forces[link.sourceSyncUuid] ?: Offset.Zero) + Offset(dx * 0.08f, dy * 0.08f)
            forces[link.targetSyncUuid] = (forces[link.targetSyncUuid] ?: Offset.Zero) - Offset(dx * 0.08f, dy * 0.08f)
        }
        for (note in notes) {
            val f = forces[note.syncUuid] ?: Offset.Zero
            val current = pos[note.syncUuid]!!
            pos[note.syncUuid] = Offset(
                x = (current.x + f.x.coerceIn(-18f, 18f)).coerceIn(-680f, 680f),
                y = (current.y + f.y.coerceIn(-18f, 18f)).coerceIn(-460f, 460f)
            )
        }
    }
    return pos
}
