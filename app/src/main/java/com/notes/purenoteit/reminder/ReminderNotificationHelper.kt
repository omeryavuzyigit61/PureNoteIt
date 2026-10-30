package com.notes.purenoteit.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.notes.purenoteit.R
import com.notes.purenoteit.view.MainActivity

object ReminderNotificationHelper {

    const val CHANNEL_ALARM_ID = "purenoteit_alarm_channel"
    const val CHANNEL_REMINDER_ID = "purenoteit_reminder_channel"

    const val ACTION_STOP_ALARM = "com.notes.purenoteit.ACTION_STOP_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.notes.purenoteit.ACTION_SNOOZE_ALARM"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Alarm Channel (High Priority, Sound, Vibration, Heads-up)
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "PureNoteIt Alarmlar & Zil",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zamanlanmış yüksek öncelikli PureNoteIt sesli not alarmları"
                enableLights(true)
                lightColor = Color.rgb(255, 122, 0) // Vibrant Orange
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(alarmSoundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // 2. Standard Reminder Channel (Default Priority)
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "PureNoteIt Standart Hatırlatıcılar",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standart PureNoteIt hatırlatma bildirimleri"
                enableLights(true)
                lightColor = Color.rgb(255, 122, 0)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    fun showReminderNotification(
        context: Context,
        noteId: Int,
        noteTitle: String,
        noteDesc: String,
        reminderType: String,
        soundType: String?
    ) {
        createNotificationChannels(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val isAlarm = reminderType == "ALARM"
        val channelId = if (isAlarm) CHANNEL_ALARM_ID else CHANNEL_REMINDER_ID

        // Intent to open note in MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_NOTE_ID", noteId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            noteId,
            openIntent,
            flags
        )

        // Delete intent (user swiped away notification -> stop alarm sound)
        val stopIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra(ReminderManager.EXTRA_NOTE_ID, noteId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            noteId * 10 + 1,
            stopIntent,
            flags
        )

        // Snooze intent (5 min delay)
        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(ReminderManager.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderManager.EXTRA_NOTE_TITLE, noteTitle)
            putExtra(ReminderManager.EXTRA_NOTE_DESC, noteDesc)
            putExtra(ReminderManager.EXTRA_REMINDER_TYPE, reminderType)
            putExtra(ReminderManager.EXTRA_REMINDER_SOUND, soundType)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            noteId * 10 + 2,
            snoozeIntent,
            flags
        )

        val cleanDesc = if (noteDesc.isBlank()) "Zamanlanmış PureNoteIt hatırlatıcısı." else noteDesc.take(200)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.note)
            .setContentTitle(if (isAlarm) "⏰ ALARM: $noteTitle" else "🔔 Hatırlatıcı: $noteTitle")
            .setContentText(cleanDesc)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("⏰ $noteTitle")
                    .bigText(cleanDesc)
                    .setSummaryText("PureNoteIt • Hatırlatıcı")
            )
            .setColor(Color.rgb(255, 122, 0)) // #FF7A00 Vibrant Orange
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setDeleteIntent(stopPendingIntent)
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)

        if (isAlarm) {
            builder.setCategory(NotificationCompat.CATEGORY_ALARM)
            builder.setFullScreenIntent(contentPendingIntent, true)
            builder.addAction(R.drawable.cancel, "⏹️ Durdur", stopPendingIntent)
            builder.addAction(R.drawable.calendar, "⏱️ 5 Dk Ertele", snoozePendingIntent)
            builder.setOngoing(true) // Keeps notification until user responds
        } else {
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        notificationManager.notify(noteId, builder.build())
    }

    fun cancelNotification(context: Context, noteId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(noteId)
    }
}
