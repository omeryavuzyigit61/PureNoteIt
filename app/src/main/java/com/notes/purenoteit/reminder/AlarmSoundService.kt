package com.notes.purenoteit.reminder

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AlarmSoundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val soundType = intent?.getStringExtra(ReminderManager.EXTRA_REMINDER_SOUND)
        AlarmSoundPlayer.play(this, soundType)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundPlayer.stop()
    }
}
