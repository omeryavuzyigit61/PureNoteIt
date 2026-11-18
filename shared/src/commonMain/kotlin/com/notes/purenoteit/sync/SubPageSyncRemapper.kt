package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType

/**
 * Remaps cross-device subpage and parent integer pointers using canonical sync UUIDs
 * and strips stale foreign integer IDs so fresh installs never collide with unrelated local notes.
 */
object SubPageSyncRemapper {

    /**
     * Computes updated active notes whose `SUB_PAGE` block IDs or subpage parent metadata needed
     * remapping or sanitization, without modifying `updatedAt` or `syncVersion`.
     */
    fun remapActiveNotes(
        allNotes: List<Note>,
        allLinks: List<NoteLink>,
        remoteBatchNoteUuids: Set<String>
    ): List<Note> {
        val activeNotes = allNotes.filter { it.deletedAt == null }
        if (activeNotes.isEmpty()) return emptyList()

        val activeByUuid = activeNotes.associateBy { it.syncUuid }
        val activeById = activeNotes.filter { it.id > 0 }.associateBy { it.id }
        val activeLinks = allLinks.filter { it.deletedAt == null }
        val linksBySourceUuid = activeLinks.groupBy { it.sourceSyncUuid }
        val linksByTargetUuid = activeLinks.groupBy { it.targetSyncUuid }

        val parsedSubBlocksByParentUuid = activeNotes.associate { parent ->
            val desc = parent.description
            val subBlocks = if (!desc.isNullOrBlank() && desc.startsWith("NOTION_BLOCKS_V1:")) {
                BlockParser.parse(desc).filter { it.type == BlockType.SUB_PAGE }
            } else {
                emptyList()
            }
            parent.syncUuid to subBlocks
        }

        val updates = ArrayList<Note>()
        for (note in activeNotes) {
            val hasSubBlocks = note.description?.contains("SUB_PAGE") == true
            val hasRichSubMeta = BlockParser.isSubPageMetadata(note.richBlocksJson)
            if (!hasSubBlocks && !hasRichSubMeta) continue

            val isRemoteNote = note.syncUuid in remoteBatchNoteUuids
            var changed = false
            var updatedDesc = note.description

            if (hasSubBlocks) {
                val blocks = BlockParser.parse(note.description)
                var blocksChanged = false
                val remappedBlocks = blocks.map { blk ->
                    if (blk.type != BlockType.SUB_PAGE) {
                        blk
                    } else {
                        val subUuid = blk.subPageSyncUuid?.takeIf { it.isNotBlank() }
                        if (subUuid != null) {
                            val targetChild = activeByUuid[subUuid]
                            val desiredId = targetChild?.id
                            if (blk.subPageNoteId != desiredId) {
                                blocksChanged = true
                                blk.copy(subPageNoteId = desiredId)
                            } else {
                                blk
                            }
                        } else {
                            val localCandidate = blk.subPageNoteId?.let { activeById[it] }
                            val localCandidateValid = !isRemoteNote &&
                                localCandidate != null &&
                                (
                                    BlockParser.isSubPageMetadata(localCandidate.richBlocksJson) ||
                                        (
                                            blk.content.isNotBlank() &&
                                                localCandidate.title.orEmpty().trim().equals(blk.content.trim(), ignoreCase = true)
                                            )
                                    )
                            if (localCandidateValid) {
                                blk
                            } else {
                                val resolvedChild = resolveLegacySubPageChild(
                                    parentNote = note,
                                    blockTitle = blk.content,
                                    activeNotes = activeNotes,
                                    activeByUuid = activeByUuid,
                                    outgoingLinks = linksBySourceUuid[note.syncUuid].orEmpty()
                                )
                                if (resolvedChild != null) {
                                    blocksChanged = true
                                    blk.copy(
                                        subPageNoteId = resolvedChild.id,
                                        subPageSyncUuid = resolvedChild.syncUuid
                                    )
                                } else if (blk.subPageNoteId != null) {
                                    // Clear foreign/stale integer IDs that could collide with unrelated local notes.
                                    blocksChanged = true
                                    blk.copy(subPageNoteId = null)
                                } else {
                                    blk
                                }
                            }
                        }
                    }
                }
                if (blocksChanged) {
                    updatedDesc = BlockParser.serialize(remappedBlocks)
                    changed = true
                }
            }

            var updatedRich = note.richBlocksJson
            val meta = BlockParser.parseRichMetadata(updatedRich)
            if (meta != null && (meta.isSubPage || meta.parentNoteId != null || !meta.parentSyncUuid.isNullOrBlank())) {
                val parentUuid = meta.parentSyncUuid?.takeIf { it.isNotBlank() }
                if (parentUuid != null) {
                    val parent = activeByUuid[parentUuid]
                    val desiredParentId = parent?.id
                    if (!meta.isSubPage || meta.parentNoteId != desiredParentId) {
                        updatedRich = BlockParser.buildSubPageMetadataJson(desiredParentId, parentUuid)
                        changed = true
                    }
                } else {
                    val localParent = meta.parentNoteId?.let { activeById[it] }
                    val localParentValid = !isRemoteNote &&
                        localParent != null &&
                        parentReferencesChild(
                            parentBlocks = parsedSubBlocksByParentUuid[localParent.syncUuid].orEmpty(),
                            child = note
                        )
                    if (!localParentValid) {
                        val resolvedParent = resolveLegacyParentForSubPage(
                            child = note,
                            activeNotes = activeNotes,
                            activeByUuid = activeByUuid,
                            parsedSubBlocksByParentUuid = parsedSubBlocksByParentUuid,
                            incomingLinks = linksByTargetUuid[note.syncUuid].orEmpty()
                        )
                        val desiredParentId = resolvedParent?.id
                        val desiredParentUuid = resolvedParent?.syncUuid
                        if (desiredParentId != meta.parentNoteId || desiredParentUuid != null) {
                            updatedRich = BlockParser.buildSubPageMetadataJson(desiredParentId, desiredParentUuid)
                            changed = true
                        }
                    }
                }
            }

            if (changed) {
                updates += note.copy(description = updatedDesc, richBlocksJson = updatedRich)
            }
        }
        return updates
    }

    private fun resolveLegacySubPageChild(
        parentNote: Note,
        blockTitle: String,
        activeNotes: List<Note>,
        activeByUuid: Map<String, Note>,
        outgoingLinks: List<NoteLink>
    ): Note? {
        val cleanTitle = blockTitle.trim()
        // 1. Child declares parentSyncUuid matching parentNote
        val byDeclaredParent = activeNotes.filter { candidate ->
            val meta = BlockParser.parseRichMetadata(candidate.richBlocksJson)
            meta?.parentSyncUuid == parentNote.syncUuid
        }
        if (cleanTitle.isNotBlank()) {
            byDeclaredParent.firstOrNull {
                it.title.orEmpty().trim().equals(cleanTitle, ignoreCase = true)
            }?.let { return it }
        } else if (byDeclaredParent.size == 1) {
            return byDeclaredParent.first()
        }

        // 2. Active NoteLink from parent to subpage with matching title
        if (cleanTitle.isNotBlank()) {
            val linkedTarget = outgoingLinks.firstOrNull {
                it.linkText.trim().equals(cleanTitle, ignoreCase = true)
            }?.let { activeByUuid[it.targetSyncUuid] }
            if (linkedTarget != null && BlockParser.isSubPageMetadata(linkedTarget.richBlocksJson)) {
                return linkedTarget
            }

            // 3. Subpage-marked note without parentSyncUuid matching title cleanly
            activeNotes.firstOrNull { candidate ->
                val meta = BlockParser.parseRichMetadata(candidate.richBlocksJson)
                BlockParser.isSubPageMetadata(candidate.richBlocksJson) &&
                    meta?.parentSyncUuid.isNullOrBlank() &&
                    candidate.title.orEmpty().trim().equals(cleanTitle, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    private fun parentReferencesChild(
        parentBlocks: List<com.notes.purenoteit.model.NoteBlock>,
        child: Note
    ): Boolean {
        val cleanChildTitle = child.title.orEmpty().trim()
        return parentBlocks.any { blk ->
            (blk.subPageSyncUuid == child.syncUuid) ||
                (blk.subPageNoteId != null && blk.subPageNoteId == child.id &&
                    (cleanChildTitle.isBlank() || blk.content.trim().equals(cleanChildTitle, ignoreCase = true)))
        }
    }

    private fun resolveLegacyParentForSubPage(
        child: Note,
        activeNotes: List<Note>,
        activeByUuid: Map<String, Note>,
        parsedSubBlocksByParentUuid: Map<String, List<com.notes.purenoteit.model.NoteBlock>>,
        incomingLinks: List<NoteLink>
    ): Note? {
        activeNotes.firstOrNull { parent ->
            parsedSubBlocksByParentUuid[parent.syncUuid].orEmpty().any { it.subPageSyncUuid == child.syncUuid }
        }?.let { return it }

        val cleanChildTitle = child.title.orEmpty().trim()
        if (cleanChildTitle.isNotBlank()) {
            incomingLinks.firstOrNull {
                it.linkText.trim().equals(cleanChildTitle, ignoreCase = true)
            }?.let { activeByUuid[it.sourceSyncUuid] }?.let { return it }

            activeNotes.firstOrNull { parent ->
                parsedSubBlocksByParentUuid[parent.syncUuid].orEmpty().any {
                    it.content.trim().equals(cleanChildTitle, ignoreCase = true)
                }
            }?.let { return it }
        }
        return null
    }
}
