package com.notes.purenoteit.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.core.content.ContextCompat
import com.notes.purenoteit.util.SecurityUtil
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages hardware-backed Android Keystore wrapping of the 256-bit SQLCipher database key
 * and performs crash-safe, zero-data-loss `sqlcipher_export` migration from legacy plaintext
 * SQLite databases before Room opens the connection.
 */
internal object SqlCipherDatabaseManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DB_MASTER_KEY_ALIAS = "purenoteit_sqlcipher_master_v1"
    private const val WRAPPED_KEY_FILENAME = "purenote_sqlcipher_keyset.bin"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val RAW_DB_KEY_BYTES = 32 // 256-bit random entropy -> 64-byte ASCII hex key

    private val SQLITE_PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    @Volatile
    private var isNativeLoaded = false

    private val lock = Any()

    fun ensureNativeLoaded() {
        if (!isNativeLoaded) {
            synchronized(lock) {
                if (!isNativeLoaded) {
                    System.loadLibrary("sqlcipher")
                    isNativeLoaded = true
                }
            }
        }
    }

    /**
     * Returns true if [dbFile] exists and starts with the unencrypted 16-byte SQLite 3 header
     * (`"SQLite format 3\u0000"`). Encrypted SQLCipher files begin with a 16-byte random salt.
     */
    internal fun isPlaintextSqliteHeader(dbFile: File): Boolean {
        if (!dbFile.exists() || !dbFile.isFile || dbFile.length() < SQLITE_PLAINTEXT_HEADER.size) {
            return false
        }
        val header = ByteArray(SQLITE_PLAINTEXT_HEADER.size)
        val readCount = try {
            FileInputStream(dbFile).use { fis -> fis.read(header) }
        } catch (_: IOException) {
            return false
        }
        if (readCount != SQLITE_PLAINTEXT_HEADER.size) return false
        return header.contentEquals(SQLITE_PLAINTEXT_HEADER)
    }

    /**
     * Prepares the encrypted SQLCipher database (atomically migrating an existing plaintext Room DB
     * via `sqlcipher_export` if detected) and returns a configured [SupportOpenHelperFactory].
     */
    fun createSupportFactory(context: Context, dbName: String): SupportOpenHelperFactory {
        synchronized(lock) {
            ensureNativeLoaded()
            recoverInterruptedSwapIfNeeded(context, dbName)

            val passphraseBytes = getOrCreateDatabasePassphrase(context, dbName)
            val dbFile = context.getDatabasePath(dbName)

            if (isPlaintextSqliteHeader(dbFile)) {
                val migrationKey = passphraseBytes.copyOf()
                try {
                    migratePlaintextToSqlCipher(dbFile, migrationKey)
                } finally {
                    migrationKey.fill(0)
                }
            }

            return SupportOpenHelperFactory(passphraseBytes)
        }
    }

    private fun recoverInterruptedSwapIfNeeded(context: Context, dbName: String) {
        val dbFile = context.getDatabasePath(dbName)
        val parent = dbFile.parentFile ?: return
        val backupPlain = File(parent, "$dbName.plain.bak")
        if (!dbFile.exists() && backupPlain.exists() && backupPlain.length() > 0L) {
            if (!backupPlain.renameTo(dbFile)) {
                backupPlain.copyTo(dbFile, overwrite = true)
                backupPlain.delete()
            }
        }
    }

    private fun migratePlaintextToSqlCipher(dbFile: File, passphraseBytes: ByteArray) {
        val parentDir = dbFile.parentFile ?: throw IOException("Database parent directory missing")
        if (!parentDir.exists()) parentDir.mkdirs()

        val dbName = dbFile.name
        val tempEncFile = File(parentDir, "$dbName.sqlcipher.tmp")
        val tempEncWal = File(parentDir, "$dbName.sqlcipher.tmp-wal")
        val tempEncShm = File(parentDir, "$dbName.sqlcipher.tmp-shm")
        val backupPlainFile = File(parentDir, "$dbName.plain.bak")

        // Clean any leftover temp artifacts from prior aborted attempts
        tempEncFile.delete()
        tempEncWal.delete()
        tempEncShm.delete()

        // 1. Flush WAL journal into main plaintext file & capture exact user_version + row count
        val originalUserVersion: Int
        val expectedNoteCount: Long
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        ).use { plainFwDb ->
            plainFwDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    // Checkpoint completed
                }
            }
            originalUserVersion = plainFwDb.version
            expectedNoteCount = queryNoteCountFramework(plainFwDb)
        }

        try {
            // 2. Open fresh encrypted SQLCipher target DB using identical ByteArray key derivation
            val escapedPlainPath = dbFile.absolutePath.replace("'", "''")
            val encDb = SQLiteDatabase.openOrCreateDatabase(tempEncFile, passphraseBytes, null, null)
            try {
                encDb.rawExecSQL("ATTACH DATABASE '$escapedPlainPath' AS plaintext KEY '';")
                encDb.rawQuery("SELECT sqlcipher_export('main', 'plaintext');", null).use { cursor ->
                    cursor.moveToFirst()
                }
                encDb.rawExecSQL("DETACH DATABASE plaintext;")
                encDb.version = originalUserVersion
            } finally {
                encDb.close()
            }

            // 3. Forensic & data-completeness verification BEFORE touching original file
            if (isPlaintextSqliteHeader(tempEncFile) || tempEncFile.length() <= SQLITE_PLAINTEXT_HEADER.size) {
                throw IOException("SQLCipher export verification failed: output is not encrypted")
            }

            val verifyDb = SQLiteDatabase.openDatabase(
                tempEncFile.absolutePath,
                passphraseBytes,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            )
            try {
                if (verifyDb.version != originalUserVersion) {
                    throw IOException(
                        "SQLCipher version mismatch: expected $originalUserVersion, got ${verifyDb.version}"
                    )
                }
                val exportedNoteCount = queryNoteCountSqlCipher(verifyDb)
                if (exportedNoteCount != expectedNoteCount) {
                    throw IOException(
                        "SQLCipher row count mismatch: expected $expectedNoteCount, got $exportedNoteCount"
                    )
                }
            } finally {
                verifyDb.close()
            }

            // 4. Atomic crash-safe file replacement
            File(parentDir, "$dbName-wal").delete()
            File(parentDir, "$dbName-shm").delete()
            tempEncWal.delete()
            tempEncShm.delete()
            backupPlainFile.delete()

            if (!dbFile.renameTo(backupPlainFile)) {
                dbFile.copyTo(backupPlainFile, overwrite = true)
                dbFile.delete()
            }

            if (!tempEncFile.renameTo(dbFile)) {
                tempEncFile.copyTo(dbFile, overwrite = true)
                tempEncFile.delete()
            }

            if (dbFile.exists() && dbFile.length() > 0L && !isPlaintextSqliteHeader(dbFile)) {
                secureDeleteFile(backupPlainFile)
            } else {
                // Rollback immediately if target file check fails
                if (backupPlainFile.exists()) {
                    backupPlainFile.copyTo(dbFile, overwrite = true)
                    backupPlainFile.delete()
                }
                throw IOException("Atomic swap validation failed; rolled back to original database")
            }
        } catch (e: Exception) {
            tempEncFile.delete()
            tempEncWal.delete()
            tempEncShm.delete()
            throw e
        }
    }

    private fun queryNoteCountFramework(db: android.database.sqlite.SQLiteDatabase): Long {
        val hasNotesTable = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='notes'",
            null
        ).use { it.moveToFirst() }
        if (!hasNotesTable) return 0L
        return db.rawQuery("SELECT COUNT(*) FROM notes", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun queryNoteCountSqlCipher(db: SQLiteDatabase): Long {
        val hasNotesTable = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='notes'",
            null
        ).use { it.moveToFirst() }
        if (!hasNotesTable) return 0L
        return db.rawQuery("SELECT COUNT(*) FROM notes", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun secureDeleteFile(file: File) {
        if (!file.exists()) return
        try {
            val len = file.length().coerceAtMost(1024 * 1024L).toInt()
            if (len > 0) {
                val zeros = ByteArray(4096)
                FileOutputStream(file).use { fos ->
                    var remaining = len
                    while (remaining > 0) {
                        val chunk = minOf(remaining, zeros.size)
                        fos.write(zeros, 0, chunk)
                        remaining -= chunk
                    }
                    fos.fd.sync()
                }
            }
        } catch (_: Exception) {
        } finally {
            file.delete()
        }
    }

    // --- Android Keystore AES-256-GCM Wrapping of 256-Bit SQLCipher Passphrase ---

    private fun getWrappedKeyFile(context: Context): File {
        val dir = ContextCompat.getNoBackupFilesDir(context) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, WRAPPED_KEY_FILENAME)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(DB_MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            DB_MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun deleteMasterKeyQuietly() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(DB_MASTER_KEY_ALIAS)) {
                ks.deleteEntry(DB_MASTER_KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateDatabasePassphrase(context: Context, dbName: String): ByteArray {
        val keyFile = getWrappedKeyFile(context)
        if (keyFile.exists() && keyFile.length() > GCM_IV_LENGTH) {
            try {
                val blob = keyFile.readBytes()
                val iv = blob.copyOfRange(0, GCM_IV_LENGTH)
                val cipherText = blob.copyOfRange(GCM_IV_LENGTH, blob.size)
                return try {
                    val masterKey = getOrCreateMasterKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.doFinal(cipherText)
                } finally {
                    blob.fill(0)
                    iv.fill(0)
                    cipherText.fill(0)
                }
            } catch (_: KeyPermanentlyInvalidatedException) {
                return resetInvalidatedKeystoreAndDatabase(context, dbName, keyFile)
            } catch (_: GeneralSecurityException) {
                return resetInvalidatedKeystoreAndDatabase(context, dbName, keyFile)
            }
        }

        return generateAndPersistFreshPassphrase(keyFile)
    }

    /**
     * Per `android_native_rules.md` (Güvenli Depolama › Hata Davranışı):
     * If the Android Keystore key is invalidated or decryption of the keyset fails, never fall back
     * to plaintext and never crash: lock the Vault session, wipe the undecryptable keyset/encrypted DB,
     * and provision a fresh hardware-backed key.
     */
    private fun resetInvalidatedKeystoreAndDatabase(
        context: Context,
        dbName: String,
        keyFile: File
    ): ByteArray {
        SecurityUtil.lockVault()
        keyFile.delete()
        deleteMasterKeyQuietly()

        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists() && !isPlaintextSqliteHeader(dbFile)) {
            val parent = dbFile.parentFile
            dbFile.delete()
            parent?.let {
                File(it, "$dbName-wal").delete()
                File(it, "$dbName-shm").delete()
            }
        }
        return generateAndPersistFreshPassphrase(keyFile)
    }

    private fun generateAndPersistFreshPassphrase(keyFile: File): ByteArray {
        val rawEntropy = ByteArray(RAW_DB_KEY_BYTES)
        SecureRandom().nextBytes(rawEntropy)
        val hexKeyBytes = ByteArray(RAW_DB_KEY_BYTES * 2)
        for (i in rawEntropy.indices) {
            val v = rawEntropy[i].toInt() and 0xFF
            hexKeyBytes[i * 2] = HEX_CHARS[v ushr 4].code.toByte()
            hexKeyBytes[i * 2 + 1] = HEX_CHARS[v and 0x0F].code.toByte()
        }
        rawEntropy.fill(0)

        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encryptedKey = cipher.doFinal(hexKeyBytes)

        val outBlob = ByteArray(iv.size + encryptedKey.size)
        try {
            System.arraycopy(iv, 0, outBlob, 0, iv.size)
            System.arraycopy(encryptedKey, 0, outBlob, iv.size, encryptedKey.size)
            FileOutputStream(keyFile).use { fos ->
                fos.write(outBlob)
                fos.fd.sync()
            }
        } finally {
            iv.fill(0)
            encryptedKey.fill(0)
            outBlob.fill(0)
        }
        return hexKeyBytes
    }
}
