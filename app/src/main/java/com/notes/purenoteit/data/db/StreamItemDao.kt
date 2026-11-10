package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface StreamItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(item: StreamItemData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuspend(items: List<StreamItemData>)

    @Upsert
    suspend fun upsertSuspend(item: StreamItemData)

    @Query("SELECT * FROM stream_items WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getAllItemsSuspend(): List<StreamItemData>

    @Query("SELECT * FROM stream_items WHERE deletedAt IS NULL AND isProcessed = 0 ORDER BY timestamp DESC")
    suspend fun getUnprocessedItemsSuspend(): List<StreamItemData>

    @Query(
        """
        UPDATE stream_items
        SET isProcessed = 1,
            mergedNoteId = :noteId,
            mergedNoteSyncUuid = :mergedNoteSyncUuid,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE id IN (:ids) AND deletedAt IS NULL
        """
    )
    suspend fun markAsProcessedSuspend(
        ids: List<Int>,
        noteId: Int,
        mergedNoteSyncUuid: String?,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE stream_items
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteWithTombstoneSuspend(id: Int, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM stream_items WHERE id = :id")
    suspend fun getByIdIncludingDeletedSuspend(id: Int): StreamItemData?

    @Query("SELECT * FROM stream_items WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getBySyncUuidIncludingDeletedSuspend(syncUuid: String): StreamItemData?

    @Query("SELECT * FROM stream_items WHERE updatedAt > :sinceMillis ORDER BY updatedAt ASC")
    suspend fun getStreamItemsModifiedSinceSuspend(sinceMillis: Long): List<StreamItemData>
}
