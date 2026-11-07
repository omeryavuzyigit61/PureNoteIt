package com.notes.purenoteit.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notes.purenoteit.di.ApplicationScope
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.util.SecurityUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun getNoteRepository(): NoteRepository
        fun getCategoryRepository(): CategoryRepository
        fun getAppClock(): AppClock
        @ApplicationScope fun getApplicationScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        try {
            val appContext = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                BootReceiverEntryPoint::class.java
            )
            val noteRepository = entryPoint.getNoteRepository()
            val categoryRepository = entryPoint.getCategoryRepository()
            val clock = entryPoint.getAppClock()
            val appScope = entryPoint.getApplicationScope()

            appScope.launch {
                try {
                    val now = clock.nowMillis()
                    val lockedCategoryIds = categoryRepository.getAllCategories()
                        .filter { it.isPassword == true }
                        .map { it.id }
                        .toSet()
                    val activeNotes = noteRepository.getActiveReminders(now)
                    activeNotes.forEach { note ->
                        val remTime = note.reminderTime
                        if (remTime != null && remTime > now && note.status != "TRASH") {
                            val isLocked = note.isPassword == true ||
                                note.isEncrypted ||
                                SecurityUtil.isEncryptedPayload(note.description) ||
                                (note.categoryId in lockedCategoryIds)
                            ReminderManager.scheduleReminder(
                                context = appContext,
                                noteId = note.id,
                                noteTitle = if (isLocked) "🔒 Korumalı Not" else (note.title ?: "Başlıksız Not"),
                                noteDesc = if (isLocked) "ENC:" else (note.description ?: ""),
                                reminderTimeMs = remTime,
                                reminderType = note.reminderType ?: "ALARM",
                                soundType = note.reminderSound ?: "default"
                            )
                        }
                    }
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
