package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private data class DesktopStroke(
    val points: List<Offset>,
    val color: Color,
    val widthPx: Float
)

@Composable
fun DesktopDrawingCanvasDialog(
    onSavePng: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val strokes = remember { mutableStateListOf<DesktopStroke>() }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var activeColor by remember { mutableStateOf(Color(0xFFFF900F)) }
    var strokeWidth by remember { mutableFloatStateOf(5f) }
    var canvasSize by remember { mutableStateOf(IntSize(760, 440)) }

    val palette = listOf(
        Color(0xFF1C1C1E),
        Color(0xFFFF900F),
        Color(0xFF3269FF),
        Color(0xFF10B981),
        Color(0xFFF43F5E)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(820.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Çizim Tahtası (Masaüstü SVG/PNG Tuval)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    palette.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (col == activeColor) 2.5.dp else 1.dp,
                                    color = if (col == activeColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { activeColor = col }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    listOf(3f to "İnce", 6f to "Orta", 11f to "Kalın").forEach { (w, label) ->
                        OutlinedButton(
                            onClick = { strokeWidth = w },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                    }) {
                        Text("Geri Al")
                    }
                    OutlinedButton(onClick = { strokes.clear() }) {
                        Text("Temizle")
                    }
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFFDF9))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(activeColor, strokeWidth) {
                            detectDragGestures(
                                onDragStart = { start -> activePoints = listOf(start) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    activePoints = activePoints + change.position
                                },
                                onDragEnd = {
                                    if (activePoints.size >= 2) {
                                        strokes += DesktopStroke(activePoints, activeColor, strokeWidth)
                                    }
                                    activePoints = emptyList()
                                },
                                onDragCancel = { activePoints = emptyList() }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        fun drawList(pts: List<Offset>, col: Color, w: Float) {
                            if (pts.size < 2) return
                            val path = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x, pts[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = col,
                                style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        strokes.forEach { s -> drawList(s.points, s.color, s.widthPx) }
                        drawList(activePoints, activeColor, strokeWidth)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("İptal") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = strokes.isNotEmpty(),
                        onClick = {
                            val bytes = renderStrokesToPng(
                                strokes = strokes.toList(),
                                width = canvasSize.width.coerceAtLeast(320),
                                height = canvasSize.height.coerceAtLeast(240)
                            )
                            onSavePng(bytes)
                        }
                    ) {
                        Text("Çizimi Nota Kaydet")
                    }
                }
            }
        }
    }
}

private fun renderStrokesToPng(strokes: List<DesktopStroke>, width: Int, height: Int): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = img.createGraphics()
    try {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.color = java.awt.Color(0xFF, 0xFD, 0xF9)
        g2d.fillRect(0, 0, width, height)
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            g2d.color = java.awt.Color(stroke.color.toArgb(), true)
            g2d.stroke = BasicStroke(stroke.widthPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in 0 until stroke.points.lastIndex) {
                val p1 = stroke.points[i]
                val p2 = stroke.points[i + 1]
                g2d.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
            }
        }
    } finally {
        g2d.dispose()
    }
    return ByteArrayOutputStream().use { out ->
        ImageIO.write(img, "png", out)
        out.toByteArray()
    }
}
