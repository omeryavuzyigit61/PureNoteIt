package com.notes.purenoteit.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class DrawingTool {
    PEN,
    LINE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    ARROW,
    STAR,
    ERASER
}

data class CanvasItem(
    val tool: DrawingTool,
    val points: List<Offset>,
    val start: Offset = Offset.Zero,
    val end: Offset = Offset.Zero,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

private val PALETTE_COLORS = listOf(
    Color(0xFF1E1E1E), // Charcoal Black
    Color(0xFFFF7A00), // Vibrant Orange
    Color(0xFF2979FF), // Royal Blue
    Color(0xFF00C853), // Emerald Green
    Color(0xFFD50000), // Crimson Red
    Color(0xFFAA00FF)  // Purple
)

/**
 * Advanced Notion Drawing Studio.
 * Features:
 * - Real-time 120 FPS freehand drawing
 * - Apple Notes Snap-to-Straight-Line gesture with angle and tilt adjustment
 * - Geometric Polygons: Rectangle, Circle, Triangle, Arrow, Star
 * - Precision Retouching Eraser
 * - Animated Brush Thickness Slider with live physical size preview bubble (2px - 48px)
 * - Ultra HD PNG rendering and block persistence
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvasSheet(
    existingImagePath: String? = null,
    onDismiss: () -> Unit,
    onSaveDrawing: (filePath: String) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Lock sheet drag-to-dismiss so resting a palm or drawing downward never pulls the sheet down
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue -> targetValue != SheetValue.Hidden }
    )

    val baseBitmap = remember(existingImagePath) {
        existingImagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        }
    }

    val items = remember { mutableStateListOf<CanvasItem>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }

    var selectedTool by remember { mutableStateOf(DrawingTool.PEN) }
    var selectedColor by remember { mutableStateOf(PALETTE_COLORS[0]) }
    var strokeThickness by remember { mutableFloatStateOf(8f) }

    // Universal Stylus & Palm Rejection State:
    // Starts in Hybrid Mode (Finger + Stylus enabled) so users without a stylus can draw immediately.
    // Automatically locks to Stylus-Only when a hardware Stylus touches the screen, or can be toggled manually.
    var isPalmRejectionLocked by remember { mutableStateOf(false) }
    var userManuallyToggledMode by remember { mutableStateOf(false) }
    var isHardwareEraserActive by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragCurrent by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    // Apple Notes Snap-to-Line State
    var isSnappedToLine by remember { mutableStateOf(false) }
    var snapTimerJob by remember { mutableStateOf<Job?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize(1080, 1080)) }
    var previewBitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var isBaseBitmapCleared by remember(existingImagePath) { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val activeBaseBitmap = if (isBaseBitmapCleared) null else baseBitmap

    val currentToolState by rememberUpdatedState(selectedTool)
    val currentColorState by rememberUpdatedState(selectedColor)
    val currentThicknessState by rememberUpdatedState(strokeThickness)
    val palmRejectionLockedState by rememberUpdatedState(isPalmRejectionLocked)
    val userManualModeState by rememberUpdatedState(userManuallyToggledMode)
    val palmContactThresholdPx = with(density) { 46.dp.toPx() }

    fun renderCurrentCanvasToBitmap(): Bitmap {
        val width = canvasSize.width.coerceAtLeast(720)
        val height = canvasSize.height.coerceAtLeast(720)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Render existing drawing bitmap as the base canvas layer (unless cleared)
        activeBaseBitmap?.let { bmp ->
            val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
            val dstRect = android.graphics.Rect(0, 0, width, height)
            canvas.drawBitmap(bmp, srcRect, dstRect, null)
        }

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (item in items) {
            paint.strokeWidth = item.strokeWidth
            paint.color = if (item.isEraser) android.graphics.Color.WHITE else item.color.toArgb()

            when (item.tool) {
                DrawingTool.PEN, DrawingTool.ERASER -> {
                    if (item.points.size >= 2) {
                        val path = android.graphics.Path()
                        path.moveTo(item.points[0].x, item.points[0].y)
                        for (i in 1 until item.points.size) {
                            path.lineTo(item.points[i].x, item.points[i].y)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                DrawingTool.LINE -> {
                    canvas.drawLine(item.start.x, item.start.y, item.end.x, item.end.y, paint)
                }
                DrawingTool.RECTANGLE -> {
                    val left = minOf(item.start.x, item.end.x)
                    val top = minOf(item.start.y, item.end.y)
                    val right = maxOf(item.start.x, item.end.x)
                    val bottom = maxOf(item.start.y, item.end.y)
                    canvas.drawRoundRect(left, top, right, bottom, 12f, 12f, paint)
                }
                DrawingTool.CIRCLE -> {
                    val left = minOf(item.start.x, item.end.x)
                    val top = minOf(item.start.y, item.end.y)
                    val right = maxOf(item.start.x, item.end.x)
                    val bottom = maxOf(item.start.y, item.end.y)
                    canvas.drawOval(left, top, right, bottom, paint)
                }
                DrawingTool.TRIANGLE -> {
                    val path = android.graphics.Path()
                    val midX = (item.start.x + item.end.x) / 2f
                    path.moveTo(midX, minOf(item.start.y, item.end.y))
                    path.lineTo(item.end.x, maxOf(item.start.y, item.end.y))
                    path.lineTo(item.start.x, maxOf(item.start.y, item.end.y))
                    path.close()
                    canvas.drawPath(path, paint)
                }
                DrawingTool.ARROW -> {
                    canvas.drawLine(item.start.x, item.start.y, item.end.x, item.end.y, paint)
                    val angle = atan2((item.end.y - item.start.y).toDouble(), (item.end.x - item.start.x).toDouble())
                    val arrowHeadLen = (item.strokeWidth * 3f).coerceAtLeast(24f)
                    val x1 = item.end.x - arrowHeadLen * cos(angle - PI / 6).toFloat()
                    val y1 = item.end.y - arrowHeadLen * sin(angle - PI / 6).toFloat()
                    val x2 = item.end.x - arrowHeadLen * cos(angle + PI / 6).toFloat()
                    val y2 = item.end.y - arrowHeadLen * sin(angle + PI / 6).toFloat()
                    canvas.drawLine(item.end.x, item.end.y, x1, y1, paint)
                    canvas.drawLine(item.end.x, item.end.y, x2, y2, paint)
                }
                DrawingTool.STAR -> {
                    val centerX = (item.start.x + item.end.x) / 2f
                    val centerY = (item.start.y + item.end.y) / 2f
                    val radius = sqrt((item.end.x - item.start.x) * (item.end.x - item.start.x) + (item.end.y - item.start.y) * (item.end.y - item.start.y)) / 2f
                    if (radius > 5f) {
                        val path = android.graphics.Path()
                        val innerRadius = radius * 0.45f
                        var a = -PI / 2
                        val step = PI / 5
                        path.moveTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                        for (i in 0 until 5) {
                            a += step
                            path.lineTo((centerX + innerRadius * cos(a)).toFloat(), (centerY + innerRadius * sin(a)).toFloat())
                            a += step
                            path.lineTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                        }
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }
        return bitmap
    }

    fun saveDrawingToPng() {
        if (items.isEmpty() && activeBaseBitmap == null && existingImagePath == null) {
            context.showToastShort(context.getString(R.string.drawing_empty_toast))
            return
        }

        try {
            val bitmap = renderCurrentCanvasToBitmap()
            val dir = File(context.filesDir, "drawings").apply { mkdirs() }
            val file = File(dir, "sketch_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            onSaveDrawing(file.absolutePath)
            onDismiss()
        } catch (e: Exception) {
            context.showToastShort(context.getString(R.string.drawing_save_error, e.message ?: ""))
        }
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val canvasHeight = (screenHeight * 0.42f).coerceIn(240.dp, 360.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = !isDragging && !isPalmRejectionLocked
                )
                .padding(14.dp)
        ) {
            // Header: Close + Title + Actions (Undo, Inspect/Preview, Clear, Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (existingImagePath != null) stringResource(R.string.drawing_title_edit) else stringResource(R.string.drawing_title_canvas),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Undo (Restores last stroke, or restores cleared base drawing)
                    val canUndo = items.isNotEmpty() || (isBaseBitmapCleared && baseBitmap != null)
                    IconButton(
                        onClick = {
                            if (items.isNotEmpty()) {
                                items.removeAt(items.lastIndex)
                            } else if (isBaseBitmapCleared && baseBitmap != null) {
                                isBaseBitmapCleared = false
                            }
                        },
                        enabled = canUndo,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = stringResource(R.string.undo),
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Full-Screen Read-Only Inspect / Zoom Preview Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftOrangeLight)
                            .border(0.8.dp, VibrantOrange.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable {
                                if (items.isEmpty() && activeBaseBitmap == null) {
                                    context.showToastShort(context.getString(R.string.drawing_empty_toast))
                                } else {
                                    previewBitmapState = renderCurrentCanvasToBitmap()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔍 İncele",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Clear (Opens confirmation dialog before clearing canvas)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                if (items.isEmpty() && activeBaseBitmap == null) {
                                    context.showToastShort(context.getString(R.string.drawing_empty_toast))
                                } else {
                                    showClearConfirmDialog = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tool_clear),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Save Button (Guaranteed single line, no text wrapping)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(VibrantOrange)
                            .clickable { saveDrawingToPng() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (existingImagePath != null) stringResource(R.string.cat_update_btn) else stringResource(R.string.cat_add_btn_action),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stylus & Palm Rejection Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isPalmRejectionLocked) VibrantOrange.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isPalmRejectionLocked) VibrantOrange.copy(alpha = 0.45f)
                        else BorderLight.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                        isPalmRejectionLocked = !isPalmRejectionLocked
                        userManuallyToggledMode = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isPalmRejectionLocked) "🖊️ Avuç İçi Kilidi: Açık (Sadece Kalem)" else "👆 Parmak & Kalem Modu (Avuç Filtreli)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPalmRejectionLocked) VibrantOrange else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPalmRejectionLocked) VibrantOrange else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPalmRejectionLocked) "Parmak Aç" else "Sadece Kalem",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPalmRejectionLocked) PureWhite else VibrantOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tool Switcher: Primary Tools (Kalem & Silgi Yan Yana) + Shape Palette
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fixed Primary Tools: Kalem + Silgi (Always visible, prominent at front)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolChip(
                        title = stringResource(R.string.tool_pen),
                        isSelected = selectedTool == DrawingTool.PEN
                    ) {
                        selectedTool = DrawingTool.PEN
                    }

                    ToolChip(
                        title = stringResource(R.string.tool_eraser),
                        isSelected = selectedTool == DrawingTool.ERASER,
                        activeColor = Color(0xFFE53935)
                    ) {
                        selectedTool = DrawingTool.ERASER
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(BorderLight)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Scrollable Shapes Palette
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolChip(title = stringResource(R.string.tool_line), isSelected = selectedTool == DrawingTool.LINE) { selectedTool = DrawingTool.LINE }
                    ToolChip(title = stringResource(R.string.tool_rect), isSelected = selectedTool == DrawingTool.RECTANGLE) { selectedTool = DrawingTool.RECTANGLE }
                    ToolChip(title = stringResource(R.string.tool_circle), isSelected = selectedTool == DrawingTool.CIRCLE) { selectedTool = DrawingTool.CIRCLE }
                    ToolChip(title = stringResource(R.string.tool_triangle), isSelected = selectedTool == DrawingTool.TRIANGLE) { selectedTool = DrawingTool.TRIANGLE }
                    ToolChip(title = stringResource(R.string.tool_arrow), isSelected = selectedTool == DrawingTool.ARROW) { selectedTool = DrawingTool.ARROW }
                    ToolChip(title = stringResource(R.string.tool_star), isSelected = selectedTool == DrawingTool.STAR) { selectedTool = DrawingTool.STAR }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // White Interactive Drawing Board Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(canvasHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, BorderLight, RoundedCornerShape(16.dp))
                    .onSizeChanged { canvasSize = it },
                color = PureWhite
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            fun startStroke(offset: Offset, hardwareEraser: Boolean) {
                                isHardwareEraserActive = hardwareEraser
                                val activeTool = if (hardwareEraser) DrawingTool.ERASER else currentToolState
                                isDragging = true
                                dragStart = offset
                                dragCurrent = offset
                                isSnappedToLine = false

                                if (activeTool == DrawingTool.PEN || activeTool == DrawingTool.ERASER) {
                                    currentPoints.clear()
                                    currentPoints.add(offset)

                                    if (activeTool == DrawingTool.PEN) {
                                        snapTimerJob?.cancel()
                                        snapTimerJob = scope.launch {
                                            delay(450L)
                                            if (isDragging && currentPoints.size > 5) {
                                                isSnappedToLine = true
                                            }
                                        }
                                    }
                                }
                            }

                            fun moveStroke(positions: List<Offset>, finalPosition: Offset) {
                                val activeTool = if (isHardwareEraserActive) DrawingTool.ERASER else currentToolState
                                dragCurrent = finalPosition

                                if (activeTool == DrawingTool.PEN || activeTool == DrawingTool.ERASER) {
                                    for (pt in positions) {
                                        currentPoints.add(pt)
                                    }

                                    if (!isSnappedToLine && activeTool == DrawingTool.PEN) {
                                        snapTimerJob?.cancel()
                                        snapTimerJob = scope.launch {
                                            delay(450L)
                                            if (isDragging && currentPoints.size > 5) {
                                                isSnappedToLine = true
                                            }
                                        }
                                    }
                                }
                            }

                            fun finishStroke() {
                                snapTimerJob?.cancel()
                                if (!isDragging) return
                                val activeTool = if (isHardwareEraserActive) DrawingTool.ERASER else currentToolState

                                if (activeTool == DrawingTool.PEN || activeTool == DrawingTool.ERASER) {
                                    if (isSnappedToLine && currentPoints.size >= 2) {
                                        items.add(
                                            CanvasItem(
                                                tool = DrawingTool.LINE,
                                                points = emptyList(),
                                                start = currentPoints.first(),
                                                end = dragCurrent,
                                                color = currentColorState,
                                                strokeWidth = currentThicknessState
                                            )
                                        )
                                    } else if (currentPoints.isNotEmpty()) {
                                        val pointsCopy = if (currentPoints.size == 1) {
                                            listOf(currentPoints[0], Offset(currentPoints[0].x + 0.5f, currentPoints[0].y + 0.5f))
                                        } else {
                                            currentPoints.toList()
                                        }
                                        items.add(
                                            CanvasItem(
                                                tool = activeTool,
                                                points = pointsCopy,
                                                color = if (activeTool == DrawingTool.ERASER) PureWhite else currentColorState,
                                                strokeWidth = currentThicknessState,
                                                isEraser = activeTool == DrawingTool.ERASER
                                            )
                                        )
                                    }
                                    currentPoints.clear()
                                } else {
                                    items.add(
                                        CanvasItem(
                                            tool = activeTool,
                                            points = emptyList(),
                                            start = dragStart,
                                            end = dragCurrent,
                                            color = currentColorState,
                                            strokeWidth = currentThicknessState
                                        )
                                    )
                                }

                                isDragging = false
                                isSnappedToLine = false
                                isHardwareEraserActive = false
                            }

                            fun cancelStroke() {
                                snapTimerJob?.cancel()
                                isDragging = false
                                currentPoints.clear()
                                isSnappedToLine = false
                                isHardwareEraserActive = false
                            }

                            awaitEachGesture {
                                var activePointerId: PointerId? = null
                                var activePointerIsStylus = false

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val changes = event.changes

                                    // Always consume all pointer events on the canvas so rejected palm touches
                                    // never leak up to the ModalBottomSheet or ScrollView.
                                    changes.forEach { it.consume() }

                                    // 1. Check for Hardware Stylus / Eraser (Universal: S-Pen, Xiaomi, Huawei, Lenovo, USI 2.0)
                                    val stylusChange = changes.firstOrNull {
                                        it.type == PointerType.Stylus || it.type == PointerType.Eraser
                                    }

                                    if (stylusChange != null) {
                                        // Auto-enable Palm Rejection Lock the first instant a hardware stylus touches the screen
                                        if (!palmRejectionLockedState && !userManualModeState) {
                                            isPalmRejectionLocked = true
                                        }

                                        // If a palm/finger touch accidentally started right before the stylus tip landed,
                                        // discard the accidental finger trace and switch immediately to the stylus!
                                        if (activePointerId != null && !activePointerIsStylus) {
                                            cancelStroke()
                                            activePointerId = null
                                        }

                                        if (stylusChange.pressed) {
                                            if (activePointerId == null) {
                                                activePointerId = stylusChange.id
                                                activePointerIsStylus = true
                                                startStroke(
                                                    offset = stylusChange.position,
                                                    hardwareEraser = stylusChange.type == PointerType.Eraser
                                                )
                                            } else if (activePointerId == stylusChange.id) {
                                                val points = buildList {
                                                    stylusChange.historical.forEach { add(it.position) }
                                                    add(stylusChange.position)
                                                }
                                                moveStroke(points, stylusChange.position)
                                            }
                                        } else if (activePointerId == stylusChange.id) {
                                            // Stylus lifted: commit stroke immediately even if palm is still resting on screen!
                                            finishStroke()
                                            activePointerId = null
                                            activePointerIsStylus = false
                                        }
                                    } else if (!palmRejectionLockedState) {
                                        // 2. Finger & Passive Capacitive Stylus Mode (with Geometric & Anatomical Palm Filtering)
                                        val pressedTouches = changes.filter { it.pressed && it.type == PointerType.Touch }

                                        // Multi-touch palm cluster detection (3+ simultaneous touch points = resting palm)
                                        if (pressedTouches.size >= 3) {
                                            if (isDragging && currentPoints.size <= 8) {
                                                cancelStroke()
                                            } else if (isDragging) {
                                                finishStroke()
                                            }
                                            activePointerId = null
                                            activePointerIsStylus = false
                                        } else if (activePointerId != null) {
                                            // If palm landed at the bottom first (<= 6 pts) and passive pen/finger tip landed higher up,
                                            // switch tracking from the lower wrist touch to the upper pen/finger tip!
                                            val currentTracked = pressedTouches.firstOrNull { it.id == activePointerId }
                                            val upperTipCandidate = if (pressedTouches.size == 2 && currentPoints.size <= 6 && currentTracked != null) {
                                                pressedTouches.firstOrNull {
                                                    it.id != activePointerId && (currentTracked.position.y - it.position.y) > palmContactThresholdPx
                                                }
                                            } else null

                                            if (upperTipCandidate != null) {
                                                cancelStroke()
                                                activePointerId = upperTipCandidate.id
                                                activePointerIsStylus = false
                                                startStroke(offset = upperTipCandidate.position, hardwareEraser = false)
                                            } else if (currentTracked != null && currentTracked.pressed) {
                                                val points = buildList {
                                                    currentTracked.historical.forEach { add(it.position) }
                                                    add(currentTracked.position)
                                                }
                                                moveStroke(points, currentTracked.position)
                                            } else {
                                                finishStroke()
                                                activePointerId = null
                                                activePointerIsStylus = false
                                            }
                                        } else if (pressedTouches.isNotEmpty()) {
                                            // Choose the uppermost touch point (pen/finger tip is anatomically above resting wrist)
                                            val candidate = pressedTouches.minByOrNull { it.position.y }!!
                                            val isFlatPalmPressure = candidate.pressure > 0.94f && pressedTouches.size >= 2
                                            if (!isFlatPalmPressure) {
                                                activePointerId = candidate.id
                                                activePointerIsStylus = false
                                                startStroke(offset = candidate.position, hardwareEraser = false)
                                            }
                                        }
                                    }

                                    // Exit gesture loop once all pointers (including any resting palm) have lifted
                                    if (changes.none { it.pressed }) {
                                        if (activePointerId != null) {
                                            finishStroke()
                                            activePointerId = null
                                            activePointerIsStylus = false
                                        }
                                        break
                                    }
                                }
                            }
                        }
                ) {
                    // 0. Render existing base bitmap if editing an existing sketch (unless cleared)
                    activeBaseBitmap?.let { bmp ->
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                    }

                    // 1. Draw all committed canvas items
                    for (item in items) {
                        val drawColor = if (item.isEraser) PureWhite else item.color
                        val strokeStyle = Stroke(width = item.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

                        when (item.tool) {
                            DrawingTool.PEN, DrawingTool.ERASER -> {
                                if (item.points.size >= 2) {
                                    for (i in 0 until item.points.size - 1) {
                                        drawLine(
                                            color = drawColor,
                                            start = item.points[i],
                                            end = item.points[i + 1],
                                            strokeWidth = item.strokeWidth,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            DrawingTool.LINE -> {
                                drawLine(color = drawColor, start = item.start, end = item.end, strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                            }
                            DrawingTool.RECTANGLE -> {
                                val left = minOf(item.start.x, item.end.x)
                                val top = minOf(item.start.y, item.end.y)
                                val w = (maxOf(item.start.x, item.end.x) - left).coerceAtLeast(1f)
                                val h = (maxOf(item.start.y, item.end.y) - top).coerceAtLeast(1f)
                                drawRoundRect(color = drawColor, topLeft = Offset(left, top), size = Size(w, h), style = strokeStyle, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f))
                            }
                            DrawingTool.CIRCLE -> {
                                val left = minOf(item.start.x, item.end.x)
                                val top = minOf(item.start.y, item.end.y)
                                val w = (maxOf(item.start.x, item.end.x) - left).coerceAtLeast(1f)
                                val h = (maxOf(item.start.y, item.end.y) - top).coerceAtLeast(1f)
                                drawOval(color = drawColor, topLeft = Offset(left, top), size = Size(w, h), style = strokeStyle)
                            }
                            DrawingTool.TRIANGLE -> {
                                val path = Path()
                                val midX = (item.start.x + item.end.x) / 2f
                                path.moveTo(midX, minOf(item.start.y, item.end.y))
                                path.lineTo(item.end.x, maxOf(item.start.y, item.end.y))
                                path.lineTo(item.start.x, maxOf(item.start.y, item.end.y))
                                path.close()
                                drawPath(path, color = drawColor, style = strokeStyle)
                            }
                            DrawingTool.ARROW -> {
                                drawLine(color = drawColor, start = item.start, end = item.end, strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                                val angle = atan2((item.end.y - item.start.y).toDouble(), (item.end.x - item.start.x).toDouble())
                                val arrowHeadLen = (item.strokeWidth * 3f).coerceAtLeast(20f)
                                val x1 = item.end.x - arrowHeadLen * cos(angle - PI / 6).toFloat()
                                val y1 = item.end.y - arrowHeadLen * sin(angle - PI / 6).toFloat()
                                val x2 = item.end.x - arrowHeadLen * cos(angle + PI / 6).toFloat()
                                val y2 = item.end.y - arrowHeadLen * sin(angle + PI / 6).toFloat()
                                drawLine(color = drawColor, start = item.end, end = Offset(x1, y1), strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                                drawLine(color = drawColor, start = item.end, end = Offset(x2, y2), strokeWidth = item.strokeWidth, cap = StrokeCap.Round)
                            }
                            DrawingTool.STAR -> {
                                val centerX = (item.start.x + item.end.x) / 2f
                                val centerY = (item.start.y + item.end.y) / 2f
                                val radius = sqrt((item.end.x - item.start.x) * (item.end.x - item.start.x) + (item.end.y - item.start.y) * (item.end.y - item.start.y)) / 2f
                                if (radius > 5f) {
                                    val path = Path()
                                    val innerRadius = radius * 0.45f
                                    var a = -PI / 2
                                    val step = PI / 5
                                    path.moveTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                                    for (s in 0 until 5) {
                                        a += step
                                        path.lineTo((centerX + innerRadius * cos(a)).toFloat(), (centerY + innerRadius * sin(a)).toFloat())
                                        a += step
                                        path.lineTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                                    }
                                    path.close()
                                    drawPath(path, color = drawColor, style = strokeStyle)
                                }
                            }
                        }
                    }

                    // 2. Real-time Live Drag Drawing
                    if (isDragging) {
                        val effectiveTool = if (isHardwareEraserActive) DrawingTool.ERASER else selectedTool
                        val activeColor = if (effectiveTool == DrawingTool.ERASER) PureWhite else selectedColor
                        val liveStroke = Stroke(width = strokeThickness, cap = StrokeCap.Round, join = StrokeJoin.Round)

                        when (effectiveTool) {
                            DrawingTool.PEN -> {
                                if (isSnappedToLine && currentPoints.isNotEmpty()) {
                                    // Snapped to straight line: dynamic angle and length from start to drag position!
                                    drawLine(
                                        color = activeColor,
                                        start = currentPoints.first(),
                                        end = dragCurrent,
                                        strokeWidth = strokeThickness,
                                        cap = StrokeCap.Round
                                    )
                                } else if (currentPoints.size >= 2) {
                                    // 120 FPS Real-time smooth freehand stroke under fingertip
                                    for (i in 0 until currentPoints.size - 1) {
                                        drawLine(
                                            color = activeColor,
                                            start = currentPoints[i],
                                            end = currentPoints[i + 1],
                                            strokeWidth = strokeThickness,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            DrawingTool.ERASER -> {
                                if (currentPoints.size >= 2) {
                                    for (i in 0 until currentPoints.size - 1) {
                                        drawLine(
                                            color = PureWhite,
                                            start = currentPoints[i],
                                            end = currentPoints[i + 1],
                                            strokeWidth = strokeThickness,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                // Visual circular eraser indicator under finger for fine retouching
                                drawCircle(
                                    color = Color.LightGray.copy(alpha = 0.5f),
                                    radius = strokeThickness / 2f,
                                    center = dragCurrent,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                            DrawingTool.LINE -> {
                                drawLine(color = activeColor, start = dragStart, end = dragCurrent, strokeWidth = strokeThickness, cap = StrokeCap.Round)
                            }
                            DrawingTool.RECTANGLE -> {
                                val left = minOf(dragStart.x, dragCurrent.x)
                                val top = minOf(dragStart.y, dragCurrent.y)
                                val w = (maxOf(dragStart.x, dragCurrent.x) - left).coerceAtLeast(1f)
                                val h = (maxOf(dragStart.y, dragCurrent.y) - top).coerceAtLeast(1f)
                                drawRoundRect(color = activeColor, topLeft = Offset(left, top), size = Size(w, h), style = liveStroke, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f))
                            }
                            DrawingTool.CIRCLE -> {
                                val left = minOf(dragStart.x, dragCurrent.x)
                                val top = minOf(dragStart.y, dragCurrent.y)
                                val w = (maxOf(dragStart.x, dragCurrent.x) - left).coerceAtLeast(1f)
                                val h = (maxOf(dragStart.y, dragCurrent.y) - top).coerceAtLeast(1f)
                                drawOval(color = activeColor, topLeft = Offset(left, top), size = Size(w, h), style = liveStroke)
                            }
                            DrawingTool.TRIANGLE -> {
                                val path = Path()
                                val midX = (dragStart.x + dragCurrent.x) / 2f
                                path.moveTo(midX, minOf(dragStart.y, dragCurrent.y))
                                path.lineTo(dragCurrent.x, maxOf(dragStart.y, dragCurrent.y))
                                path.lineTo(dragStart.x, maxOf(dragStart.y, dragCurrent.y))
                                path.close()
                                drawPath(path, color = activeColor, style = liveStroke)
                            }
                            DrawingTool.ARROW -> {
                                drawLine(color = activeColor, start = dragStart, end = dragCurrent, strokeWidth = strokeThickness, cap = StrokeCap.Round)
                                val angle = atan2((dragCurrent.y - dragStart.y).toDouble(), (dragCurrent.x - dragStart.x).toDouble())
                                val arrowHeadLen = (strokeThickness * 3f).coerceAtLeast(20f)
                                val x1 = dragCurrent.x - arrowHeadLen * cos(angle - PI / 6).toFloat()
                                val y1 = dragCurrent.y - arrowHeadLen * sin(angle - PI / 6).toFloat()
                                val x2 = dragCurrent.x - arrowHeadLen * cos(angle + PI / 6).toFloat()
                                val y2 = dragCurrent.y - arrowHeadLen * sin(angle + PI / 6).toFloat()
                                drawLine(color = activeColor, start = dragCurrent, end = Offset(x1, y1), strokeWidth = strokeThickness, cap = StrokeCap.Round)
                                drawLine(color = activeColor, start = dragCurrent, end = Offset(x2, y2), strokeWidth = strokeThickness, cap = StrokeCap.Round)
                            }
                            DrawingTool.STAR -> {
                                val centerX = (dragStart.x + dragCurrent.x) / 2f
                                val centerY = (dragStart.y + dragCurrent.y) / 2f
                                val radius = sqrt((dragCurrent.x - dragStart.x) * (dragCurrent.x - dragStart.x) + (dragCurrent.y - dragStart.y) * (dragCurrent.y - dragStart.y)) / 2f
                                if (radius > 5f) {
                                    val path = Path()
                                    val innerRadius = radius * 0.45f
                                    var a = -PI / 2
                                    val step = PI / 5
                                    path.moveTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                                    for (s in 0 until 5) {
                                        a += step
                                        path.lineTo((centerX + innerRadius * cos(a)).toFloat(), (centerY + innerRadius * sin(a)).toFloat())
                                        a += step
                                        path.lineTo((centerX + radius * cos(a)).toFloat(), (centerY + radius * sin(a)).toFloat())
                                    }
                                    path.close()
                                    drawPath(path, color = activeColor, style = liveStroke)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Brush Thickness Slider & Floating Dynamic Preview
            val animatedPreviewThickness by animateFloatAsState(
                targetValue = strokeThickness,
                animationSpec = spring(),
                label = "previewSize"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Physical Size Preview Bubble
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((animatedPreviewThickness * 0.85f).coerceIn(3f, 34f).dp)
                            .clip(CircleShape)
                            .background(if (selectedTool == DrawingTool.ERASER) CharcoalGray.copy(alpha = 0.5f) else selectedColor)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Modern Slider
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTool == DrawingTool.ERASER) "Silgi Kalınlığı" else "Fırça Kalınlığı",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${strokeThickness.toInt()} px",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }

                    Slider(
                        value = strokeThickness,
                        onValueChange = { strokeThickness = it },
                        valueRange = 2f..48f,
                        colors = SliderDefaults.colors(
                            thumbColor = VibrantOrange,
                            activeTrackColor = VibrantOrange,
                            inactiveTrackColor = BorderLight
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Color Palette Chips
            if (selectedTool != DrawingTool.ERASER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PALETTE_COLORS.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) VibrantOrange else BorderLight,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    // Live Read-Only Full-Screen Zoom Inspection Overlay inside Drawing Studio
    previewBitmapState?.let { bmp ->
        MediaLightboxDialog(
            previewBitmap = bmp,
            isDrawing = true,
            onDismiss = { previewBitmapState = null }
        )
    }

    // Clear Confirmation Dialog (Prevents accidental loss of drawing & supports clearing saved base layer)
    if (showClearConfirmDialog) {
        val hasBothSavedAndNew = activeBaseBitmap != null && items.isNotEmpty()
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Çizimi Temizle?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (hasBothSavedAndNew) {
                            "Kayıtlı çizim üzerinde yeni eklediğiniz çizgiler var. Sadece son eklediklerinizi mi yoksa eski çizim dahil tüm tuvali mi temizlemek istiyorsunuz?"
                        } else {
                            "Tuval üzerindeki tüm çiziminiz silinecektir. Temizlemek istediğinize emin misiniz?"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasBothSavedAndNew) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    items.clear()
                                    showClearConfirmDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = SoftOrangeLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🧹 Sadece Yeni Eklediklerimi Sil",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        items.clear()
                        isBaseBitmapCleared = true
                        showClearConfirmDialog = false
                    }
                ) {
                    Text(
                        text = if (hasBothSavedAndNew) "Tüm Tuvali Sıfırla" else "Evet, Temizle",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun ToolChip(
    title: String,
    isSelected: Boolean,
    activeColor: Color = VibrantOrange,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
