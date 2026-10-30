package com.notes.purenoteit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.notes.purenoteit.data.db.NoteDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmActionEntryPoint {
        fun getNoteDao(): NoteDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1)
        val action = intent.action

        // 1. Immediately stop alarm sound
        AlarmSoundPlayer.stop()

        if (noteId != -1) {
            ReminderNotificationHelper.cancelNotification(context, noteId)
        }

        when (action) {
            ReminderNotificationHelper.ACTION_STOP_ALARM -> {
                Toast.makeText(context, "Alarm durduruldu", Toast.LENGTH_SHORT).show()
            }

            ReminderNotificationHelper.ACTION_SNOOZE_ALARM -> {
                val noteTitle = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE) ?: "Başlıksız Not"
                val noteDesc = intent.getStringExtra(ReminderManager.EXTRA_NOTE_DESC) ?: ""
                val reminderType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TYPE) ?: "ALARM"
                val soundType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_SOUND) ?: "default"

                val newReminderTime = System.currentTimeMillis() + (5 * 60 * 1000L)
                ReminderManager.scheduleReminder(
                    context = context,
                    noteId = noteId,
                    noteTitle = noteTitle,
                    noteDesc = noteDesc,
                    reminderTimeMs = newReminderTime,
                    reminderType = reminderType,
                    soundType = soundType
                )

                // Update database
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        AlarmActionEntryPoint::class.java
                    )
                    val noteDao = entryPoint.getNoteDao()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            noteDao.updateReminderTime(noteId, newReminderTime).blockingAwait()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                Toast.makeText(context, "Alarm 5 dakika ertelendi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
