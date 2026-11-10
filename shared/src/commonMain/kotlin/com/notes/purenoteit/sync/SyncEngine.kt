package com.notes.purenoteit.sync

import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.SyncCursorState
import com.notes.purenoteit.sync.model.SyncDeltaPayload
import com.notes.purenoteit.sync.model.SyncSummary
import com.notes.purenoteit.sync.model.toDomain
import com.notes.purenoteit.sync.model.toSyncDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * End-to-end encrypted bi-directional delta synchronization orchestrator.
 * - Decrypts & pulls remote deltas and `PVM3` media blobs before applying database state.
 * - Verifies SHA-256 hash integrity against canonical `media/<subFolder>/<sha256>.<ext>` filenames.
 * - Resolves concurrent offline edits losslessly via [ConflictResolver] (`[Çakışma Kopyası]`).
 * - Encrypts and pushes local delta changes and newly referenced media blobs via [RemoteSyncDataSource].
 */
class SyncEngine(
    private val localStore: LocalSyncStore,
    private val remoteDataSource: RemoteSyncDataSource,
    private val vaultCrypto: VaultCrypto,
    private val conflictResolver: ConflictResolver,
    private val clock: AppClock
) {
    private val syncMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun synchronize(passphrase: CharArray): AppResult<SyncSummary, AppError> {
        if (passphrase.isEmpty()) {
            return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        }
        val keyCopy = passphrase.copyOf()
        return syncMutex.withLock {
            try {
                executeSyncInternal(keyCopy)
            } catch (e: CancellationException) {
                throw e
            } finally {
                keyCopy.fill('\u0000')
            }
        }
    }

    private suspend fun executeSyncInternal(keyChars: CharArray): AppResult<SyncSummary, AppError> {
        val initialCursorState = localStore.getSyncCursorState()
        val deviceId = initialCursorState.deviceId.ifBlank { sharedRandomUuid() }
        var workingCursor = initialCursorState.remoteCursor

        // 1. Pull remote paginated delta envelopes
        val pulledPayloads = mutableListOf<SyncDeltaPayload>()
        var pageLoopGuard = 0
        do {
            val pageRes = remoteDataSource.pullChanges(workingCursor)
            val page = when (pageRes) {
                is AppResult.Failure -> return pageRes
                is AppResult.Success -> pageRes.data
            }

            for (change in page.changes) {
                if (change.isRemoteDeleted || change.encryptedEnvelope.isNullOrBlank()) continue
                val decRes = vaultCrypto.decryptEnvelope(
                    encryptedText = change.encryptedEnvelope,
                    passphrase = keyChars,
                    legacyFallbackSecret = null
                )
                val decryptedJson = when (decRes) {
                    is AppResult.Failure -> return decRes
                    is AppResult.Success -> decRes.data
                }
                val payload = try {
                    json.decodeFromString<SyncDeltaPayload>(decryptedJson)
                } catch (_: Exception) {
                    return AppResult.Failure(DataError.Network.SERIALIZATION)
                }
                if (payload.deviceId != deviceId && !payload.isEmpty()) {
                    pulledPayloads += payload
                }
            }

            workingCursor = page.nextCursor ?: workingCursor
            pageLoopGuard++
        } while (page.hasMore && pageLoopGuard < MAX_PULL_PAGES)

        // 2. Fold pulled remote deltas by syncUuid (newest revision per UUID across pages wins before local merge)
        val mergedCategoriesByUuid = linkedMapOf<String, Category>()
        val mergedNotesByUuid = linkedMapOf<String, Note>()
        val mergedLinksByUuid = linkedMapOf<String, NoteLink>()
        val mergedStreamByUuid = linkedMapOf<String, StreamItem>()
        val mergedRulesByUuid = linkedMapOf<String, AutomationRule>()

        for (payload in pulledPayloads.sortedBy { it.emittedAt }) {
            payload.categories.forEach { dto ->
                val incoming = dto.toDomain()
                val existing = mergedCategoriesByUuid[incoming.syncUuid]
                if (existing == null || isIncomingNewer(incoming.updatedAt, incoming.syncVersion, existing.updatedAt, existing.syncVersion)) {
                    mergedCategoriesByUuid[incoming.syncUuid] = incoming
                }
            }
            payload.notes.forEach { dto ->
                val incoming = dto.toDomain()
                val existing = mergedNotesByUuid[incoming.syncUuid]
                if (existing == null || isIncomingNewer(incoming.updatedAt, incoming.syncVersion, existing.updatedAt, existing.syncVersion)) {
                    mergedNotesByUuid[incoming.syncUuid] = incoming
                }
            }
            payload.noteLinks.forEach { dto ->
                val incoming = dto.toDomain()
                val existing = mergedLinksByUuid[incoming.syncUuid]
                if (existing == null || isIncomingNewer(incoming.updatedAt, incoming.syncVersion, existing.updatedAt, existing.syncVersion)) {
                    mergedLinksByUuid[incoming.syncUuid] = incoming
                }
            }
            payload.streamItems.forEach { dto ->
                val incoming = dto.toDomain()
                val existing = mergedStreamByUuid[incoming.syncUuid]
                if (existing == null || isIncomingNewer(incoming.updatedAt, incoming.syncVersion, existing.updatedAt, existing.syncVersion)) {
                    mergedStreamByUuid[incoming.syncUuid] = incoming
                }
            }
            payload.automationRules.forEach { dto ->
                val incoming = dto.toDomain()
                val existing = mergedRulesByUuid[incoming.syncUuid]
                if (existing == null || isIncomingNewer(incoming.updatedAt, incoming.syncVersion, existing.updatedAt, existing.syncVersion)) {
                    mergedRulesByUuid[incoming.syncUuid] = incoming
                }
            }
        }

        // 3. Download any missing canonical media blobs referenced by pulled active notes/stream items BEFORE DB apply
        var mediaDownloaded = 0
        val downloadedMediaKeys = mutableSetOf<String>()
        val pulledMediaKeys = collectReferencedCanonicalMediaKeys(
            notes = mergedNotesByUuid.values,
            streamItems = mergedStreamByUuid.values
        )
        for (mediaKey in pulledMediaKeys) {
            if (localStore.hasLocalMedia(mediaKey)) continue
            val dlRes = remoteDataSource.downloadMediaBlob(mediaKey)
            val encryptedBlob = when (dlRes) {
                is AppResult.Failure -> return dlRes
                is AppResult.Success -> dlRes.data
            }
            val decBytesRes = vaultCrypto.decryptMediaBytesStrict(encryptedBlob, keyChars)
            val plainBytes = when (decBytesRes) {
                is AppResult.Failure -> return decBytesRes
                is AppResult.Success -> decBytesRes.data
            }
            val expectedSha = extractSha256FromCanonicalKey(mediaKey)
            if (expectedSha != null && sha256Hex(plainBytes) != expectedSha) {
                return AppResult.Failure(DataError.Security.INTEGRITY_MISMATCH)
            }
            when (val writeRes = localStore.writeLocalMediaBytes(mediaKey, plainBytes)) {
                is AppResult.Failure -> return writeRes
                is AppResult.Success -> {
                    mediaDownloaded++
                    downloadedMediaKeys += mediaKey
                }
            }
        }

        // 4. Conflict resolution & ordered batch commit (Categories -> Notes -> Links/Stream/Rules)
        var conflictCopiesCreated = 0
        val skippedEchoCatStamps = mutableMapOf<String, Pair<Long, Long>>()
        val skippedEchoNoteStamps = mutableMapOf<String, Pair<Long, Long>>()
        val skippedEchoLinkStamps = mutableMapOf<String, Pair<Long, Long>>()
        val skippedEchoStreamStamps = mutableMapOf<String, Pair<Long, Long>>()
        val skippedEchoRuleStamps = mutableMapOf<String, Pair<Long, Long>>()

        if (mergedCategoriesByUuid.isNotEmpty() || mergedNotesByUuid.isNotEmpty() ||
            mergedLinksByUuid.isNotEmpty() || mergedStreamByUuid.isNotEmpty() || mergedRulesByUuid.isNotEmpty()
        ) {
            val existingCats = localStore.findCategoriesBySyncUuids(mergedCategoriesByUuid.keys)
            val resolvedCats = mergedCategoriesByUuid.values.map { remote ->
                val resolved = conflictResolver.mergeCategory(existingCats[remote.syncUuid], remote, initialCursorState.lastSyncedAt)
                if (resolved.syncVersion == remote.syncVersion && resolved.updatedAt == remote.updatedAt) {
                    skippedEchoCatStamps[resolved.syncUuid] = resolved.updatedAt to resolved.syncVersion
                }
                resolved
            }

            val existingNotes = localStore.findNotesBySyncUuids(mergedNotesByUuid.keys)
            val resolvedNotes = mutableListOf<Note>()
            for (remoteNote in mergedNotesByUuid.values) {
                val res = conflictResolver.mergeNote(existingNotes[remoteNote.syncUuid], remoteNote, initialCursorState.lastSyncedAt)
                resolvedNotes += res.primary
                if (res.conflictCopy == null &&
                    res.primary.syncVersion == remoteNote.syncVersion &&
                    res.primary.updatedAt == remoteNote.updatedAt
                ) {
                    skippedEchoNoteStamps[res.primary.syncUuid] = res.primary.updatedAt to res.primary.syncVersion
                }
                res.conflictCopy?.let {
                    resolvedNotes += it
                    conflictCopiesCreated++
                }
            }

            val existingLinks = localStore.findLinksBySyncUuids(mergedLinksByUuid.keys)
            val resolvedLinks = mergedLinksByUuid.values.map { remote ->
                val resolved = conflictResolver.mergeNoteLink(existingLinks[remote.syncUuid], remote)
                if (resolved.syncVersion == remote.syncVersion && resolved.updatedAt == remote.updatedAt) {
                    skippedEchoLinkStamps[resolved.syncUuid] = resolved.updatedAt to resolved.syncVersion
                }
                resolved
            }

            val existingStream = localStore.findStreamItemsBySyncUuids(mergedStreamByUuid.keys)
            val resolvedStream = mergedStreamByUuid.values.map { remote ->
                val resolved = conflictResolver.mergeStreamItem(existingStream[remote.syncUuid], remote)
                if (resolved.syncVersion == remote.syncVersion && resolved.updatedAt == remote.updatedAt) {
                    skippedEchoStreamStamps[resolved.syncUuid] = resolved.updatedAt to resolved.syncVersion
                }
                resolved
            }

            val existingRules = localStore.findRulesBySyncUuids(mergedRulesByUuid.keys)
            val resolvedRules = mergedRulesByUuid.values.map { remote ->
                val resolved = conflictResolver.mergeAutomationRule(existingRules[remote.syncUuid], remote)
                if (resolved.syncVersion == remote.syncVersion && resolved.updatedAt == remote.updatedAt) {
                    skippedEchoRuleStamps[resolved.syncUuid] = resolved.updatedAt to resolved.syncVersion
                }
                resolved
            }

            when (
                val applyRes = localStore.applyResolvedRemoteBatch(
                    ResolvedRemoteBatch(
                        categories = resolvedCats,
                        notes = resolvedNotes,
                        noteLinks = resolvedLinks,
                        streamItems = resolvedStream,
                        automationRules = resolvedRules
                    )
                )
            ) {
                is AppResult.Failure -> return applyRes
                is AppResult.Success -> Unit
            }
        }

        // 5. Push local modifications since lastPushedAt (filtering out pure remote fast-forwards just applied)
        val snapshotRes = localStore.readModifiedSince(initialCursorState.lastPushedAt)
        val rawSnapshot = when (snapshotRes) {
            is AppResult.Failure -> return snapshotRes
            is AppResult.Success -> snapshotRes.data
        }
        val snapshot = LocalSyncSnapshot(
            categories = rawSnapshot.categories.filterNot { skippedEchoCatStamps[it.syncUuid] == (it.updatedAt to it.syncVersion) },
            notes = rawSnapshot.notes.filterNot { skippedEchoNoteStamps[it.syncUuid] == (it.updatedAt to it.syncVersion) },
            noteLinks = rawSnapshot.noteLinks.filterNot { skippedEchoLinkStamps[it.syncUuid] == (it.updatedAt to it.syncVersion) },
            streamItems = rawSnapshot.streamItems.filterNot { skippedEchoStreamStamps[it.syncUuid] == (it.updatedAt to it.syncVersion) },
            automationRules = rawSnapshot.automationRules.filterNot { skippedEchoRuleStamps[it.syncUuid] == (it.updatedAt to it.syncVersion) }
        )

        // Upload referenced canonical media blobs that haven't been uploaded yet
        val alreadyUploaded = initialCursorState.uploadedMediaKeys + downloadedMediaKeys
        val localMediaKeys = collectReferencedCanonicalMediaKeys(snapshot.notes, snapshot.streamItems)
        val newlyUploadedKeys = mutableSetOf<String>()
        var mediaUploaded = 0

        for (mediaKey in localMediaKeys) {
            if (mediaKey in alreadyUploaded) continue
            val rawBytes = localStore.readLocalMediaBytes(mediaKey) ?: continue
            val encBytesRes = vaultCrypto.encryptMediaBytes(rawBytes, keyChars)
            val encBytes = when (encBytesRes) {
                is AppResult.Failure -> return encBytesRes
                is AppResult.Success -> encBytesRes.data
            }
            when (val upRes = remoteDataSource.uploadMediaBlob(mediaKey, encBytes)) {
                is AppResult.Failure -> return upRes
                is AppResult.Success -> {
                    newlyUploadedKeys += mediaKey
                    mediaUploaded++
                }
            }
        }

        val now = clock.nowMillis()
        val outgoingPayload = SyncDeltaPayload(
            deviceId = deviceId,
            emittedAt = now,
            categories = snapshot.categories.map { it.toSyncDto() },
            notes = snapshot.notes.map { it.toSyncDto() },
            noteLinks = snapshot.noteLinks.map { it.toSyncDto() },
            streamItems = snapshot.streamItems.map { it.toSyncDto() },
            automationRules = snapshot.automationRules.map { it.toSyncDto() }
        )

        var pushedEntities = 0
        var finalPushAt = initialCursorState.lastPushedAt
        if (!outgoingPayload.isEmpty()) {
            val payloadJson = json.encodeToString(outgoingPayload)
            val encPayloadRes = vaultCrypto.encryptWithPassphrase(payloadJson, keyChars)
            val encPayload = when (encPayloadRes) {
                is AppResult.Failure -> return encPayloadRes
                is AppResult.Success -> encPayloadRes.data
            }

            val pushRes = remoteDataSource.pushDelta(
                EncryptedDeltaEnvelope(
                    batchId = "${now}_${sharedRandomUuid().take(8)}",
                    sourceDeviceId = deviceId,
                    encryptedPayload = encPayload,
                    baseCursor = workingCursor
                )
            )
            val ack = when (pushRes) {
                is AppResult.Failure -> return pushRes
                is AppResult.Success -> pushRes.data
            }
            workingCursor = ack.newCursor.ifBlank { workingCursor }
            pushedEntities = snapshot.categories.size +
                snapshot.notes.size +
                snapshot.noteLinks.size +
                snapshot.streamItems.size +
                snapshot.automationRules.size
            finalPushAt = maxOf(
                now,
                snapshot.notes.maxOfOrNull { it.updatedAt } ?: 0L,
                snapshot.categories.maxOfOrNull { it.updatedAt } ?: 0L,
                snapshot.noteLinks.maxOfOrNull { it.updatedAt } ?: 0L,
                snapshot.streamItems.maxOfOrNull { it.updatedAt } ?: 0L,
                snapshot.automationRules.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }

        localStore.saveSyncCursorState(
            SyncCursorState(
                remoteCursor = workingCursor,
                lastPushedAt = finalPushAt,
                lastSyncedAt = now,
                deviceId = deviceId,
                uploadedMediaKeys = alreadyUploaded + newlyUploadedKeys
            )
        )

        return AppResult.Success(
            SyncSummary(
                pulledCategories = mergedCategoriesByUuid.size,
                pulledNotes = mergedNotesByUuid.size,
                pulledLinks = mergedLinksByUuid.size,
                pulledStreamItems = mergedStreamByUuid.size,
                pulledRules = mergedRulesByUuid.size,
                pushedEntities = pushedEntities,
                conflictCopiesCreated = conflictCopiesCreated,
                mediaUploaded = mediaUploaded,
                mediaDownloaded = mediaDownloaded,
                syncedAt = now
            )
        )
    }

    private fun isIncomingNewer(
        inUpdated: Long,
        inVersion: Long,
        curUpdated: Long,
        curVersion: Long
    ): Boolean = if (inUpdated != curUpdated) inUpdated > curUpdated else inVersion > curVersion

    private fun collectReferencedCanonicalMediaKeys(
        notes: Collection<Note>,
        streamItems: Collection<StreamItem>
    ): Set<String> {
        val keys = linkedSetOf<String>()
        for (note in notes) {
            if (note.deletedAt != null) continue
            note.images.orEmpty().forEach { path -> if (isCanonicalMediaKey(path)) keys += path }
            note.sounds.orEmpty().forEach { path -> if (isCanonicalMediaKey(path)) keys += path }
            val desc = note.description
            if (!desc.isNullOrBlank() && desc.startsWith("NOTION_BLOCKS_V1:")) {
                BlockParser.parse(desc).forEach { blk ->
                    val path = blk.mediaPath
                    if (!path.isNullOrBlank() && isCanonicalMediaKey(path)) {
                        keys += path
                    }
                }
            }
        }
        for (item in streamItems) {
            if (item.deletedAt != null) continue
            item.audioPath?.takeIf { isCanonicalMediaKey(it) }?.let { keys += it }
            item.imagePath?.takeIf { isCanonicalMediaKey(it) }?.let { keys += it }
        }
        return keys
    }

    private fun isCanonicalMediaKey(path: String): Boolean =
        CANONICAL_MEDIA_REGEX.matches(path.replace('\\', '/').trimStart('/'))

    private fun extractSha256FromCanonicalKey(mediaKey: String): String? {
        val fileName = mediaKey.substringAfterLast('/')
        val hexPart = fileName.substringBeforeLast('.')
        return if (hexPart.length == 64 && hexPart.all { it in '0'..'9' || it in 'a'..'f' }) {
            hexPart
        } else null
    }

    companion object {
        private const val MAX_PULL_PAGES = 50
        private val CANONICAL_MEDIA_REGEX =
            Regex("^media/(images|drawings|audio|files)/[a-f0-9]{64}\\.[a-z0-9]{1,8}$")
    }
}
