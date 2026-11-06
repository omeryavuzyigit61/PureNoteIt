package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StreamItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(item: StreamItemData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuspend(items: List<StreamItemData>)

    @Delete
    suspend fun deleteSuspend(item: StreamItemData)

    @Query("SELECT * FROM stream_items ORDER BY timestamp DESC")
    suspend fun getAllItemsSuspend(): List<StreamItemData>

    @Query("SELECT * FROM stream_items WHERE isProcessed = 0 ORDER BY timestamp DESC")
    suspend fun getUnprocessedItemsSuspend(): List<StreamItemData>

    @Query("UPDATE stream_items SET isProcessed = 1, mergedNoteId = :noteId WHERE id IN (:ids)")
    suspend fun markAsProcessedSuspend(ids: List<Int>, noteId: Int)

    @Query("DELETE FROM stream_items WHERE id = :id")
    suspend fun deleteByIdSuspend(id: Int)
}
