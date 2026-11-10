package com.notes.purenoteit.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.notes.purenoteit.data.db.AutomationRuleDao
import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteDatabase
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.StreamItemDao
import com.notes.purenoteit.data.mapper.defaultCategorySyncUuid
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.sync.LocalSyncSnapshot
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.ResolvedRemoteBatch
import com.notes.purenoteit.sync.model.SyncCursorState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Room v6 + SQLCipher adapter for `:shared`'s [LocalSyncStore].
 * Commits resolved remote deltas in parent-before-child transactional order and keeps sync
 * cursor metadata inside `noBackupFilesDir` isolated from unencrypted Android Auto Backup.
 */
@Singleton
internal class AndroidLocalSyncStoreImpl @Inject constructor(
    private val database: NoteDatabase,
    private val noteDao: NoteDao,
    private val categoryDao: CategoryDao,
    private val noteLinkDao: NoteLinkDao,
    private val streamItemDao: StreamItemDao,
    private val automationRuleDao: AutomationRuleDao,
    private val mediaPathResolver: MediaPathResolver,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LocalSyncStore {

    private val cursorMutex = Mutex()
    private val cursorJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val cursorFile: File by lazy {
        File(context.noBackupFilesDir, SYNC_STATE_FILENAME)
    }

    override suspend fun readModifiedSince(sinceMillis: Long): AppResult<LocalSyncSnapshot, DataError.Local> =
        withContext(ioDispatcher) {
            try {
                AppResult.Success(
                    LocalSyncSnapshot(
                        categories = categoryDao.getCategoriesModifiedSinceSuspend(sinceMillis).map { it.toDomain() },
                        notes = noteDao.getNotesModifiedSinceSuspend(sinceMillis).map { it.toDomain() },
                        noteLinks = noteLinkDao.getLinksModifiedSinceSuspend(sinceMillis).map { it.toDomain() },
                        streamItems = streamItemDao.getStreamItemsModifiedSinceSuspend(sinceMillis).map { it.toDomain() },
                        automationRules = automationRuleDao.getRulesModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(DataError.Local.UNKNOWN)
            }
        }

    override suspend fun applyResolvedRemoteBatch(batch: ResolvedRemoteBatch): AppResult<Unit, DataError.Local> =
        withContext(ioDispatcher) {
            try {
                database.withTransaction {
                    // 1. Categories first (parents for Notes ForeignKey)
                    val categoryIdByUuid = mutableMapOf<String, Int>()
                    categoryDao.getCategoriesModifiedSinceSuspend(Long.MIN_VALUE).forEach { cat ->
                        categoryIdByUuid[cat.syncUuid] = cat.id
                    }
                    for (cat in batch.categories) {
                        val existing = categoryDao.getCategoryBySyncUuidIncludingDeletedSuspend(cat.syncUuid)
                        val resolvedId = existing?.id ?: 0
                        categoryDao.upsertCategorySuspend(cat.copy(id = resolvedId).toEntity())
                        val stored = categoryDao.getCategoryBySyncUuidIncludingDeletedSuspend(cat.syncUuid)
                        if (stored != null) {
                            categoryIdByUuid[stored.syncUuid] = stored.id
                        }
                    }

                    val fallbackCategoryId = categoryDao.getAllCategoriesSuspend().firstOrNull()?.id ?: 1

                    // 2. Notes pass 1: commit records resolving local categoryId
                    val noteIdByUuid = mutableMapOf<String, Int>()
                    noteDao.getNotesModifiedSinceSuspend(Long.MIN_VALUE).forEach { n ->
                        noteIdByUuid[n.syncUuid] = n.id
                    }

                    val insertedOrUpdatedNotes = mutableListOf<Note>()
                    for (note in batch.notes) {
                        val existing = noteDao.getNoteBySyncUuidIncludingDeletedSuspend(note.syncUuid)
                        val resolvedId = existing?.id ?: 0
                        val resolvedCategoryId = note.categorySyncUuid?.let { categoryIdByUuid[it] }
                            ?: existing?.categoryId
                            ?: fallbackCategoryId
                        val resolvedCategoryUuid = note.categorySyncUuid
                            ?: existing?.categorySyncUuid
                            ?: defaultCategorySyncUuid(resolvedCategoryId, "")

                        val readyNote = note.copy(
                            id = resolvedId,
                            categoryId = resolvedCategoryId,
                            categorySyncUuid = resolvedCategoryUuid
                        )
                        noteDao.upsertNote(readyNote.toEntity())
                        val saved = noteDao.getNoteBySyncUuidIncludingDeletedSuspend(readyNote.syncUuid)
                        if (saved != null) {
                            noteIdByUuid[saved.syncUuid] = saved.id
                            insertedOrUpdatedNotes += saved.toDomain()
                        }
                    }

                    // 2b. Remap local Int subpage/parent pointers inside blocks via cross-device UUIDs
                    for (savedNote in insertedOrUpdatedNotes) {
                        if (savedNote.deletedAt != null) continue
                        val updatedNote = remapSubPageLocalIds(savedNote, noteIdByUuid)
                        if (updatedNote != savedNote) {
                            noteDao.upsertNote(updatedNote.toEntity())
                        }
                    }

                    // 3. NoteLinks (require both sourceNoteId & targetNoteId to exist locally)
                    for (link in batch.noteLinks) {
                        val sourceId = noteIdByUuid[link.sourceSyncUuid]
                        val targetId = noteIdByUuid[link.targetSyncUuid]
                        if (sourceId == null || targetId == null) continue
                        val existing = noteLinkDao.getLinkBySyncUuidIncludingDeletedSuspend(link.syncUuid)
                        val entity = link.copy(
                            id = existing?.id ?: 0,
                            sourceNoteId = sourceId,
                            targetNoteId = targetId
                        ).toEntity(link.updatedAt)
                        noteLinkDao.upsertSuspend(entity)
                    }

                    // 4. StreamItems & AutomationRules
                    for (item in batch.streamItems) {
                        val existing = streamItemDao.getBySyncUuidIncludingDeletedSuspend(item.syncUuid)
                        val mergedId = item.mergedNoteSyncUuid?.let { noteIdByUuid[it] }
                        val entity = item.copy(
                            id = existing?.id ?: 0,
                            mergedNoteId = mergedId
                        ).toEntity(item.updatedAt)
                        streamItemDao.upsertSuspend(entity)
                    }

                    for (rule in batch.automationRules) {
                        val existing = automationRuleDao.getRuleBySyncUuidIncludingDeletedSuspend(rule.syncUuid)
                        val entity = rule.copy(id = existing?.id ?: 0).toEntity(rule.updatedAt)
                        automationRuleDao.upsertSuspend(entity)
                    }
                }
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(DataError.Local.UNKNOWN)
            }
        }

    override suspend fun findCategoriesBySyncUuids(syncUuids: Collection<String>): Map<String, Category> =
        withContext(ioDispatcher) {
            syncUuids.mapNotNull { uuid ->
                categoryDao.getCategoryBySyncUuidIncludingDeletedSuspend(uuid)?.toDomain()
            }.associateBy { it.syncUuid }
        }

    override suspend fun findNotesBySyncUuids(syncUuids: Collection<String>): Map<String, Note> =
        withContext(ioDispatcher) {
            syncUuids.mapNotNull { uuid ->
                noteDao.getNoteBySyncUuidIncludingDeletedSuspend(uuid)?.toDomain()
            }.associateBy { it.syncUuid }
        }

    override suspend fun findLinksBySyncUuids(syncUuids: Collection<String>): Map<String, NoteLink> =
        withContext(ioDispatcher) {
            syncUuids.mapNotNull { uuid ->
                noteLinkDao.getLinkBySyncUuidIncludingDeletedSuspend(uuid)?.toDomain()
            }.associateBy { it.syncUuid }
        }

    override suspend fun findStreamItemsBySyncUuids(syncUuids: Collection<String>): Map<String, StreamItem> =
        withContext(ioDispatcher) {
            syncUuids.mapNotNull { uuid ->
                streamItemDao.getBySyncUuidIncludingDeletedSuspend(uuid)?.toDomain()
            }.associateBy { it.syncUuid }
        }

    override suspend fun findRulesBySyncUuids(syncUuids: Collection<String>): Map<String, AutomationRule> =
        withContext(ioDispatcher) {
            syncUuids.mapNotNull { uuid ->
                automationRuleDao.getRuleBySyncUuidIncludingDeletedSuspend(uuid)?.toDomain()
            }.associateBy { it.syncUuid }
        }

    override suspend fun hasLocalMedia(mediaPath: String): Boolean = withContext(ioDispatcher) {
        val resolved = mediaPathResolver.resolveToReadablePath(mediaPath) ?: return@withContext false
        val file = File(resolved)
        file.exists() && file.isFile
    }

    override suspend fun readLocalMediaBytes(mediaPath: String): ByteArray? = withContext(ioDispatcher) {
        val resolved = mediaPathResolver.resolveToReadablePath(mediaPath) ?: return@withContext null
        val file = File(resolved)
        if (file.exists() && file.isFile) file.readBytes() else null
    }

    override suspend fun writeLocalMediaBytes(mediaPath: String, bytes: ByteArray): AppResult<Unit, DataError.Local> =
        withContext(ioDispatcher) {
            try {
                if (!mediaPathResolver.isCanonicalRelativePath(mediaPath)) {
                    return@withContext AppResult.Failure(DataError.Local.VALIDATION_FAILED)
                }
                val cleanRel = mediaPath.replace('\\', '/').trimStart('/')
                val parts = cleanRel.split('/')
                val subFolder = parts.getOrNull(1) ?: "files"
                val ext = parts.lastOrNull()?.substringAfterLast('.', "bin") ?: "bin"
                mediaPathResolver.storeMediaBytes(bytes, subFolder, ext)
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                AppResult.Failure(DataError.Local.VALIDATION_FAILED)
            } catch (_: Exception) {
                AppResult.Failure(DataError.Local.DISK_FULL)
            }
        }

    override suspend fun getSyncCursorState(): SyncCursorState = withContext(ioDispatcher) {
        cursorMutex.withLock {
            val loaded = if (cursorFile.exists()) {
                try {
                    cursorJson.decodeFromString<SyncCursorState>(cursorFile.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
            } else null

            if (loaded != null && loaded.deviceId.isNotBlank()) {
                loaded
            } else {
                val created = (loaded ?: SyncCursorState()).copy(deviceId = UUID.randomUUID().toString())
                persistCursorLocked(created)
                created
            }
        }
    }

    override suspend fun saveSyncCursorState(state: SyncCursorState): Unit = withContext(ioDispatcher) {
        cursorMutex.withLock {
            persistCursorLocked(state)
        }
    }

    private fun persistCursorLocked(state: SyncCursorState) {
        val parent = cursorFile.parentFile ?: context.filesDir
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, "${SYNC_STATE_FILENAME}.tmp")
        temp.writeText(cursorJson.encodeToString(state), Charsets.UTF_8)
        if (!temp.renameTo(cursorFile)) {
            temp.copyTo(cursorFile, overwrite = true)
            temp.delete()
        }
    }

    private fun remapSubPageLocalIds(note: Note, noteIdByUuid: Map<String, Int>): Note {
        var changed = false
        var updatedDesc = note.description
        if (!updatedDesc.isNullOrBlank() && updatedDesc.startsWith("NOTION_BLOCKS_V1:")) {
            val blocks = BlockParser.parse(updatedDesc)
            var blocksChanged = false
            val remapped = blocks.map { blk ->
                if (blk.type == BlockType.SUB_PAGE && !blk.subPageSyncUuid.isNullOrBlank()) {
                    val localSubId = noteIdByUuid[blk.subPageSyncUuid]
                    if (localSubId != null && localSubId != blk.subPageNoteId) {
                        blocksChanged = true
                        blk.copy(subPageNoteId = localSubId)
                    } else blk
                } else blk
            }
            if (blocksChanged) {
                updatedDesc = BlockParser.serialize(remapped)
                changed = true
            }
        }

        var updatedRich = note.richBlocksJson
        val meta = BlockParser.parseRichMetadata(updatedRich)
        if (meta != null && meta.isSubPage && !meta.parentSyncUuid.isNullOrBlank()) {
            val localParentId = noteIdByUuid[meta.parentSyncUuid]
            if (localParentId != null && localParentId != meta.parentNoteId) {
                updatedRich = BlockParser.buildSubPageMetadataJson(localParentId, meta.parentSyncUuid)
                changed = true
            }
        }

        return if (changed) {
            note.copy(description = updatedDesc, richBlocksJson = updatedRich)
        } else {
            note
        }
    }

    companion object {
        private const val SYNC_STATE_FILENAME = "purenote_sync_state.json"
    }
}
