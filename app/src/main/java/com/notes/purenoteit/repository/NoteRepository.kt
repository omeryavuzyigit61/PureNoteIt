package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(private val noteDao: NoteDao) {
    // --- Modern Coroutines & Flow API (Main-safe, Zero SQL schema changes) ---
    suspend fun upsertSuspend(item: NoteData) = noteDao.upsertNote(item)
    suspend fun updateSuspend(item: NoteData) = noteDao.updateNote(item)
    suspend fun insertNoteSuspend(item: NoteData): Long = noteDao.insertNoteSuspend(item)
    suspend fun deleteSuspend(item: NoteData) = noteDao.deleteNote(item)
    suspend fun deleteNoteByIdSuspend(id: Int) = noteDao.deleteNoteByIdSuspend(id)
    fun getAllNotesFlow(): Flow<List<NoteData>> = noteDao.getAllNotesFlow()
    suspend fun getAllNotesSuspend(): List<NoteData> = noteDao.getAllNotesSuspend()
    suspend fun getNoteByIdSuspend(id: Int): NoteData? = noteDao.getNoteByIdSuspend(id)
    fun searchNoteFlow(query: String): Flow<List<NoteData>> = noteDao.searchNoteFlow(query)
    fun getNotesByCategoryFlow(categoryName: String): Flow<List<NoteData>> = noteDao.getNotesByCategoryFlow(categoryName)
    suspend fun getNotesBetweenDatesSuspend(startDate: Long, endDate: Long): List<NoteData> =
        noteDao.getNotesBetweenDatesSuspend(startDate, endDate)
    suspend fun getActiveRemindersSuspend(now: Long): List<NoteData> = noteDao.getActiveRemindersSuspend(now)
    suspend fun updatePinStatusSuspend(id: Int, isPinned: Boolean) = noteDao.updatePinStatusSuspend(id, isPinned)
    suspend fun updateReminderDoneSuspend(id: Int, isDone: Boolean) = noteDao.updateReminderDoneSuspend(id, isDone)
    suspend fun updateReminderTimeSuspend(id: Int, reminderTime: Long) = noteDao.updateReminderTimeSuspend(id, reminderTime)
}
