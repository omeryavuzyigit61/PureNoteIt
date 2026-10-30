package com.notes.purenoteit.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ReminderManager {

    private const val TAG = "ReminderManager"

    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_DESC = "extra_note_desc"
    const val EXTRA_REMINDER_TYPE = "extra_reminder_type" // "ALARM" or "NOTIFICATION"
    const val EXTRA_REMINDER_SOUND = "extra_reminder_sound" // "default", "digital", "chime", "gentle"

    fun scheduleReminder(
        context: Context,
        noteId: Int,
        noteTitle: String,
        noteDesc: String,
        reminderTimeMs: Long,
        reminderType: String = "ALARM",
        soundType: String? = "default"
    ) {
        if (reminderTimeMs <= System.currentTimeMillis()) {
            Log.w(TAG, "Cannot schedule reminder in the past")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, noteTitle)
            putExtra(EXTRA_NOTE_DESC, noteDesc)
            putExtra(EXTRA_REMINDER_TYPE, reminderType)
            putExtra(EXTRA_REMINDER_SOUND, soundType ?: "default")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId,
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMs,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderTimeMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled reminder for note $noteId at $reminderTimeMs")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission denied, fallback to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminderTimeMs,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, noteId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, noteId, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled reminder for note $noteId")
        }
    }

    fun snoozeReminder(
        context: Context,
        noteId: Int,
        noteTitle: String,
        noteDesc: String,
        reminderType: String,
        soundType: String?,
        snoozeMinutes: Int = 5
    ) {
        val snoozeTimeMs = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        scheduleReminder(
            context = context,
            noteId = noteId,
            noteTitle = noteTitle,
            noteDesc = noteDesc,
            reminderTimeMs = snoozeTimeMs,
            reminderType = reminderType,
            soundType = soundType
        )
    }
}
