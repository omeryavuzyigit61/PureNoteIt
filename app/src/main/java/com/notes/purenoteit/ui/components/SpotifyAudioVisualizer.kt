package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SpotifyAudioVisualizer(
    isPlayingOrRecording: Boolean,
    currentAmplitude: Int = 0,
    barCount: Int = 20,
    maxHeight: Dp = 32.dp,
    minHeight: Dp = 4.dp,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 3.dp,
    activeColor: Color = VibrantOrange,
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        mutableStateListOf<Float>().apply {
            repeat(barCount) { add(0.2f) }
        }
    }

    LaunchedEffect(isPlayingOrRecording, currentAmplitude) {
        if (isPlayingOrRecording) {
            val normalizedAmp = (currentAmplitude / 32767f).coerceIn(0.1f, 1f)
            for (i in 0 until barCount) {
                // Harmonic Spotify equalizer wave
                val variation = Random.nextFloat() * 0.4f
                val waveFactor = kotlin.math.sin((i.toDouble() / barCount) * Math.PI).toFloat()
                val target = ((normalizedAmp * 0.7f + variation) * (0.4f + waveFactor * 0.6f)).coerceIn(0.15f, 1f)
                barHeights[i] = target
            }
        } else {
            // Resting state
            for (i in 0 until barCount) {
                val restingWave = 0.2f + (kotlin.math.sin((i.toDouble() / barCount) * Math.PI) * 0.15f).toFloat()
                barHeights[i] = restingWave
            }
        }
    }

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(barSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val heightFraction = barHeights.getOrElse(i) { 0.2f }
            val currentH = minHeight + (maxHeight - minHeight) * heightFraction

            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(currentH)
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isPlayingOrRecording) {
                                listOf(activeColor, activeColor.copy(alpha = 0.7f))
                            } else {
                                listOf(activeColor.copy(alpha = 0.4f), activeColor.copy(alpha = 0.2f))
                            }
                        )
                    )
            )
        }
    }
}
