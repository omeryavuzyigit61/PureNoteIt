package com.notes.purenoteit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.notes.purenoteit.di.ApplicationScope
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlarmActionEntryPoint {
        fun getNoteRepository(): NoteRepository
        fun getAppClock(): AppClock
        @ApplicationScope fun getApplicationScope(): CoroutineScope
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

                val pendingResult = goAsync()
                try {
                    val appContext = context.applicationContext
                    val entryPoint = EntryPointAccessors.fromApplication(
                        appContext,
                        AlarmActionEntryPoint::class.java
                    )
                    val noteRepository = entryPoint.getNoteRepository()
                    val clock = entryPoint.getAppClock()
                    val appScope = entryPoint.getApplicationScope()
                    val newReminderTime = clock.nowMillis() + (5 * 60 * 1000L)

                    ReminderManager.scheduleReminder(
                        context = appContext,
                        noteId = noteId,
                        noteTitle = noteTitle,
                        noteDesc = noteDesc,
                        reminderTimeMs = newReminderTime,
                        reminderType = reminderType,
                        soundType = soundType
                    )

                    appScope.launch {
                        try {
                            noteRepository.updateReminderTime(noteId, newReminderTime)
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

                Toast.makeText(context, "Alarm 5 dakika ertelendi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
