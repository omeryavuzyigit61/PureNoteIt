package com.notes.purenoteit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notes.purenoteit.di.ApplicationScope
import com.notes.purenoteit.domain.repository.NoteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderReceiverEntryPoint {
        fun getNoteRepository(): NoteRepository
        @ApplicationScope fun getApplicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getIntExtra(ReminderManager.EXTRA_NOTE_ID, -1)
        val noteTitle = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE) ?: "Başlıksız Not"
        val noteDesc = intent.getStringExtra(ReminderManager.EXTRA_NOTE_DESC) ?: ""
        val reminderType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_TYPE) ?: "ALARM"
        val soundType = intent.getStringExtra(ReminderManager.EXTRA_REMINDER_SOUND) ?: "default"

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

        // 3. Mark reminder as done in Room database via domain NoteRepository
        val pendingResult = goAsync()
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ReminderReceiverEntryPoint::class.java
            )
            val noteRepository = entryPoint.getNoteRepository()
            val appScope = entryPoint.getApplicationScope()
            appScope.launch {
                try {
                    noteRepository.updateReminderDone(noteId, true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: Exception) {
            pendingResult.finish()
        }
    }
}
