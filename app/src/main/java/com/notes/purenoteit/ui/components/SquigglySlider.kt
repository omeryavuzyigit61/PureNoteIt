package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance 120 FPS Spotify / Android 13+ Wavy Squiggly Slider.
 * Uses hardware-accelerated drawing, cached Path object, and smooth value interpolation
 * to eliminate all stuttering during audio playback.
 */
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = false,
    waveAmplitude: Dp = 3.5.dp,
    waveLength: Dp = 26.dp,
    activeColor: Color = VibrantOrange,
    inactiveColor: Color = BorderLight,
    thumbColor: Color = VibrantOrange,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    // Smooth value interpolation to prevent jerky progress jumps while audio is playing
    val smoothValue by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "smoothProgress"
    )

    val currentDisplayValue = if (isDragging) value else smoothValue

    // Phase shift animation for fluid wave motion when playing
    val infiniteTransition = rememberInfiniteTransition(label = "squigglyPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseAnimation"
    )

    // Reuse Path to eliminate GC allocations on every animation frame
    val reusablePath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newValue)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val centerY = size.height / 2f
            val totalSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val progressFraction = ((currentDisplayValue - valueRange.start) / totalSpan).coerceIn(0f, 1f)
            val thumbX = progressFraction * size.width

            val ampPx = waveAmplitude.toPx()
            val waveLenPx = waveLength.toPx().coerceAtLeast(10f)
            val strokeWidthPx = 3.5.dp.toPx()

            // 1. Draw inactive line (beyond thumb to the end)
            if (thumbX < size.width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw elapsed sinusoidal squiggly wave (optimized with reused path and 10px steps)
            if (thumbX > 0f) {
                reusablePath.reset()
                reusablePath.moveTo(0f, centerY)

                val step = 10f
                var x = 0f
                while (x <= thumbX) {
                    val edgeDamping = when {
                        x < 24f -> (x / 24f).coerceIn(0f, 1f)
                        (thumbX - x) < 24f -> ((thumbX - x) / 24f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    val currentAmp = ampPx * edgeDamping
                    val rad = (x / waveLenPx) * (2 * PI).toFloat() - phase
                    val y = centerY + currentAmp * sin(rad)
                    reusablePath.lineTo(x, y)
                    x += step
                }
                reusablePath.lineTo(thumbX, centerY)

                drawPath(
                    path = reusablePath,
                    color = activeColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            // 3. Draw Thumb Knob (Knob with outer circle and crisp inner dot, exactly like Android lock screen)
            val thumbRadius = 7.dp.toPx()
            val innerRadius = 3.dp.toPx()

            // Outer thumb circle
            drawCircle(
                color = thumbColor,
                radius = if (isDragging) thumbRadius * 1.25f else thumbRadius,
                center = Offset(thumbX, centerY)
            )

            // Inner white dot
            drawCircle(
                color = PureWhite,
                radius = if (isDragging) innerRadius * 1.25f else innerRadius,
                center = Offset(thumbX, centerY)
            )
        }
    }
}
