package com.notes.purenoteit.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.showToastShort
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

private val DRAWING_COLORS = listOf(
    Color(0xFF1E1E1E), // Black
    Color(0xFFFF7A00), // Vibrant Orange
    Color(0xFF2979FF), // Blue
    Color(0xFF00C853), // Green
    Color(0xFFD50000), // Red
    Color(0xFFAA00FF)  // Purple
)

/**
 * Interactive Notion Drawing Board.
 * Allows freehand drawing with smooth curves, color selection, stroke thickness,
 * undo, erase, clear, and direct PNG export to note blocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingCanvasSheet(
    onDismiss: () -> Unit,
    onSaveDrawing: (filePath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }

    var selectedColor by remember { mutableStateOf(DRAWING_COLORS[0]) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }
    var isEraserMode by remember { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(IntSize(1080, 1080)) }

    fun saveCanvasToPng() {
        if (strokes.isEmpty()) {
            context.showToastShort("Çizim boş!")
            return
        }

        try {
            val width = canvasSize.width.coerceAtLeast(600)
            val height = canvasSize.height.coerceAtLeast(600)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // Canvas background: Pure White
            canvas.drawColor(android.graphics.Color.WHITE)

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            for (stroke in strokes) {
                if (stroke.points.size < 2) continue

                paint.strokeWidth = stroke.strokeWidth
                paint.color = if (stroke.isEraser) android.graphics.Color.WHITE else stroke.color.toArgb()

                val path = android.graphics.Path()
                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                }
                canvas.drawPath(path, paint)
            }

            val dir = File(context.filesDir, "drawings").apply { mkdirs() }
            val file = File(dir, "sketch_${System.currentTimeMillis()}.png")

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            onSaveDrawing(file.absolutePath)
            onDismiss()
        } catch (e: Exception) {
            context.showToastShort("Çizim kaydedilemedi")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Actions (İptal, Temizle, Kaydet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "El Çizimi Ekle",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Undo Button
                    IconButton(
                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Geri Al",
                            tint = if (strokes.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Clear Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { strokes.clear() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Temizle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Save Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VibrantOrange)
                            .clickable { saveCanvasToPng() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Ekle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // White Drawing Board Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderLight, RoundedCornerShape(16.dp))
                    .onSizeChanged { canvasSize = it },
                color = PureWhite
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints = mutableStateListOf(offset)
                                },
                                onDragEnd = {
                                    if (currentPoints.isNotEmpty()) {
                                        strokes.add(
                                            DrawingStroke(
                                                points = currentPoints.toList(),
                                                color = selectedColor,
                                                strokeWidth = strokeWidth,
                                                isEraser = isEraserMode
                                            )
                                        )
                                        currentPoints.clear()
                                    }
                                },
                                onDragCancel = {
                                    currentPoints.clear()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPoints.add(change.position)
                                }
                            )
                        }
                ) {
                    // Draw existing strokes
                    for (stroke in strokes) {
                        if (stroke.points.size < 2) continue
                        val drawColor = if (stroke.isEraser) PureWhite else stroke.color
                        for (i in 0 until stroke.points.size - 1) {
                            drawLine(
                                color = drawColor,
                                start = stroke.points[i],
                                end = stroke.points[i + 1],
                                strokeWidth = stroke.strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw live current stroke
                    if (currentPoints.size >= 2) {
                        val drawColor = if (isEraserMode) PureWhite else selectedColor
                        for (i in 0 until currentPoints.size - 1) {
                            drawLine(
                                color = drawColor,
                                start = currentPoints[i],
                                end = currentPoints[i + 1],
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Palette Controls: Color Picker + Stroke Thickness + Eraser
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Color Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DRAWING_COLORS.forEach { color ->
                        val isSelected = selectedColor == color && !isEraserMode
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) VibrantOrange else BorderLight,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedColor = color
                                    isEraserMode = false
                                }
                        )
                    }
                }

                // Stroke Width Chips (İnce, Orta, Kalın)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(4f to "İnce", 9f to "Orta", 18f to "Kalın").forEach { (width, label) ->
                        val isSelected = strokeWidth == width
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) SoftOrangeLight else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { strokeWidth = width }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Eraser Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isEraserMode) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { isEraserMode = !isEraserMode }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Silgi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEraserMode) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
