package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
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

    @Delete
    suspend fun deleteNote(note: NoteData)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteByIdSuspend(id: Int)

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, date DESC")
    fun getAllNotesFlow(): Flow<List<NoteData>>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, date DESC")
    suspend fun getAllNotesSuspend(): List<NoteData>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteByIdSuspend(id: Int): NoteData?

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id
    WHERE (categories.isPassword IS NULL OR categories.isPassword = 0)
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
    WHERE categories.name = :categoryName
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    fun getNotesByCategoryFlow(categoryName: String): Flow<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id
    WHERE (categories.isPassword IS NULL OR categories.isPassword = 0)
      AND (notes.date BETWEEN :startDate AND :endDate)
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    suspend fun getNotesBetweenDatesSuspend(startDate: Long, endDate: Long): List<NoteData>

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > :now AND isReminderDone = 0")
    suspend fun getActiveRemindersSuspend(now: Long): List<NoteData>

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatusSuspend(id: Int, isPinned: Boolean)

    @Query("UPDATE notes SET isReminderDone = :isDone WHERE id = :id")
    suspend fun updateReminderDoneSuspend(id: Int, isDone: Boolean)

    @Query("UPDATE notes SET reminderTime = :reminderTime, isReminderDone = 0 WHERE id = :id")
    suspend fun updateReminderTimeSuspend(id: Int, reminderTime: Long)
}
