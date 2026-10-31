package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(note: NoteData): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNote(note: NoteData): Single<Long>

    @Query("SELECT * FROM notes")
    fun getAllNotes(): Single<List<NoteData>>

    @Delete
    fun delete(note: NoteData): Completable

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id
    WHERE categories.isPassword IS NULL OR categories.isPassword = 0
    ORDER BY notes.isPinned DESC, notes.date DESC
"""
    )
    fun getNotes(): Single<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id
    WHERE (categories.isPassword IS NULL OR categories.isPassword = 0)
      AND (notes.title LIKE '%' || :query || '%' OR notes.description LIKE '%' || :query || '%')
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    fun searchNote(query: String): Flowable<List<NoteData>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Single<NoteData>

    @Query("DELETE FROM notes WHERE id = :id")
    fun deleteNoteById(id: Int): Completable

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
    fun getNotesBetweenDates(startDate: Long, endDate: Long): Single<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    INNER JOIN categories ON notes.categoryId = categories.id
    WHERE categories.name = :categoryName
    ORDER BY notes.isPinned DESC, notes.date DESC
    """
    )
    fun getNotesByCategory(categoryName: String): Flowable<List<NoteData>>

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :id")
    fun updatePinStatus(id: Int, isPinned: Boolean): Completable

    @Query("SELECT * FROM notes WHERE reminderTime IS NOT NULL AND reminderTime > :now AND isReminderDone = 0")
    fun getActiveReminders(now: Long): Single<List<NoteData>>

    @Query("UPDATE notes SET isReminderDone = :isDone WHERE id = :id")
    fun updateReminderDone(id: Int, isDone: Boolean): Completable

    @Query("UPDATE notes SET reminderTime = :reminderTime, isReminderDone = 0 WHERE id = :id")
    fun updateReminderTime(id: Int, reminderTime: Long): Completable
}
