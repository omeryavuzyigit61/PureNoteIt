package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface StreamItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: StreamItemData): Single<Long>

    @Delete
    fun delete(item: StreamItemData): Completable

    @Query("SELECT * FROM stream_items ORDER BY timestamp DESC")
    fun getAllItems(): Single<List<StreamItemData>>

    @Query("SELECT * FROM stream_items WHERE isProcessed = 0 ORDER BY timestamp DESC")
    fun getUnprocessedItems(): Single<List<StreamItemData>>

    @Query("UPDATE stream_items SET isProcessed = 1, mergedNoteId = :noteId WHERE id IN (:ids)")
    fun markAsProcessed(ids: List<Int>, noteId: Int): Completable

    @Query("DELETE FROM stream_items WHERE id = :id")
    fun deleteById(id: Int): Completable
}
