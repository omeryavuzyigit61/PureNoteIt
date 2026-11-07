package com.notes.purenoteit.data.mapper

import com.notes.purenoteit.data.db.AutomationRuleData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemData
import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem

internal fun NoteLinkData.toDomain(): NoteLink = NoteLink(
    id = id,
    sourceNoteId = sourceNoteId,
    targetNoteId = targetNoteId,
    linkText = linkText,
    createdAt = createdAt
)

internal fun NoteLink.toEntity(): NoteLinkData = NoteLinkData(
    sourceNoteId = sourceNoteId,
    targetNoteId = targetNoteId,
    linkText = linkText,
    createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
).also { it.id = this.id }

internal fun StreamItemData.toDomain(): StreamItem = StreamItem(
    id = id,
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = timestamp,
    isProcessed = isProcessed,
    mergedNoteId = mergedNoteId
)

internal fun StreamItem.toEntity(): StreamItemData = StreamItemData(
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = if (timestamp > 0L) timestamp else System.currentTimeMillis(),
    isProcessed = isProcessed,
    mergedNoteId = mergedNoteId
).also { it.id = this.id }

internal fun AutomationRuleData.toDomain(): AutomationRule = AutomationRule(
    id = id,
    title = title,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
    isEnabled = isEnabled,
    createdAt = createdAt
)

internal fun AutomationRule.toEntity(): AutomationRuleData = AutomationRuleData(
    title = title,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
    isEnabled = isEnabled,
    createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis()
).also { it.id = this.id }
