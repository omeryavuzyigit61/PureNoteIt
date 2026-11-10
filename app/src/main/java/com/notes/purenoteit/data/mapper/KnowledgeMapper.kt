package com.notes.purenoteit.data.mapper

import com.notes.purenoteit.data.db.AutomationRuleData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemData
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import java.util.UUID

internal fun NoteLinkData.toDomain(): NoteLink = NoteLink(
    id = id,
    sourceNoteId = sourceNoteId,
    targetNoteId = targetNoteId,
    linkText = linkText,
    createdAt = createdAt,
    syncUuid = syncUuid,
    sourceSyncUuid = sourceSyncUuid,
    targetSyncUuid = targetSyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

internal fun NoteLink.toEntity(nowMillis: Long = System.currentTimeMillis()): NoteLinkData = NoteLinkData(
    sourceNoteId = sourceNoteId,
    targetNoteId = targetNoteId,
    linkText = linkText,
    createdAt = if (createdAt > 0L) createdAt else nowMillis,
    syncUuid = syncUuid.ifBlank { UUID.randomUUID().toString() },
    sourceSyncUuid = sourceSyncUuid,
    targetSyncUuid = targetSyncUuid,
    updatedAt = if (updatedAt > 0L) updatedAt else nowMillis,
    deletedAt = deletedAt,
    syncVersion = syncVersion.coerceAtLeast(1L)
).also { it.id = this.id }

internal fun StreamItemData.toDomain(): StreamItem = StreamItem(
    id = id,
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = timestamp,
    isProcessed = isProcessed,
    mergedNoteId = mergedNoteId,
    syncUuid = syncUuid,
    mergedNoteSyncUuid = mergedNoteSyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

internal fun StreamItem.toEntity(nowMillis: Long = System.currentTimeMillis()): StreamItemData = StreamItemData(
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = if (timestamp > 0L) timestamp else nowMillis,
    isProcessed = isProcessed,
    mergedNoteId = mergedNoteId,
    syncUuid = syncUuid.ifBlank { UUID.randomUUID().toString() },
    mergedNoteSyncUuid = mergedNoteSyncUuid,
    updatedAt = if (updatedAt > 0L) updatedAt else nowMillis,
    deletedAt = deletedAt,
    syncVersion = syncVersion.coerceAtLeast(1L)
).also { it.id = this.id }

internal fun AutomationRuleData.toDomain(): AutomationRule = AutomationRule(
    id = id,
    title = title,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
    isEnabled = isEnabled,
    createdAt = createdAt,
    syncUuid = syncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

internal fun AutomationRule.toEntity(nowMillis: Long = System.currentTimeMillis()): AutomationRuleData = AutomationRuleData(
    title = title,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
    isEnabled = isEnabled,
    createdAt = if (createdAt > 0L) createdAt else nowMillis,
    syncUuid = syncUuid.ifBlank {
        when (id) {
            1 -> "rule-default-1"
            2 -> "rule-default-2"
            else -> UUID.randomUUID().toString()
        }
    },
    updatedAt = if (updatedAt > 0L) updatedAt else nowMillis,
    deletedAt = deletedAt,
    syncVersion = syncVersion.coerceAtLeast(1L)
).also { it.id = this.id }
