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
    fun upsert (note: NoteData): Completable
    @Delete
    fun delete (note: NoteData): Completable
    @Query("SELECT * FROM notes")
    fun getNotes(): Single<List<NoteData>>
    //(title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') AND isPassword = 0"
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchNote(query:String): Flowable<List<NoteData>> //Flowable veriyi sürekli dinler(büyük veri setleri için)
    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteById(id:Int): Single<NoteData>
    @Query("DELETE FROM notes WHERE id = :id")
    fun deleteNoteById(id:Int): Completable
    @Query("SELECT * FROM notes WHERE date BETWEEN :startDate AND :endDate")
    fun getNotesBetweenDates(startDate: Long, endDate: Long): Single<List<NoteData>>
    @Query("SELECT * FROM notes WHERE LOWER(category) = LOWER(:category)")
    fun searchByCategory(category: String): Single<List<NoteData>>
}