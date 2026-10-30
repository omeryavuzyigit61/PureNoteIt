package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlin.math.PI
import kotlin.math.sin

/**
 * Optimized 120 FPS continuous sinusoidal audio wave visualizer.
 * Reuses internal Paths and runs smooth GPU-accelerated drawing.
 */
@Composable
fun SpotifyAudioVisualizer(
    isPlayingOrRecording: Boolean,
    currentAmplitude: Int = 0,
    maxHeight: Dp = 28.dp,
    activeColor: Color = VibrantOrange,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluidWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlayingOrRecording) (2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlayingOrRecording) (-2 * PI).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryPhase"
    )

    // Ambient amplitude swell when playing
    val ambientSwell by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientSwell"
    )

    // Path reuse to eliminate GC churn
    val path1 = remember { Path() }
    val path2 = remember { Path() }

    Box(
        modifier = modifier.height(maxHeight)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            val liveAmpFraction = if (currentAmplitude > 0) {
                (currentAmplitude / 32767f).coerceIn(0.2f, 1f)
            } else if (isPlayingOrRecording) {
                ambientSwell
            } else {
                0.08f
            }

            val maxAmpPx = (height / 2f - 2.dp.toPx()) * liveAmpFraction

            path1.reset()
            path1.moveTo(0f, centerY)

            path2.reset()
            path2.moveTo(0f, centerY)

            val step = 8f
            var x = 0f
            val waveLen = (width / 2.2f).coerceAtLeast(30f)

            while (x <= width) {
                val envelope = sin((x / width) * PI).toFloat()

                val y1 = centerY + (maxAmpPx * envelope) * sin((x / waveLen) * (2 * PI).toFloat() - phase)
                path1.lineTo(x, y1)

                val y2 = centerY + (maxAmpPx * 0.7f * envelope) * sin((x / (waveLen * 1.3f)) * (2 * PI).toFloat() - secondaryPhase)
                path2.lineTo(x, y2)

                x += step
            }

            // Draw secondary wave
            drawPath(
                path = path2,
                color = activeColor.copy(alpha = if (isPlayingOrRecording) 0.4f else 0.15f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw primary wave
            drawPath(
                path = path1,
                brush = Brush.horizontalGradient(
                    colors = if (isPlayingOrRecording) {
                        listOf(activeColor.copy(alpha = 0.7f), activeColor, activeColor.copy(alpha = 0.7f))
                    } else {
                        listOf(activeColor.copy(alpha = 0.3f), activeColor.copy(alpha = 0.4f), activeColor.copy(alpha = 0.3f))
                    }
                ),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
