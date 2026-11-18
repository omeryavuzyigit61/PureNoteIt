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
import com.notes.purenoteit.security.VaultPinSyncProtocol
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.LocalSyncSnapshot
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.ResolvedRemoteBatch
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.VaultSettingsSyncCoordinator
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.RemoteEncryptedChange
import com.notes.purenoteit.sync.model.RemoteSyncPullPage
import com.notes.purenoteit.sync.model.RemoteSyncPushAck
import com.notes.purenoteit.sync.model.SyncCursorState
import com.notes.purenoteit.sync.sha256Hex
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPinSyncProtocolTest {

    private val crypto = SharedCryptoFactory.createVaultCrypto()
    private val protocol = VaultPinSyncProtocol(crypto)

    @Test
    fun `PIN senkron belgesi duz metin PIN icermemeli ve baska cihazda ENC_P3_PWD notu acabilmelidir`() = runBlocking {
        val driveConfigFiles = linkedMapOf<String, String>()
        val fakeRemote = object : RemoteSyncDataSource {
            override suspend fun pullChanges(sinceCursor: String?) =
                AppResult.Success(RemoteSyncPullPage(changes = emptyList(), nextCursor = "0", hasMore = false))

            override suspend fun pushDelta(encryptedDelta: EncryptedDeltaEnvelope) =
                AppResult.Success(RemoteSyncPushAck("id", "0", 0L))

            override suspend fun uploadMediaBlob(mediaKey: String, encryptedBytes: ByteArray) =
                AppResult.Success(Unit)

            override suspend fun downloadMediaBlob(mediaKey: String) =
                AppResult.Failure(DataError.Network.UNKNOWN)

            override suspend fun readAppDataConfigFile(fileName: String): AppResult<String?, DataError.Network> =
                AppResult.Success(driveConfigFiles[fileName])

            override suspend fun upsertAppDataConfigFile(fileName: String, content: String): AppResult<Unit, DataError.Network> {
                driveConfigFiles[fileName] = content
                return AppResult.Success(Unit)
            }
        }

        val syncPass = "DriveMasterSyncPass!2026".toCharArray()
        val phonePin = "482910".toCharArray()
        val created = protocol.createPinDocument(
            pinChars = phonePin,
            existingVaultKeyChars = null,
            currentDoc = null,
            nowMillis = 1_760_500_000_000L,
            deviceId = "phone-pixel"
        ) as AppResult.Success
        val (phoneDoc, phoneVaultKey) = created.data

        try {
            assertEquals(120_000, phoneDoc.kdfIterations)
            assertTrue(phoneDoc.hasPin)
            val serializedLocal = protocol.encodeDocumentJson(phoneDoc)
            assertFalse("Plaintext PIN must never appear in persisted verifier doc", serializedLocal.contains("482910"))
            assertFalse("Plaintext vault key must never appear unencrypted", serializedLocal.contains(String(phoneVaultKey)))

            val encPhoneNote = (crypto.encryptWithPassphrase("Gizli kasa icerigi 🚀", phoneVaultKey) as AppResult.Success).data
            assertTrue(encPhoneNote.startsWith("ENC_P3_PWD:"))

            val coordPhone = VaultSettingsSyncCoordinator(fakeRemote, protocol)
            val phoneUpdated = protocol.updateRetentionDays(phoneDoc, retentionDays = 14, nowMillis = 1_760_500_005_000L)
            val pushRes = coordPhone.synchronize(phoneUpdated, syncPass.copyOf()) as AppResult.Success
            assertEquals(14, pushRes.data?.trashRetentionDays)

            val rawDriveJson = driveConfigFiles[VaultSettingsSyncCoordinator.REMOTE_SETTINGS_FILE]!!
            assertFalse("Drive payload must be ENC_P3_PWD encrypted", rawDriveJson.contains("482910"))
            assertFalse("Drive payload must not leak PBKDF2 metadata in cleartext", rawDriveJson.contains("pin_hash_b64"))

            val coordPc = VaultSettingsSyncCoordinator(fakeRemote, protocol)
            val pcPulled = (coordPc.synchronize(null, syncPass.copyOf()) as AppResult.Success).data!!
            assertTrue(pcPulled.hasPin)
            assertEquals(14, pcPulled.trashRetentionDays)

            val wrongPinRes = protocol.verifyPinAndUnwrapKey("999999".toCharArray(), pcPulled)
            assertTrue(wrongPinRes is AppResult.Failure)

            val pcUnwrap = protocol.verifyPinAndUnwrapKey("482910".toCharArray(), pcPulled) as AppResult.Success
            try {
                assertArrayEquals(phoneVaultKey, pcUnwrap.data)
                val decNote = crypto.decryptEnvelope(encPhoneNote, pcUnwrap.data, null) as AppResult.Success
                assertEquals("Gizli kasa icerigi 🚀", decNote.data)
            } finally {
                pcUnwrap.data.fill('\u0000')
            }

            // Newer retention update (7 days) on PC propagates back cleanly to Phone
            val pcDoc7Days = protocol.updateRetentionDays(pcPulled, retentionDays = 7, nowMillis = 1_760_500_010_000L)
            val pcPush7 = coordPc.synchronize(pcDoc7Days, syncPass.copyOf()) as AppResult.Success
            assertEquals(7, pcPush7.data?.trashRetentionDays)

            val phonePull7 = coordPhone.synchronize(phoneUpdated, syncPass.copyOf()) as AppResult.Success
            assertEquals(7, phonePull7.data?.trashRetentionDays)
            assertEquals(phoneDoc.pinHashB64, phonePull7.data?.pinHashB64)
        } finally {
            phoneVaultKey.fill('\u0000')
            syncPass.fill('\u0000')
            phonePin.fill('\u0000')
        }
    }

    @Test
    fun `blok siralama cizim guncelleme ve cop kutusu geri yukleme silme dongusunde cakisma kopyasi olusmaz`() = runBlocking {
        var nowMs = 2_000_000_000_000L
        val clock = AppClock { nowMs }
        val remote = TestRemoteSource()
        val storePhone = InMemStore("phone-1")
        val storePc = InMemStore("pc-1")

        val enginePhone = SyncEngine(storePhone, remote, crypto, ConflictResolver(clock), clock)
        val enginePc = SyncEngine(storePc, remote, crypto, ConflictResolver(clock), clock)
        val pass = "SyncRoundtripKey!2026".toCharArray()

        // Step 1: Phone creates note with ordered blocks & initial drawing PNG
        val b1 = NoteBlock(id = "blk-1", type = BlockType.PARAGRAPH, content = "Satir 1")
        val b2 = NoteBlock(id = "blk-2", type = BlockType.TODO, content = "Gorev A", isChecked = false)
        val drawBytesV1 = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val drawPathV1 = "media/drawings/${sha256Hex(drawBytesV1)}.png"
        val bDrawV1 = NoteBlock(id = "blk-draw", type = BlockType.DRAWING, mediaPath = drawPathV1)
        storePhone.media[drawPathV1] = drawBytesV1

        val noteV1 = Note(
            id = 1,
            syncUuid = "note-reorder-draw-uuid",
            title = "Test Akis",
            description = BlockParser.serialize(listOf(b1, b2, bDrawV1)),
            date = nowMs,
            updatedAt = nowMs,
            syncVersion = 1L
        )
        storePhone.notes[noteV1.syncUuid] = noteV1
        nowMs += 100L

        assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)
        assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
        assertEquals(1, storePc.notes.size)
        assertTrue(storePc.hasLocalMedia(drawPathV1))

        // Step 2: PC reorders blocks AND continues/saves drawing as new path -> immediate sync -> 0 conflicts
        val drawBytesV2 = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 9, 9)
        val drawPathV2 = "media/drawings/${sha256Hex(drawBytesV2)}.png"
        storePc.media[drawPathV2] = drawBytesV2
        nowMs += 500L
        val noteV2 = storePc.notes.getValue(noteV1.syncUuid).copy(
            description = BlockParser.serialize(
                listOf(
                    b2.copy(isChecked = true),
                    b1,
                    bDrawV1.copy(mediaPath = drawPathV2)
                )
            ),
            updatedAt = nowMs,
            syncVersion = 2L
        )
        storePc.notes[noteV2.syncUuid] = noteV2
        nowMs += 100L

        assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
        assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)

        assertEquals(1, storePhone.notes.size)
        assertEquals(1, storePc.notes.size)
        assertFalse(storePhone.notes.values.any { it.title.orEmpty().contains("Çakışma Kopyası") })
        val phoneBlocksAfter = BlockParser.parse(storePhone.notes.getValue(noteV1.syncUuid).description)
        assertEquals(listOf("blk-2", "blk-1", "blk-draw"), phoneBlocksAfter.map { it.id })
        assertEquals(drawPathV2, phoneBlocksAfter[2].mediaPath)
        assertTrue(storePhone.hasLocalMedia(drawPathV2))

        // Step 3: Phone moves note to TRASH -> PC sees TRASH -> PC restores -> Phone permanently deletes
        nowMs += 500L
        storePhone.notes[noteV1.syncUuid] = storePhone.notes.getValue(noteV1.syncUuid).copy(
            status = "TRASH",
            deletedAt = nowMs,
            updatedAt = nowMs,
            syncVersion = 3L
        )
        nowMs += 100L
        assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)
        assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
        val pcTrashed = storePc.notes.getValue(noteV1.syncUuid)
        assertEquals("TRASH", pcTrashed.status)
        assertEquals(nowMs - 100L, pcTrashed.deletedAt)

        // PC restores
        nowMs += 500L
        storePc.notes[noteV1.syncUuid] = pcTrashed.copy(
            status = "TODO",
            deletedAt = null,
            updatedAt = nowMs,
            syncVersion = 4L
        )
        nowMs += 100L
        assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
        assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)
        val phoneRestored = storePhone.notes.getValue(noteV1.syncUuid)
        assertEquals("TODO", phoneRestored.status)
        assertNull(phoneRestored.deletedAt)

        // Phone permanently deletes (tombstone status = DELETED)
        nowMs += 500L
        storePhone.notes[noteV1.syncUuid] = phoneRestored.copy(
            status = "DELETED",
            deletedAt = nowMs,
            title = "",
            description = "",
            updatedAt = nowMs,
            syncVersion = 5L
        )
        nowMs += 100L
        assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)
        assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
        assertEquals("DELETED", storePc.notes.getValue(noteV1.syncUuid).status)
        assertEquals(1, storePhone.notes.size)
        assertEquals(1, storePc.notes.size)
        pass.fill('\u0000')
    }

    private class TestRemoteSource : RemoteSyncDataSource {
        private val deltas = mutableListOf<RemoteEncryptedChange>()
        private val blobs = linkedMapOf<String, ByteArray>()

        override suspend fun pullChanges(sinceCursor: String?): AppResult<RemoteSyncPullPage, DataError.Network> {
            val start = sinceCursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val slice = if (start < deltas.size) deltas.subList(start, deltas.size).toList() else emptyList()
            return AppResult.Success(RemoteSyncPullPage(changes = slice, nextCursor = deltas.size.toString(), hasMore = false))
        }

        override suspend fun pushDelta(encryptedDelta: EncryptedDeltaEnvelope): AppResult<RemoteSyncPushAck, DataError.Network> {
            val idx = deltas.size + 1
            deltas += RemoteEncryptedChange(
                remoteId = "r-$idx",
                fileName = "sync_delta_${encryptedDelta.batchId}.enc",
                modifiedAt = idx.toLong(),
                encryptedEnvelope = encryptedDelta.encryptedPayload
            )
            return AppResult.Success(RemoteSyncPushAck("r-$idx", deltas.size.toString(), idx.toLong()))
        }

        override suspend fun uploadMediaBlob(mediaKey: String, encryptedBytes: ByteArray): AppResult<Unit, DataError.Network> {
            blobs[mediaKey] = encryptedBytes
            return AppResult.Success(Unit)
        }

        override suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network> {
            val b = blobs[mediaKey] ?: return AppResult.Failure(DataError.Network.UNKNOWN)
            return AppResult.Success(b)
        }
    }

    private class InMemStore(deviceId: String) : LocalSyncStore {
        val categories = linkedMapOf<String, Category>()
        val notes = linkedMapOf<String, Note>()
        val links = linkedMapOf<String, NoteLink>()
        val streamItems = linkedMapOf<String, StreamItem>()
        val rules = linkedMapOf<String, AutomationRule>()
        val media = linkedMapOf<String, ByteArray>()
        private var cursor = SyncCursorState(deviceId = deviceId)

        override suspend fun readModifiedSince(sinceMillis: Long) = AppResult.Success(
            LocalSyncSnapshot(
                categories = categories.values.filter { it.updatedAt > sinceMillis },
                notes = notes.values.filter { it.updatedAt > sinceMillis },
                noteLinks = links.values.filter { it.updatedAt > sinceMillis },
                streamItems = streamItems.values.filter { it.updatedAt > sinceMillis },
                automationRules = rules.values.filter { it.updatedAt > sinceMillis }
            )
        )

        override suspend fun applyResolvedRemoteBatch(batch: ResolvedRemoteBatch): AppResult<Unit, DataError.Local> {
            batch.categories.forEach { categories[it.syncUuid] = it }
            batch.notes.forEach { notes[it.syncUuid] = it }
            batch.noteLinks.forEach { links[it.syncUuid] = it }
            batch.streamItems.forEach { streamItems[it.syncUuid] = it }
            batch.automationRules.forEach { rules[it.syncUuid] = it }
            return AppResult.Success(Unit)
        }

        override suspend fun findCategoriesBySyncUuids(syncUuids: Collection<String>) = categories.filterKeys { it in syncUuids }
        override suspend fun findNotesBySyncUuids(syncUuids: Collection<String>) = notes.filterKeys { it in syncUuids }
        override suspend fun findLinksBySyncUuids(syncUuids: Collection<String>) = links.filterKeys { it in syncUuids }
        override suspend fun findStreamItemsBySyncUuids(syncUuids: Collection<String>) = streamItems.filterKeys { it in syncUuids }
        override suspend fun findRulesBySyncUuids(syncUuids: Collection<String>) = rules.filterKeys { it in syncUuids }
        override suspend fun hasLocalMedia(mediaPath: String) = mediaPath in media
        override suspend fun readLocalMediaBytes(mediaPath: String) = media[mediaPath]
        override suspend fun writeLocalMediaBytes(mediaPath: String, bytes: ByteArray): AppResult<Unit, DataError.Local> {
            media[mediaPath] = bytes
            return AppResult.Success(Unit)
        }
        override suspend fun getSyncCursorState() = cursor
        override suspend fun saveSyncCursorState(state: SyncCursorState) { cursor = state }
    }
}
