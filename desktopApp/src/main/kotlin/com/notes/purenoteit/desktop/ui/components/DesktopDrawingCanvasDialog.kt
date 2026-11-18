package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.desktop.ui.DesktopStrings
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class DesktopDrawingTool {
    PEN,
    LINE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    ARROW,
    STAR,
    ERASER
}

internal data class DesktopStroke(
    val points: List<Offset>,
    val color: Color,
    val widthPx: Float,
    val tool: DesktopDrawingTool = DesktopDrawingTool.PEN
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopDrawingCanvasDialog(
    initialImageBytes: ByteArray? = null,
    canvasKey: Any? = null,
    strings: DesktopStrings = DesktopStrings.TR,
    onSavePng: (ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val decodedBase: Pair<BufferedImage?, ImageBitmap?> = remember(canvasKey, initialImageBytes) {
        if (initialImageBytes == null || initialImageBytes.isEmpty()) {
            null to null
        } else {
            try {
                val img = ImageIO.read(ByteArrayInputStream(initialImageBytes))
                if (img != null) img to img.toComposeImageBitmap() else null to null
            } catch (_: Exception) {
                null to null
            }
        }
    }

    val strokes = remember(canvasKey) { mutableStateListOf<DesktopStroke>() }
    val redoStack = remember(canvasKey) { mutableStateListOf<DesktopStroke>() }
    var baseLayerCleared by remember(canvasKey) { mutableStateOf(false) }

    var selectedTool by remember { mutableStateOf(DesktopDrawingTool.PEN) }
    var activePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeCurrent by remember { mutableStateOf<Offset?>(null) }

    var activeColor by remember { mutableStateOf(Color(0xFFFF7A00)) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    var canvasSize by remember { mutableStateOf(IntSize(820, 440)) }

    val activeBaseBuffered = if (baseLayerCleared) null else decodedBase.first
    val activeBaseBitmap = if (baseLayerCleared) null else decodedBase.second

    val palette = listOf(
        Color(0xFF1E1E1E),
        Color(0xFFFF7A00),
        Color(0xFF2979FF),
        Color(0xFF00C853),
        Color(0xFFD50000),
        Color(0xFFAA00FF)
    )

    val isEraser = selectedTool == DesktopDrawingTool.ERASER
    val effectiveDrawColor = if (isEraser) Color.White else activeColor
    val effectiveDrawWidth = if (isEraser) (strokeWidth * 2.2f).coerceAtLeast(16f) else strokeWidth

    val toolEntries: List<Triple<DesktopDrawingTool, ImageVector, String>> = listOf(
        Triple(DesktopDrawingTool.PEN, Icons.Rounded.Edit, strings.drawingPenBtn),
        Triple(DesktopDrawingTool.LINE, Icons.Rounded.HorizontalRule, strings.drawingLineBtn),
        Triple(DesktopDrawingTool.RECTANGLE, Icons.Rounded.CheckBoxOutlineBlank, strings.drawingRectBtn),
        Triple(DesktopDrawingTool.CIRCLE, Icons.Rounded.RadioButtonUnchecked, strings.drawingCircleBtn),
        Triple(DesktopDrawingTool.TRIANGLE, Icons.Rounded.ChangeHistory, strings.drawingTriangleBtn),
        Triple(DesktopDrawingTool.ARROW, Icons.Rounded.NorthEast, strings.drawingArrowBtn),
        Triple(DesktopDrawingTool.STAR, Icons.Rounded.StarBorder, strings.drawingStarBtn),
        Triple(DesktopDrawingTool.ERASER, Icons.Rounded.AutoFixNormal, strings.drawingEraserBtn)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.width(920.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.drawingTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    val popped = strokes.removeAt(strokes.lastIndex)
                                    redoStack += popped
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.drawingUndoBtn, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            enabled = redoStack.isNotEmpty(),
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val restored = redoStack.removeAt(redoStack.lastIndex)
                                    strokes += restored
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.drawingRedoBtn, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                strokes.clear()
                                redoStack.clear()
                                baseLayerCleared = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.drawingClearBtn, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Tools bar: Pen, Line, Rect, Circle, Triangle, Arrow, Star, Eraser
                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    toolEntries.forEach { (tool, icon, label) ->
                        val selected = selectedTool == tool
                        FilterChip(
                            selected = selected,
                            onClick = { selectedTool = tool },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DesktopColors.VibrantOrange.copy(alpha = 0.16f),
                                selectedLabelColor = DesktopColors.VibrantOrange,
                                selectedLeadingIconColor = DesktopColors.VibrantOrange
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Colors + Smooth Stroke Thickness Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    palette.forEach { col ->
                        val isSelected = !isEraser && col == activeColor
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) DesktopColors.VibrantOrange else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (selectedTool == DesktopDrawingTool.ERASER) {
                                        selectedTool = DesktopDrawingTool.PEN
                                    }
                                    activeColor = col
                                }
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${strings.drawingThicknessLabel}: ${strokeWidth.roundToInt()}px",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 2f..32f,
                        colors = SliderDefaults.colors(
                            thumbColor = DesktopColors.VibrantOrange,
                            activeTrackColor = DesktopColors.VibrantOrange
                        ),
                        modifier = Modifier.width(170.dp)
                    )
                    listOf(
                        4f to strings.drawingThin,
                        10f to strings.drawingMedium,
                        20f to strings.drawingThick
                    ).forEach { (w, label) ->
                        OutlinedButton(
                            onClick = { strokeWidth = w },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (kotlin.math.abs(strokeWidth - w) < 1.5f) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(selectedTool, effectiveDrawColor, effectiveDrawWidth) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (selectedTool == DesktopDrawingTool.PEN || selectedTool == DesktopDrawingTool.ERASER) {
                                        activePoints = listOf(start)
                                        shapeStart = null
                                        shapeCurrent = null
                                    } else {
                                        shapeStart = start
                                        shapeCurrent = start
                                        activePoints = emptyList()
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (selectedTool == DesktopDrawingTool.PEN || selectedTool == DesktopDrawingTool.ERASER) {
                                        activePoints = activePoints + change.position
                                    } else {
                                        shapeCurrent = change.position
                                    }
                                },
                                onDragEnd = {
                                    if (selectedTool == DesktopDrawingTool.PEN || selectedTool == DesktopDrawingTool.ERASER) {
                                        if (activePoints.size >= 2) {
                                            strokes += DesktopStroke(activePoints, effectiveDrawColor, effectiveDrawWidth, selectedTool)
                                            redoStack.clear()
                                        }
                                        activePoints = emptyList()
                                    } else {
                                        val s = shapeStart
                                        val e = shapeCurrent
                                        if (s != null && e != null && (s - e).getDistance() >= 3f) {
                                            strokes += DesktopStroke(listOf(s, e), effectiveDrawColor, effectiveDrawWidth, selectedTool)
                                            redoStack.clear()
                                        }
                                        shapeStart = null
                                        shapeCurrent = null
                                    }
                                },
                                onDragCancel = {
                                    activePoints = emptyList()
                                    shapeStart = null
                                    shapeCurrent = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        activeBaseBitmap?.let { bmp ->
                            drawImage(
                                image = bmp,
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                            )
                        }
                        strokes.forEach { stroke -> drawStrokeCompose(stroke) }

                        if (activePoints.size >= 2) {
                            drawStrokeCompose(DesktopStroke(activePoints, effectiveDrawColor, effectiveDrawWidth, selectedTool))
                        }
                        val s = shapeStart
                        val e = shapeCurrent
                        if (s != null && e != null) {
                            drawStrokeCompose(DesktopStroke(listOf(s, e), effectiveDrawColor, effectiveDrawWidth, selectedTool))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) { Text(strings.cancelBtn) }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = strokes.isNotEmpty() || activeBaseBuffered != null || baseLayerCleared,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                        onClick = {
                            val bytes = renderStrokesToPng(
                                baseImage = activeBaseBuffered,
                                strokes = strokes.toList(),
                                width = canvasSize.width.coerceAtLeast(320),
                                height = canvasSize.height.coerceAtLeast(240)
                            )
                            onSavePng(bytes)
                        }
                    ) {
                        Text(strings.drawingSaveBtn, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawStrokeCompose(stroke: DesktopStroke) {
    if (stroke.points.size < 2) return
    val style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val start = stroke.points.first()
    val end = stroke.points.last()

    when (stroke.tool) {
        DesktopDrawingTool.PEN, DesktopDrawingTool.ERASER -> {
            val path = Path().apply {
                moveTo(start.x, start.y)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                }
            }
            drawPath(path = path, color = stroke.color, style = style)
        }
        DesktopDrawingTool.LINE -> {
            drawLine(
                color = stroke.color,
                start = start,
                end = end,
                strokeWidth = stroke.widthPx,
                cap = StrokeCap.Round
            )
        }
        DesktopDrawingTool.RECTANGLE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            drawRect(
                color = stroke.color,
                topLeft = Offset(left, top),
                size = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)),
                style = style
            )
        }
        DesktopDrawingTool.CIRCLE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            drawOval(
                color = stroke.color,
                topLeft = Offset(left, top),
                size = Size(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y)),
                style = style
            )
        }
        DesktopDrawingTool.TRIANGLE -> {
            val verts = triangleVertices(start, end)
            val path = Path().apply {
                moveTo(verts[0].x, verts[0].y)
                lineTo(verts[1].x, verts[1].y)
                lineTo(verts[2].x, verts[2].y)
                close()
            }
            drawPath(path = path, color = stroke.color, style = style)
        }
        DesktopDrawingTool.ARROW -> {
            val (wing1, wing2) = arrowHeadPoints(start, end, headLength = (stroke.widthPx * 3.5f).coerceAtLeast(18f))
            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
                moveTo(wing1.x, wing1.y)
                lineTo(end.x, end.y)
                lineTo(wing2.x, wing2.y)
            }
            drawPath(path = path, color = stroke.color, style = style)
        }
        DesktopDrawingTool.STAR -> {
            val verts = starVertices(start, end)
            val path = Path().apply {
                moveTo(verts[0].x, verts[0].y)
                for (i in 1 until verts.size) {
                    lineTo(verts[i].x, verts[i].y)
                }
                close()
            }
            drawPath(path = path, color = stroke.color, style = style)
        }
    }
}

private fun triangleVertices(start: Offset, end: Offset): List<Offset> {
    val left = min(start.x, end.x)
    val right = max(start.x, end.x)
    val top = min(start.y, end.y)
    val bottom = max(start.y, end.y)
    val cx = (left + right) / 2f
    return listOf(
        Offset(cx, top),
        Offset(right, bottom),
        Offset(left, bottom)
    )
}

private fun starVertices(start: Offset, end: Offset): List<Offset> {
    val left = min(start.x, end.x)
    val right = max(start.x, end.x)
    val top = min(start.y, end.y)
    val bottom = max(start.y, end.y)
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val rx = ((right - left) / 2f).coerceAtLeast(2f)
    val ry = ((bottom - top) / 2f).coerceAtLeast(2f)
    val innerRx = rx * 0.4f
    val innerRy = ry * 0.4f
    return List(10) { i ->
        val angle = -PI / 2.0 + i * (PI / 5.0)
        val rX = if (i % 2 == 0) rx else innerRx
        val rY = if (i % 2 == 0) ry else innerRy
        Offset(
            x = cx + (rX * cos(angle)).toFloat(),
            y = cy + (rY * sin(angle)).toFloat()
        )
    }
}

private fun arrowHeadPoints(start: Offset, end: Offset, headLength: Float): Pair<Offset, Offset> {
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val wingAngle = Math.toRadians(30.0)
    val p1 = Offset(
        x = end.x - (headLength * cos(angle - wingAngle)).toFloat(),
        y = end.y - (headLength * sin(angle - wingAngle)).toFloat()
    )
    val p2 = Offset(
        x = end.x - (headLength * cos(angle + wingAngle)).toFloat(),
        y = end.y - (headLength * sin(angle + wingAngle)).toFloat()
    )
    return p1 to p2
}

internal fun renderStrokesToPng(
    baseImage: BufferedImage?,
    strokes: List<DesktopStroke>,
    width: Int,
    height: Int
): ByteArray {
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = img.createGraphics()
    try {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.color = java.awt.Color.WHITE
        g2d.fillRect(0, 0, width, height)
        if (baseImage != null) {
            g2d.drawImage(baseImage, 0, 0, width, height, null)
        }
        for (stroke in strokes) {
            drawStrokeAwt(g2d, stroke)
        }
    } finally {
        g2d.dispose()
    }
    return ByteArrayOutputStream().use { out ->
        ImageIO.write(img, "png", out)
        out.toByteArray()
    }
}

private fun drawStrokeAwt(g2d: Graphics2D, stroke: DesktopStroke) {
    if (stroke.points.size < 2) return
    g2d.color = java.awt.Color(stroke.color.toArgb(), true)
    g2d.stroke = BasicStroke(stroke.widthPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val start = stroke.points.first()
    val end = stroke.points.last()

    when (stroke.tool) {
        DesktopDrawingTool.PEN, DesktopDrawingTool.ERASER -> {
            val path = GeneralPath().apply {
                moveTo(start.x.toDouble(), start.y.toDouble())
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x.toDouble(), stroke.points[i].y.toDouble())
                }
            }
            g2d.draw(path)
        }
        DesktopDrawingTool.LINE -> {
            g2d.draw(Line2D.Float(start.x, start.y, end.x, end.y))
        }
        DesktopDrawingTool.RECTANGLE -> {
            g2d.draw(
                Rectangle2D.Float(
                    min(start.x, end.x),
                    min(start.y, end.y),
                    kotlin.math.abs(end.x - start.x),
                    kotlin.math.abs(end.y - start.y)
                )
            )
        }
        DesktopDrawingTool.CIRCLE -> {
            g2d.draw(
                Ellipse2D.Float(
                    min(start.x, end.x),
                    min(start.y, end.y),
                    kotlin.math.abs(end.x - start.x),
                    kotlin.math.abs(end.y - start.y)
                )
            )
        }
        DesktopDrawingTool.TRIANGLE -> {
            val verts = triangleVertices(start, end)
            val path = GeneralPath().apply {
                moveTo(verts[0].x.toDouble(), verts[0].y.toDouble())
                lineTo(verts[1].x.toDouble(), verts[1].y.toDouble())
                lineTo(verts[2].x.toDouble(), verts[2].y.toDouble())
                closePath()
            }
            g2d.draw(path)
        }
        DesktopDrawingTool.ARROW -> {
            val (wing1, wing2) = arrowHeadPoints(start, end, headLength = (stroke.widthPx * 3.5f).coerceAtLeast(18f))
            val path = GeneralPath().apply {
                moveTo(start.x.toDouble(), start.y.toDouble())
                lineTo(end.x.toDouble(), end.y.toDouble())
                moveTo(wing1.x.toDouble(), wing1.y.toDouble())
                lineTo(end.x.toDouble(), end.y.toDouble())
                lineTo(wing2.x.toDouble(), wing2.y.toDouble())
            }
            g2d.draw(path)
        }
        DesktopDrawingTool.STAR -> {
            val verts = starVertices(start, end)
            val path = GeneralPath().apply {
                moveTo(verts[0].x.toDouble(), verts[0].y.toDouble())
                for (i in 1 until verts.size) {
                    lineTo(verts[i].x.toDouble(), verts[i].y.toDouble())
                }
                closePath()
            }
            g2d.draw(path)
        }
    }
}
