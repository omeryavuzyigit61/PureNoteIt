package com.notes.purenoteit.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.io.File
import kotlin.math.roundToInt

/**
 * Full-Screen Read-Only Pinch-to-Zoom Lightbox Viewer for Images & Drawings.
 * Features:
 * - Edge-to-edge full-screen overlay (preserves underlying note scroll position)
 * - 1x to 5x multi-touch Pinch-to-Zoom & boundary-clamped Pan
 * - Double-tap quick zoom toggle (1x <-> 2.5x)
 * - 100% Read-Only (prevents accidental drawing strokes or layout resizing while inspecting)
 * - Quick +/- zoom controls and optional "Düzenle" shortcut for drawings
 */
@Composable
fun MediaLightboxDialog(
    imagePath: String? = null,
    previewBitmap: Bitmap? = null,
    isDrawing: Boolean = false,
    onDismiss: () -> Unit,
    onSwitchToEdit: (() -> Unit)? = null
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(),
        label = "lightboxScale"
    )

    fun clampOffset(targetOffset: Offset, targetScale: Float): Offset {
        if (targetScale <= 1.01f) return Offset.Zero
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = targetOffset.x.coerceIn(-maxX, maxX),
            y = targetOffset.y.coerceIn(-maxY, maxY)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEB0E0E12))
                .onSizeChanged { containerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                val targetScale = 2.5f
                                val centerX = containerSize.width / 2f
                                val centerY = containerSize.height / 2f
                                val rawOffset = Offset(
                                    x = (centerX - tapOffset.x) * (targetScale - 1f),
                                    y = (centerY - tapOffset.y) * (targetScale - 1f)
                                )
                                scale = targetScale
                                offset = clampOffset(rawOffset, targetScale)
                            }
                        },
                        onTap = {
                            if (scale <= 1.02f) {
                                // Do nothing on single tap inside image area; top X or back button closes cleanly
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        val newOffset = if (newScale <= 1.01f) {
                            Offset.Zero
                        } else {
                            clampOffset(offset + pan, newScale)
                        }
                        scale = newScale
                        offset = newOffset
                    }
                }
        ) {
            // 1. Interactive Zoomable Read-Only Media Surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 72.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDrawing) PureWhite else Color.Transparent)
                        .then(
                            if (isDrawing) {
                                Modifier
                                    .border(1.5.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Önizleme",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!imagePath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = "Tam Ekran Görsel",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Top Floating Header Bar (Read-Only Badge + Edit Shortcut + Close Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mode Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xCC1E1E24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDrawing) "🎨 Çizim İnceleme (Salt Okunur)" else "🖼️ Görsel İnceleme (Salt Okunur)",
                            color = PureWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Optional "Düzenle" button if opened from a Note Drawing Block
                    if (onSwitchToEdit != null) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    onDismiss()
                                    onSwitchToEdit()
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = VibrantOrange
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "✏️", fontSize = 11.sp)
                                Text(
                                    text = "Düzenle",
                                    color = PureWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Close Button
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onDismiss() },
                        shape = CircleShape,
                        color = Color(0xCC1E1E24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✕",
                                color = PureWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Bottom Floating Zoom Controls Pill (- / 100% Reset / +)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xD91E1E24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PureWhite.copy(alpha = 0.18f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Zoom Out (-)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(PureWhite.copy(alpha = 0.1f))
                                .clickable {
                                    val next = (scale - 0.5f).coerceAtLeast(1f)
                                    scale = next
                                    offset = clampOffset(offset, next)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                color = PureWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Current Zoom Percentage (Tap to Reset to 100%)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔍 ${(scale * 100).roundToInt()}% (Sıfırla)",
                                color = if (scale > 1.05f) VibrantOrange else PureWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Zoom In (+)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(PureWhite.copy(alpha = 0.1f))
                                .clickable {
                                    val next = (scale + 0.5f).coerceAtMost(5f)
                                    scale = next
                                    offset = clampOffset(offset, next)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = PureWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
