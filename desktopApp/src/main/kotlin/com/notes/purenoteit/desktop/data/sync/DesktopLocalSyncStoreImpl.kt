package com.notes.purenoteit.desktop.data.sync

import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.UUID

/**
 * Desktop SQLite + Filesystem implementation of `:shared`'s [LocalSyncStore].
 * Commits resolved remote deltas inside a single JDBC transaction in Parent->Child order
 * (`Category` -> `Note` -> subpage UUID remap -> `NoteLink` -> `StreamItem` -> `AutomationRule`).
 */
internal class DesktopLocalSyncStoreImpl(
    private val db: DesktopSqliteDatabaseManager,
    private val mediaPathResolver: MediaPathResolver,
    private val syncCursorFile: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocalSyncStore {

    private val cursorMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun readModifiedSince(sinceMillis: Long): AppResult<LocalSyncSnapshot, DataError.Local> {
        return try {
            val snapshot = db.withConnection { conn ->
                LocalSyncSnapshot(
                    categories = queryCategoriesModifiedSince(conn, sinceMillis),
                    notes = queryNotesModifiedSince(conn, sinceMillis),
                    noteLinks = queryLinksModifiedSince(conn, sinceMillis),
                    streamItems = queryStreamItemsModifiedSince(conn, sinceMillis),
                    automationRules = queryRulesModifiedSince(conn, sinceMillis)
                )
            }
            AppResult.Success(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun applyResolvedRemoteBatch(batch: ResolvedRemoteBatch): AppResult<Unit, DataError.Local> {
        return try {
            db.inTransaction(notifyOnCommit = true) { conn ->
                // 1. Categories first
                val categoryIdByUuid = mutableMapOf<String, Int>()
                queryCategoriesModifiedSince(conn, Long.MIN_VALUE).forEach { cat ->
                    categoryIdByUuid[cat.syncUuid] = cat.id
                }
                for (cat in batch.categories) {
                    val existingId = categoryIdByUuid[cat.syncUuid]
                    if (existingId != null) {
                        conn.prepareStatement(
                            "UPDATE categories SET name = ?, isPassword = ?, updatedAt = ?, deletedAt = ?, syncVersion = ? WHERE id = ?"
                        ).use { ps ->
                            ps.setString(1, cat.name)
                            setNullableBool(ps, 2, cat.isPassword)
                            ps.setLong(3, cat.updatedAt)
                            setNullableLong(ps, 4, cat.deletedAt)
                            ps.setLong(5, cat.syncVersion)
                            ps.setInt(6, existingId)
                            ps.executeUpdate()
                        }
                    } else {
                        val newId = conn.prepareStatement(
                            "INSERT INTO categories (name, isPassword, syncUuid, updatedAt, deletedAt, syncVersion) VALUES (?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS
                        ).use { ps ->
                            ps.setString(1, cat.name)
                            setNullableBool(ps, 2, cat.isPassword)
                            ps.setString(3, cat.syncUuid)
                            ps.setLong(4, cat.updatedAt)
                            setNullableLong(ps, 5, cat.deletedAt)
                            ps.setLong(6, cat.syncVersion)
                            ps.executeUpdate()
                            ps.generatedKeys.use { gk -> if (gk.next()) gk.getInt(1) else 0 }
                        }
                        if (newId > 0) categoryIdByUuid[cat.syncUuid] = newId
                    }
                }

                val fallbackCategoryId = conn.createStatement().use { s ->
                    s.executeQuery("SELECT id FROM categories WHERE deletedAt IS NULL ORDER BY id ASC LIMIT 1").use { rs ->
                        if (rs.next()) rs.getInt(1) else 1
                    }
                }

                // 2. Notes
                val noteIdByUuid = mutableMapOf<String, Int>()
                queryNotesModifiedSince(conn, Long.MIN_VALUE).forEach { n ->
                    noteIdByUuid[n.syncUuid] = n.id
                }
                val touchedNotes = mutableListOf<Note>()
                for (note in batch.notes) {
                    val existingId = noteIdByUuid[note.syncUuid]
                    val resolvedCategoryId = note.categorySyncUuid?.let { categoryIdByUuid[it] } ?: fallbackCategoryId
                    val ready = note.copy(
                        id = existingId ?: 0,
                        categoryId = resolvedCategoryId
                    )
                    if (existingId != null) {
                        updateNoteFull(conn, ready)
                        noteIdByUuid[ready.syncUuid] = existingId
                        touchedNotes += ready
                    } else {
                        val insertedId = insertNoteFull(conn, ready)
                        if (insertedId > 0) {
                            noteIdByUuid[ready.syncUuid] = insertedId
                            touchedNotes += ready.copy(id = insertedId)
                        }
                    }
                }

                // 2b. Remap local Int subpage/parent pointers inside blocks via cross-device UUIDs
                for (n in touchedNotes) {
                    if (n.deletedAt != null) continue
                    val remapped = remapSubPageLocalIds(n, noteIdByUuid)
                    if (remapped != n) {
                        updateNoteFull(conn, remapped)
                    }
                }

                // 3. NoteLinks
                val existingLinkIds = queryLinksModifiedSince(conn, Long.MIN_VALUE).associate { it.syncUuid to it.id }
                for (link in batch.noteLinks) {
                    val sourceId = noteIdByUuid[link.sourceSyncUuid] ?: continue
                    val targetId = noteIdByUuid[link.targetSyncUuid] ?: continue
                    val existingId = existingLinkIds[link.syncUuid]
                    if (existingId != null) {
                        conn.prepareStatement(
                            """
                            UPDATE note_links SET sourceNoteId = ?, targetNoteId = ?, linkText = ?, createdAt = ?,
                            sourceSyncUuid = ?, targetSyncUuid = ?, updatedAt = ?, deletedAt = ?, syncVersion = ? WHERE id = ?
                            """.trimIndent()
                        ).use { ps ->
                            ps.setInt(1, sourceId)
                            ps.setInt(2, targetId)
                            ps.setString(3, link.linkText)
                            ps.setLong(4, link.createdAt)
                            ps.setString(5, link.sourceSyncUuid)
                            ps.setString(6, link.targetSyncUuid)
                            ps.setLong(7, link.updatedAt)
                            setNullableLong(ps, 8, link.deletedAt)
                            ps.setLong(9, link.syncVersion)
                            ps.setInt(10, existingId)
                            ps.executeUpdate()
                        }
                    } else {
                        conn.prepareStatement(
                            """
                            INSERT INTO note_links (sourceNoteId, targetNoteId, linkText, createdAt, syncUuid, sourceSyncUuid, targetSyncUuid, updatedAt, deletedAt, syncVersion)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { ps ->
                            ps.setInt(1, sourceId)
                            ps.setInt(2, targetId)
                            ps.setString(3, link.linkText)
                            ps.setLong(4, link.createdAt)
                            ps.setString(5, link.syncUuid)
                            ps.setString(6, link.sourceSyncUuid)
                            ps.setString(7, link.targetSyncUuid)
                            ps.setLong(8, link.updatedAt)
                            setNullableLong(ps, 9, link.deletedAt)
                            ps.setLong(10, link.syncVersion)
                            ps.executeUpdate()
                        }
                    }
                }

                // 4. StreamItems & AutomationRules
                val existingStreams = queryStreamItemsModifiedSince(conn, Long.MIN_VALUE).associate { it.syncUuid to it.id }
                for (item in batch.streamItems) {
                    val existingId = existingStreams[item.syncUuid]
                    val mergedId = item.mergedNoteSyncUuid?.let { noteIdByUuid[it] }
                    if (existingId != null) {
                        conn.prepareStatement(
                            """
                            UPDATE stream_items SET content = ?, type = ?, audioPath = ?, imagePath = ?, timestamp = ?,
                            isProcessed = ?, mergedNoteId = ?, mergedNoteSyncUuid = ?, updatedAt = ?, deletedAt = ?, syncVersion = ? WHERE id = ?
                            """.trimIndent()
                        ).use { ps ->
                            ps.setString(1, item.content)
                            ps.setString(2, item.type)
                            setNullableString(ps, 3, item.audioPath)
                            setNullableString(ps, 4, item.imagePath)
                            ps.setLong(5, item.timestamp)
                            ps.setInt(6, if (item.isProcessed) 1 else 0)
                            setNullableInt(ps, 7, mergedId)
                            setNullableString(ps, 8, item.mergedNoteSyncUuid)
                            ps.setLong(9, item.updatedAt)
                            setNullableLong(ps, 10, item.deletedAt)
                            ps.setLong(11, item.syncVersion)
                            ps.setInt(12, existingId)
                            ps.executeUpdate()
                        }
                    } else {
                        conn.prepareStatement(
                            """
                            INSERT INTO stream_items (content, type, audioPath, imagePath, timestamp, isProcessed, mergedNoteId, syncUuid, mergedNoteSyncUuid, updatedAt, deletedAt, syncVersion)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { ps ->
                            ps.setString(1, item.content)
                            ps.setString(2, item.type)
                            setNullableString(ps, 3, item.audioPath)
                            setNullableString(ps, 4, item.imagePath)
                            ps.setLong(5, item.timestamp)
                            ps.setInt(6, if (item.isProcessed) 1 else 0)
                            setNullableInt(ps, 7, mergedId)
                            ps.setString(8, item.syncUuid)
                            setNullableString(ps, 9, item.mergedNoteSyncUuid)
                            ps.setLong(10, item.updatedAt)
                            setNullableLong(ps, 11, item.deletedAt)
                            ps.setLong(12, item.syncVersion)
                            ps.executeUpdate()
                        }
                    }
                }

                val existingRules = queryRulesModifiedSince(conn, Long.MIN_VALUE).associate { it.syncUuid to it.id }
                for (rule in batch.automationRules) {
                    val existingId = existingRules[rule.syncUuid]
                    if (existingId != null) {
                        conn.prepareStatement(
                            """
                            UPDATE automation_rules SET title = ?, triggerType = ?, triggerConfig = ?, actionType = ?,
                            actionConfig = ?, isEnabled = ?, createdAt = ?, updatedAt = ?, deletedAt = ?, syncVersion = ? WHERE id = ?
                            """.trimIndent()
                        ).use { ps ->
                            ps.setString(1, rule.title)
                            ps.setString(2, rule.triggerType)
                            setNullableString(ps, 3, rule.triggerConfig)
                            ps.setString(4, rule.actionType)
                            setNullableString(ps, 5, rule.actionConfig)
                            ps.setInt(6, if (rule.isEnabled) 1 else 0)
                            ps.setLong(7, rule.createdAt)
                            ps.setLong(8, rule.updatedAt)
                            setNullableLong(ps, 9, rule.deletedAt)
                            ps.setLong(10, rule.syncVersion)
                            ps.setInt(11, existingId)
                            ps.executeUpdate()
                        }
                    } else {
                        conn.prepareStatement(
                            """
                            INSERT INTO automation_rules (title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt, syncUuid, updatedAt, deletedAt, syncVersion)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        ).use { ps ->
                            ps.setString(1, rule.title)
                            ps.setString(2, rule.triggerType)
                            setNullableString(ps, 3, rule.triggerConfig)
                            ps.setString(4, rule.actionType)
                            setNullableString(ps, 5, rule.actionConfig)
                            ps.setInt(6, if (rule.isEnabled) 1 else 0)
                            ps.setLong(7, rule.createdAt)
                            ps.setString(8, rule.syncUuid)
                            ps.setLong(9, rule.updatedAt)
                            setNullableLong(ps, 10, rule.deletedAt)
                            ps.setLong(11, rule.syncVersion)
                            ps.executeUpdate()
                        }
                    }
                }
            }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun findCategoriesBySyncUuids(syncUuids: Collection<String>): Map<String, Category> {
        if (syncUuids.isEmpty()) return emptyMap()
        val set = syncUuids.toSet()
        return db.withConnection { conn ->
            queryCategoriesModifiedSince(conn, Long.MIN_VALUE)
                .filter { it.syncUuid in set }
                .associateBy { it.syncUuid }
        }
    }

    override suspend fun findNotesBySyncUuids(syncUuids: Collection<String>): Map<String, Note> {
        if (syncUuids.isEmpty()) return emptyMap()
        val set = syncUuids.toSet()
        return db.withConnection { conn ->
            queryNotesModifiedSince(conn, Long.MIN_VALUE)
                .filter { it.syncUuid in set }
                .associateBy { it.syncUuid }
        }
    }

    override suspend fun findLinksBySyncUuids(syncUuids: Collection<String>): Map<String, NoteLink> {
        if (syncUuids.isEmpty()) return emptyMap()
        val set = syncUuids.toSet()
        return db.withConnection { conn ->
            queryLinksModifiedSince(conn, Long.MIN_VALUE)
                .filter { it.syncUuid in set }
                .associateBy { it.syncUuid }
        }
    }

    override suspend fun findStreamItemsBySyncUuids(syncUuids: Collection<String>): Map<String, StreamItem> {
        if (syncUuids.isEmpty()) return emptyMap()
        val set = syncUuids.toSet()
        return db.withConnection { conn ->
            queryStreamItemsModifiedSince(conn, Long.MIN_VALUE)
                .filter { it.syncUuid in set }
                .associateBy { it.syncUuid }
        }
    }

    override suspend fun findRulesBySyncUuids(syncUuids: Collection<String>): Map<String, AutomationRule> {
        if (syncUuids.isEmpty()) return emptyMap()
        val set = syncUuids.toSet()
        return db.withConnection { conn ->
            queryRulesModifiedSince(conn, Long.MIN_VALUE)
                .filter { it.syncUuid in set }
                .associateBy { it.syncUuid }
        }
    }

    override suspend fun hasLocalMedia(mediaPath: String): Boolean = withContext(ioDispatcher) {
        val resolved = mediaPathResolver.resolveToReadablePath(mediaPath) ?: return@withContext false
        val file = File(resolved)
        file.exists() && file.isFile
    }

    override suspend fun readLocalMediaBytes(mediaPath: String): ByteArray? = withContext(ioDispatcher) {
        if (mediaPathResolver is DesktopEncryptedMediaStorage) {
            return@withContext mediaPathResolver.readDecryptedBytes(mediaPath)
        }
        val resolved = mediaPathResolver.resolveToReadablePath(mediaPath) ?: return@withContext null
        try {
            File(resolved).takeIf { it.isFile }?.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeLocalMediaBytes(mediaPath: String, bytes: ByteArray): AppResult<Unit, DataError.Local> =
        withContext(ioDispatcher) {
            try {
                if (mediaPathResolver.isCanonicalRelativePath(mediaPath)) {
                    val withoutPrefix = mediaPath.removePrefix("media/")
                    val subFolder = withoutPrefix.substringBefore('/', "images")
                    val ext = withoutPrefix.substringAfterLast('.', "bin")
                    mediaPathResolver.storeMediaBytes(bytes, subFolder, ext)
                    AppResult.Success(Unit)
                } else {
                    AppResult.Failure(DataError.Local.VALIDATION_FAILED)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(DataError.Local.DISK_FULL)
            }
        }

    override suspend fun getSyncCursorState(): SyncCursorState = withContext(ioDispatcher) {
        cursorMutex.withLock {
            if (!syncCursorFile.exists()) {
                val fresh = SyncCursorState(deviceId = UUID.randomUUID().toString())
                persistCursorLocked(fresh)
                return@withLock fresh
            }
            try {
                val decoded = json.decodeFromString<SyncCursorState>(syncCursorFile.readText(Charsets.UTF_8))
                if (decoded.deviceId.isBlank()) {
                    decoded.copy(deviceId = UUID.randomUUID().toString()).also { persistCursorLocked(it) }
                } else {
                    decoded
                }
            } catch (_: Exception) {
                SyncCursorState(deviceId = UUID.randomUUID().toString()).also { persistCursorLocked(it) }
            }
        }
    }

    override suspend fun saveSyncCursorState(state: SyncCursorState) = withContext(ioDispatcher) {
        cursorMutex.withLock {
            persistCursorLocked(state)
        }
    }

    private fun persistCursorLocked(state: SyncCursorState) {
        try {
            syncCursorFile.parentFile?.mkdirs()
            syncCursorFile.writeText(json.encodeToString(state), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun remapSubPageLocalIds(note: Note, noteIdByUuid: Map<String, Int>): Note {
        var changed = false
        var updatedDesc = note.description
        val blocks = BlockParser.parse(note.description)
        if (blocks.any { it.type == BlockType.SUB_PAGE && !it.subPageSyncUuid.isNullOrBlank() }) {
            val remapped = blocks.map { b ->
                val uuid = b.subPageSyncUuid
                val localId = if (uuid != null) noteIdByUuid[uuid] else null
                if (b.type == BlockType.SUB_PAGE && localId != null && b.subPageNoteId != localId) {
                    changed = true
                    b.copy(subPageNoteId = localId)
                } else {
                    b
                }
            }
            if (changed) {
                updatedDesc = BlockParser.serialize(remapped)
            }
        }

        var updatedRich = note.richBlocksJson
        val richMeta = BlockParser.parseRichMetadata(note.richBlocksJson)
        if (richMeta != null && richMeta.isSubPage && !richMeta.parentSyncUuid.isNullOrBlank()) {
            val resolvedParentId = noteIdByUuid[richMeta.parentSyncUuid]
            if (resolvedParentId != null && richMeta.parentNoteId != resolvedParentId) {
                updatedRich = BlockParser.buildSubPageMetadataJson(resolvedParentId, richMeta.parentSyncUuid)
                changed = true
            }
        }
        return if (changed) note.copy(description = updatedDesc, richBlocksJson = updatedRich) else note
    }

    private fun insertNoteFull(conn: Connection, note: Note): Int {
        val sql = """
            INSERT INTO notes (
                title, description, date, isPassword, images, categoryId, isSound, isPinned, icon,
                isEncrypted, totalTasks, completedTasks, status, reminderTime, reminderType, reminderSound,
                isReminderDone, richBlocksJson, syncUuid, categorySyncUuid, updatedAt, deletedAt, syncVersion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        return conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            bindNote(ps, note)
            ps.executeUpdate()
            ps.generatedKeys.use { gk -> if (gk.next()) gk.getInt(1) else 0 }
        }
    }

    private fun updateNoteFull(conn: Connection, note: Note) {
        val sql = """
            UPDATE notes SET
                title = ?, description = ?, date = ?, isPassword = ?, images = ?, categoryId = ?, isSound = ?,
                isPinned = ?, icon = ?, isEncrypted = ?, totalTasks = ?, completedTasks = ?, status = ?,
                reminderTime = ?, reminderType = ?, reminderSound = ?, isReminderDone = ?, richBlocksJson = ?,
                syncUuid = ?, categorySyncUuid = ?, updatedAt = ?, deletedAt = ?, syncVersion = ?
            WHERE id = ?
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            bindNote(ps, note)
            ps.setInt(24, note.id)
            ps.executeUpdate()
        }
    }

    private fun bindNote(ps: PreparedStatement, note: Note) {
        setNullableString(ps, 1, note.title)
        setNullableString(ps, 2, note.description)
        ps.setLong(3, note.date)
        setNullableBool(ps, 4, note.isPassword)
        setNullableString(ps, 5, note.images?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) })
        setNullableInt(ps, 6, note.categoryId)
        setNullableString(ps, 7, note.sounds?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) })
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

    private fun queryCategoriesModifiedSince(conn: Connection, sinceMillis: Long): List<Category> {
        val list = mutableListOf<Category>()
        conn.prepareStatement("SELECT * FROM categories WHERE updatedAt >= ?").use { ps ->
            ps.setLong(1, sinceMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += Category(
                        id = rs.getInt("id"),
                        name = rs.getString("name"),
                        isPassword = (rs.getObject("isPassword") as? Number)?.let { it.toInt() != 0 },
                        syncUuid = rs.getString("syncUuid"),
                        updatedAt = rs.getLong("updatedAt"),
                        deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
                        syncVersion = rs.getLong("syncVersion")
                    )
                }
            }
        }
        return list
    }

    private fun queryNotesModifiedSince(conn: Connection, sinceMillis: Long): List<Note> {
        val list = mutableListOf<Note>()
        conn.prepareStatement("SELECT * FROM notes WHERE updatedAt >= ?").use { ps ->
            ps.setLong(1, sinceMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += Note(
                        id = rs.getInt("id"),
                        title = rs.getString("title"),
                        description = rs.getString("description"),
                        date = rs.getLong("date"),
                        isPassword = (rs.getObject("isPassword") as? Number)?.let { it.toInt() != 0 },
                        images = decodeList(rs.getString("images")),
                        categoryId = (rs.getObject("categoryId") as? Number)?.toInt(),
                        sounds = decodeList(rs.getString("isSound")),
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
            }
        }
        return list
    }

    private fun queryLinksModifiedSince(conn: Connection, sinceMillis: Long): List<NoteLink> {
        val list = mutableListOf<NoteLink>()
        conn.prepareStatement("SELECT * FROM note_links WHERE updatedAt >= ?").use { ps ->
            ps.setLong(1, sinceMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += NoteLink(
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
                }
            }
        }
        return list
    }

    private fun queryStreamItemsModifiedSince(conn: Connection, sinceMillis: Long): List<StreamItem> {
        val list = mutableListOf<StreamItem>()
        conn.prepareStatement("SELECT * FROM stream_items WHERE updatedAt >= ?").use { ps ->
            ps.setLong(1, sinceMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += StreamItem(
                        id = rs.getInt("id"),
                        content = rs.getString("content"),
                        type = rs.getString("type"),
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
                }
            }
        }
        return list
    }

    private fun queryRulesModifiedSince(conn: Connection, sinceMillis: Long): List<AutomationRule> {
        val list = mutableListOf<AutomationRule>()
        conn.prepareStatement("SELECT * FROM automation_rules WHERE updatedAt >= ?").use { ps ->
            ps.setLong(1, sinceMillis)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list += AutomationRule(
                        id = rs.getInt("id"),
                        title = rs.getString("title"),
                        triggerType = rs.getString("triggerType"),
                        triggerConfig = rs.getString("triggerConfig"),
                        actionType = rs.getString("actionType"),
                        actionConfig = rs.getString("actionConfig"),
                        isEnabled = rs.getInt("isEnabled") != 0,
                        createdAt = rs.getLong("createdAt"),
                        syncUuid = rs.getString("syncUuid"),
                        updatedAt = rs.getLong("updatedAt"),
                        deletedAt = (rs.getObject("deletedAt") as? Number)?.toLong(),
                        syncVersion = rs.getLong("syncVersion")
                    )
                }
            }
        }
        return list
    }

    private fun decodeList(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<List<String>>(raw)
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
