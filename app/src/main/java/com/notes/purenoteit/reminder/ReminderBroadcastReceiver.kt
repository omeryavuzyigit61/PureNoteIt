package com.notes.purenoteit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.notes.purenoteit.data.db.NoteDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderReceiverEntryPoint {
        fun getNoteDao(): NoteDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1)
        val noteTitle = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE) ?: "Başlıksız Not"
        val noteDesc = intent.getStringExtra(ReminderManager.EXTRA_NOTE_DESC) ?: ""
        val reminderType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TYPE) ?: "ALARM"
        val soundType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_SOUND) ?: "default"

        Log.d("ReminderReceiver", "Reminder received for note $noteId ($reminderType)")

        if (noteId == -1) return

        // 1. If Alarm mode, start continuous alarm sound loop
        if (reminderType == "ALARM") {
            AlarmSoundPlayer.play(context.applicationContext, soundType)
        }

        // 2. Post high-priority notification
        ReminderNotificationHelper.showReminderNotification(
            context = context,
            noteId = noteId,
            noteTitle = noteTitle,
            noteDesc = noteDesc,
            reminderType = reminderType,
            soundType = soundType
        )

        // 3. Mark reminder as done in Room database
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ReminderReceiverEntryPoint::class.java
            )
            val noteDao = entryPoint.getNoteDao()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    noteDao.updateReminderDone(noteId, true).blockingAwait()
                } catch (e: Exception) {
                    Log.e("ReminderReceiver", "Error marking reminder done", e)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Error accessing Hilt EntryPoint", e)
        }
    }
}
