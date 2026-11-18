package com.notes.purenoteit.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.model.BlockParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DesktopWhiteboardView(
    notes: List<Note>,
    links: List<NoteLink>,
    categories: List<Category>,
    onSelectNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val baseCardWidthPx = with(density) { 230.dp.toPx() }
    val baseCardHeightPx = with(density) { 144.dp.toPx() }

    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset(48f, 48f)) }
    val cardPositions = remember { mutableStateMapOf<String, Offset>() }

    fun defaultGridOffset(index: Int): Offset {
        val col = index % 4
        val row = index / 4
        return Offset(
            x = col * 270f + (row % 2) * 32f,
            y = row * 190f
        )
    }

    LaunchedEffect(notes) {
        notes.forEachIndexed { idx, note ->
            if (!cardPositions.containsKey(note.syncUuid)) {
                cardPositions[note.syncUuid] = defaultGridOffset(idx)
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val gridColor = if (isDark) Color(0xFF2E2E33) else Color(0xFFDCDCE3)
    val canvasBg = if (isDark) Color(0xFF161618) else Color(0xFFF3F4F8)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = canvasBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                    val factor = if (deltaY < 0f) 1.08f else 0.92f
                                    zoom = (zoom * factor).coerceIn(0.45f, 2.0f)
                                }
                            }
                        }
                    }
                }
        ) {
            // Dotted grid + arrow links between connected notes
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28f * zoom
                if (step > 6f) {
                    val startX = ((panOffset.x % step) + step) % step
                    val startY = ((panOffset.y % step) + step) % step
                    var gx = startX
                    while (gx < size.width) {
                        var gy = startY
                        while (gy < size.height) {
                            drawCircle(color = gridColor, radius = 1.4f, center = Offset(gx, gy))
                            gy += step
                        }
                        gx += step
                    }
                }

                val posByUuid = HashMap<String, Offset>(notes.size)
                notes.forEachIndexed { idx, n ->
                    posByUuid[n.syncUuid] = cardPositions[n.syncUuid] ?: defaultGridOffset(idx)
                }
                val centerHalfW = (baseCardWidthPx * zoom) / 2f
                val centerHalfH = (baseCardHeightPx * zoom) / 2f
                for (link in links) {
                    val srcPos = posByUuid[link.sourceSyncUuid] ?: continue
                    val tgtPos = posByUuid[link.targetSyncUuid] ?: continue
                    val start = Offset(
                        x = panOffset.x + srcPos.x * zoom + centerHalfW,
                        y = panOffset.y + srcPos.y * zoom + centerHalfH
                    )
                    val end = Offset(
                        x = panOffset.x + tgtPos.x * zoom + centerHalfW,
                        y = panOffset.y + tgtPos.y * zoom + centerHalfH
                    )
                    val ctrl = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - 42f * zoom)
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
                    }
                    drawPath(
                        path = path,
                        color = DesktopColors.VibrantOrange.copy(alpha = 0.72f),
                        style = Stroke(
                            width = 2.2f * zoom,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                        )
                    )
                    val angle = atan2(end.y - ctrl.y, end.x - ctrl.x)
                    val arrowLen = 11f * zoom
                    val a1 = Offset(
                        x = end.x - arrowLen * cos(angle - 0.45f),
                        y = end.y - arrowLen * sin(angle - 0.45f)
                    )
                    val a2 = Offset(
                        x = end.x - arrowLen * cos(angle + 0.45f),
                        y = end.y - arrowLen * sin(angle + 0.45f)
                    )
                    drawLine(DesktopColors.VibrantOrange, end, a1, strokeWidth = 2.4f * zoom)
                    drawLine(DesktopColors.VibrantOrange, end, a2, strokeWidth = 2.4f * zoom)
                }
            }

            // Draggable note cards
            notes.forEachIndexed { idx, note ->
                val worldPos = cardPositions[note.syncUuid] ?: defaultGridOffset(idx)
                val screenX = panOffset.x + worldPos.x * zoom
                val screenY = panOffset.y + worldPos.y * zoom
                val categoryName = categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                val preview = remember(note.description) {
                    BlockParser.toPlainTextSummary(BlockParser.parse(note.description), maxLines = 2, includeDecorations = false)
                }
                val scaledWidth = 230.dp * zoom
                val scaledHeight = 144.dp * zoom

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        if (note.isPinned) DesktopColors.VibrantOrange.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
                        .size(width = scaledWidth, height = scaledHeight)
                        .pointerInput(note.syncUuid, zoom, idx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val current = cardPositions[note.syncUuid] ?: defaultGridOffset(idx)
                                cardPositions[note.syncUuid] = current + Offset(
                                    dragAmount.x / zoom,
                                    dragAmount.y / zoom
                                )
                            }
                        }
                        .clickable { onSelectNote(note.syncUuid) }
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DesktopNoteIconBadge(
                                iconKey = note.icon,
                                isLocked = note.isPassword == true || note.isEncrypted,
                                size = (26f * zoom.coerceIn(0.8f, 1.2f)).dp,
                                iconSize = (14f * zoom.coerceIn(0.8f, 1.2f)).dp,
                                cornerRadius = 7.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = note.title?.takeIf { it.isNotBlank() } ?: "Başlıksız Not",
                                fontSize = (13f * zoom.coerceIn(0.8f, 1.15f)).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.OpenWith,
                                contentDescription = "Sürükle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (preview.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = preview,
                                fontSize = (11.5f * zoom.coerceIn(0.8f, 1.1f)).sp,
                                lineHeight = (15f * zoom.coerceIn(0.8f, 1.1f)).sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!categoryName.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DesktopColors.VibrantOrange.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = categoryName,
                                        fontSize = 10.sp,
                                        color = DesktopColors.VibrantOrange,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            Text(
                                text = "Düzenle →",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = DesktopColors.VibrantOrange
                            )
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("Sonsuz Tuval Hazır", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sürüklenebilir kartlar ve bağlantı okları için ilk notunuzu oluşturun.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = onCreateNote,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Yeni Not Ekle")
                            }
                        }
                    }
                }
            }

            // Floating Whiteboard Controls (Top-Right)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { zoom = (zoom - 0.15f).coerceIn(0.45f, 2.0f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Uzaklaştır", modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = "${(zoom * 100).roundToInt()}%",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { zoom = (zoom + 0.15f).coerceIn(0.45f, 2.0f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Yakınlaştır", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = {
                            zoom = 1f
                            panOffset = Offset(48f, 48f)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Görünümü Sıfırla",
                            tint = DesktopColors.VibrantOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private val STREAM_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("tr-TR"))

@Composable
fun DesktopStreamView(
    streamItems: List<StreamItem>,
    onAddItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onConvertToNote: (List<String>, String) -> Unit,
    onOpenNote: (String) -> Unit,
    onPlayAudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftText by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, PENDING, PROCESSED
    val selectedUuids = remember { mutableStateListOf<String>() }
    var showConvertDialog by remember { mutableStateOf(false) }
    var convertedNoteTitle by remember { mutableStateOf("") }

    val visibleItems = remember(streamItems, filterMode) {
        when (filterMode) {
            "PENDING" -> streamItems.filter { !it.isProcessed }
            "PROCESSED" -> streamItems.filter { it.isProcessed }
            else -> streamItems
        }
    }

    fun submitThought() {
        val clean = draftText.trim()
        if (clean.isNotEmpty()) {
            onAddItem(clean)
            draftText = ""
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick Capture Dock
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, DesktopColors.VibrantOrange.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DesktopColors.VibrantOrange.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = DesktopColors.VibrantOrange,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (draftText.isEmpty()) {
                            Text(
                                "Aklından geçeni hızlıca yaz, daha sonra tek tıkla nota dönüştür... (Enter)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                                    ) {
                                        submitThought()
                                        true
                                    } else {
                                        false
                                    }
                                }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = ::submitThought,
                        enabled = draftText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Akışa Ekle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Filter row & batch select info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "ALL" to "Tümü (${streamItems.size})",
                    "PENDING" to "Bekleyenler (${streamItems.count { !it.isProcessed }})",
                    "PROCESSED" to "Nota Dönüşenler"
                ).forEach { (key, label) ->
                    val active = filterMode == key
                    val bg by animateColorAsState(
                        if (active) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surface
                    )
                    val fg by animateColorAsState(
                        if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = bg,
                        border = BorderStroke(1.dp, if (active) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { filterMode = key }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = fg,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selectedUuids.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { selectedUuids.clear() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Seçimi Temizle (${selectedUuids.size})", fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (visibleItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Text("⚡ Zaman Tüneli Boş", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Yukarıya hızlı notlar yaz veya telefondan eşitlediğin fikirlerin burada akmasını izle.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = if (selectedUuids.isNotEmpty()) 72.dp else 12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(visibleItems, key = { it.syncUuid }) { item ->
                        val isSelected = item.syncUuid in selectedUuids
                        val timeText = remember(item.timestamp) {
                            runCatching {
                                Instant.ofEpochMilli(item.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .format(STREAM_DATE_FORMAT)
                            }.getOrDefault("")
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (isSelected) selectedUuids.remove(item.syncUuid) else selectedUuids.add(item.syncUuid)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isSelected) selectedUuids.remove(item.syncUuid) else selectedUuids.add(item.syncUuid)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                        contentDescription = "Seç",
                                        tint = if (isSelected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = timeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DesktopColors.VibrantOrange
                                        )
                                        if (item.isProcessed) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = DesktopColors.EmeraldGreen.copy(alpha = 0.14f),
                                                modifier = Modifier.clickable(enabled = !item.mergedNoteSyncUuid.isNullOrBlank()) {
                                                    item.mergedNoteSyncUuid?.let(onOpenNote)
                                                }
                                            ) {
                                                Text(
                                                    text = "✓ Nota Dönüştürüldü",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DesktopColors.EmeraldGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (item.type.equals("AUDIO", ignoreCase = true) && !item.audioPath.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = { item.audioPath?.let(onPlayAudio) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Sesi Aç", fontSize = 10.5.sp)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.content.ifBlank { if (item.type == "AUDIO") "Sesli Fikir Kaydı" else "" },
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedUuids.remove(item.syncUuid)
                                        onDeleteItem(item.syncUuid)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Sil",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Convert to Note Floating Bar
        AnimatedVisibility(
            visible = selectedUuids.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DesktopColors.VibrantOrange,
                shadowElevation = 10.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            convertedNoteTitle = "Fikir Notu (${selectedUuids.size} Madde)"
                            showConvertDialog = true
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${selectedUuids.size} Fikri Nota Dönüştür",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (showConvertDialog) {
            DesktopAnimatedDialog(
                onDismiss = { showConvertDialog = false },
                width = 420.dp,
                title = "Fikirleri Nota Dönüştür",
                subtitle = "Seçilen ${selectedUuids.size} fikir bir araya getirilerek yeni bir not oluşturulur.",
                badgeIcon = Icons.Rounded.AutoAwesome,
                badgeTint = DesktopColors.VibrantOrange
            ) {
                OutlinedTextField(
                    value = convertedNoteTitle,
                    onValueChange = { convertedNoteTitle = it.take(80) },
                    label = { Text("Yeni not başlığı") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = { showConvertDialog = false }, shape = RoundedCornerShape(12.dp)) {
                        Text("İptal")
                    }
                    Button(
                        onClick = {
                            val ids = selectedUuids.toList()
                            val title = convertedNoteTitle.trim()
                            showConvertDialog = false
                            selectedUuids.clear()
                            onConvertToNote(ids, title)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Not Oluştur")
                    }
                }
            }
        }
    }
}
