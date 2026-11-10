package com.notes.purenoteit.desktop

import com.notes.purenoteit.SharedCryptoFactory
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.SharedSyncFactory
import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepositoryImpl
import com.notes.purenoteit.desktop.data.sync.DesktopLocalSyncStoreImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncDataSourceImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.model.SyncSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopDataAndSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var nowMs = 1_760_100_000_000L
    private val clock = AppClock { nowMs }

    @Test
    fun `SQLite v6 semasi varsayilan kategoriler Wikilink AltSayfa ve Tombstone silme calismalidir`() = runBlocking {
        val dbFile = tempFolder.newFile("test_repo.db")
        val db = DesktopSqliteDatabaseManager(dbFile, clock, Dispatchers.IO)
        db.use {
            val repo = DesktopNoteRepositoryImpl(db, clock)

            val categories = repo.observeCategories().first()
            assertEquals(listOf("cat-default-1", "cat-default-2", "cat-default-3"), categories.map { it.syncUuid })

            val target = (repo.upsertNote(Note(title = "Mimari", description = "Temel")) as AppResult.Success).data
            val sourceBlocks = listOf(
                NoteBlock(type = BlockType.PARAGRAPH, content = "Bakınız [[Mimari]] notu.")
            )
            val source = (
                repo.upsertNote(
                    Note(
                        title = "Ana Plan",
                        description = BlockParser.serialize(sourceBlocks),
                        categoryId = categories.first().id,
                        categorySyncUuid = categories.first().syncUuid
                    )
                ) as AppResult.Success
                ).data

            val linksAfterCreate = repo.observeNoteLinks().first()
            assertEquals(1, linksAfterCreate.size)
            assertEquals(source.syncUuid, linksAfterCreate[0].sourceSyncUuid)
            assertEquals(target.syncUuid, linksAfterCreate[0].targetSyncUuid)

            // Create sub-page and verify parent link + child note creation
            val childRes = repo.createSubPage(source.syncUuid, "Alt Sayfa Detayı")
            assertTrue(childRes is AppResult.Success)
            assertEquals(2, repo.observeNoteLinks().first().size)

            // Tombstone soft-delete & restore
            repo.softDeleteNote(source.syncUuid)
            assertFalseOrEmpty(repo.observeNotes(includeDeleted = false).first().any { it.syncUuid == source.syncUuid })
            val allIncludingDeleted = repo.observeNotes(includeDeleted = true).first()
            val tombstoned = allIncludingDeleted.first { it.syncUuid == source.syncUuid }
            assertNotNull(tombstoned.deletedAt)

            repo.restoreNote(source.syncUuid)
            val restored = repo.observeNotes(includeDeleted = false).first().first { it.syncUuid == source.syncUuid }
            assertNull(restored.deletedAt)
        }
    }

    @Test
    fun `Iki Desktop cihazi LAN WebSocket Ktor sunucusu uzerinden E2EE ENC_P3 ve PVM3 medya ile senkronize olmalidir`() =
        runBlocking {
            val dirA = tempFolder.newFolder("deviceA")
            val dirB = tempFolder.newFolder("deviceB")

            val dbA = DesktopSqliteDatabaseManager(File(dirA, "a.db"), clock, Dispatchers.IO)
            val dbB = DesktopSqliteDatabaseManager(File(dirB, "b.db"), clock, Dispatchers.IO)

            val mediaA = SharedStorageFactory.createMediaPathResolver(File(dirA, "files"), File(dirA, "cache"))
            val mediaB = SharedStorageFactory.createMediaPathResolver(File(dirB, "files"), File(dirB, "cache"))

            val syncStoreA = DesktopLocalSyncStoreImpl(dbA, mediaA, File(dirA, "cursor.json"))
            val syncStoreB = DesktopLocalSyncStoreImpl(dbB, mediaB, File(dirB, "cursor.json"))

            val repoA = DesktopNoteRepositoryImpl(dbA, clock)
            val repoB = DesktopNoteRepositoryImpl(dbB, clock)

            val crypto = SharedCryptoFactory.createVaultCrypto()
            val server = LanWebSocketSyncServer()

            try {
                val pin = "849205".toCharArray()
                val (_, port) = server.start(pin.copyOf(), requestedPort = 0)
                val baseUrl = "http://127.0.0.1:$port"

                // Verify unauthorized token is rejected (401 UNAUTHORIZED)
                val rogueClient = LanWebSocketSyncDataSourceImpl(baseUrl, "wrong-token")
                assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), rogueClient.pullChanges(null))

                val validHexToken = String(LanWebSocketSyncServer.hashPasscode(pin), Charsets.UTF_8)
                pin.fill('\u0000')

                val remoteA = LanWebSocketSyncDataSourceImpl(baseUrl, validHexToken)
                val remoteB = LanWebSocketSyncDataSourceImpl(baseUrl, validHexToken)

                val engineA = SyncEngine(syncStoreA, remoteA, crypto, ConflictResolver(clock), clock)
                val engineB = SyncEngine(syncStoreB, remoteB, crypto, ConflictResolver(clock), clock)

                // Populate Device A with drawing PNG media + rich blocks + subpage
                val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5)
                val drawingPath = mediaA.storeMediaBytes(pngBytes, "drawings", "png")
                val noteA = (
                    repoA.upsertNote(
                        Note(
                            title = "Ortak Çizimli Not",
                            description = BlockParser.serialize(
                                listOf(
                                    NoteBlock(type = BlockType.HEADING_1, content = "Proje Mimarisi"),
                                    NoteBlock(type = BlockType.DRAWING, mediaPath = drawingPath),
                                    NoteBlock(type = BlockType.CODE, codeLanguage = "python", content = "print(1)")
                                )
                            ),
                            date = nowMs
                        )
                    ) as AppResult.Success
                    ).data
                nowMs += 50L
                repoA.createSubPage(noteA.syncUuid, "Alt Tasarım")

                val passA = "PureNoteSyncPass!2026".toCharArray()
                val pushRes = engineA.synchronize(passA)
                assertTrue("Cihaz A push basarili olmali: $pushRes", pushRes is AppResult.Success)
                val pushSummary = (pushRes as AppResult.Success<SyncSummary>).data
                assertTrue("En az 2 not gonderilmis olmali: ${pushSummary.pushedEntities}", pushSummary.pushedEntities >= 2)

                // Wrong E2EE passphrase on Device B must fail decryption without saving corrupted data
                val wrongPass = "WrongPass!999".toCharArray()
                val failedPull = engineB.synchronize(wrongPass)
                assertEquals(AppResult.Failure(DataError.Security.DECRYPTION_FAILED), failedPull)

                // Correct passphrase on Device B pulls all notes, subpage & verified SHA-256 media bytes
                val passB = "PureNoteSyncPass!2026".toCharArray()
                val pullRes = engineB.synchronize(passB)
                assertTrue("Cihaz B pull basarili olmali: $pullRes", pullRes is AppResult.Success)

                val notesB = repoB.observeNotes(includeDeleted = false).first()
                assertEquals(2, notesB.size)
                val syncedMain = notesB.first { it.syncUuid == noteA.syncUuid }
                assertEquals("Ortak Çizimli Not", syncedMain.title)

                val syncedMedia = syncStoreB.readLocalMediaBytes(drawingPath)
                assertNotNull(syncedMedia)
                assertArrayEquals(pngBytes, syncedMedia)
            } finally {
                server.close()
                dbA.close()
                dbB.close()
            }
        }

    private fun assertFalseOrEmpty(condition: Boolean) {
        assertEquals(false, condition)
    }
}
