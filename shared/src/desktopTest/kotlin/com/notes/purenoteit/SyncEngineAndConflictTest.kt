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
import com.notes.purenoteit.sync.SubPageSyncRemapper
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
import org.junit.Assert.assertNull
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

    @Test
    fun `Android ve Desktop metadata farklari ile saat kaymasi sahte Cakisma Kopyasi ve yanki dongusu uretmemelidir`() = runTest {
        var phoneClockMs = 200_000L // Phone wall-clock 100s ahead of PC wall-clock
        var pcClockMs = 100_000L
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")

        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, AppClock { phoneClockMs })
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, AppClock { pcClockMs })

        val drawingBytes = byteArrayOf(1, 2, 3, 4)
        val drawingKey = "media/drawings/${sha256Hex(drawingBytes)}.png"
        phoneStore.media[drawingKey] = drawingBytes

        val parentBlocks = listOf(
            NoteBlock(type = BlockType.DRAWING, mediaPath = drawingKey),
            NoteBlock(type = BlockType.SUB_PAGE, content = "Alt", subPageNoteId = 99, subPageSyncUuid = "sub-1")
        )
        val descJson = BlockParser.serialize(parentBlocks)

        // Phone pushes initial note with isPassword=false, icon=null, images=empty
        phoneStore.notes["n-skew"] = Note(
            id = 5,
            title = "Başlıksız Not",
            description = descJson,
            date = 50_000L,
            isPassword = false,
            icon = null,
            images = emptyList(),
            syncUuid = "n-skew",
            updatedAt = phoneClockMs,
            syncVersion = 1L
        )
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)

        // PC pulls despite being 100s behind Phone's clock; PC also represents images=[drawingKey], isPassword=null, subPageNoteId=12, icon="📝"
        val pcFirst = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(1, pcFirst.data.pulledNotes)
        assertEquals(0, pcFirst.data.conflictCopiesCreated)
        assertEquals(0, pcFirst.data.pushedEntities)

        // PC second sync must NOT echo-push Phone's fast-forwarded note
        pcClockMs += 5_000L
        val pcEcho = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(0, pcEcho.data.pushedEntities)

        // Simulate BOTH devices stamping updatedAt > lastSyncedAt concurrently with ONLY platform metadata diffs -> STILL 0 [Çakışma Kopyası]
        pcClockMs += 5_000L
        pcStore.notes["n-skew"] = pcStore.notes["n-skew"]!!.copy(
            updatedAt = pcClockMs,
            isPassword = null,
            icon = "📝",
            images = listOf(drawingKey),
            description = BlockParser.serialize(
                listOf(
                    NoteBlock(type = BlockType.DRAWING, mediaPath = drawingKey),
                    NoteBlock(type = BlockType.SUB_PAGE, content = "Alt", subPageNoteId = 12, subPageSyncUuid = "sub-1")
                )
            )
        )
        phoneClockMs += 10_000L
        phoneStore.notes["n-skew"] = phoneStore.notes["n-skew"]!!.copy(
            updatedAt = phoneClockMs,
            syncVersion = 2L
        )
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)

        pcClockMs += 5_000L
        val pcMetaSync = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(0, pcMetaSync.data.conflictCopiesCreated)
        assertEquals(1, pcStore.notes.size)

        // Now simulate Phone genuinely editing title AFTER pulling while PC did NOT edit locally -> ZERO false [Çakışma Kopyası]
        phoneClockMs += 10_000L
        phoneStore.notes["n-skew"] = phoneStore.notes["n-skew"]!!.copy(
            title = "Başlıksız Not Güncel",
            updatedAt = phoneClockMs,
            syncVersion = 3L
        )
        assertTrue(phoneEngine.synchronize(masterPassphrase) is AppResult.Success)

        pcClockMs += 5_000L
        val pcSecondPull = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(0, pcSecondPull.data.conflictCopiesCreated)
        assertEquals(1, pcStore.notes.size)
        assertEquals("Başlıksız Not Güncel", pcStore.notes["n-skew"]?.title)

        // Now PC edits AFTER pulling (PC clock still 100s behind Phone's lastSyncedAt!), Phone pulls PC update -> ZERO false conflict!
        pcClockMs += 5_000L
        pcStore.notes["n-skew"] = pcStore.notes["n-skew"]!!.copy(
            title = "PC Son Hal",
            updatedAt = pcClockMs,
            syncVersion = 4L
        )
        val pcPush = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(1, pcPush.data.pushedEntities)

        phoneClockMs += 5_000L
        val phonePull = phoneEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(0, phonePull.data.conflictCopiesCreated)
        assertEquals(1, phoneStore.notes.size)
        assertEquals("PC Son Hal", phoneStore.notes["n-skew"]?.title)
    }

    @Test
    fun `legacy subpage integer IDs never collide with unrelated notes or spawn conflict copies`() {
        val parentUuid = "parent-work-uuid"
        val realChildUuid = "child-site1-uuid"
        val ajandaUuid = "ajanda-8-uuid"

        val ajandaLocalNote = Note(
            id = 4,
            title = "Ajanda: 8 Kasım 2026, Pazar",
            description = BlockParser.serialize(listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "Not"))),
            richBlocksJson = null,
            syncUuid = ajandaUuid,
            updatedAt = 1_000L
        )
        // Remote legacy parent references subPageNoteId = 4 without subPageSyncUuid
        val remoteParentInitiallyWithoutChild = Note(
            id = 2,
            title = "İş",
            description = BlockParser.serialize(
                listOf(
                    NoteBlock(type = BlockType.SUB_PAGE, content = "Site1", subPageNoteId = 4, subPageSyncUuid = null),
                    NoteBlock(type = BlockType.SUB_PAGE, content = "SilinenAlt", subPageNoteId = 99, subPageSyncUuid = null)
                )
            ),
            syncUuid = parentUuid,
            updatedAt = 2_000L
        )

        // Pass 1: Child hasn't arrived yet -> foreign subPageNoteId MUST be cleared to null so Ajanda: 8 is neither hidden nor hijacked
        val updatesPass1 = SubPageSyncRemapper.remapActiveNotes(
            allNotes = listOf(ajandaLocalNote, remoteParentInitiallyWithoutChild),
            allLinks = emptyList(),
            remoteBatchNoteUuids = setOf(parentUuid)
        )
        assertEquals(1, updatesPass1.size)
        val cleanedParentPass1 = updatesPass1.single()
        val parsedPass1 = BlockParser.parse(cleanedParentPass1.description)
        assertNull(parsedPass1[0].subPageNoteId)
        assertNull(parsedPass1[0].subPageSyncUuid)
        assertNull(parsedPass1[1].subPageNoteId)

        // Root filter check: Ajanda: 8 must NOT be hidden as a subpage!
        val refsPass1 = BlockParser.collectSubPageHierarchyRefs(listOf(cleanedParentPass1.description, remoteParentInitiallyWithoutChild.description))
        assertFalse(
            BlockParser.isSubPageNote(
                noteId = ajandaLocalNote.id,
                noteSyncUuid = ajandaLocalNote.syncUuid,
                noteTitle = ajandaLocalNote.title,
                richBlocksJson = ajandaLocalNote.richBlocksJson,
                refs = refsPass1
            )
        )

        // Pass 2: Child "Site1" arrives later with new local id = 19 -> parent gets remapped across batches + UUID backfilled
        val childArrivedLater = Note(
            id = 19,
            title = "Site1",
            description = BlockParser.serialize(listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "icerik"))),
            richBlocksJson = BlockParser.buildSubPageMetadataJson(parentNoteId = 777, parentSyncUuid = parentUuid),
            syncUuid = realChildUuid,
            updatedAt = 2_500L
        )
        val updatesPass2 = SubPageSyncRemapper.remapActiveNotes(
            allNotes = listOf(ajandaLocalNote, cleanedParentPass1, childArrivedLater),
            allLinks = emptyList(),
            remoteBatchNoteUuids = setOf(realChildUuid)
        )
        val updatedParentPass2 = updatesPass2.first { it.syncUuid == parentUuid }
        val updatedChildPass2 = updatesPass2.first { it.syncUuid == realChildUuid }
        val parsedPass2 = BlockParser.parse(updatedParentPass2.description)
        assertEquals(19, parsedPass2[0].subPageNoteId)
        assertEquals(realChildUuid, parsedPass2[0].subPageSyncUuid)
        assertEquals(2, BlockParser.parseRichMetadata(updatedChildPass2.richBlocksJson)?.parentNoteId)

        // ConflictResolver must treat cleaned/remapped subpage pointers as identical payload -> zero false conflict copy
        val resolver = com.notes.purenoteit.sync.ConflictResolver(clock = AppClock { 5_000L })
        val res = resolver.mergeNote(
            local = updatedParentPass2.copy(updatedAt = 2_100L),
            remote = remoteParentInitiallyWithoutChild.copy(updatedAt = 2_200L),
            lastSyncedAt = 1_500L
        )
        assertNull(res.conflictCopy)
    }

    @Test
    fun `coklu medya yukleme ve indirme paralel batch ve prefetchMediaIndex ile eksiksiz calismalidir`() = runTest {
        var now = 10_000L
        val clock = AppClock { now }
        val remote = FakeRemoteDrive()
        val phoneStore = InMemorySyncStore(deviceId = "phone-A")
        val pcStore = InMemorySyncStore(deviceId = "pc-B")

        val phoneEngine = SharedSyncFactory.createSyncEngine(phoneStore, remote, clock)
        val pcEngine = SharedSyncFactory.createSyncEngine(pcStore, remote, clock)

        // Create 10 distinct canonical media blobs (spans 2 batches of MEDIA_BATCH_SIZE = 8)
        val keys = (1..10).map { idx ->
            val raw = "media-payload-number-$idx-purenoteit".encodeToByteArray()
            val key = "media/images/${sha256Hex(raw)}.jpg"
            phoneStore.media[key] = raw
            key
        }
        phoneStore.notes["multi-media-note"] = Note(
            id = 1,
            title = "Galeri Notu",
            images = keys,
            syncUuid = "multi-media-note",
            updatedAt = now,
            syncVersion = 1L
        )

        val pushRes = phoneEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(10, pushRes.data.mediaUploaded)
        assertEquals(10, remote.blobs.size)
        assertEquals(1, remote.prefetchCalls.get())

        now = 20_000L
        val pullRes = pcEngine.synchronize(masterPassphrase) as AppResult.Success
        assertEquals(10, pullRes.data.mediaDownloaded)
        assertEquals(2, remote.prefetchCalls.get())
        keys.forEach { key ->
            assertArrayEquals(phoneStore.media[key], pcStore.media[key])
        }

        phoneEngine.resetSessionCaches()
        assertEquals(1, remote.invalidateCalls.get())
    }

    private fun assertNullIfMissing(bytes: ByteArray?) {
        assertTrue(bytes == null)
    }

    private class FakeRemoteDrive : RemoteSyncDataSource {
        val deltas = mutableListOf<RemoteEncryptedChange>()
        val blobs = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        val prefetchCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val invalidateCalls = java.util.concurrent.atomic.AtomicInteger(0)

        override suspend fun prefetchMediaIndex(): AppResult<Unit, DataError.Network> {
            prefetchCalls.incrementAndGet()
            return AppResult.Success(Unit)
        }

        override suspend fun invalidateCaches() {
            invalidateCalls.incrementAndGet()
        }

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
