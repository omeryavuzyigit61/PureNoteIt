package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
 * Spotify / Android 13+ Wavy Squiggly Slider.
 * The elapsed portion renders as a fluid, animated sinusoidal wave ending at the thumb knob,
 * while the remaining portion continues as a sleek horizontal rail.
 */
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = false,
    waveAmplitude: Dp = 4.dp,
    waveLength: Dp = 22.dp,
    activeColor: Color = VibrantOrange,
    inactiveColor: Color = BorderLight,
    thumbColor: Color = VibrantOrange,
    modifier: Modifier = Modifier
) {
    // Continuously animate phase shift when playing to give fluid water-like motion
    val infiniteTransition = rememberInfiniteTransition(label = "squigglyPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseAnimation"
    )

    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

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
            widthPx = size.width
            val height = size.height
            val centerY = height / 2f

            val totalSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val progressFraction = ((value - valueRange.start) / totalSpan).coerceIn(0f, 1f)
            val thumbX = progressFraction * size.width

            val ampPx = waveAmplitude.toPx()
            val waveLenPx = waveLength.toPx().coerceAtLeast(10f)
            val strokeWidthPx = 3.dp.toPx()

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

            // 2. Draw elapsed sinusoidal squiggly wave (from start up to thumb)
            if (thumbX > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                val step = 3f
                var x = 0f
                while (x <= thumbX) {
                    // Dampen amplitude near the start and near the thumb for smooth entry & exit
                    val edgeDamping = when {
                        x < 20f -> (x / 20f).coerceIn(0f, 1f)
                        (thumbX - x) < 20f -> ((thumbX - x) / 20f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    val currentAmp = ampPx * edgeDamping
                    val rad = (x / waveLenPx) * (2 * PI).toFloat() - phase
                    val y = centerY + currentAmp * sin(rad)
                    wavePath.lineTo(x, y)
                    x += step
                }
                // Ensure wave touches thumb exactly at centerY
                wavePath.lineTo(thumbX, centerY)

                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            // 3. Draw Thumb Knob (Knob with outer glow / ring and crisp inner circle)
            val thumbRadius = 7.dp.toPx()
            val innerRadius = 3.dp.toPx()

            // Outer thumb circle
            drawCircle(
                color = thumbColor,
                radius = if (isDragging) thumbRadius * 1.25f else thumbRadius,
                center = Offset(thumbX, centerY)
            )

            // Inner white dot for high-end Spotify look
            drawCircle(
                color = PureWhite,
                radius = if (isDragging) innerRadius * 1.25f else innerRadius,
                center = Offset(thumbX, centerY)
            )
        }
    }
}
