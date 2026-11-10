package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.sync.model.SyncCursorState

data class LocalSyncSnapshot(
    val categories: List<Category> = emptyList(),
    val notes: List<Note> = emptyList(),
    val noteLinks: List<NoteLink> = emptyList(),
    val streamItems: List<StreamItem> = emptyList(),
    val automationRules: List<AutomationRule> = emptyList()
)

data class ResolvedRemoteBatch(
    val categories: List<Category> = emptyList(),
    val notes: List<Note> = emptyList(),
    val noteLinks: List<NoteLink> = emptyList(),
    val streamItems: List<StreamItem> = emptyList(),
    val automationRules: List<AutomationRule> = emptyList()
)

/**
 * Platform database/filesystem bridge used by [SyncEngine].
 * Implementations apply resolved batches in parent-before-child foreign key order.
 */
interface LocalSyncStore {
    suspend fun readModifiedSince(sinceMillis: Long): AppResult<LocalSyncSnapshot, DataError.Local>

    suspend fun applyResolvedRemoteBatch(batch: ResolvedRemoteBatch): AppResult<Unit, DataError.Local>

    suspend fun findCategoriesBySyncUuids(syncUuids: Collection<String>): Map<String, Category>

    suspend fun findNotesBySyncUuids(syncUuids: Collection<String>): Map<String, Note>

    suspend fun findLinksBySyncUuids(syncUuids: Collection<String>): Map<String, NoteLink>

    suspend fun findStreamItemsBySyncUuids(syncUuids: Collection<String>): Map<String, StreamItem>

    suspend fun findRulesBySyncUuids(syncUuids: Collection<String>): Map<String, AutomationRule>

    suspend fun hasLocalMedia(mediaPath: String): Boolean

    suspend fun readLocalMediaBytes(mediaPath: String): ByteArray?

    suspend fun writeLocalMediaBytes(mediaPath: String, bytes: ByteArray): AppResult<Unit, DataError.Local>

    suspend fun getSyncCursorState(): SyncCursorState

    suspend fun saveSyncCursorState(state: SyncCursorState)
}
