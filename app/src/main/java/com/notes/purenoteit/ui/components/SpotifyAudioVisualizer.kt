package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * Continuous, fluid sinusoidal audio wave visualizer.
 * Responds to real-time speech amplitude with flowing waves.
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

    Box(
        modifier = modifier
            .height(maxHeight)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            val normalizedAmp = if (isPlayingOrRecording) {
                (currentAmplitude / 32767f).coerceIn(0.18f, 1f)
            } else {
                0.08f
            }

            val maxAmpPx = (height / 2f - 2.dp.toPx()) * normalizedAmp

            // Primary harmonic wave
            val path1 = Path()
            path1.moveTo(0f, centerY)

            val path2 = Path()
            path2.moveTo(0f, centerY)

            val step = 3f
            var x = 0f
            val waveLen = (width / 2.2f).coerceAtLeast(30f)

            while (x <= width) {
                // Envelope window: 0 at edges, 1 in the middle
                val envelope = sin((x / width) * PI).toFloat()

                val y1 = centerY + (maxAmpPx * envelope) * sin((x / waveLen) * (2 * PI).toFloat() - phase)
                path1.lineTo(x, y1)

                val y2 = centerY + (maxAmpPx * 0.7f * envelope) * sin((x / (waveLen * 1.3f)) * (2 * PI).toFloat() - secondaryPhase)
                path2.lineTo(x, y2)

                x += step
            }

            // Draw secondary ambient wave (semi-transparent)
            drawPath(
                path = path2,
                color = activeColor.copy(alpha = if (isPlayingOrRecording) 0.45f else 0.2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw primary crisp wave
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
