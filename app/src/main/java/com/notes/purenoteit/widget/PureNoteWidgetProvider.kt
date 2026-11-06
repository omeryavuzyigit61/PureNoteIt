package com.notes.purenoteit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notes.purenoteit.R
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.NoteRepository
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.view.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PureNoteWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun getNoteRepository(): NoteRepository
        fun getCategoryRepository(): CategoryRepository
    }

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

            // 5. Asynchronously fetch latest public (non-locked, non-trashed) note via Hilt Repository
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                val noteRepository = entryPoint.getNoteRepository()
                val categoryRepository = entryPoint.getCategoryRepository()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val notes = noteRepository.getAllNotesSuspend()
                        val categories = categoryRepository.getAllCategoriesSuspend()
                        val lockedCategoryIds = categories.filter { it.isPassword == true }.map { it.id }.toSet()
                        val publicNotes = notes.filter { note ->
                            note.status != "TRASH" &&
                                note.isPassword != true &&
                                !note.isEncrypted &&
                                !SecurityUtil.isEncryptedPayload(note.description) &&
                                (note.categoryId == null || note.categoryId !in lockedCategoryIds)
                        }
                        val latestPublicNote = publicNotes.maxByOrNull { it.date }

                        val data = if (latestPublicNote != null) {
                            val title = if (!latestPublicNote.title.isNullOrBlank()) latestPublicNote.title!! else "Başlıksız Not"
                            val snippet = try {
                                val blocks = com.notes.purenoteit.model.BlockParser.parse(latestPublicNote.description)
                                val summary = com.notes.purenoteit.model.BlockParser.toPlainTextSummary(blocks, maxLines = 2)
                                if (summary.isNotBlank()) summary else "Açmak için dokunun"
                            } catch (e: Exception) {
                                "Açmak için dokunun"
                            }

                            val totalTasks = latestPublicNote.totalTasks
                            val completedTasks = latestPublicNote.completedTasks
                            val taskSuffix = if (totalTasks > 0) " • ✓ $completedTasks/$totalTasks" else ""
                            val iconPrefix = if (!latestPublicNote.icon.isNullOrBlank()) "${latestPublicNote.icon} " else "📝 "

                            WidgetDisplayData(
                                headerSubtext = "${publicNotes.size} Not • Son: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(latestPublicNote.date))}",
                                noteTitle = "$iconPrefix$title$taskSuffix",
                                noteSnippet = snippet,
                                noteId = latestPublicNote.id
                            )
                        } else {
                            WidgetDisplayData(
                                headerSubtext = "Aklındakileri anında yakala...",
                                noteTitle = "Henüz Not Eklenmedi",
                                noteSnippet = "Dokun ve ilk notunu oluştur ✨",
                                noteId = null
                            )
                        }

                        withContext(Dispatchers.Main) {
                            views.setTextViewText(R.id.widget_recent_note_text, data.headerSubtext)
                            views.setTextViewText(R.id.widget_note_card_title, data.noteTitle)
                            views.setTextViewText(R.id.widget_note_card_snippet, data.noteSnippet)

                            val previewIntent = Intent(context, MainActivity::class.java).apply {
                                if (data.noteId != null) {
                                    putExtra("OPEN_NOTE_ID", data.noteId)
                                } else {
                                    action = ACTION_NEW_NOTE
                                    putExtra(EXTRA_ACTION, "NEW_NOTE")
                                }
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            val previewPending = PendingIntent.getActivity(
                                context,
                                4,
                                previewIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(R.id.widget_note_preview_card, previewPending)

                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    } catch (e: Exception) {
                        views.setTextViewText(R.id.widget_recent_note_text, "Aklındakileri anında yakala...")
                        views.setTextViewText(R.id.widget_note_card_title, "PureNoteIt")
                        views.setTextViewText(R.id.widget_note_card_snippet, "Hızlı not almak için dokunun ✨")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

private data class WidgetDisplayData(
    val headerSubtext: String,
    val noteTitle: String,
    val noteSnippet: String,
    val noteId: Int?
)
