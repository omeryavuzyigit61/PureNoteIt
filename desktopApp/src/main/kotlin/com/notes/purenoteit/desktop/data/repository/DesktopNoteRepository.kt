package com.notes.purenoteit.desktop.data.repository

import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.UUID

interface DesktopNoteRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeNotes(includeDeleted: Boolean = false): Flow<List<Note>>
    fun observeNoteLinks(): Flow<List<NoteLink>>
    fun observeStreamItems(): Flow<List<StreamItem>>

    suspend fun upsertNote(note: Note): AppResult<Note, DataError.Local>
    suspend fun softDeleteNote(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun restoreNote(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun permanentDeleteNote(syncUuid: String): AppResult<Unit, DataError.Local> = AppResult.Success(Unit)
    suspend fun emptyTrash(): AppResult<Int, DataError.Local>
    suspend fun purgeExpiredTrashNotes(thresholdMillis: Long): AppResult<Int, DataError.Local> = AppResult.Success(0)
    suspend fun togglePin(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun upsertCategory(name: String): AppResult<Category, DataError.Local>
    suspend fun renameCategory(syncUuid: String, newName: String): AppResult<Category, DataError.Local>
    suspend fun toggleCategoryLock(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun softDeleteCategory(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun addStreamItem(content: String, type: String = "TEXT"): AppResult<StreamItem, DataError.Local>
    suspend fun deleteStreamItem(syncUuid: String): AppResult<Unit, DataError.Local>
    suspend fun convertStreamItemsToNote(streamSyncUuids: List<String>, title: String): AppResult<Note, DataError.Local>
    suspend fun createSubPage(
        parentNoteSyncUuid: String,
        subPageTitle: String
    ): AppResult<Note, DataError.Local>
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class DesktopNoteRepositoryImpl(
    private val db: DesktopSqliteDatabaseManager,
    private val clock: AppClock
) : DesktopNoteRepository {

    private val listJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val wikilinkRegex = Regex("""\[\[([^\]\r\n]{1,120})]]""")

    override fun observeCategories(): Flow<List<Category>> = db.changeVersion.mapLatest {
        db.withConnection { conn -> queryCategories(conn, includeDeleted = false) }
    }

    override fun observeNotes(includeDeleted: Boolean): Flow<List<Note>> = db.changeVersion.mapLatest {
        db.withConnection { conn -> queryNotes(conn, includeDeleted = includeDeleted) }
    }

    override fun observeNoteLinks(): Flow<List<NoteLink>> = db.changeVersion.mapLatest {
        db.withConnection { conn -> queryNoteLinks(conn, includeDeleted = false) }
    }

    override fun observeStreamItems(): Flow<List<StreamItem>> = db.changeVersion.mapLatest {
        db.withConnection { conn -> queryStreamItems(conn) }
    }

    override suspend fun upsertNote(note: Note): AppResult<Note, DataError.Local> {
        return try {
            val saved = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val effectiveUuid = note.syncUuid.ifBlank { UUID.randomUUID().toString() }
                val existing = findNoteBySyncUuidLocked(conn, effectiveUuid)
                val resolvedCategoryId = resolveCategoryIdLocked(conn, note.categoryId, note.categorySyncUuid)
                val resolvedCategoryUuid = note.categorySyncUuid
                    ?: existing?.categorySyncUuid
                    ?: findCategorySyncUuidByIdLocked(conn, resolvedCategoryId)

                val blocks = BlockParser.parse(note.description)
                val (totalTasks, completedTasks) = BlockParser.calculateTasks(blocks)
                val drawingPaths = blocks.mapNotNull { b ->
                    b.mediaPath?.takeIf { b.type == BlockType.DRAWING }
                }.toSet()
                val extractedImages = blocks.mapNotNull { b ->
                    b.mediaPath?.takeIf { b.type == BlockType.IMAGE }
                }.ifEmpty { note.images.orEmpty().filterNot { it in drawingPaths } }

                val nextVersion = if (existing != null) existing.syncVersion + 1L else note.syncVersion.coerceAtLeast(1L)
                val nextDate = if (note.date > 0L) note.date else now

                val persisted = if (existing == null) {
                    insertNoteLocked(
                        conn = conn,
                        note = note.copy(
                            date = nextDate,
                            categoryId = resolvedCategoryId,
                            categorySyncUuid = resolvedCategoryUuid,
                            images = extractedImages,
                            totalTasks = totalTasks,
                            completedTasks = completedTasks,
                            syncUuid = effectiveUuid,
                            updatedAt = now,
                            deletedAt = null,
                            syncVersion = nextVersion
                        )
                    )
                } else {
                    updateNoteLocked(
                        conn = conn,
                        note = note.copy(
                            id = existing.id,
                            date = existing.date.takeIf { it > 0L } ?: nextDate,
                            categoryId = resolvedCategoryId,
                            categorySyncUuid = resolvedCategoryUuid,
                            images = extractedImages,
                            totalTasks = totalTasks,
                            completedTasks = completedTasks,
                            syncUuid = effectiveUuid,
                            updatedAt = now,
                            deletedAt = note.deletedAt,
                            syncVersion = nextVersion
                        )
                    )
                }
                syncWikiLinksForNoteLocked(conn, persisted, now)
                persisted
            }
            AppResult.Success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun softDeleteNote(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                val existing = findNoteBySyncUuidLocked(conn, syncUuid) ?: return@inTransaction
                conn.prepareStatement(
                    "UPDATE notes SET deletedAt = ?, status = 'TRASH', updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setString(3, syncUuid)
                    ps.executeUpdate()
                }
                // Tombstone associated note_links instead of physical delete
                conn.prepareStatement(
                    "UPDATE note_links SET deletedAt = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE (sourceNoteId = ? OR targetNoteId = ?) AND deletedAt IS NULL"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setInt(3, existing.id)
                    ps.setInt(4, existing.id)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun restoreNote(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    "UPDATE notes SET deletedAt = NULL, status = CASE WHEN UPPER(status) IN ('TRASH', 'DELETED') THEN 'TODO' ELSE status END, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, syncUuid)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun permanentDeleteNote(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                val existing = findNoteBySyncUuidLocked(conn, syncUuid) ?: return@inTransaction
                conn.prepareStatement(
                    "UPDATE notes SET deletedAt = ?, status = 'DELETED', updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setString(3, syncUuid)
                    ps.executeUpdate()
                }
                conn.prepareStatement(
                    "UPDATE note_links SET deletedAt = COALESCE(deletedAt, ?), updatedAt = ?, syncVersion = syncVersion + 1 WHERE (sourceNoteId = ? OR targetNoteId = ?) AND deletedAt IS NULL"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setInt(3, existing.id)
                    ps.setInt(4, existing.id)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun emptyTrash(): AppResult<Int, DataError.Local> {
        return try {
            val count = db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    "UPDATE notes SET deletedAt = ?, status = 'DELETED', updatedAt = ?, syncVersion = syncVersion + 1 WHERE (deletedAt IS NOT NULL OR UPPER(status) = 'TRASH') AND UPPER(status) != 'DELETED'"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun purgeExpiredTrashNotes(thresholdMillis: Long): AppResult<Int, DataError.Local> {
        return try {
            val count = db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    """
                    UPDATE notes
                    SET deletedAt = ?,
                        status = 'DELETED',
                        updatedAt = ?,
                        syncVersion = syncVersion + 1
                    WHERE (deletedAt IS NOT NULL OR UPPER(COALESCE(status, 'TODO')) = 'TRASH')
                      AND UPPER(COALESCE(status, 'TODO')) != 'DELETED'
                      AND COALESCE(deletedAt, CASE WHEN updatedAt > 0 THEN updatedAt ELSE date END) < ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setLong(3, thresholdMillis)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun togglePin(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    "UPDATE notes SET isPinned = CASE WHEN isPinned = 1 THEN 0 ELSE 1 END, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ? AND deletedAt IS NULL"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, syncUuid)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun upsertCategory(name: String): AppResult<Category, DataError.Local> {
        val clean = name.trim()
        if (clean.isEmpty()) {
            return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        }
        return try {
            val created = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val existing = findCategoryByNameLocked(conn, clean)
                if (existing != null) {
                    conn.prepareStatement(
                        "UPDATE categories SET deletedAt = NULL, updatedAt = ?, syncVersion = syncVersion + 1 WHERE id = ?"
                    ).use { ps ->
                        ps.setLong(1, now)
                        ps.setInt(2, existing.id)
                        ps.executeUpdate()
                    }
                    existing.copy(deletedAt = null, updatedAt = now, syncVersion = existing.syncVersion + 1)
                } else {
                    val uuid = UUID.randomUUID().toString()
                    val newId = conn.prepareStatement(
                        "INSERT INTO categories (name, isPassword, syncUuid, updatedAt, deletedAt, syncVersion) VALUES (?, NULL, ?, ?, NULL, 1)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { ps ->
                        ps.setString(1, clean)
                        ps.setString(2, uuid)
                        ps.setLong(3, now)
                        ps.executeUpdate()
                        ps.generatedKeys.use { gk -> if (gk.next()) gk.getInt(1) else 0 }
                    }
                    Category(
                        id = newId,
                        name = clean,
                        syncUuid = uuid,
                        updatedAt = now,
                        syncVersion = 1L
                    )
                }
            }
            AppResult.Success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun renameCategory(syncUuid: String, newName: String): AppResult<Category, DataError.Local> {
        val clean = newName.trim()
        if (clean.isEmpty()) return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        return try {
            val updated = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val existing = findCategoryBySyncUuidLocked(conn, syncUuid) ?: return@inTransaction null
                conn.prepareStatement(
                    "UPDATE categories SET name = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setString(1, clean)
                    ps.setLong(2, now)
                    ps.setString(3, syncUuid)
                    ps.executeUpdate()
                }
                existing.copy(name = clean, updatedAt = now, syncVersion = existing.syncVersion + 1L)
            }
            if (updated != null) AppResult.Success(updated) else AppResult.Failure(DataError.Local.NOT_FOUND)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun toggleCategoryLock(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    "UPDATE categories SET isPassword = CASE WHEN COALESCE(isPassword, 0) = 1 THEN 0 ELSE 1 END, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ? AND deletedAt IS NULL"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setString(2, syncUuid)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun addStreamItem(content: String, type: String): AppResult<StreamItem, DataError.Local> {
        val clean = content.trim()
        if (clean.isEmpty()) return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        return try {
            val created = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val uuid = UUID.randomUUID().toString()
                val newId = conn.prepareStatement(
                    """
                    INSERT INTO stream_items (content, type, audioPath, imagePath, timestamp, isProcessed, mergedNoteId, syncUuid, mergedNoteSyncUuid, updatedAt, deletedAt, syncVersion)
                    VALUES (?, ?, NULL, NULL, ?, 0, NULL, ?, NULL, ?, NULL, 1)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, clean)
                    ps.setString(2, type)
                    ps.setLong(3, now)
                    ps.setString(4, uuid)
                    ps.setLong(5, now)
                    ps.executeUpdate()
                    ps.generatedKeys.use { gk -> if (gk.next()) gk.getInt(1) else 0 }
                }
                StreamItem(
                    id = newId,
                    content = clean,
                    type = type,
                    timestamp = now,
                    isProcessed = false,
                    syncUuid = uuid,
                    updatedAt = now,
                    syncVersion = 1L
                )
            }
            AppResult.Success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun deleteStreamItem(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                conn.prepareStatement(
                    "UPDATE stream_items SET deletedAt = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setString(3, syncUuid)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun convertStreamItemsToNote(
        streamSyncUuids: List<String>,
        title: String
    ): AppResult<Note, DataError.Local> {
        val uniqueUuids = streamSyncUuids.distinct()
        if (uniqueUuids.isEmpty()) return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        return try {
            val savedNote = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val allItems = queryStreamItems(conn)
                val selected = allItems.filter { it.syncUuid in uniqueUuids }
                if (selected.isEmpty()) throw IllegalStateException("No matching stream items")

                val blocks = mutableListOf(NoteBlock(type = BlockType.HEADING_2, content = "Hızlı Fikirler Akışı"))
                for (item in selected) {
                    when (item.type.uppercase()) {
                        "AUDIO" -> if (!item.audioPath.isNullOrBlank()) {
                            blocks += NoteBlock(type = BlockType.AUDIO, mediaPath = item.audioPath, content = item.content)
                        } else {
                            blocks += NoteBlock(type = BlockType.BULLET_LIST, content = item.content)
                        }
                        "IMAGE" -> if (!item.imagePath.isNullOrBlank()) {
                            blocks += NoteBlock(type = BlockType.IMAGE, mediaPath = item.imagePath, content = item.content)
                        } else {
                            blocks += NoteBlock(type = BlockType.BULLET_LIST, content = item.content)
                        }
                        else -> blocks += NoteBlock(type = BlockType.BULLET_LIST, content = item.content)
                    }
                }

                val defaultCatId = resolveCategoryIdLocked(conn, null, null)
                val defaultCatUuid = findCategorySyncUuidByIdLocked(conn, defaultCatId)
                val cleanTitle = title.trim().ifEmpty { "Fikir Notu (${selected.size} Madde)" }
                val noteUuid = UUID.randomUUID().toString()
                val insertedNote = insertNoteLocked(
                    conn = conn,
                    note = Note(
                        title = cleanTitle,
                        description = BlockParser.serialize(blocks),
                        date = now,
                        categoryId = defaultCatId,
                        categorySyncUuid = defaultCatUuid,
                        icon = "⚡",
                        status = "TODO",
                        syncUuid = noteUuid,
                        updatedAt = now,
                        syncVersion = 1L
                    )
                )

                conn.prepareStatement(
                    "UPDATE stream_items SET isProcessed = 1, mergedNoteId = ?, mergedNoteSyncUuid = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    for (item in selected) {
                        ps.setInt(1, insertedNote.id)
                        ps.setString(2, insertedNote.syncUuid)
                        ps.setLong(3, now)
                        ps.setString(4, item.syncUuid)
                        ps.executeUpdate()
                    }
                }
                insertedNote
            }
            AppResult.Success(savedNote)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalStateException) {
            AppResult.Failure(DataError.Local.NOT_FOUND)
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun softDeleteCategory(syncUuid: String): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction { conn ->
                val now = clock.nowMillis()
                val cat = findCategoryBySyncUuidLocked(conn, syncUuid) ?: return@inTransaction
                conn.prepareStatement(
                    "UPDATE categories SET deletedAt = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE syncUuid = ?"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setString(3, syncUuid)
                    ps.executeUpdate()
                }
                conn.prepareStatement(
                    "UPDATE notes SET deletedAt = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE categoryId = ? AND deletedAt IS NULL"
                ).use { ps ->
                    ps.setLong(1, now)
                    ps.setLong(2, now)
                    ps.setInt(3, cat.id)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun createSubPage(
        parentNoteSyncUuid: String,
        subPageTitle: String
    ): AppResult<Note, DataError.Local> {
        val cleanTitle = subPageTitle.trim().ifEmpty { "Alt Sayfa" }
        return try {
            val childNote = db.inTransaction { conn ->
                val now = clock.nowMillis()
                val parent = findNoteBySyncUuidLocked(conn, parentNoteSyncUuid)
                    ?: throw IllegalStateException("Parent note not found")

                val childUuid = UUID.randomUUID().toString()
                val childDescription = BlockParser.serialize(listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "")))
                val childMeta = BlockParser.buildSubPageMetadataJson(parent.id, parent.syncUuid)
                val insertedChild = insertNoteLocked(
                    conn = conn,
                    note = Note(
                        title = cleanTitle,
                        description = childDescription,
                        date = now,
                        categoryId = parent.categoryId,
                        categorySyncUuid = parent.categorySyncUuid,
                        richBlocksJson = childMeta,
                        syncUuid = childUuid,
                        updatedAt = now,
                        syncVersion = 1L
                    )
                )

                // Append a clickable SUB_PAGE block inside parent note
                val parentBlocks = BlockParser.parse(parent.description).toMutableList()
                parentBlocks += NoteBlock(
                    type = BlockType.SUB_PAGE,
                    content = cleanTitle,
                    subPageNoteId = insertedChild.id,
                    subPageSyncUuid = insertedChild.syncUuid
                )
                updateNoteLocked(
                    conn = conn,
                    note = parent.copy(
                        description = BlockParser.serialize(parentBlocks),
                        updatedAt = now,
                        syncVersion = parent.syncVersion + 1L
                    )
                )

                // Record link relation so 2D Graph View immediately visualizes parent -> subpage edge
                val linkUuid = UUID.randomUUID().toString()
                conn.prepareStatement(
                    """
                    INSERT INTO note_links (sourceNoteId, targetNoteId, linkText, createdAt, syncUuid, sourceSyncUuid, targetSyncUuid, updatedAt, deletedAt, syncVersion)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1)
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, parent.id)
                    ps.setInt(2, insertedChild.id)
                    ps.setString(3, cleanTitle)
                    ps.setLong(4, now)
                    ps.setString(5, linkUuid)
                    ps.setString(6, parent.syncUuid)
                    ps.setString(7, insertedChild.syncUuid)
                    ps.setLong(8, now)
                    ps.executeUpdate()
                }
                insertedChild
            }
            AppResult.Success(childNote)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalStateException) {
            AppResult.Failure(DataError.Local.NOT_FOUND)
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    private fun syncWikiLinksForNoteLocked(conn: Connection, sourceNote: Note, now: Long) {
        val blocks = BlockParser.parse(sourceNote.description)
        val desiredTargets = mutableListOf<Pair<Int, Pair<String, String>>>() // targetId to (targetUuid, label)

        // Check sub-page blocks first
        for (block in blocks) {
            if (block.type == BlockType.SUB_PAGE && !block.subPageSyncUuid.isNullOrBlank()) {
                val target = findNoteBySyncUuidLocked(conn, block.subPageSyncUuid!!)
                if (target != null && target.deletedAt == null && target.id != sourceNote.id) {
                    desiredTargets += target.id to (target.syncUuid to block.content.ifBlank { target.title ?: "Alt Sayfa" })
                }
            }
        }

        // Check [[Title]] references inside block text
        val allActiveNotes = queryNotes(conn, includeDeleted = false)
        val byTitleLower = allActiveNotes
            .filter { !it.title.isNullOrBlank() && it.id != sourceNote.id }
            .associateBy { it.title!!.trim().lowercase() }

        for (block in blocks) {
            for (match in wikilinkRegex.findAll(block.content + "\n" + block.toggleBody)) {
                val titleRef = match.groupValues[1].trim()
                val matched = byTitleLower[titleRef.lowercase()]
                if (matched != null) {
                    desiredTargets += matched.id to (matched.syncUuid to titleRef)
                }
            }
        }

        // Soft-delete existing links from this sourceNote that are no longer referenced
        val desiredTargetIds = desiredTargets.map { it.first }.toSet()
        conn.prepareStatement(
            "SELECT id, targetNoteId FROM note_links WHERE sourceNoteId = ? AND deletedAt IS NULL"
        ).use { ps ->
            ps.setInt(1, sourceNote.id)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val linkId = rs.getInt(1)
                    val targetId = rs.getInt(2)
                    if (targetId !in desiredTargetIds) {
                        conn.prepareStatement(
                            "UPDATE note_links SET deletedAt = ?, updatedAt = ?, syncVersion = syncVersion + 1 WHERE id = ?"
                        ).use { upd ->
                            upd.setLong(1, now)
                            upd.setLong(2, now)
                            upd.setInt(3, linkId)
                            upd.executeUpdate()
                        }
                    }
                }
            }
        }

        for ((targetId, pair) in desiredTargets.distinctBy { it.first }) {
            val (targetUuid, label) = pair
            val exists = conn.prepareStatement(
                "SELECT id FROM note_links WHERE sourceNoteId = ? AND targetNoteId = ? AND deletedAt IS NULL LIMIT 1"
            ).use { ps ->
                ps.setInt(1, sourceNote.id)
                ps.setInt(2, targetId)
                ps.executeQuery().use { rs -> rs.next() }
            }
            if (!exists) {
                conn.prepareStatement(
                    """
                    INSERT INTO note_links (sourceNoteId, targetNoteId, linkText, createdAt, syncUuid, sourceSyncUuid, targetSyncUuid, updatedAt, deletedAt, syncVersion)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, 1)
                    """.trimIndent()
                ).use { ps ->
                    ps.setInt(1, sourceNote.id)
                    ps.setInt(2, targetId)
                    ps.setString(3, label)
                    ps.setLong(4, now)
                    ps.setString(5, UUID.randomUUID().toString())
                    ps.setString(6, sourceNote.syncUuid)
                    ps.setString(7, targetUuid)
                    ps.setLong(8, now)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun resolveCategoryIdLocked(conn: Connection, requestedId: Int?, requestedUuid: String?): Int {
        if (!requestedUuid.isNullOrBlank()) {
            findCategoryBySyncUuidLocked(conn, requestedUuid)?.let { return it.id }
        }
        if (requestedId != null && requestedId > 0) {
            conn.prepareStatement("SELECT id FROM categories WHERE id = ? LIMIT 1").use { ps ->
                ps.setInt(1, requestedId)
                ps.executeQuery().use { rs -> if (rs.next()) return rs.getInt(1) }
            }
        }
        return conn.createStatement().use { st ->
            st.executeQuery("SELECT id FROM categories WHERE deletedAt IS NULL ORDER BY id ASC LIMIT 1").use { rs ->
                if (rs.next()) rs.getInt(1) else 1
            }
        }
    }

    private fun findCategorySyncUuidByIdLocked(conn: Connection, categoryId: Int): String? {
        return conn.prepareStatement("SELECT syncUuid FROM categories WHERE id = ? LIMIT 1").use { ps ->
            ps.setInt(1, categoryId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    private fun findCategoryByNameLocked(conn: Connection, name: String): Category? {
        return conn.prepareStatement("SELECT * FROM categories WHERE LOWER(name) = LOWER(?) LIMIT 1").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> if (rs.next()) mapCategory(rs) else null }
        }
    }

    private fun findCategoryBySyncUuidLocked(conn: Connection, uuid: String): Category? {
        return conn.prepareStatement("SELECT * FROM categories WHERE syncUuid = ? LIMIT 1").use { ps ->
            ps.setString(1, uuid)
            ps.executeQuery().use { rs -> if (rs.next()) mapCategory(rs) else null }
        }
    }

    private fun findNoteBySyncUuidLocked(conn: Connection, uuid: String): Note? {
        return conn.prepareStatement("SELECT * FROM notes WHERE syncUuid = ? LIMIT 1").use { ps ->
            ps.setString(1, uuid)
            ps.executeQuery().use { rs -> if (rs.next()) mapNote(rs) else null }
        }
    }

    private fun insertNoteLocked(conn: Connection, note: Note): Note {
        val sql = """
            INSERT INTO notes (
                title, description, date, isPassword, images, categoryId, isSound, isPinned, icon,
                isEncrypted, totalTasks, completedTasks, status, reminderTime, reminderType, reminderSound,
                isReminderDone, richBlocksJson, syncUuid, categorySyncUuid, updatedAt, deletedAt, syncVersion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        val id = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            bindNoteParams(ps, note)
            ps.executeUpdate()
            ps.generatedKeys.use { gk -> if (gk.next()) gk.getInt(1) else 0 }
        }
        return note.copy(id = id)
    }

    private fun updateNoteLocked(conn: Connection, note: Note): Note {
        val sql = """
            UPDATE notes SET
                title = ?, description = ?, date = ?, isPassword = ?, images = ?, categoryId = ?, isSound = ?,
                isPinned = ?, icon = ?, isEncrypted = ?, totalTasks = ?, completedTasks = ?, status = ?,
                reminderTime = ?, reminderType = ?, reminderSound = ?, isReminderDone = ?, richBlocksJson = ?,
                syncUuid = ?, categorySyncUuid = ?, updatedAt = ?, deletedAt = ?, syncVersion = ?
            WHERE id = ?
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            bindNoteParams(ps, note)
            ps.setInt(24, note.id)
            ps.executeUpdate()
        }
        return note
    }

    private fun bindNoteParams(ps: PreparedStatement, note: Note) {
        setNullableString(ps, 1, note.title)
        setNullableString(ps, 2, note.description)
        ps.setLong(3, note.date)
        setNullableBool(ps, 4, note.isPassword)
        setNullableString(ps, 5, note.images?.takeIf { it.isNotEmpty() }?.let { listJson.encodeToString(it) })
        setNullableInt(ps, 6, note.categoryId)
        setNullableString(ps, 7, note.sounds?.takeIf { it.isNotEmpty() }?.let { listJson.encodeToString(it) })
        ps.setInt(8, if (note.isPinned) 1 else 0)
        setNullableString(ps, 9, note.icon)
        ps.setInt(10, if (note.isEncrypted) 1 else 0)
        ps.setInt(11, note.totalTasks)
        ps.setInt(12, note.completedTasks)
        ps.setString(13, note.status)
        setNullableLong(ps, 14, note.reminderTime)
        setNullableString(ps, 15, note.reminderType)
        setNullableString(ps, 16, note.reminderSound)
        ps.setInt(17, if (note.isReminderDone) 1 else 0)
        setNullableString(ps, 18, note.richBlocksJson)
        ps.setString(19, note.syncUuid)
        setNullableString(ps, 20, note.categorySyncUuid)
        ps.setLong(21, note.updatedAt)
        setNullableLong(ps, 22, note.deletedAt)
        ps.setLong(23, note.syncVersion)
    }

    private fun queryCategories(conn: Connection, includeDeleted: Boolean): List<Category> {
        val sql = if (includeDeleted) {
            "SELECT * FROM categories ORDER BY id ASC"
        } else {
            "SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY id ASC"
        }
        val list = mutableListOf<Category>()
        conn.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                while (rs.next()) list += mapCategory(rs)
            }
        }
        return list
    }

    private fun queryNotes(conn: Connection, includeDeleted: Boolean): List<Note> {
        val sql = if (includeDeleted) {
            "SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC, id DESC"
        } else {
            "SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC, id DESC"
        }
        val list = mutableListOf<Note>()
        conn.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                while (rs.next()) list += mapNote(rs)
            }
        }
        return list
    }

    private fun queryNoteLinks(conn: Connection, includeDeleted: Boolean): List<NoteLink> {
        val sql = if (includeDeleted) {
            "SELECT * FROM note_links ORDER BY id ASC"
        } else {
            "SELECT * FROM note_links WHERE deletedAt IS NULL ORDER BY id ASC"
        }
        val list = mutableListOf<NoteLink>()
        conn.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                while (rs.next()) list += mapNoteLink(rs)
            }
        }
        return list
    }

    private fun queryStreamItems(conn: Connection): List<StreamItem> {
        val list = mutableListOf<StreamItem>()
        conn.createStatement().use { s ->
            s.executeQuery("SELECT * FROM stream_items WHERE deletedAt IS NULL ORDER BY timestamp DESC, id DESC").use { rs ->
                while (rs.next()) list += mapStreamItem(rs)
            }
        }
        return list
    }

    private fun mapStreamItem(rs: ResultSet): StreamItem = StreamItem(
        id = rs.getInt("id"),
        content = rs.getString("content"),
        type = rs.getString("type") ?: "TEXT",
        audioPath = rs.getString("audioPath"),
        imagePath = rs.getString("imagePath"),
        timestamp = rs.getLong("timestamp"),
        isProcessed = rs.getInt("isProcessed") != 0,
        mergedNoteId = (rs.getObject("mergedNoteId") as? Number)?.toInt(),
        syncUuid = rs.getString("syncUuid"),
        mergedNoteSyncUuid = rs.getString("mergedNoteSyncUuid"),
        updatedAt = rs.getLong("updatedAt"),
        deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
        syncVersion = rs.getLong("syncVersion")
    )

    private fun mapCategory(rs: ResultSet): Category {
        val isPwdObj = rs.getObject("isPassword")
        return Category(
            id = rs.getInt("id"),
            name = rs.getString("name"),
            isPassword = (isPwdObj as? Number)?.let { it.toInt() != 0 },
            syncUuid = rs.getString("syncUuid"),
            updatedAt = rs.getLong("updatedAt"),
            deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
            syncVersion = rs.getLong("syncVersion")
        )
    }

    private fun mapNote(rs: ResultSet): Note {
        return Note(
            id = rs.getInt("id"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            date = rs.getLong("date"),
            isPassword = (rs.getObject("isPassword") as? Number)?.let { it.toInt() != 0 },
            images = decodeStringList(rs.getString("images")),
            categoryId = (rs.getObject("categoryId") as? Number)?.toInt(),
            sounds = decodeStringList(rs.getString("isSound")),
            isPinned = rs.getInt("isPinned") != 0,
            icon = rs.getString("icon"),
            isEncrypted = rs.getInt("isEncrypted") != 0,
            totalTasks = rs.getInt("totalTasks"),
            completedTasks = rs.getInt("completedTasks"),
            status = rs.getString("status") ?: "TODO",
            reminderTime = (rs.getObject("reminderTime") as? Number)?.toLong(),
            reminderType = rs.getString("reminderType"),
            reminderSound = rs.getString("reminderSound"),
            isReminderDone = rs.getInt("isReminderDone") != 0,
            richBlocksJson = rs.getString("richBlocksJson"),
            syncUuid = rs.getString("syncUuid"),
            categorySyncUuid = rs.getString("categorySyncUuid"),
            updatedAt = rs.getLong("updatedAt"),
            deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
            syncVersion = rs.getLong("syncVersion")
        )
    }

    private fun mapNoteLink(rs: ResultSet): NoteLink = NoteLink(
        id = rs.getInt("id"),
        sourceNoteId = rs.getInt("sourceNoteId"),
        targetNoteId = rs.getInt("targetNoteId"),
        linkText = rs.getString("linkText"),
        createdAt = rs.getLong("createdAt"),
        syncUuid = rs.getString("syncUuid"),
        sourceSyncUuid = rs.getString("sourceSyncUuid"),
        targetSyncUuid = rs.getString("targetSyncUuid"),
        updatedAt = rs.getLong("updatedAt"),
        deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
        syncVersion = rs.getLong("syncVersion")
    )

    private fun decodeStringList(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return try {
            listJson.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun setNullableString(ps: PreparedStatement, index: Int, value: String?) {
        if (value == null) ps.setNull(index, Types.VARCHAR) else ps.setString(index, value)
    }

    private fun setNullableInt(ps: PreparedStatement, index: Int, value: Int?) {
        if (value == null) ps.setNull(index, Types.INTEGER) else ps.setInt(index, value)
    }

    private fun setNullableLong(ps: PreparedStatement, index: Int, value: Long?) {
        if (value == null) ps.setNull(index, Types.BIGINT) else ps.setLong(index, value)
    }

    private fun setNullableBool(ps: PreparedStatement, index: Int, value: Boolean?) {
        if (value == null) ps.setNull(index, Types.INTEGER) else ps.setInt(index, if (value) 1 else 0)
    }
}
