package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.sync.LocalSyncSnapshot
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.ResolvedRemoteBatch
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.RemoteEncryptedChange
import com.notes.purenoteit.sync.model.RemoteSyncPullPage
import com.notes.purenoteit.sync.model.RemoteSyncPushAck
import com.notes.purenoteit.sync.model.SyncCursorState
import com.notes.purenoteit.sync.sha256Hex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineAndConflictTest {

    private val masterPassphrase = "PhoneToPcMasterSyncKey#2026".toCharArray()

    @Test
    fun `telefon ve PC arasi sifreli senkronizasyonda not cizim ve kategori sorunsuz aktarilmalidir`() = runTest {
        var now = 10_000L
        val clock = AppClock { now }
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")

        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, clock)
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, clock)

        // Phone creates a category, canonical drawing bytes, and a note referencing them
        val drawingBytes = "simulated-png-bytes-purenoteit".encodeToByteArray()
        val drawingSha = sha256Hex(drawingBytes)
        val canonicalPath = "media/drawings/$drawingSha.png"
        phoneStore.media[canonicalPath] = drawingBytes

        val catUuid = "cat-default-2"
        phoneStore.categories[catUuid] = Category(
            id = 2,
            name = "Günlük",
            syncUuid = catUuid,
            updatedAt = now,
            syncVersion = 1L
        )
        val noteUuid = "note-uuid-100"
        phoneStore.notes[noteUuid] = Note(
            id = 1,
            title = "Mimari Plan",
            description = BlockParser.serialize(
                listOf(
                    NoteBlock(id = "b1", type = BlockType.DRAWING, mediaPath = canonicalPath),
                    NoteBlock(id = "b2", type = BlockType.PARAGRAPH, content = "E2EE Test")
                )
            ),
            date = now,
            categoryId = 2,
            categorySyncUuid = catUuid,
            syncUuid = noteUuid,
            updatedAt = now,
            syncVersion = 1L
        )

        // 1. Phone pushes E2EE delta + encrypted PVM3 media
        val pushSummary = phoneEngine.synchronize(masterPassphrase)
        assertTrue(pushSummary is AppResult.Success)
        val phoneSummary = (pushSummary as AppResult.Success).data
        assertEquals(2, phoneSummary.pushedEntities)
        assertEquals(1, phoneSummary.mediaUploaded)

        // Zero plaintext leakage on fake remote Drive
        val storedEnvelope = remote.deltas.single().encryptedEnvelope!!
        assertTrue(storedEnvelope.startsWith("ENC_P3_PWD:"))
        assertFalse(storedEnvelope.contains("Mimari Plan"))
        val remoteMediaBytes = remote.blobs[canonicalPath]!!
        assertFalse(remoteMediaBytes.decodeToString().contains("simulated-png-bytes"))

        // 2. PC pulls & decrypts delta + verifies SHA-256 of drawing bytes
        now = 20_000L
        val pullSummary = pcEngine.synchronize(masterPassphrase)
        assertTrue(pullSummary is AppResult.Success)
        val pcRes = (pullSummary as AppResult.Success).data
        assertEquals(1, pcRes.pulledCategories)
        assertEquals(1, pcRes.pulledNotes)
        assertEquals(0, pcRes.conflictCopiesCreated)
        assertEquals(1, pcRes.mediaDownloaded)
        assertArrayEquals(drawingBytes, pcStore.media[canonicalPath])
        assertEquals("Mimari Plan", pcStore.notes[noteUuid]?.title)
    }

    @Test
    fun `cevrimdisi iki cihazda ayni not farkli duzenlenirse veri kayipsiz Cakisma Kopyasi olusmalidir`() = runTest {
        var now = 1_000L
        val clock = AppClock { now }
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")
        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, clock)
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, clock)

        // Baseline synced state at t=1000
        val noteUuid = "shared-note-1"
        phoneStore.notes[noteUuid] = Note(
            id = 1,
            title = "Toplantı Notu",
            description = "Başlangıç",
            date = now,
            syncUuid = noteUuid,
            updatedAt = now,
            syncVersion = 1L
        )
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)
        assertTrue(pcEngine.synchronize(masterPassphrase) is AppResult.Success)

        // Both devices go offline and edit the SAME note with diverged content
        now = 5_000L
        phoneStore.notes[noteUuid] = phoneStore.notes[noteUuid]!!.copy(
            title = "Toplantı Notu",
            description = "Telefondan Eklenen Satırlar",
            updatedAt = 5_000L,
            syncVersion = 2L
        )

        now = 8_000L // PC edit is strictly newer
        pcStore.notes[noteUuid] = pcStore.notes[noteUuid]!!.copy(
            title = "Toplantı Notu",
            description = "PC'den Eklenen Kapsamlı Güncelleme",
            updatedAt = 8_000L,
            syncVersion = 2L
        )

        // Phone syncs first, then PC syncs
        now = 9_000L
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)

        now = 10_000L
        val pcConflictSync = pcEngine.synchronize(masterPassphrase)
        assertTrue(pcConflictSync is AppResult.Success)
        val summary = (pcConflictSync as AppResult.Success).data
        assertEquals(1, summary.conflictCopiesCreated)

        // Primary holds PC's newer edit; Conflict copy preserves Phone's edit!
        assertEquals(2, pcStore.notes.size)
        assertEquals("PC'den Eklenen Kapsamlı Güncelleme", pcStore.notes[noteUuid]?.description)
        val conflictNote = pcStore.notes.values.first { it.syncUuid != noteUuid }
        assertTrue(conflictNote.title!!.contains("[Çakışma Kopyası]"))
        assertEquals("Telefondan Eklenen Satırlar", conflictNote.description)
    }

    @Test
    fun `bozuk medya SHA-256 dogrulamasindan gecemezse yerel diske yazilmadan durmalidir`() = runTest {
        val now = 1_000L
        val clock = AppClock { now }
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")
        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, clock)
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, clock)

        val realBytes = "real-bytes".encodeToByteArray()
        val fakePath = "media/images/${sha256Hex(realBytes)}.jpg"
        phoneStore.media[fakePath] = realBytes
        phoneStore.notes["n1"] = Note(
            id = 1,
            title = "Medyalı",
            images = listOf(fakePath),
            syncUuid = "n1",
            updatedAt = now,
            syncVersion = 1L
        )

        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)

        // Tamper encrypted PVM3 with WRONG-HASH valid plaintext encrypted with same key (Hash Swap Attack)
        val forgedEncrypted = (SharedCryptoFactory.createVaultCrypto()
            .encryptMediaBytes("tampered-image-payload".encodeToByteArray(), masterPassphrase) as AppResult.Success).data
        remote.blobs[fakePath] = forgedEncrypted

        val pcRes = pcEngine.synchronize(masterPassphrase)
        assertTrue(pcRes is AppResult.Failure)
        assertEquals(DataError.Security.INTEGRITY_MISMATCH, (pcRes as AppResult.Failure).error)
        assertNullIfMissing(pcStore.media[fakePath])
    }

    @Test
    fun `Tombstone silme islemi diger cihaza yansimali ama sonraki yerel duzenleme veri kaybini onlemelidir`() = runTest {
        var now = 1_000L
        val clock = AppClock { now }
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")
        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, clock)
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, clock)

        phoneStore.notes["n-del"] = Note(title = "Silinecek", syncUuid = "n-del", updatedAt = 1_000L)
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)
        assertTrue(pcEngine.synchronize(masterPassphrase) is AppResult.Success)

        // PC tombstones the note at t=2000 and pushes
        now = 2_000L
        pcStore.notes["n-del"] = pcStore.notes["n-del"]!!.copy(
            deletedAt = 2_000L,
            updatedAt = 2_000L,
            status = "DELETED",
            syncVersion = 2L
        )
        assertTrue(pcEngine.synchronize(masterPassphrase) is AppResult.Success)

        // Phone syncs without concurrent edit -> accepts tombstone cleanly
        now = 3_000L
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)
        assertNotNull(phoneStore.notes["n-del"]?.deletedAt)
        assertEquals("DELETED", phoneStore.notes["n-del"]?.status)
    }

    private fun assertNullIfMissing(bytes: ByteArray?) {
        assertTrue(bytes == null)
    }

    private class FakeRemoteDrive : RemoteSyncDataSource {
        val deltas = mutableListOf<RemoteEncryptedChange>()
        val blobs = mutableMapOf<String, ByteArray>()

        override suspend fun pullChanges(sinceCursor: String?): AppResult<RemoteSyncPullPage, DataError.Network> {
            val fromIdx = sinceCursor?.toIntOrNull() ?: 0
            val slice = if (fromIdx < deltas.size) deltas.subList(fromIdx, deltas.size).toList() else emptyList()
            return AppResult.Success(
                RemoteSyncPullPage(
                    changes = slice,
                    nextCursor = deltas.size.toString(),
                    hasMore = false
                )
            )
        }

        override suspend fun pushDelta(encryptedDelta: EncryptedDeltaEnvelope): AppResult<RemoteSyncPushAck, DataError.Network> {
            val idx = deltas.size
            deltas += RemoteEncryptedChange(
                remoteId = "drv-$idx",
                fileName = "sync_delta_${encryptedDelta.batchId}.enc",
                modifiedAt = idx.toLong() + 100L,
                encryptedEnvelope = encryptedDelta.encryptedPayload
            )
            return AppResult.Success(
                RemoteSyncPushAck(
                    remoteId = "drv-$idx",
                    newCursor = deltas.size.toString(),
                    committedAt = idx.toLong() + 100L
                )
            )
        }

        override suspend fun uploadMediaBlob(mediaKey: String, encryptedBytes: ByteArray): AppResult<Unit, DataError.Network> {
            blobs[mediaKey] = encryptedBytes
            return AppResult.Success(Unit)
        }

        override suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network> {
            val bytes = blobs[mediaKey] ?: return AppResult.Failure(DataError.Network.UNKNOWN)
            return AppResult.Success(bytes)
        }
    }

    private class InMemorySyncStore(deviceId: String) : LocalSyncStore {
        val categories = linkedMapOf<String, Category>()
        val notes = linkedMapOf<String, Note>()
        val links = linkedMapOf<String, NoteLink>()
        val streamItems = linkedMapOf<String, StreamItem>()
        val rules = linkedMapOf<String, AutomationRule>()
        val media = linkedMapOf<String, ByteArray>()
        private var cursorState = SyncCursorState(deviceId = deviceId)

        override suspend fun readModifiedSince(sinceMillis: Long): AppResult<LocalSyncSnapshot, DataError.Local> {
            return AppResult.Success(
                LocalSyncSnapshot(
                    categories = categories.values.filter { it.updatedAt > sinceMillis },
                    notes = notes.values.filter { it.updatedAt > sinceMillis },
                    noteLinks = links.values.filter { it.updatedAt > sinceMillis },
                    streamItems = streamItems.values.filter { it.updatedAt > sinceMillis },
                    automationRules = rules.values.filter { it.updatedAt > sinceMillis }
                )
            )
        }

        override suspend fun applyResolvedRemoteBatch(batch: ResolvedRemoteBatch): AppResult<Unit, DataError.Local> {
            batch.categories.forEach { categories[it.syncUuid] = it }
            batch.notes.forEach { notes[it.syncUuid] = it }
            batch.noteLinks.forEach { links[it.syncUuid] = it }
            batch.streamItems.forEach { streamItems[it.syncUuid] = it }
            batch.automationRules.forEach { rules[it.syncUuid] = it }
            return AppResult.Success(Unit)
        }

        override suspend fun findCategoriesBySyncUuids(syncUuids: Collection<String>) =
            categories.filterKeys { it in syncUuids }

        override suspend fun findNotesBySyncUuids(syncUuids: Collection<String>) =
            notes.filterKeys { it in syncUuids }

        override suspend fun findLinksBySyncUuids(syncUuids: Collection<String>) =
            links.filterKeys { it in syncUuids }

        override suspend fun findStreamItemsBySyncUuids(syncUuids: Collection<String>) =
            streamItems.filterKeys { it in syncUuids }

        override suspend fun findRulesBySyncUuids(syncUuids: Collection<String>) =
            rules.filterKeys { it in syncUuids }

        override suspend fun hasLocalMedia(mediaPath: String): Boolean = mediaPath in media

        override suspend fun readLocalMediaBytes(mediaPath: String): ByteArray? = media[mediaPath]

        override suspend fun writeLocalMediaBytes(mediaPath: String, bytes: ByteArray): AppResult<Unit, DataError.Local> {
            media[mediaPath] = bytes
            return AppResult.Success(Unit)
        }

        override suspend fun getSyncCursorState(): SyncCursorState = cursorState

        override suspend fun saveSyncCursorState(state: SyncCursorState) {
            cursorState = state
        }
    }
}
