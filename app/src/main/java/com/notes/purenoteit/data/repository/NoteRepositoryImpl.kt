package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NoteRepositoryImpl private constructor(
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao?,
    private val noteLinkDao: NoteLinkDao?,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: AppClock,
    private val isConstructed: Boolean
) : NoteRepository {

    @Inject
    constructor(
        noteDao: NoteDao,
        categoryDao: CategoryDao,
        noteLinkDao: NoteLinkDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        clock: AppClock
    ) : this(noteDao, categoryDao, noteLinkDao, ioDispatcher, clock, true)

    internal constructor(
        noteDao: NoteDao,
        ioDispatcher: CoroutineDispatcher,
        clock: AppClock = AppClock { System.currentTimeMillis() }
    ) : this(noteDao, null, null, ioDispatcher, clock, false)

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
        noteDao.upsertNote(prepareEntityForWrite(note))
    }

    override suspend fun insertNote(note: Note): Long = withContext(ioDispatcher) {
        noteDao.insertNoteSuspend(prepareEntityForWrite(note))
    }

    override suspend fun updateNote(note: Note) = withContext(ioDispatcher) {
        noteDao.updateNote(prepareEntityForWrite(note))
    }

    override suspend fun deleteNote(note: Note) = withContext(ioDispatcher) {
        if (note.id > 0) {
            softDeleteById(note.id)
        } else if (note.syncUuid.isNotBlank()) {
            noteDao.getNoteBySyncUuidSuspend(note.syncUuid)?.let { softDeleteById(it.id) }
        }
    }

    override suspend fun deleteNoteById(id: Int) = withContext(ioDispatcher) {
        if (id > 0) {
            softDeleteById(id)
        }
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

    override suspend fun getActiveReminders(now: Long): List<Note> = withContext(ioDispatcher) {
        noteDao.getActiveRemindersSuspend(now).map { it.toDomain() }
    }

    override suspend fun updatePinStatus(id: Int, isPinned: Boolean) = withContext(ioDispatcher) {
        noteDao.updatePinStatusSuspend(id, isPinned, clock.nowMillis())
    }

    override suspend fun updateReminderDone(id: Int, isDone: Boolean) = withContext(ioDispatcher) {
        noteDao.updateReminderDoneSuspend(id, isDone, clock.nowMillis())
    }

    override suspend fun updateReminderTime(id: Int, reminderTime: Long) = withContext(ioDispatcher) {
        noteDao.updateReminderTimeSuspend(id, reminderTime, clock.nowMillis())
    }

    override suspend fun getNoteBySyncUuid(syncUuid: String): Note? = withContext(ioDispatcher) {
        noteDao.getNoteBySyncUuidSuspend(syncUuid)?.toDomain()
    }

    override suspend fun getNotesModifiedSince(sinceMillis: Long): List<Note> = withContext(ioDispatcher) {
        noteDao.getNotesModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
    }

    private suspend fun softDeleteById(id: Int) {
        val now = clock.nowMillis()
        noteDao.softDeleteWithTombstoneSuspend(id = id, deletedAt = now, updatedAt = now)
        noteLinkDao?.softDeleteLinksForNoteSuspend(noteId = id, deletedAt = now, updatedAt = now)
    }

    private suspend fun prepareEntityForWrite(note: Note): NoteData {
        val now = clock.nowMillis()
        val existing = if (note.id > 0) {
            noteDao.getNoteByIdIncludingDeletedSuspend(note.id)?.takeIf { it.id > 0 }
        } else if (note.syncUuid.isNotBlank()) {
            noteDao.getNoteBySyncUuidIncludingDeletedSuspend(note.syncUuid)?.takeIf { it.id > 0 }
        } else {
            null
        }

        val resolvedSyncUuid = note.syncUuid.ifBlank {
            existing?.syncUuid?.ifBlank { null } ?: UUID.randomUUID().toString()
        }
        val resolvedCategorySyncUuid = note.categorySyncUuid?.takeIf { it.isNotBlank() }
            ?: note.categoryId?.let { catId ->
                categoryDao?.getCategoryByIdIncludingDeletedSuspend(catId)?.takeIf { it.id > 0 }?.syncUuid
            }
            ?: existing?.categorySyncUuid
        val resolvedVersion = if (existing != null) {
            maxOf(note.syncVersion, existing.syncVersion) + 1L
        } else {
            note.syncVersion.coerceAtLeast(1L)
        }
        val resolvedUpdatedAt = if (existing != null) {
            now
        } else if (note.updatedAt > 0L) {
            note.updatedAt
        } else {
            now
        }

        return note.copy(
            id = if (note.id > 0) note.id else (existing?.id ?: 0),
            syncUuid = resolvedSyncUuid,
            categorySyncUuid = resolvedCategorySyncUuid,
            updatedAt = resolvedUpdatedAt,
            syncVersion = resolvedVersion
        ).toEntity()
    }
}
