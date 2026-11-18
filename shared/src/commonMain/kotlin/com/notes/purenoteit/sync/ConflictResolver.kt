package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType

data class NoteMergeResolution(
    val primary: Note,
    val conflictCopy: Note? = null
)

/**
 * Lossless cross-device conflict resolver supporting:
 * - Fast-forward updates when only the remote device edited a record after [lastSyncedAt]
 * - Automatic `[Çakışma Kopyası]` branch creation on concurrent diverged edits
 * - Tombstone (`deletedAt`) vs active edit reconciliation without silent data loss
 */
class ConflictResolver(
    private val clock: AppClock,
    private val uuidGenerator: () -> String = { sharedRandomUuid() }
) {
    fun mergeCategory(local: Category?, remote: Category, lastSyncedAt: Long = 0L): Category {
        if (local == null) return remote.copy(id = 0)
        val mergedVersion = maxOf(local.syncVersion, remote.syncVersion)

        if (remote.deletedAt != null && local.deletedAt == null) {
            return if (local.updatedAt > remote.deletedAt) {
                local.copy(deletedAt = null, syncVersion = mergedVersion + 1L)
            } else {
                local.copy(
                    deletedAt = remote.deletedAt,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                    syncVersion = mergedVersion
                )
            }
        }
        if (local.deletedAt != null && remote.deletedAt == null) {
            return if (remote.updatedAt > local.deletedAt) {
                remote.copy(id = local.id, deletedAt = null, syncVersion = mergedVersion + 1L)
            } else {
                local.copy(syncVersion = mergedVersion + 1L)
            }
        }

        val preferRemote = isRemotePreferred(
            localUpdated = local.updatedAt,
            localVersion = local.syncVersion,
            remoteUpdated = remote.updatedAt,
            remoteVersion = remote.syncVersion,
            lastSyncedAt = lastSyncedAt
        )
        return if (preferRemote) {
            remote.copy(
                id = local.id,
                updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                syncVersion = mergedVersion
            )
        } else {
            local.copy(syncVersion = mergedVersion + 1L)
        }
    }

    fun mergeNote(local: Note?, remote: Note, lastSyncedAt: Long = 0L): NoteMergeResolution {
        if (local == null) {
            return NoteMergeResolution(primary = remote.copy(id = 0))
        }

        val maxVersion = maxOf(local.syncVersion, remote.syncVersion)

        // Case 1: Remote tombstone/trash vs local active edit
        if (remote.deletedAt != null && local.deletedAt == null) {
            return if (local.updatedAt > remote.deletedAt) {
                NoteMergeResolution(
                    primary = local.copy(
                        deletedAt = null,
                        syncVersion = maxVersion + 1L
                    )
                )
            } else if (remote.status.equals("TRASH", ignoreCase = true)) {
                val winner = if (remote.updatedAt >= local.updatedAt) remote else local
                NoteMergeResolution(
                    primary = winner.copy(
                        id = local.id,
                        syncUuid = local.syncUuid,
                        deletedAt = remote.deletedAt,
                        updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                        status = "TRASH",
                        syncVersion = maxVersion
                    )
                )
            } else {
                NoteMergeResolution(
                    primary = local.copy(
                        deletedAt = remote.deletedAt,
                        updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                        status = "DELETED",
                        syncVersion = maxVersion
                    )
                )
            }
        }

        // Case 2: Both tombstoned or trashed
        if (remote.deletedAt != null && local.deletedAt != null) {
            val remotePerm = !remote.status.equals("TRASH", ignoreCase = true)
            val localPerm = !local.status.equals("TRASH", ignoreCase = true)
            val resolvedStatus = when {
                remotePerm && (!localPerm || remote.updatedAt >= local.updatedAt) -> "DELETED"
                localPerm && (!remotePerm || local.updatedAt >= remote.updatedAt) -> "DELETED"
                remote.status.equals("TRASH", ignoreCase = true) || local.status.equals("TRASH", ignoreCase = true) -> "TRASH"
                else -> "DELETED"
            }
            val winner = if (remote.updatedAt >= local.updatedAt) remote else local
            val resolvedDeletedAt = if (resolvedStatus == "TRASH") {
                winner.deletedAt ?: maxOf(local.deletedAt, remote.deletedAt)
            } else {
                minOf(local.deletedAt, remote.deletedAt)
            }
            return NoteMergeResolution(
                primary = winner.copy(
                    id = local.id,
                    syncUuid = local.syncUuid,
                    deletedAt = resolvedDeletedAt,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                    status = resolvedStatus,
                    syncVersion = maxVersion
                )
            )
        }

        // Case 3: Local tombstoned/trashed vs remote active edit
        if (local.deletedAt != null && remote.deletedAt == null) {
            val cutoff = if (local.status.equals("DELETED", ignoreCase = true)) {
                maxOf(local.deletedAt, local.updatedAt)
            } else {
                local.deletedAt
            }
            return if (remote.updatedAt > cutoff) {
                NoteMergeResolution(
                    primary = remote.copy(
                        id = local.id,
                        deletedAt = null,
                        syncVersion = maxVersion + 1L
                    )
                )
            } else {
                NoteMergeResolution(primary = local.copy(syncVersion = maxVersion + 1L))
            }
        }

        // Case 4: Both active notes
        if (hasSameNotePayload(local, remote)) {
            val winnerIsRemote = remote.updatedAt >= local.updatedAt
            val merged = if (winnerIsRemote) {
                remote.copy(id = local.id, syncVersion = maxVersion)
            } else {
                local.copy(syncVersion = maxVersion)
            }
            return NoteMergeResolution(primary = merged)
        }

        val localModifiedSinceSync = lastSyncedAt == 0L || local.updatedAt > lastSyncedAt
        val remoteModifiedSinceSync = lastSyncedAt == 0L || remote.updatedAt > lastSyncedAt

        // If local was NOT edited after lastSyncedAt, never branch a false conflict copy
        if (!localModifiedSinceSync) {
            val acceptRemote = remoteModifiedSinceSync ||
                remote.syncVersion >= local.syncVersion ||
                remote.updatedAt >= local.updatedAt
            return NoteMergeResolution(
                primary = if (acceptRemote) {
                    remote.copy(
                        id = local.id,
                        syncVersion = maxVersion
                    )
                } else {
                    local.copy(syncVersion = maxVersion)
                }
            )
        }

        // Local-only change vs stale remote revision
        if (!remoteModifiedSinceSync) {
            return NoteMergeResolution(
                primary = local.copy(syncVersion = maxVersion + 1L)
            )
        }

        // True concurrent offline conflict with diverged contents: winner owns primary UUID,
        // loser is branched as a standalone `[Çakışma Kopyası]` note with zero data loss.
        val remoteWinsPrimary = when {
            remote.updatedAt != local.updatedAt -> remote.updatedAt > local.updatedAt
            else -> remote.syncVersion > local.syncVersion
        }
        val winner = if (remoteWinsPrimary) remote else local
        val loser = if (remoteWinsPrimary) local else remote

        val primaryResolved = winner.copy(
            id = local.id,
            syncUuid = local.syncUuid,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            deletedAt = null,
            syncVersion = maxVersion + 1L
        )

        val now = clock.nowMillis()
        val conflictCopy = loser.copy(
            id = 0,
            syncUuid = uuidGenerator(),
            title = formatConflictTitle(loser.title),
            updatedAt = now,
            deletedAt = null,
            status = if (loser.status == "TRASH" || loser.status == "DELETED") "TODO" else loser.status,
            syncVersion = 1L
        )

        return NoteMergeResolution(primary = primaryResolved, conflictCopy = conflictCopy)
    }

    fun mergeNoteLink(local: NoteLink?, remote: NoteLink): NoteLink {
        if (local == null) return remote.copy(id = 0)
        val winner = if (remote.updatedAt >= local.updatedAt) remote else local
        return winner.copy(
            id = local.id,
            sourceNoteId = local.sourceNoteId,
            targetNoteId = local.targetNoteId,
            deletedAt = remote.deletedAt ?: local.deletedAt,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            syncVersion = maxOf(local.syncVersion, remote.syncVersion)
        )
    }

    fun mergeStreamItem(local: StreamItem?, remote: StreamItem): StreamItem {
        if (local == null) return remote.copy(id = 0)
        val winner = if (remote.updatedAt >= local.updatedAt) remote else local
        return winner.copy(
            id = local.id,
            mergedNoteId = local.mergedNoteId,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            syncVersion = maxOf(local.syncVersion, remote.syncVersion)
        )
    }

    fun mergeAutomationRule(local: AutomationRule?, remote: AutomationRule): AutomationRule {
        if (local == null) return remote.copy(id = 0)
        val winner = if (remote.updatedAt >= local.updatedAt) remote else local
        return winner.copy(
            id = local.id,
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            syncVersion = maxOf(local.syncVersion, remote.syncVersion)
        )
    }

    private fun isRemotePreferred(
        localUpdated: Long,
        localVersion: Long,
        remoteUpdated: Long,
        remoteVersion: Long,
        lastSyncedAt: Long
    ): Boolean {
        val localDirty = lastSyncedAt == 0L || localUpdated > lastSyncedAt
        val remoteDirty = lastSyncedAt == 0L || remoteUpdated > lastSyncedAt
        return when {
            !localDirty && remoteDirty -> true
            !localDirty && !remoteDirty -> remoteVersion >= localVersion || remoteUpdated >= localUpdated
            localDirty && !remoteDirty -> false
            remoteUpdated != localUpdated -> remoteUpdated > localUpdated
            else -> remoteVersion >= localVersion
        }
    }

    private fun hasSameNotePayload(a: Note, b: Note): Boolean =
        (a.title?.trim().orEmpty()) == (b.title?.trim().orEmpty()) &&
            hasSameDescription(a.description, b.description) &&
            a.status.ifBlank { "TODO" } == b.status.ifBlank { "TODO" } &&
            a.isPinned == b.isPinned &&
            (a.icon?.takeIf { it.isNotBlank() } ?: "📝") == (b.icon?.takeIf { it.isNotBlank() } ?: "📝") &&
            (a.isPassword == true) == (b.isPassword == true) &&
            a.isEncrypted == b.isEncrypted &&
            (a.categorySyncUuid ?: "") == (b.categorySyncUuid ?: "") &&
            normalizedImageList(a) == normalizedImageList(b) &&
            (a.sounds.orEmpty() == b.sounds.orEmpty()) &&
            normalizeRichMetadataForComparison(a.richBlocksJson) == normalizeRichMetadataForComparison(b.richBlocksJson) &&
            a.reminderTime == b.reminderTime &&
            (a.reminderType ?: "") == (b.reminderType ?: "") &&
            (a.reminderSound ?: "") == (b.reminderSound ?: "") &&
            a.isReminderDone == b.isReminderDone

    private fun hasSameDescription(descA: String?, descB: String?): Boolean {
        val rawA = descA.orEmpty()
        val rawB = descB.orEmpty()
        if (!rawA.startsWith("NOTION_BLOCKS_V1:") || !rawB.startsWith("NOTION_BLOCKS_V1:")) {
            return normalizeDescriptionForComparison(rawA) == normalizeDescriptionForComparison(rawB)
        }
        val blocksA = normalizeBlocksForComparison(rawA)
        val blocksB = normalizeBlocksForComparison(rawB)
        if (blocksA.size != blocksB.size) return false
        return blocksA.indices.all { idx ->
            val blkA = blocksA[idx]
            val blkB = blocksB[idx]
            if (blkA.type == BlockType.SUB_PAGE &&
                blkB.type == BlockType.SUB_PAGE &&
                (blkA.subPageSyncUuid.isNullOrBlank() || blkB.subPageSyncUuid.isNullOrBlank())
            ) {
                blkA.copy(subPageSyncUuid = null) == blkB.copy(subPageSyncUuid = null)
            } else {
                blkA == blkB
            }
        }
    }

    private fun normalizeBlocksForComparison(raw: String): List<com.notes.purenoteit.model.NoteBlock> =
        BlockParser.parse(raw)
            .filterNot { it.type == BlockType.PAGE_RELATIONS }
            .map { blk ->
                blk.copy(
                    id = "",
                    subPageNoteId = if (blk.type == BlockType.SUB_PAGE) null else blk.subPageNoteId
                )
            }

    private fun normalizeDescriptionForComparison(description: String?): String {
        val raw = description.orEmpty()
        if (!raw.startsWith("NOTION_BLOCKS_V1:")) return raw
        return BlockParser.serialize(normalizeBlocksForComparison(raw))
    }

    private fun normalizedImageList(note: Note): List<String> {
        val desc = note.description.orEmpty()
        val drawingPaths = if (desc.startsWith("NOTION_BLOCKS_V1:")) {
            BlockParser.parse(desc)
                .mapNotNull { blk -> blk.mediaPath?.takeIf { blk.type == BlockType.DRAWING } }
                .toSet()
        } else {
            emptySet()
        }
        return note.images.orEmpty().filterNot { it in drawingPaths }.distinct()
    }

    private fun normalizeRichMetadataForComparison(richBlocksJson: String?): String {
        val meta = BlockParser.parseRichMetadata(richBlocksJson)
        if (meta != null && (meta.isSubPage || !meta.parentSyncUuid.isNullOrBlank())) {
            return "subpage:${meta.parentSyncUuid.orEmpty()}"
        }
        return richBlocksJson.orEmpty()
    }

    private fun formatConflictTitle(originalTitle: String?): String {
        val clean = originalTitle?.trim().orEmpty().ifBlank { "Adsız Not" }
        return if (clean.endsWith(CONFLICT_SUFFIX)) clean else "$clean $CONFLICT_SUFFIX"
    }

    companion object {
        const val CONFLICT_SUFFIX = "[Çakışma Kopyası]"
    }
}
