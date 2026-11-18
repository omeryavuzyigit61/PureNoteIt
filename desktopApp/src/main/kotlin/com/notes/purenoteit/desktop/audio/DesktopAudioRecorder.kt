package com.notes.purenoteit.desktop.audio

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.abs

data class DesktopAudioUiState(
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordingAmplitude: Float = 0f,
    val targetBlockId: String? = null,
    val playingMediaPath: String? = null
)

/**
 * Pure JVM in-memory microphone capture and WAV playback helper.
 * Captures PCM frames directly in RAM (no plaintext temp audio file ever touches disk)
 * and encodes a standard RIFF/WAVE payload compatible with Android `MediaPlayer` and JVM `Clip`.
 */
class DesktopAudioRecorder {

    private val _state = MutableStateFlow(DesktopAudioUiState())
    val state: StateFlow<DesktopAudioUiState> = _state.asStateFlow()

    private val lock = Any()
    private var targetLine: TargetDataLine? = null
    private var captureThread: Thread? = null
    private var activeFormat: AudioFormat = AudioFormat(16_000f, 16, 1, true, false)
    private var pcmBuffer: ByteArrayOutputStream? = null

    @Volatile
    private var shouldCapture: Boolean = false

    private var activeClip: Clip? = null

    fun startRecording(targetBlockId: String? = null): AppResult<Unit, DataError> {
        synchronized(lock) {
            if (_state.value.isRecording) return AppResult.Success(Unit)
            stopPlaybackLocked()

            val candidateFormats = listOf(
                AudioFormat(16_000f, 16, 1, true, false),
                AudioFormat(44_100f, 16, 1, true, false),
                AudioFormat(48_000f, 16, 1, true, false)
            )
            var openedLine: TargetDataLine? = null
            var chosenFormat: AudioFormat? = null
            for (fmt in candidateFormats) {
                val info = DataLine.Info(TargetDataLine::class.java, fmt)
                if (!AudioSystem.isLineSupported(info)) continue
                try {
                    val line = AudioSystem.getLine(info) as? TargetDataLine ?: continue
                    line.open(fmt)
                    line.start()
                    openedLine = line
                    chosenFormat = fmt
                    break
                } catch (_: Exception) {
                }
            }
            if (openedLine == null || chosenFormat == null) {
                return AppResult.Failure(DataError.Local.UNKNOWN)
            }

            val baos = ByteArrayOutputStream(64 * 1024)
            targetLine = openedLine
            activeFormat = chosenFormat
            pcmBuffer = baos
            shouldCapture = true
            val startedAtMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    isRecording = true,
                    recordingDurationMs = 0L,
                    recordingAmplitude = 0f,
                    targetBlockId = targetBlockId
                )
            }

            captureThread = thread(start = true, isDaemon = true, name = "PureNoteIt-AudioCapture") {
                val readChunk = ByteArray(2048)
                try {
                    while (shouldCapture) {
                        val read = openedLine.read(readChunk, 0, readChunk.size)
                        if (read > 0) {
                            synchronized(lock) {
                                pcmBuffer?.write(readChunk, 0, read)
                            }
                            val amp = computePeakNormalized(readChunk, read)
                            val elapsed = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
                            _state.update {
                                it.copy(
                                    recordingDurationMs = elapsed,
                                    recordingAmplitude = amp
                                )
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return AppResult.Success(Unit)
        }
    }

    fun stopRecordingAndBuildWav(): AppResult<ByteArray, DataError> {
        val rawPcm: ByteArray
        val formatUsed: AudioFormat
        val worker: Thread?
        synchronized(lock) {
            if (!_state.value.isRecording) {
                return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
            }
            shouldCapture = false
            worker = captureThread
            runCatching { targetLine?.stop() }
            runCatching { targetLine?.close() }
            targetLine = null
            captureThread = null
        }

        runCatching { worker?.join(500L) }

        synchronized(lock) {
            val bytes = pcmBuffer?.toByteArray() ?: ByteArray(0)
            pcmBuffer?.reset()
            pcmBuffer = null
            formatUsed = activeFormat
            rawPcm = bytes
            _state.update {
                it.copy(
                    isRecording = false,
                    recordingDurationMs = 0L,
                    recordingAmplitude = 0f,
                    targetBlockId = null
                )
            }
        }

        if (rawPcm.isEmpty()) {
            return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        }
        return AppResult.Success(encodePcmToWav(rawPcm, formatUsed))
    }

    fun cancelRecording() {
        val worker: Thread?
        synchronized(lock) {
            shouldCapture = false
            worker = captureThread
            runCatching { targetLine?.stop() }
            runCatching { targetLine?.close() }
            targetLine = null
            captureThread = null
            pcmBuffer?.reset()
            pcmBuffer = null
            _state.update {
                it.copy(
                    isRecording = false,
                    recordingDurationMs = 0L,
                    recordingAmplitude = 0f,
                    targetBlockId = null
                )
            }
        }
        runCatching { worker?.join(300L) }
    }

    fun playOrToggleWav(mediaPath: String, audioBytes: ByteArray): Boolean {
        synchronized(lock) {
            val currentlyPlaying = _state.value.playingMediaPath
            if (currentlyPlaying == mediaPath && activeClip != null) {
                stopPlaybackLocked()
                return true
            }
            stopPlaybackLocked()
            return try {
                val baseStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioBytes))
                val baseFormat = baseStream.format
                val targetPcm = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels.coerceAtLeast(1),
                    baseFormat.channels.coerceAtLeast(1) * 2,
                    baseFormat.sampleRate,
                    false
                )
                val decodedStream = if (
                    baseFormat.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                    baseFormat.sampleSizeInBits == 16 &&
                    !baseFormat.isBigEndian
                ) {
                    baseStream
                } else {
                    AudioSystem.getAudioInputStream(targetPcm, baseStream)
                }

                val clip = AudioSystem.getClip()
                clip.open(decodedStream)
                clip.addLineListener { ev ->
                    if (ev.type == LineEvent.Type.STOP) {
                        synchronized(lock) {
                            if (activeClip === clip) {
                                runCatching { clip.close() }
                                activeClip = null
                                _state.update { it.copy(playingMediaPath = null) }
                            }
                        }
                    }
                }
                activeClip = clip
                _state.update { it.copy(playingMediaPath = mediaPath) }
                clip.start()
                true
            } catch (_: Exception) {
                _state.update { it.copy(playingMediaPath = null) }
                false
            }
        }
    }

    fun stopPlayback() {
        synchronized(lock) {
            stopPlaybackLocked()
        }
    }

    private fun stopPlaybackLocked() {
        activeClip?.let { clip ->
            runCatching { clip.stop() }
            runCatching { clip.close() }
        }
        activeClip = null
        _state.update { it.copy(playingMediaPath = null) }
    }

    fun close() {
        cancelRecording()
        stopPlayback()
    }

    internal companion object {
        fun encodePcmToWav(pcmData: ByteArray, format: AudioFormat): ByteArray {
            val sampleRate = format.sampleRate.toInt().coerceAtLeast(8000)
            val channels = format.channels.coerceAtLeast(1)
            val bitsPerSample = format.sampleSizeInBits.coerceAtLeast(8)
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val totalDataLen = pcmData.size + 36

            val out = ByteArray(44 + pcmData.size)
            val header = ByteBuffer.wrap(out, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            header.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
            header.putInt(totalDataLen)
            header.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
            header.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
            header.putInt(16) // PCM subchunk size
            header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
            header.putInt(pcmData.size)
            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }

        private fun computePeakNormalized(buf: ByteArray, length: Int): Float {
            if (length < 2) return 0f
            var peak = 0
            var i = 0
            while (i + 1 < length) {
                val lo = buf[i].toInt() and 0xFF
                val hi = buf[i + 1].toInt()
                val sample = (hi shl 8) or lo
                peak = maxOf(peak, abs(sample))
                i += 2
            }
            return (peak / 32768f).coerceIn(0f, 1f)
        }
    }
}
