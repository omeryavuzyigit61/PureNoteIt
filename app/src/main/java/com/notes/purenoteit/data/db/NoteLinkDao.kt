package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface NoteLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(link: NoteLinkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuspend(links: List<NoteLinkData>)

    @Upsert
    suspend fun upsertSuspend(link: NoteLinkData)

    @Query(
        """
        UPDATE note_links
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE sourceNoteId = :noteId AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteLinksFromSourceSuspend(noteId: Int, deletedAt: Long, updatedAt: Long)

    @Query(
        """
        UPDATE note_links
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE (sourceNoteId = :noteId OR targetNoteId = :noteId) AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteLinksForNoteSuspend(noteId: Int, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM note_links WHERE sourceNoteId = :sourceNoteId AND deletedAt IS NULL")
    suspend fun getOutgoingLinksSuspend(sourceNoteId: Int): List<NoteLinkData>

    @Query("SELECT * FROM note_links WHERE targetNoteId = :targetNoteId AND deletedAt IS NULL")
    suspend fun getBacklinksSuspend(targetNoteId: Int): List<NoteLinkData>

    @Query("UPDATE note_links SET linkText = :newTitle, updatedAt = :updatedAt, syncVersion = syncVersion + 1 WHERE targetNoteId = :targetNoteId AND deletedAt IS NULL")
    suspend fun updateTargetLinkTextSuspend(targetNoteId: Int, newTitle: String, updatedAt: Long)

    @Query("SELECT * FROM note_links WHERE deletedAt IS NULL")
    suspend fun getAllLinksSuspend(): List<NoteLinkData>

    @Query("SELECT * FROM note_links WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getLinkBySyncUuidIncludingDeletedSuspend(syncUuid: String): NoteLinkData?

    @Query("SELECT * FROM note_links WHERE updatedAt > :sinceMillis ORDER BY updatedAt ASC")
    suspend fun getLinksModifiedSinceSuspend(sinceMillis: Long): List<NoteLinkData>
}
