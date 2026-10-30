package com.notes.purenoteit.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

object AlarmSoundPlayer {
    private const val TAG = "AlarmSoundPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    fun play(context: Context, soundType: String?) {
        stop()

        try {
            val alarmUri: Uri = when (soundType) {
                "chime" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "gentle" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            timeoutRunnable = Runnable {
                stop()
            }
            handler.postDelayed(timeoutRunnable!!, 60_000L)
            Log.d(TAG, "Started alarm ring loop")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound, fallback to ToneGenerator", e)
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
            } catch (te: Exception) {
                Log.e(TAG, "Error with ToneGenerator fallback", te)
            }
        }
    }

    fun stop() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaPlayer", e)
        }

        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
