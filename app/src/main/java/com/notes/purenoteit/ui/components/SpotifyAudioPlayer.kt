package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import kotlinx.coroutines.delay

private val AVAILABLE_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyAudioPlayer(
    filePath: String,
    audioRecorder: AudioRecorder,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(audioRecorder.getAudioDuration(filePath).coerceAtLeast(1000)) }
    var selectedSpeed by remember { mutableFloatStateOf(audioRecorder.getSpeed()) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    // Live progress tracker when playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                val current = audioRecorder.getCurrentPosition()
                val total = audioRecorder.getDuration()
                if (total > 0) durationMs = total
                currentPositionMs = current
                isPlaying = audioRecorder.isPlaying()
                delay(80L)
            }
        }
    }

    // Stop player when this composable leaves composition
    DisposableEffect(filePath) {
        onDispose {
            if (isPlaying) {
                audioRecorder.stopPlaying()
            }
        }
    }

    fun togglePlayPause() {
        audioRecorder.playOrPause(
            filePath = filePath,
            onStatusChange = { playing, current, total ->
                isPlaying = playing
                currentPositionMs = current
                if (total > 0) durationMs = total
            },
            onCompletion = {
                isPlaying = false
                currentPositionMs = 0
            }
        )
    }

    fun cycleSpeed() {
        val nextIndex = (AVAILABLE_SPEEDS.indexOf(selectedSpeed) + 1) % AVAILABLE_SPEEDS.size
        val newSpeed = AVAILABLE_SPEEDS[nextIndex]
        selectedSpeed = newSpeed
        audioRecorder.setSpeed(newSpeed)
    }

    fun formatTime(ms: Int): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.8f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header: Play/Pause button + Visualizer + Speed Pill + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VibrantOrange)
                        .clickable { togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.play_pause else R.drawable.play_pause
                        ),
                        contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                        tint = PureWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Spotify Visualizer
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Ses Notu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Spotify Fluid Waveform
                        SpotifyAudioVisualizer(
                            isPlayingOrRecording = isPlaying,
                            currentAmplitude = if (isPlaying) (12000..28000).random() else 0,
                            maxHeight = 22.dp,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Speed Pill Button (1.0x, 1.25x, 1.5x, 2.0x)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SoftOrangeLight)
                        .clickable { cycleSpeed() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${selectedSpeed}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VibrantOrange
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cancel),
                        contentDescription = "Kaydı Sil",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Spotify Wavy Squiggly Scrubber
            val activePos = if (isUserSeeking) seekPositionMs else currentPositionMs.toFloat()
            val safeDuration = durationMs.toFloat().coerceAtLeast(1000f)

            SquigglySlider(
                value = activePos.coerceIn(0f, safeDuration),
                onValueChange = {
                    isUserSeeking = true
                    seekPositionMs = it
                },
                onValueChangeFinished = {
                    audioRecorder.seekTo(seekPositionMs.toInt())
                    currentPositionMs = seekPositionMs.toInt()
                    isUserSeeking = false
                },
                valueRange = 0f..safeDuration,
                isPlaying = isPlaying,
                activeColor = VibrantOrange,
                inactiveColor = BorderLight,
                thumbColor = VibrantOrange,
                modifier = Modifier.fillMaxWidth()
            )

            // Current Time & Total Duration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isUserSeeking) seekPositionMs.toInt() else currentPositionMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(durationMs),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
