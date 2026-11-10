package com.notes.purenoteit.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepositoryImpl
import com.notes.purenoteit.desktop.data.security.DesktopVaultSecurityManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.ui.PureNoteItDesktopTheme
import com.notes.purenoteit.desktop.ui.components.DesktopVaultLockScreen
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.DesktopOsKeyringVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException

class DesktopVaultSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = AppClock { 1_760_300_000_000L }

    @Test
    fun `SQLCipher v4 dosya basligi duz metin olmamali ve anahtarsiz acilis basarisiz olmalidir`() =
        runBlocking {
            val root = tempFolder.newFolder("sqlcipher_disk")
            val dbFile = File(root, "vault.db")
            val keyring = InMemoryKeyringVault()

            lateinit var secMgr: DesktopVaultSecurityManager
            val db = DesktopSqliteDatabaseManager(
                dbFile = dbFile,
                clock = clock,
                ioDispatcher = Dispatchers.IO,
                dbKeyProvider = { secMgr.currentDbKeyCopy() }
            )
            val rawMedia = SharedStorageFactory.createMediaPathResolver(File(root, "files"), File(root, "cache"))
            val encMedia = DesktopEncryptedMediaStorage(
                delegate = rawMedia,
                mediaKeyProvider = { secMgr.currentMediaKeyCopy() },
                rootFilesDir = File(root, "files")
            )
            secMgr = DesktopVaultSecurityManager(
                securityMetadataFile = File(root, "vault_security.json"),
                keyringVault = keyring,
                dbManagerProvider = { db },
                encryptedMediaStorageProvider = { encMedia }
            )

            try {
                val boot = secMgr.initializeOnStartup() as AppResult.Success
                assertFalse(boot.data.isLocked)

                val repo = DesktopNoteRepositoryImpl(db, clock)
                val inserted = repo.upsertNote(Note(title = "Çok Gizli PC Notu", description = "Şifreli İçerik"))
                assertTrue(inserted is AppResult.Success)

                // Store media file and verify disk bytes carry encrypted PDM1 header (NOT plaintext PNG)
                val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 99, 88)
                val relPath = encMedia.storeMediaBytes(fakePng, "drawings", "png")
                val diskFile = File(encMedia.resolveToReadablePath(relPath)!!)
                assertTrue(DesktopEncryptedMediaStorage.isEncryptedMediaFile(diskFile))
                assertArrayEquals(fakePng, encMedia.readDecryptedBytes(relPath))

                // Close connection so WAL flushes cleanly and inspect raw SQLite file header
                db.lockAndClose()
                assertFalse(
                    "SQLCipher v4 veritabani asla 'SQLite format 3' duz basligi tasimamali",
                    DesktopSqliteDatabaseManager.isPlaintextSqliteHeader(dbFile)
                )

                // Plain DriverManager without key MUST fail reading encrypted DB
                var rawReadSucceeded = false
                try {
                    DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { c ->
                        c.createStatement().use { s ->
                            s.executeQuery("SELECT count(*) FROM notes").use { rs -> rs.next() }
                        }
                    }
                    rawReadSucceeded = true
                } catch (_: SQLException) {
                    rawReadSucceeded = false
                }
                assertFalse("Anahtarsiz ham SQLite erisimi basarisiz olmali", rawReadSucceeded)

                // Enable Master Password -> DPAPI/Keyring auto-key MUST be wiped!
                val enableRes = secMgr.enableMasterPassword("GelistirmisKasa!2026".toCharArray(), autoLockMinutes = 5)
                assertTrue(enableRes is AppResult.Success)
                assertNull(
                    "Master Password aktifken DPAPI / OS Keychain auto-key tamamen silinmeli",
                    keyring.peek(DesktopVaultSecurityManager.ACCOUNT_LOCAL_AUTO_ROOT)
                )

                // Disk media still decrypts transparently under rotated Master Media Key
                assertTrue(DesktopEncryptedMediaStorage.isEncryptedMediaFile(diskFile))
                assertArrayEquals(fakePng, encMedia.readDecryptedBytes(relPath))

                // Lock, verify locked behavior + wrong password rejection + valid unlock
                val locked = secMgr.lockVault()
                assertTrue(locked.isLocked)
                assertNull(secMgr.currentDbKeyCopy())

                val wrongUnlock = secMgr.unlockWithMasterPassword("YanlisParola123".toCharArray())
                assertEquals(AppResult.Failure(DataError.Security.DECRYPTION_FAILED), wrongUnlock)
                assertFalse(secMgr.isVaultUnlocked())

                val goodUnlock = secMgr.unlockWithMasterPassword("GelistirmisKasa!2026".toCharArray())
                assertTrue(goodUnlock is AppResult.Success)
                assertFalse((goodUnlock as AppResult.Success).data.isLocked)
                val notesAfter = repo.observeNotes(includeDeleted = false).first()
                assertEquals("Çok Gizli PC Notu", notesAfter.first().title)
            } finally {
                db.close()
            }
        }

    @Test
    fun `Mevcut duz metin eski veritabani ilk acilista otomatik SQLCipher v4 formatina yukselmelidir`() =
        runBlocking {
            val root = tempFolder.newFolder("legacy_upgrade")
            val dbFile = File(root, "legacy_plain.db")

            // Create a plaintext legacy SQLite v6 DB on disk first
            val plainDb = DesktopSqliteDatabaseManager(dbFile, clock, Dispatchers.IO, dbKeyProvider = null)
            val plainRepo = DesktopNoteRepositoryImpl(plainDb, clock)
            plainRepo.upsertNote(Note(title = "Eski Not", description = "Plaintext gecis testi"))
            plainDb.close()
            assertTrue(DesktopSqliteDatabaseManager.isPlaintextSqliteHeader(dbFile))

            val keyring = InMemoryKeyringVault()
            lateinit var secMgr: DesktopVaultSecurityManager
            val encDb = DesktopSqliteDatabaseManager(
                dbFile = dbFile,
                clock = clock,
                ioDispatcher = Dispatchers.IO,
                dbKeyProvider = { secMgr.currentDbKeyCopy() }
            )
            val rawMedia = SharedStorageFactory.createMediaPathResolver(File(root, "files"), File(root, "cache"))
            val encMedia = DesktopEncryptedMediaStorage(rawMedia, { secMgr.currentMediaKeyCopy() }, File(root, "files"))
            secMgr = DesktopVaultSecurityManager(
                securityMetadataFile = File(root, "vault_security.json"),
                keyringVault = keyring,
                dbManagerProvider = { encDb },
                encryptedMediaStorageProvider = { encMedia }
            )

            try {
                val boot = secMgr.initializeOnStartup() as AppResult.Success
                assertFalse(boot.data.isLocked)

                val encRepo = DesktopNoteRepositoryImpl(encDb, clock)
                val migratedNotes = encRepo.observeNotes(includeDeleted = false).first()
                assertEquals(1, migratedNotes.size)
                assertEquals("Eski Not", migratedNotes.first().title)

                encDb.lockAndClose()
                assertFalse(
                    "Gecis sonrasi diskteki dosya artik plaintext olmamali",
                    DesktopSqliteDatabaseManager.isPlaintextSqliteHeader(dbFile)
                )
            } finally {
                encDb.close()
            }
        }

    @Test
    fun `Kasa kilit ekrani Compose Desktop sahnesinde hatasiz render edilmelidir`() {
        val scene = ImageComposeScene(width = 800, height = 600, density = Density(1f)) {
            PureNoteItDesktopTheme(darkTheme = true) {
                DesktopVaultLockScreen(
                    errorMessage = "Şifre çözme başarısız: ana parola hatalı.",
                    cooldownSeconds = 0,
                    onUnlock = {}
                )
            }
        }
        try {
            val rendered = scene.render()
            assertEquals(800, rendered.width)
            assertEquals(600, rendered.height)
            val png = rendered.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(png)
            assertTrue(png!!.bytes.size > 5_000)
        } finally {
            scene.close()
        }
    }

    private class InMemoryKeyringVault : DesktopOsKeyringVault {
        private val store = mutableMapOf<String, String>()
        fun peek(account: String): String? = store[account]
        override suspend fun saveSecret(account: String, secretChars: CharArray): AppResult<Unit, DataError.Security> {
            store[account] = String(secretChars)
            return AppResult.Success(Unit)
        }
        override suspend fun readSecret(account: String): AppResult<CharArray?, DataError.Security> =
            AppResult.Success(store[account]?.toCharArray())
        override suspend fun deleteSecret(account: String): AppResult<Unit, DataError.Security> {
            store.remove(account)
            return AppResult.Success(Unit)
        }
    }
}
