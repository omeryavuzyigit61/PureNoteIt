package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface NoteLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(link: NoteLinkData): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(links: List<NoteLinkData>): Completable

    @Delete
    fun delete(link: NoteLinkData): Completable

    @Query("DELETE FROM note_links WHERE sourceNoteId = :noteId")
    fun deleteLinksFromSource(noteId: Int): Completable

    @Query("SELECT * FROM note_links WHERE sourceNoteId = :sourceNoteId")
    fun getOutgoingLinks(sourceNoteId: Int): Single<List<NoteLinkData>>

    @Query("SELECT * FROM note_links WHERE targetNoteId = :targetNoteId")
    fun getBacklinks(targetNoteId: Int): Single<List<NoteLinkData>>

    @Query("SELECT * FROM note_links")
    fun getAllLinks(): Single<List<NoteLinkData>>
}
