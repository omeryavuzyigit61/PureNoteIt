package com.notes.purenoteit.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFilePath: String? = null
    private var currentPlayingFilePath: String? = null

    private var currentSpeed: Float = 1.0f

    fun startRecording(): String? {
        val fileName = "recorded_audio_${System.currentTimeMillis()}.m4a"
        val recordingsDir = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        val file = File(recordingsDir, fileName)
        val filePath = file.absolutePath

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder = recorder.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                } catch (e: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Studio HD Quality: 192 kbps bitrate, 48 kHz ultra HD sampling rate
                setAudioEncodingBitRate(192000)
                setAudioSamplingRate(48000)
                setAudioChannels(1)
                setOutputFile(filePath)
                prepare()
                start()
            }
            currentRecordingFilePath = filePath
        } catch (e: Exception) {
            e.printStackTrace()
            currentRecordingFilePath = null
        }

        return currentRecordingFilePath
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun stopRecording(): String? {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }
        return currentRecordingFilePath
    }

    fun cancelRecording(dialogBinding: Any? = null) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        currentRecordingFilePath?.let {
            val file = File(it)
            if (file.exists()) {
                file.delete()
            }
        }
        currentRecordingFilePath = null
    }

    // Legacy method for AddFragment
    fun playRecording(filePath: String, onCompletion: (() -> Unit)? = null) {
        playOrPause(
            filePath = filePath,
            onStatusChange = { _, _, _ -> },
            onCompletion = { onCompletion?.invoke() }
        )
    }

    // Playback functions
    fun playOrPause(
        filePath: String,
        onStatusChange: (isPlaying: Boolean, currentPositionMs: Int, durationMs: Int) -> Unit,
        onCompletion: () -> Unit
    ) {
        if (mediaPlayer != null && currentPlayingFilePath == filePath) {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                onStatusChange(false, mediaPlayer?.currentPosition ?: 0, mediaPlayer?.duration ?: 0)
            } else {
                mediaPlayer?.start()
                applySpeed(currentSpeed)
                onStatusChange(true, mediaPlayer?.currentPosition ?: 0, mediaPlayer?.duration ?: 0)
            }
            return
        }

        // New audio playback session
        stopPlaying()
        try {
            currentPlayingFilePath = filePath
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                applySpeed(currentSpeed)
                onStatusChange(true, 0, duration)
                setOnCompletionListener {
                    onStatusChange(false, duration, duration)
                    onCompletion()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentPlayingFilePath = null
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        applySpeed(speed)
    }

    fun getSpeed(): Float = currentSpeed

    private fun applySpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { player ->
                    val isPlaying = player.isPlaying
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                    if (!isPlaying) {
                        player.pause()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun stopPlaying() {
        try {
            mediaPlayer?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            currentPlayingFilePath = null
        }
    }

    fun deleteRecording(filePath: String): Boolean {
        if (currentPlayingFilePath == filePath) {
            stopPlaying()
        }
        return try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                Log.i("AudioRecorder", "Dosya başarıyla silindi: $filePath")
                true
            } else {
                Log.e("AudioRecorder", "Dosya silinemedi: $filePath")
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAudioDuration(filePath: String): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun release() {
        stopRecording()
        stopPlaying()
    }
}
