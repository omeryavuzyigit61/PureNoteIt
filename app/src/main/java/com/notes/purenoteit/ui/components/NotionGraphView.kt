package com.notes.purenoteit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D Interactive Knowledge Graph View (Obsidian / Roam style).
 * Renders notes as nodes in a celestial/neuron network.
 * Supports:
 * - Fluid pan and pinch-to-zoom gestures.
 * - Glowing link connections between notes.
 * - Node click to open target note.
 * - Auto radial / force distribution layout.
 */
@Composable
fun NotionGraphView(
    notes: List<NoteData>,
    links: List<NoteLinkData>,
    onNodeClick: (NoteData) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Precalculate initial node positions (Fibonacci sphere / golden spiral distribution)
    val nodePositions = remember(notes) {
        val map = mutableMapOf<Int, Offset>()
        val count = notes.size
        if (count == 0) return@remember map

        val radiusStep = 180f
        val goldenAngle = 137.5 * (Math.PI / 180)

        notes.forEachIndexed { index, note ->
            if (index == 0) {
                map[note.id] = Offset(0f, 0f)
            } else {
                val r = radiusStep * sqrt(index.toDouble()).toFloat()
                val theta = (index * goldenAngle).toFloat()
                val x = r * cos(theta)
                val y = r * sin(theta)
                map[note.id] = Offset(x, y)
            }
        }
        map
    }

    val linkColor = if (isDark) VibrantOrange.copy(alpha = 0.45f) else VibrantOrange.copy(alpha = 0.35f)
    val defaultNodeColor = if (isDark) Color(0xFF2A2A30) else PureWhite
    val textPaintColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 3.5f)
                        offset += pan
                    }
                }
                .pointerInput(notes, nodePositions, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val touchX = (tapOffset.x - center.x - offset.x) / scale
                        val touchY = (tapOffset.y - center.y - offset.y) / scale

                        // Hit test on nodes (radius ~ 36dp)
                        for (note in notes) {
                            val pos = nodePositions[note.id] ?: continue
                            val dist = sqrt((touchX - pos.x) * (touchX - pos.x) + (touchY - pos.y) * (touchY - pos.y))
                            if (dist <= 40f) {
                                onNodeClick(note)
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Draw Background Grid Dots
            val dotSpacing = 40f * scale
            val startX = (offset.x % dotSpacing)
            val startY = (offset.y % dotSpacing)
            val gridDotColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)

            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(gridDotColor, radius = 1.5f * scale, center = Offset(gx, gy))
                    gy += dotSpacing
                }
                gx += dotSpacing
            }

            // Draw Links (Edges)
            for (link in links) {
                val start = nodePositions[link.sourceNoteId] ?: continue
                val end = nodePositions[link.targetNoteId] ?: continue

                val startScreen = center + offset + (start * scale)
                val endScreen = center + offset + (end * scale)

                drawLine(
                    color = linkColor,
                    start = startScreen,
                    end = endScreen,
                    strokeWidth = 2.5f * scale,
                    cap = StrokeCap.Round
                )
            }

            // Draw Nodes
            for (note in notes) {
                val pos = nodePositions[note.id] ?: continue
                val screenPos = center + offset + (pos * scale)

                val nodeRadius = 26f * scale
                val isPinned = note.isPinned

                // Outer Halo Glow
                drawCircle(
                    color = VibrantOrange.copy(alpha = if (isPinned) 0.35f else 0.15f),
                    radius = nodeRadius + (8f * scale),
                    center = screenPos
                )

                // Node Body
                drawCircle(
                    color = if (isPinned) VibrantOrange else defaultNodeColor,
                    radius = nodeRadius,
                    center = screenPos
                )

                // Node Border
                drawCircle(
                    color = VibrantOrange,
                    radius = nodeRadius,
                    center = screenPos,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale)
                )

                // Node Label & Emoji via Native Canvas
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = textPaintColor
                        textSize = (11f * scale).coerceIn(18f, 38f)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }

                    // Display emoji inside node if available, else first letter
                    val iconText = note.icon ?: (note.title?.take(1) ?: "•")
                    val iconPaint = android.graphics.Paint().apply {
                        textSize = (13f * scale).coerceIn(20f, 42f)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText(iconText, screenPos.x, screenPos.y + (5f * scale), iconPaint)

                    // Display note title below node
                    val titleText = note.title?.take(16) ?: "Başlıksız"
                    drawText(titleText, screenPos.x, screenPos.y + nodeRadius + (16f * scale), paint)
                }
            }
        }

        // HUD Pill on Top Left: Node Count & Links Count
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) CharcoalGray.copy(alpha = 0.85f) else PureWhite.copy(alpha = 0.9f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🌌", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${notes.size} Not • ${links.size} Bağlantı",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Floating Control on Bottom End: Center / Reset Zoom
        IconButton(
            onClick = {
                scale = 1f
                offset = Offset.Zero
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) CharcoalGray else PureWhite)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "Merkeze Odakla",
                tint = VibrantOrange,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
