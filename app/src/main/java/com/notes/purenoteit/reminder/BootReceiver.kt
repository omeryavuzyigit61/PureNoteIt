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

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun getNoteDao(): NoteDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, rescheduling active reminders...")

            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootReceiverEntryPoint::class.java
                )
                val noteDao = entryPoint.getNoteDao()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val activeNotes = noteDao.getActiveReminders(System.currentTimeMillis()).blockingGet()
                        activeNotes.forEach { note ->
                            val remTime = note.reminderTime
                            if (remTime != null && remTime > System.currentTimeMillis()) {
                                ReminderManager.scheduleReminder(
                                    context = context,
                                    noteId = note.id,
                                    noteTitle = note.title ?: "Başlıksız Not",
                                    noteDesc = note.description ?: "",
                                    reminderTimeMs = remTime,
                                    reminderType = note.reminderType ?: "ALARM",
                                    soundType = note.reminderSound ?: "default"
                                )
                            }
                        }
                        Log.d("BootReceiver", "Rescheduled ${activeNotes.size} reminders.")
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error rescheduling reminders on boot", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error accessing EntryPoint on boot", e)
            }
        }
    }
}
