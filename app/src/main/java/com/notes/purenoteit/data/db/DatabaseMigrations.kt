package com.notes.purenoteit.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Room schema migrations from v5 (single-device) to v6 (UUID + HLC timestamp + Tombstone sync-ready).
 */
internal object DatabaseMigrations {

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()

            // 1. Add sync & tombstone columns with SQLite-safe constant defaults (API 24+ compatible)
            db.execSQL("ALTER TABLE categories ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE categories ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE categories ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

            db.execSQL("ALTER TABLE notes ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE notes ADD COLUMN categorySyncUuid TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

            db.execSQL("ALTER TABLE note_links ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE note_links ADD COLUMN sourceSyncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE note_links ADD COLUMN targetSyncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE note_links ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE note_links ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE note_links ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

            db.execSQL("ALTER TABLE stream_items ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE stream_items ADD COLUMN mergedNoteSyncUuid TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE stream_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE stream_items ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE stream_items ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

            db.execSQL("ALTER TABLE automation_rules ADD COLUMN syncUuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE automation_rules ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE automation_rules ADD COLUMN deletedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE automation_rules ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")

            // 2. Backfill deterministic default IDs & unique UUIDs per row before creating UNIQUE indices
            val categoryUuidMap = backfillCategories(db, now)
            val noteUuidMap = backfillNotes(db, categoryUuidMap, now)
            backfillNoteLinks(db, noteUuidMap, now)
            backfillStreamItems(db, noteUuidMap, now)
            backfillAutomationRules(db, now)

            // 3. Create UNIQUE indices matching @Entity(indices = [...]) Room expectations
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_syncUuid ON categories(syncUuid)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_syncUuid ON notes(syncUuid)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_links_syncUuid ON note_links(syncUuid)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stream_items_syncUuid ON stream_items(syncUuid)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_rules_syncUuid ON automation_rules(syncUuid)")
        }
    }

    private fun backfillCategories(db: SupportSQLiteDatabase, now: Long): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        val usedUuids = HashSet<String>()
        db.query("SELECT id, name FROM categories").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val name = if (cursor.isNull(1)) "" else cursor.getString(1)
                val preferred = when {
                    id == 1 || name == "Not" -> "cat-default-1"
                    id == 2 || name == "Günlük" -> "cat-default-2"
                    id == 3 || name == "Önemli" -> "cat-default-3"
                    else -> UUID.randomUUID().toString()
                }
                val uuid = if (usedUuids.add(preferred)) preferred else UUID.randomUUID().toString().also {
                    usedUuids.add(it)
                }
                result[id] = uuid
                db.execSQL(
                    "UPDATE categories SET syncUuid = ?, updatedAt = ?, syncVersion = 1 WHERE id = ?",
                    arrayOf<Any>(uuid, now, id)
                )
            }
        }
        return result
    }

    private fun backfillNotes(
        db: SupportSQLiteDatabase,
        categoryUuidMap: Map<Int, String>,
        now: Long
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        db.query("SELECT id, categoryId, date FROM notes").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val categoryId = if (cursor.isNull(1)) null else cursor.getInt(1)
                val date = if (cursor.isNull(2)) now else cursor.getLong(2)
                val uuid = UUID.randomUUID().toString()
                val categorySyncUuid = categoryId?.let { categoryUuidMap[it] }
                val updatedAt = if (date > 0L) date else now
                result[id] = uuid
                db.execSQL(
                    "UPDATE notes SET syncUuid = ?, categorySyncUuid = ?, updatedAt = ?, syncVersion = 1 WHERE id = ?",
                    arrayOf<Any?>(uuid, categorySyncUuid, updatedAt, id)
                )
            }
        }
        return result
    }

    private fun backfillNoteLinks(
        db: SupportSQLiteDatabase,
        noteUuidMap: Map<Int, String>,
        now: Long
    ) {
        db.query("SELECT id, sourceNoteId, targetNoteId, createdAt FROM note_links").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val sourceId = cursor.getInt(1)
                val targetId = cursor.getInt(2)
                val createdAt = if (cursor.isNull(3)) now else cursor.getLong(3)
                val uuid = UUID.randomUUID().toString()
                val sourceSyncUuid = noteUuidMap[sourceId] ?: ""
                val targetSyncUuid = noteUuidMap[targetId] ?: ""
                val updatedAt = if (createdAt > 0L) createdAt else now
                db.execSQL(
                    """
                    UPDATE note_links
                    SET syncUuid = ?, sourceSyncUuid = ?, targetSyncUuid = ?, updatedAt = ?, syncVersion = 1
                    WHERE id = ?
                    """.trimIndent(),
                    arrayOf<Any>(uuid, sourceSyncUuid, targetSyncUuid, updatedAt, id)
                )
            }
        }
    }

    private fun backfillStreamItems(
        db: SupportSQLiteDatabase,
        noteUuidMap: Map<Int, String>,
        now: Long
    ) {
        db.query("SELECT id, mergedNoteId, timestamp FROM stream_items").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val mergedNoteId = if (cursor.isNull(1)) null else cursor.getInt(1)
                val timestamp = if (cursor.isNull(2)) now else cursor.getLong(2)
                val uuid = UUID.randomUUID().toString()
                val mergedNoteSyncUuid = mergedNoteId?.let { noteUuidMap[it] }
                val updatedAt = if (timestamp > 0L) timestamp else now
                db.execSQL(
                    "UPDATE stream_items SET syncUuid = ?, mergedNoteSyncUuid = ?, updatedAt = ?, syncVersion = 1 WHERE id = ?",
                    arrayOf<Any?>(uuid, mergedNoteSyncUuid, updatedAt, id)
                )
            }
        }
    }

    private fun backfillAutomationRules(db: SupportSQLiteDatabase, now: Long) {
        db.query("SELECT id, createdAt FROM automation_rules").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val createdAt = if (cursor.isNull(1)) now else cursor.getLong(1)
                val uuid = when (id) {
                    1 -> "rule-default-1"
                    2 -> "rule-default-2"
                    else -> UUID.randomUUID().toString()
                }
                val updatedAt = if (createdAt > 0L) createdAt else now
                db.execSQL(
                    "UPDATE automation_rules SET syncUuid = ?, updatedAt = ?, syncVersion = 1 WHERE id = ?",
                    arrayOf<Any>(uuid, updatedAt, id)
                )
            }
        }
    }
}
