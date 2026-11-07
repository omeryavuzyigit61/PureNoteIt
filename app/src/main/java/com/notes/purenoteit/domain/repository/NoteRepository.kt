package com.notes.purenoteit.domain.repository

import com.notes.purenoteit.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotesFlow(): Flow<List<Note>>
    suspend fun getAllNotes(): List<Note>
    suspend fun getNoteById(id: Int): Note?
    suspend fun upsertNote(note: Note)
    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)
    suspend fun deleteNoteById(id: Int)
    fun searchNotesFlow(query: String): Flow<List<Note>>
    fun getNotesByCategoryFlow(categoryName: String): Flow<List<Note>>
    suspend fun getNotesBetweenDates(startDate: Long, endDate: Long): List<Note>
    suspend fun getActiveReminders(now: Long): List<Note>
    suspend fun updatePinStatus(id: Int, isPinned: Boolean)
    suspend fun updateReminderDone(id: Int, isDone: Boolean)
    suspend fun updateReminderTime(id: Int, reminderTime: Long)
}
