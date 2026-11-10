package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsertNote(note: NoteData)

    @Update
    suspend fun updateNote(note: NoteData)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteSuspend(note: NoteData): Long

    @Query(
        """
        UPDATE notes
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1,
            status = 'DELETED'
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteWithTombstoneSuspend(id: Int, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE notes
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1,
            status = 'DELETED'
        WHERE categoryId = :categoryId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteNotesByCategorySuspend(categoryId: Int, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, date DESC")
    fun getAllNotesFlow(): Flow<List<NoteData>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, date DESC")
    suspend fun getAllNotesSuspend(): List<NoteData>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getNoteByIdSuspend(id: Int): NoteData?

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteByIdIncludingDeletedSuspend(id: Int): NoteData?

    @Query("SELECT * FROM notes WHERE syncUuid = :syncUuid AND deletedAt IS NULL LIMIT 1")
    suspend fun getNoteBySyncUuidSuspend(syncUuid: String): NoteData?

    @Query("SELECT * FROM notes WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getNoteBySyncUuidIncludingDeletedSuspend(syncUuid: String): NoteData?

    @Query("SELECT * FROM notes WHERE updatedAt > :sinceMillis ORDER BY updatedAt ASC")
    suspend fun getNotesModifiedSinceSuspend(sinceMillis: Long): List<NoteData>

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id AND categories.deletedAt IS NULL
    WHERE notes.deletedAt IS NULL
      AND (categories.isPassword IS NULL OR categories.isPassword = 0)
      AND (notes.isPassword IS NULL OR notes.isPassword = 0)
      AND (notes.title LIKE '%' || :query || '%' OR notes.description LIKE '%' || :query || '%')
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    fun searchNoteFlow(query: String): Flow<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    INNER JOIN categories ON notes.categoryId = categories.id
    WHERE notes.deletedAt IS NULL
      AND categories.deletedAt IS NULL
      AND categories.name = :categoryName
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    fun getNotesByCategoryFlow(categoryName: String): Flow<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id AND categories.deletedAt IS NULL
    WHERE notes.deletedAt IS NULL
      AND (categories.isPassword IS NULL OR categories.isPassword = 0)
      AND (notes.date BETWEEN :startDate AND :endDate)
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    suspend fun getNotesBetweenDatesSuspend(startDate: Long, endDate: Long): List<NoteData>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND reminderTime IS NOT NULL AND reminderTime > :now AND isReminderDone = 0")
    suspend fun getActiveRemindersSuspend(now: Long): List<NoteData>

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt, syncVersion = syncVersion + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun updatePinStatusSuspend(id: Int, isPinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET isReminderDone = :isDone, updatedAt = :updatedAt, syncVersion = syncVersion + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateReminderDoneSuspend(id: Int, isDone: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET reminderTime = :reminderTime, isReminderDone = 0, updatedAt = :updatedAt, syncVersion = syncVersion + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateReminderTimeSuspend(id: Int, reminderTime: Long, updatedAt: Long)
}
