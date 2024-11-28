package com.notes.purenoteit.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import com.notes.purenoteit.R
import com.notes.purenoteit.databinding.CustomSaveDialogBinding
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentRecordingFilePath: String? = null

    // Ses kaydını başlat
    fun startRecording(): String? {
        val fileName = "recorded_audio_${System.currentTimeMillis()}.mp3"
        val filePath =
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.absolutePath + "/" + fileName

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
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

    // Kaydı durdur
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

    // Ses dosyasını çal
    fun playRecording(filePath: String, onCompletion: (() -> Unit)? = null) {
        try {
            mediaPlayer?.release() // Eski MediaPlayer varsa serbest bırak
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    onCompletion?.invoke()
                    release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Ses dosyasını durdur
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
        }
    }

    fun deleteRecording(filePath: String): Boolean {
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
            Log.e("AudioRecorder", "Dosya silme sırasında hata oluştu: ${e.message}")
            false
        }
    }

    fun cancelRecording(dialogBinding: CustomSaveDialogBinding) {
        if (mediaRecorder == null) {
            context.showToastShort("Şu anda aktif bir kayıt yok!")
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            // Geçici ses kaydı dosyasını sil
            currentRecordingFilePath?.let {
                val file = File(it)
                if (file.exists()) {
                    file.delete()
                }
            }

            currentRecordingFilePath = null // Geçerli dosya yolunu temizle
            context.showToastShort("Ses kaydı iptal edildi")
            dialogBinding.waveAnimationImageView.setImageResource(R.drawable.sound_wave_unscreen)
            dialogBinding.startStopSoundButton.text = context.getString(R.string.start_sound)
        } catch (e: Exception) {
            e.printStackTrace()
            context.showToastShort("Ses kaydı iptal edilemedi: ${e.message}")
        }
    }
}
