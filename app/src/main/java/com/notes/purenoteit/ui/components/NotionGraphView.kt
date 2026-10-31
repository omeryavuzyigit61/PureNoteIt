package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D Interactive Knowledge Graph View (Obsidian / Roam / Neural Network style).
 * Features:
 * - Fluid pan and pinch-to-zoom gestures.
 * - Multi-layered glowing NEON link beams between connected notes.
 * - Animated flowing energy particles traveling along connection lines.
 * - Ambient orbiting sparkle dust particles around linked clusters.
 * - Pulsing neural aura rings on connected nodes.
 * - Node click to jump directly into the target note.
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

    // Neon & Particle Animation Loops
    val infiniteTransition = rememberInfiniteTransition(label = "neonGraphPulse")

    // Flowing energy progress along lines (0f -> 1f)
    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowProgress"
    )

    // Breathing neon beam glow alpha (pulsing radiance)
    val neonGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonGlowAlpha"
    )

    // Precalculate node positions using golden spiral / force-friendly distribution
    val nodePositions = remember(notes) {
        val map = mutableMapOf<Int, Offset>()
        val count = notes.size
        if (count == 0) return@remember map

        val radiusStep = 175f
        val goldenAngle = 137.5 * (PI / 180)

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

    val defaultNodeColor = if (isDark) Color(0xFF232328) else PureWhite
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
                        scale = (scale * zoom).coerceIn(0.35f, 4.0f)
                        offset += pan
                    }
                }
                .pointerInput(notes, nodePositions, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val touchX = (tapOffset.x - center.x - offset.x) / scale
                        val touchY = (tapOffset.y - center.y - offset.y) / scale

                        // Hit test on nodes (radius ~ 42dp for effortless finger tap)
                        for (note in notes) {
                            val pos = nodePositions[note.id] ?: continue
                            val dist = sqrt((touchX - pos.x) * (touchX - pos.x) + (touchY - pos.y) * (touchY - pos.y))
                            if (dist <= 42f) {
                                onNodeClick(note)
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // 1. Draw Starfield Grid Dots
            val dotSpacing = 42f * scale
            val startX = (offset.x % dotSpacing)
            val startY = (offset.y % dotSpacing)
            val gridDotColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.045f)

            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(gridDotColor, radius = 1.6f * scale, center = Offset(gx, gy))
                    gy += dotSpacing
                }
                gx += dotSpacing
            }

            // 2. Draw Multi-Layer Neon Glowing Beams & Traveling Energy Particles
            for (link in links) {
                val start = nodePositions[link.sourceNoteId] ?: continue
                val end = nodePositions[link.targetNoteId] ?: continue

                val startScreen = center + offset + (start * scale)
                val endScreen = center + offset + (end * scale)

                val delta = endScreen - startScreen
                val length = sqrt(delta.x * delta.x + delta.y * delta.y)
                if (length < 1f) continue

                // --- LAYER A: Wide Outer Ambient Neon Glow ---
                drawLine(
                    color = VibrantOrange.copy(alpha = 0.22f * neonGlowAlpha),
                    start = startScreen,
                    end = endScreen,
                    strokeWidth = 11f * scale,
                    cap = StrokeCap.Round
                )

                // --- LAYER B: Mid Intense Luminous Aura ---
                drawLine(
                    color = Color(0xFFFF9800).copy(alpha = 0.55f * neonGlowAlpha),
                    start = startScreen,
                    end = endScreen,
                    strokeWidth = 5.5f * scale,
                    cap = StrokeCap.Round
                )

                // --- LAYER C: Sharp Brilliant Inner Laser Core ---
                drawLine(
                    color = Color(0xFFFFE082),
                    start = startScreen,
                    end = endScreen,
                    strokeWidth = 2.2f * scale,
                    cap = StrokeCap.Round
                )

                // --- LAYER D: Flowing Neon Energy Particles (Embers traveling along beam) ---
                val particleCount = 2
                for (pIdx in 0 until particleCount) {
                    val t = (flowProgress + (pIdx.toFloat() / particleCount)) % 1f
                    val px = startScreen.x + delta.x * t
                    val py = startScreen.y + delta.y * t

                    // Slight wave perpendicular oscillation
                    val perpX = -delta.y / length
                    val perpY = delta.x / length
                    val wobble = sin(t * 2 * PI + pIdx).toFloat() * 4f * scale

                    val particlePos = Offset(px + perpX * wobble, py + perpY * wobble)

                    // Outer particle neon flare
                    drawCircle(
                        color = VibrantOrange.copy(alpha = 0.65f),
                        radius = 6.5f * scale,
                        center = particlePos
                    )
                    // Bright electric white-gold nucleus
                    drawCircle(
                        color = Color(0xFFFFF9C4),
                        radius = 3.0f * scale,
                        center = particlePos
                    )
                }

                // Midpoint Sparkle Aura Dust
                val midX = (startScreen.x + endScreen.x) / 2f
                val midY = (startScreen.y + endScreen.y) / 2f
                val dustWobble = cos(flowProgress * 2 * PI).toFloat() * 8f * scale
                drawCircle(
                    color = Color(0xFFFFB74D).copy(alpha = 0.4f * neonGlowAlpha),
                    radius = 4f * scale,
                    center = Offset(midX + dustWobble, midY - dustWobble)
                )
            }

            // 3. Draw Nodes (Nöron Düğümleri)
            for (note in notes) {
                val pos = nodePositions[note.id] ?: continue
                val screenPos = center + offset + (pos * scale)

                val nodeRadius = 26f * scale
                val isPinned = note.isPinned
                val isConnected = links.any { it.sourceNoteId == note.id || it.targetNoteId == note.id }

                // Extra Pulsing Neon Aura for connected/pinned nodes
                if (isConnected || isPinned) {
                    drawCircle(
                        color = VibrantOrange.copy(alpha = 0.35f * neonGlowAlpha),
                        radius = nodeRadius + (10f * scale * neonGlowAlpha),
                        center = screenPos
                    )
                }

                // Outer Halo Glow
                drawCircle(
                    color = VibrantOrange.copy(alpha = if (isPinned) 0.4f else 0.2f),
                    radius = nodeRadius + (6f * scale),
                    center = screenPos
                )

                // Node Body
                drawCircle(
                    color = if (isPinned) VibrantOrange else defaultNodeColor,
                    radius = nodeRadius,
                    center = screenPos
                )

                // Node Neon Border
                drawCircle(
                    color = if (isConnected) VibrantOrange else VibrantOrange.copy(alpha = 0.65f),
                    radius = nodeRadius,
                    center = screenPos,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (isConnected) 2.6f * scale else 1.8f * scale
                    )
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

        // HUD Pill on Top Left: Node Count & Neon Connection Count
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) CharcoalGray.copy(alpha = 0.88f) else PureWhite.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🌌", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${notes.size} Not • ${links.size} Neon Bağlantı",
                    fontSize = 12.5.sp,
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
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isDark) CharcoalGray else PureWhite)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = "Merkeze Odakla",
                tint = VibrantOrange,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
