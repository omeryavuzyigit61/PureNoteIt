package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteRepository {

    override fun getAllNotesFlow(): Flow<List<Note>> {
        return noteDao.getAllNotesFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAllNotes(): List<Note> = withContext(ioDispatcher) {
        noteDao.getAllNotesSuspend().map { it.toDomain() }
    }

    override suspend fun getNoteById(id: Int): Note? = withContext(ioDispatcher) {
        noteDao.getNoteByIdSuspend(id)?.toDomain()
    }

    override suspend fun upsertNote(note: Note) = withContext(ioDispatcher) {
        noteDao.upsertNote(note.toEntity())
    }

    override suspend fun updateNote(note: Note) = withContext(ioDispatcher) {
        noteDao.updateNote(note.toEntity())
    }

    override suspend fun deleteNote(note: Note) = withContext(ioDispatcher) {
        noteDao.deleteNote(note.toEntity())
    }

    override suspend fun deleteNoteById(id: Int) = withContext(ioDispatcher) {
        noteDao.deleteNoteByIdSuspend(id)
    }

    override fun searchNotesFlow(query: String): Flow<List<Note>> {
        return noteDao.searchNoteFlow(query)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override fun getNotesByCategoryFlow(categoryName: String): Flow<List<Note>> {
        return noteDao.getNotesByCategoryFlow(categoryName)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getNotesBetweenDates(startDate: Long, endDate: Long): List<Note> = withContext(ioDispatcher) {
        noteDao.getNotesBetweenDatesSuspend(startDate, endDate).map { it.toDomain() }
    }

    override suspend fun updatePinStatus(id: Int, isPinned: Boolean) = withContext(ioDispatcher) {
        noteDao.updatePinStatusSuspend(id, isPinned)
    }

    override suspend fun updateReminderDone(id: Int, isDone: Boolean) = withContext(ioDispatcher) {
        noteDao.updateReminderDoneSuspend(id, isDone)
    }

    override suspend fun updateReminderTime(id: Int, reminderTime: Long) = withContext(ioDispatcher) {
        noteDao.updateReminderTimeSuspend(id, reminderTime)
    }
}
