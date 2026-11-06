package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoteLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuspend(link: NoteLinkData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuspend(links: List<NoteLinkData>)

    @Delete
    suspend fun deleteSuspend(link: NoteLinkData)

    @Query("DELETE FROM note_links WHERE sourceNoteId = :noteId")
    suspend fun deleteLinksFromSourceSuspend(noteId: Int)

    @Query("DELETE FROM note_links WHERE sourceNoteId = :noteId OR targetNoteId = :noteId")
    suspend fun deleteLinksForNoteSuspend(noteId: Int)

    @Query("SELECT * FROM note_links WHERE sourceNoteId = :sourceNoteId")
    suspend fun getOutgoingLinksSuspend(sourceNoteId: Int): List<NoteLinkData>

    @Query("SELECT * FROM note_links WHERE targetNoteId = :targetNoteId")
    suspend fun getBacklinksSuspend(targetNoteId: Int): List<NoteLinkData>

    @Query("UPDATE note_links SET linkText = :newTitle WHERE targetNoteId = :targetNoteId")
    suspend fun updateTargetLinkTextSuspend(targetNoteId: Int, newTitle: String)

    @Query("SELECT * FROM note_links")
    suspend fun getAllLinksSuspend(): List<NoteLinkData>
}
