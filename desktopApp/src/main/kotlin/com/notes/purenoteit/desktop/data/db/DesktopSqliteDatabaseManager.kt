package com.notes.purenoteit.desktop.data.db

import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteConfig
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Thread-safe SQLite3 Multiple Ciphers (SQLCipher v4 AES-256) JDBC manager maintaining
 * 100% column and index parity with Android Room v6.
 * Automatically migrates any legacy plaintext desktop `.db` file to SQLCipher v4 on first unlock.
 */
internal class DesktopSqliteDatabaseManager(
    private val dbFile: File?,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dbKeyProvider: (() -> ByteArray?)? = null
) : AutoCloseable {

    private val mutex = Mutex()
    private val jdbcUrl: String = if (dbFile == null) {
        "jdbc:sqlite::memory:"
    } else {
        dbFile.parentFile?.mkdirs()
        "jdbc:sqlite:${dbFile.absolutePath}"
    }

    private var connection: Connection? = null
    private var schemaReady = false

    private val _changeVersion = MutableStateFlow(0L)
    val changeVersion: StateFlow<Long> = _changeVersion.asStateFlow()

    suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(ioDispatcher) {
        mutex.withLock {
            val conn = getOrOpenConnectionLocked()
            block(conn)
        }
    }

    suspend fun <T> inTransaction(notifyOnCommit: Boolean = true, block: (Connection) -> T): T =
        withContext(ioDispatcher) {
            mutex.withLock {
                val conn = getOrOpenConnectionLocked()
                conn.autoCommit = false
                try {
                    val result = block(conn)
                    conn.commit()
                    if (notifyOnCommit) {
                        _changeVersion.update { it + 1L }
                    }
                    result
                } catch (e: CancellationException) {
                    runCatchingQuietly { conn.rollback() }
                    throw e
                } catch (t: Throwable) {
                    runCatchingQuietly { conn.rollback() }
                    throw t
                } finally {
                    runCatchingQuietly { conn.autoCommit = true }
                }
            }
        }

    /**
     * Verifies the SQLCipher key opens/initializes the database cleanly and upgrades any
     * legacy unencrypted `.db` file to SQLCipher v4 without losing notes.
     */
    suspend fun ensureInitializedWithCurrentKey() = withContext(ioDispatcher) {
        mutex.withLock {
            getOrOpenConnectionLocked()
        }
    }

    /**
     * Re-encrypts the existing SQLCipher v4 database under [newKeyBytes] by copying into a
     * verified encrypted staging file and atomically swapping it into place.
     */
    suspend fun rekeyDatabase(newKeyBytes: ByteArray) = withContext(ioDispatcher) {
        val targetFile = dbFile ?: return@withContext
        val newKeyCopy = newKeyBytes.copyOf(32)
        mutex.withLock {
            try {
                val sourceConn = getOrOpenConnectionLocked()
                runCatchingQuietly {
                    sourceConn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
                }
                val stagingFile = File(targetFile.parentFile, "${targetFile.name}.rekey.tmp")
                if (stagingFile.exists()) stagingFile.delete()

                val destConn = openEncryptedConnection(
                    jdbcTargetUrl = "jdbc:sqlite:${stagingFile.absolutePath}",
                    keyBytes = newKeyCopy
                )
                try {
                    createV6Schema(destConn)
                    copyAllTables(sourceConn, destConn)
                } finally {
                    runCatchingQuietly { destConn.close() }
                }

                // Swap files while closing old handle cleanly
                closeLocked()
                val backupFile = File(targetFile.parentFile, "${targetFile.name}.rekey.bak")
                if (targetFile.exists()) {
                    Files.move(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(stagingFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                deleteWalSidecars(targetFile)
                runCatchingQuietly { backupFile.delete() }

                val freshConn = openEncryptedConnection(jdbcUrl, newKeyCopy)
                connection = freshConn
                schemaReady = true
                _changeVersion.update { it + 1L }
            } finally {
                newKeyCopy.fill(0)
            }
        }
    }

    suspend fun lockAndClose() = withContext(ioDispatcher) {
        mutex.withLock {
            closeLocked()
        }
    }

    fun notifyChange() {
        _changeVersion.update { it + 1L }
    }

    private fun getOrOpenConnectionLocked(): Connection {
        val current = connection
        if (current != null && !current.isClosed) {
            return current
        }

        val provider = dbKeyProvider
        val opened = if (dbFile != null && provider != null) {
            val rawKey = provider() ?: throw IllegalStateException("Vault is locked")
            val keyCopy = rawKey.copyOf(32)
            try {
                migratePlaintextIfNeededLocked(dbFile, keyCopy)
                openEncryptedConnection(jdbcUrl, keyCopy)
            } finally {
                keyCopy.fill(0)
            }
        } else {
            DriverManager.getConnection(jdbcUrl).also { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA foreign_keys = ON")
                    stmt.execute("PRAGMA journal_mode = WAL")
                    stmt.execute("PRAGMA busy_timeout = 5000")
                }
            }
        }

        connection = opened
        if (!schemaReady) {
            createV6SchemaAndSeedDefaults(opened)
            schemaReady = true
        }
        return opened
    }

    private fun openEncryptedConnection(jdbcTargetUrl: String, keyBytes: ByteArray): Connection {
        val mcConfig = SQLiteMCSqlCipherConfig.getV4Defaults()
            .withHexKey(keyBytes)
            .build()
        mcConfig.enforceForeignKeys(true)
        mcConfig.setJournalMode(SQLiteConfig.JournalMode.WAL)
        mcConfig.setBusyTimeout(5000)
        val conn = mcConfig.createConnection(jdbcTargetUrl)
        // Touch sqlite_master immediately to fail fast on wrong key before running queries
        conn.createStatement().use { st ->
            st.executeQuery("SELECT count(*) FROM sqlite_master").use { rs -> rs.next() }
        }
        return conn
    }

    private fun migratePlaintextIfNeededLocked(file: File, keyBytes: ByteArray) {
        if (!isPlaintextSqliteHeader(file)) return

        val stagingFile = File(file.parentFile, "${file.name}.sqlcipher.tmp")
        if (stagingFile.exists()) stagingFile.delete()

        val plainConn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        try {
            runCatchingQuietly {
                plainConn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
            }
            val encConn = openEncryptedConnection("jdbc:sqlite:${stagingFile.absolutePath}", keyBytes)
            try {
                createV6Schema(encConn)
                copyAllTablesIfPresent(plainConn, encConn)
            } finally {
                runCatchingQuietly { encConn.close() }
            }
        } finally {
            runCatchingQuietly { plainConn.close() }
        }

        val backup = File(file.parentFile, "${file.name}.plain.bak")
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Files.move(stagingFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        deleteWalSidecars(file)
        runCatchingQuietly { backup.delete() }
    }

    private fun deleteWalSidecars(db: File) {
        runCatchingQuietly { File(db.absolutePath + "-wal").delete() }
        runCatchingQuietly { File(db.absolutePath + "-shm").delete() }
    }

    private fun createV6SchemaAndSeedDefaults(conn: Connection) {
        createV6Schema(conn)

        // Seed deterministic default categories aligned with Android DatabaseMigrations.MIGRATION_5_6
        val count = conn.createStatement().use { s ->
            s.executeQuery("SELECT COUNT(*) FROM categories").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
        if (count == 0) {
            val now = clock.nowMillis()
            val defaults = listOf(
                Triple(1, "Not", "cat-default-1"),
                Triple(2, "Günlük", "cat-default-2"),
                Triple(3, "Önemli", "cat-default-3")
            )
            conn.prepareStatement(
                "INSERT INTO categories (id, name, isPassword, syncUuid, updatedAt, deletedAt, syncVersion) VALUES (?, ?, NULL, ?, ?, NULL, 1)"
            ).use { ps ->
                for ((id, name, uuid) in defaults) {
                    ps.setInt(1, id)
                    ps.setString(2, name)
                    ps.setString(3, uuid)
                    ps.setLong(4, now)
                    ps.executeUpdate()
                }
            }
        }
    }

    private fun createV6Schema(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    isPassword INTEGER,
                    syncUuid TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER DEFAULT NULL,
                    syncVersion INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_syncUuid ON categories(syncUuid)")

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT,
                    description TEXT,
                    date INTEGER NOT NULL,
                    isPassword INTEGER,
                    images TEXT,
                    categoryId INTEGER,
                    isSound TEXT,
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    icon TEXT,
                    isEncrypted INTEGER NOT NULL DEFAULT 0,
                    totalTasks INTEGER NOT NULL DEFAULT 0,
                    completedTasks INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'TODO',
                    reminderTime INTEGER,
                    reminderType TEXT,
                    reminderSound TEXT,
                    isReminderDone INTEGER NOT NULL DEFAULT 0,
                    richBlocksJson TEXT,
                    syncUuid TEXT NOT NULL DEFAULT '',
                    categorySyncUuid TEXT DEFAULT NULL,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER DEFAULT NULL,
                    syncVersion INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            stmt.execute("CREATE INDEX IF NOT EXISTS index_notes_categoryId ON notes(categoryId)")
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_syncUuid ON notes(syncUuid)")

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS note_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceNoteId INTEGER NOT NULL,
                    targetNoteId INTEGER NOT NULL,
                    linkText TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    syncUuid TEXT NOT NULL DEFAULT '',
                    sourceSyncUuid TEXT NOT NULL DEFAULT '',
                    targetSyncUuid TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER DEFAULT NULL,
                    syncVersion INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(sourceNoteId) REFERENCES notes(id) ON DELETE CASCADE,
                    FOREIGN KEY(targetNoteId) REFERENCES notes(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            stmt.execute("CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)")
            stmt.execute("CREATE INDEX IF NOT EXISTS index_note_links_targetNoteId ON note_links(targetNoteId)")
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_note_links_syncUuid ON note_links(syncUuid)")

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS stream_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'TEXT',
                    audioPath TEXT,
                    imagePath TEXT,
                    timestamp INTEGER NOT NULL,
                    isProcessed INTEGER NOT NULL DEFAULT 0,
                    mergedNoteId INTEGER,
                    syncUuid TEXT NOT NULL DEFAULT '',
                    mergedNoteSyncUuid TEXT DEFAULT NULL,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER DEFAULT NULL,
                    syncVersion INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_stream_items_syncUuid ON stream_items(syncUuid)")

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS automation_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    triggerType TEXT NOT NULL,
                    triggerConfig TEXT,
                    actionType TEXT NOT NULL,
                    actionConfig TEXT,
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    syncUuid TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    deletedAt INTEGER DEFAULT NULL,
                    syncVersion INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_rules_syncUuid ON automation_rules(syncUuid)")
        }
    }

    private fun copyAllTablesIfPresent(src: Connection, dst: Connection) {
        val existingTables = HashSet<String>()
        src.createStatement().use { s ->
            s.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                while (rs.next()) existingTables += rs.getString(1).lowercase()
            }
        }
        val prevAutoCommit = dst.autoCommit
        dst.autoCommit = false
        try {
            if ("categories" in existingTables) copyTable(src, dst, "categories")
            if ("notes" in existingTables) copyTable(src, dst, "notes")
            if ("note_links" in existingTables) copyTable(src, dst, "note_links")
            if ("stream_items" in existingTables) copyTable(src, dst, "stream_items")
            if ("automation_rules" in existingTables) copyTable(src, dst, "automation_rules")
            dst.commit()
        } finally {
            runCatchingQuietly { dst.autoCommit = prevAutoCommit }
        }
    }

    private fun copyAllTables(src: Connection, dst: Connection) {
        copyAllTablesIfPresent(src, dst)
    }

    private fun copyTable(src: Connection, dst: Connection, tableName: String) {
        src.createStatement().use { st ->
            st.executeQuery("SELECT * FROM $tableName").use { rs ->
                val md = rs.metaData
                val cols = md.columnCount
                val placeholders = (1..cols).joinToString(",") { "?" }
                val colNames = (1..cols).joinToString(",") { md.getColumnName(it) }
                dst.prepareStatement("INSERT OR REPLACE INTO $tableName ($colNames) VALUES ($placeholders)").use { ps ->
                    while (rs.next()) {
                        for (i in 1..cols) {
                            val obj = rs.getObject(i)
                            bindAny(ps, i, obj)
                        }
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
        }
    }

    private fun bindAny(ps: PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> ps.setNull(index, Types.NULL)
            is Int -> ps.setInt(index, value)
            is Long -> ps.setLong(index, value)
            is String -> ps.setString(index, value)
            is Double -> ps.setDouble(index, value)
            is Float -> ps.setFloat(index, value)
            is ByteArray -> ps.setBytes(index, value)
            is Boolean -> ps.setInt(index, if (value) 1 else 0)
            else -> ps.setObject(index, value)
        }
    }

    private fun closeLocked() {
        runCatchingQuietly { connection?.close() }
        connection = null
        schemaReady = false
    }

    private inline fun runCatchingQuietly(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        closeLocked()
    }

    companion object {
        private val SQLITE_PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        fun isPlaintextSqliteHeader(dbFile: File): Boolean {
            if (!dbFile.exists() || !dbFile.isFile || dbFile.length() < SQLITE_PLAINTEXT_HEADER.size) {
                return false
            }
            val header = ByteArray(SQLITE_PLAINTEXT_HEADER.size)
            return try {
                FileInputStream(dbFile).use { fis ->
                    val read = fis.read(header)
                    read == SQLITE_PLAINTEXT_HEADER.size &&
                        MessageDigest.isEqual(header, SQLITE_PLAINTEXT_HEADER)
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
