package com.notes.purenoteit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.NoteDatabase
import com.notes.purenoteit.view.MainActivity
import io.reactivex.schedulers.Schedulers

class PureNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.notes.purenoteit.ACTION_NEW_NOTE"
        const val ACTION_OCR_SCAN = "com.notes.purenoteit.ACTION_OCR_SCAN"
        const val ACTION_VOICE_NOTE = "com.notes.purenoteit.ACTION_VOICE_NOTE"
        const val EXTRA_ACTION = "WIDGET_ACTION"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PureNoteWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_pure_note)

            // 1. Header & Root click -> Open App
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent)

            // 2. New Note button
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_NOTE
                putExtra(EXTRA_ACTION, "NEW_NOTE")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePending = PendingIntent.getActivity(
                context,
                1,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_new_note, newNotePending)
            views.setOnClickPendingIntent(R.id.widget_btn_header_add, newNotePending)

            // 3. OCR Scan button
            val ocrIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OCR_SCAN
                putExtra(EXTRA_ACTION, "OCR_SCAN")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val ocrPending = PendingIntent.getActivity(
                context,
                2,
                ocrIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_ocr, ocrPending)

            // 4. Voice Dictation button
            val voiceIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_VOICE_NOTE
                putExtra(EXTRA_ACTION, "VOICE_NOTE")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val voicePending = PendingIntent.getActivity(
                context,
                3,
                voiceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_voice, voicePending)

            // 5. Asynchronously fetch latest note to show preview
            try {
                val db = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "Note"
                ).build()

                db.getNoteDao().getAllNotes()
                    .subscribeOn(Schedulers.io())
                    .subscribe({ notes ->
                        val latestNote = notes.maxByOrNull { it.date }
                        val text = if (latestNote != null && !latestNote.title.isNullOrBlank()) {
                            "Son not: \"${latestNote.title}\""
                        } else {
                            "Aklındakileri anında yakala..."
                        }
                        views.setTextViewText(R.id.widget_recent_note_text, text)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }, {
                        views.setTextViewText(R.id.widget_recent_note_text, "Aklındakileri anında yakala...")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    })
            } catch (e: Exception) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
