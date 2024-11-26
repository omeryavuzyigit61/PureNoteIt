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

    @Delete
    fun delete(note: NoteData): Completable

    @Query(
        """
    SELECT notes.*
    FROM notes
    LEFT JOIN categories ON notes.categoryId = categories.id
    WHERE categories.isPassword IS NULL OR categories.isPassword = 0
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
    """
    )
    fun searchNote(query: String): Flowable<List<NoteData>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id: Int): Single<NoteData>

    @Query("DELETE FROM notes WHERE id = :id")
    fun deleteNoteById(id: Int): Completable

    @Query("SELECT * FROM notes WHERE date BETWEEN :startDate AND :endDate")
    fun getNotesBetweenDates(startDate: Long, endDate: Long): Single<List<NoteData>>

    @Query(
        """
    SELECT notes.*
    FROM notes
    INNER JOIN categories ON notes.categoryId = categories.id
    WHERE categories.name = :categoryName
    """
    )
    fun getNotesByCategory(categoryName: String): Flowable<List<NoteData>>
}