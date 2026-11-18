package com.notes.purenoteit.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private enum class InkMode { PEN, HIGHLIGHTER, ERASER }

private val INK_COLORS = listOf(0xFF111111L, 0xFF1565C0L, 0xFFC62828L, 0xFF2E7D32L, 0xFFF9A825L)

/**
 * Pen-first inline drawing surface. A stylus (or the eraser end of one) draws immediately;
 * finger touches pass through so the note keeps scrolling, unless [fingerDraws] is switched on.
 * Strokes are stored as vectors ([InkDocument]); a PNG preview is stored for export/sync consumers.
 */
@Composable
fun InkBlockCanvas(
    inkJson: String,
    mediaPathResolver: MediaPathResolver?,
    onInkChange: (inkJson: String, previewPath: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var doc by remember(inkJson.isEmpty()) { mutableStateOf(InkCodec.decode(inkJson) ?: InkDocument()) }
    var mode by remember { mutableStateOf(InkMode.PEN) }
    var color by remember { mutableLongStateOf(INK_COLORS[0]) }
    var fingerDraws by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val undoStack = remember { mutableStateListOf<InkDocument>() }
    val currentMode by rememberUpdatedState(mode)
    val currentColor by rememberUpdatedState(color)
    val currentFinger by rememberUpdatedState(fingerDraws)
    val currentDoc by rememberUpdatedState(doc)

    // Debounced persistence: vector JSON + PNG preview.
    LaunchedEffect(version) {
        if (version == 0) return@LaunchedEffect
        delay(900)
        val snapshot = doc
        val json = InkCodec.encode(snapshot)
        val preview = if (snapshot.isEmpty || mediaPathResolver == null) null else try {
            val bytes = withContext(Dispatchers.Default) { renderPreviewPng(snapshot) }
            mediaPathResolver.storeMediaBytes(bytes, "drawings", "png")
        } catch (_: Exception) {
            null
        }
        onInkChange(json, preview)
    }

    fun commit(newDoc: InkDocument) {
        undoStack.add(doc)
        if (undoStack.size > 50) undoStack.removeAt(0)
        doc = newDoc
        version++
    }

    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(mode == InkMode.PEN, { mode = InkMode.PEN }, label = { Text("Kalem") })
            FilterChip(mode == InkMode.HIGHLIGHTER, { mode = InkMode.HIGHLIGHTER }, label = { Text("Fosforlu") })
            FilterChip(mode == InkMode.ERASER, { mode = InkMode.ERASER }, label = { Text("Silgi") })
            FilterChip(fingerDraws, { fingerDraws = !fingerDraws }, label = { Text("Parmak") })
            if (zoom != 1f || pan != Offset.Zero) {
                FilterChip(false, { zoom = 1f; pan = Offset.Zero }, label = { Text("1x") })
            }
            FilterChip(
                selected = false,
                onClick = {
                    if (undoStack.isNotEmpty()) {
                        doc = undoStack.removeAt(undoStack.lastIndex)
                        version++
                    }
                },
                enabled = undoStack.isNotEmpty(),
                label = { Text("Geri al") }
            )
        }
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INK_COLORS.forEach { c ->
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 1.dp, Color.Gray, CircleShape)
                        .clickable { color = c; if (mode == InkMode.ERASER) mode = InkMode.PEN }
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, Color(0xFFDDDDDD), RoundedCornerShape(14.dp))
                .background(Color.White)
                .pointerInput("zoom") {
                    // Two-finger pinch/pan; single-finger touch is left alone so the note keeps scrolling.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val touches = event.changes.filter { it.pressed && it.type == PointerType.Touch }
                            if (touches.size >= 2 && event.changes.none { it.type == PointerType.Stylus }) {
                                val zc = event.calculateZoom()
                                val pc = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                val newZoom = (zoom * zc).coerceIn(1f, 5f)
                                val applied = newZoom / zoom
                                pan = if (newZoom == 1f) Offset.Zero
                                else centroid - (centroid - pan) * applied + pc
                                zoom = newZoom
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val type = down.type
                        val isEraserTool = type == PointerType.Eraser
                        val allowed = type == PointerType.Stylus || isEraserTool ||
                            type == PointerType.Mouse || (type == PointerType.Touch && currentFinger)
                        if (!allowed) return@awaitEachGesture // touch scrolls the note
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val scale = currentDoc.width / w
                        val erasing = isEraserTool || currentMode == InkMode.ERASER
                        down.consume()
                        val pts = mutableListOf<InkPoint>()
                        fun add(o: Offset, pressure: Float, t: Long) {
                            val z = currentZoom
                            val pn = currentPan
                            val p = InkPoint(((o.x - pn.x) / z) * scale, ((o.y - pn.y) / z) * scale, pressure.coerceIn(0f, 1f), t)
                            if (erasing) {
                                val next = doc.eraseAt(p.x, p.y, 14f * scale / z)
                                if (next !== doc) { undoStack.add(doc); doc = next; version++ }
                            } else {
                                pts.add(p); live = pts.toList()
                            }
                        }
                        add(down.position, if (down.pressure > 0f) down.pressure else 0.5f, down.uptimeMillis)
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { ch.consume(); break }
                            ch.consume()
                            add(ch.position, if (ch.pressure > 0f) ch.pressure else 0.5f, ch.uptimeMillis)
                        }
                        if (!erasing && pts.isNotEmpty()) {
                            val tool = if (currentMode == InkMode.HIGHLIGHTER) InkTool.HIGHLIGHTER else InkTool.PEN
                            val argb = if (tool == InkTool.HIGHLIGHTER)
                                (currentColor and 0x00FFFFFFL) or 0x66000000L else currentColor
                            commit(doc.withStroke(InkStroke(java.util.UUID.randomUUID().toString(), tool, argb, 3f, pts)))
                        }
                        live = emptyList()
                    }
                }
        ) {
            val scale = size.width / doc.width
            fun draw(stroke: InkStroke, pts: List<InkPoint>) {
                val c = Color(stroke.colorArgb)
                if (pts.size == 1) {
                    drawCircle(c, InkCodec.widthAt(stroke, pts[0]) * scale / 2f, Offset(pts[0].x * scale, pts[0].y * scale))
                }
                for (i in 1 until pts.size) {
                    drawLine(
                        c, Offset(pts[i - 1].x * scale, pts[i - 1].y * scale),
                        Offset(pts[i].x * scale, pts[i].y * scale),
                        strokeWidth = InkCodec.widthAt(stroke, pts[i]) * scale, cap = StrokeCap.Round
                    )
                }
            }
            withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, Offset.Zero) }) {
            doc.strokes.forEach { draw(it, it.points) }
            if (live.isNotEmpty()) {
                val tool = if (mode == InkMode.HIGHLIGHTER) InkTool.HIGHLIGHTER else InkTool.PEN
                val argb = if (tool == InkTool.HIGHLIGHTER) (color and 0x00FFFFFFL) or 0x66000000L else color
                draw(InkStroke("live", tool, argb, 3f, live), live)
            }
            }
        }
    }
}

private fun renderPreviewPng(doc: InkDocument): ByteArray {
    val bmp = Bitmap.createBitmap(doc.width.toInt() / 2, (doc.height.toInt() / 2).coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val s = 0.5f
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { strokeCap = AndroidPaint.Cap.ROUND }
    doc.strokes.forEach { st ->
        paint.color = st.colorArgb.toInt()
        val pts = st.points
        if (pts.size == 1) {
            paint.style = AndroidPaint.Style.FILL
            canvas.drawCircle(pts[0].x * s, pts[0].y * s, InkCodec.widthAt(st, pts[0]) * s / 2f, paint)
        }
        paint.style = AndroidPaint.Style.STROKE
        for (i in 1 until pts.size) {
            paint.strokeWidth = InkCodec.widthAt(st, pts[i]) * s
            canvas.drawLine(pts[i - 1].x * s, pts[i - 1].y * s, pts[i].x * s, pts[i].y * s, paint)
        }
    }
    return ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
}
