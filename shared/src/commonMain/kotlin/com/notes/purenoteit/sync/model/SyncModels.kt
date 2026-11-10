package com.notes.purenoteit.sync.model

import com.notes.purenoteit.domain.model.AutomationRule
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncCategoryDto(
    @SerialName("sync_uuid") val syncUuid: String,
    @SerialName("name") val name: String,
    @SerialName("is_password") val isPassword: Boolean? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L
)

@Serializable
data class SyncNoteDto(
    @SerialName("sync_uuid") val syncUuid: String,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("date") val date: Long = 0L,
    @SerialName("is_password") val isPassword: Boolean? = null,
    @SerialName("images") val images: List<String>? = null,
    @SerialName("category_sync_uuid") val categorySyncUuid: String? = null,
    @SerialName("sounds") val sounds: List<String>? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("icon") val icon: String? = null,
    @SerialName("is_encrypted") val isEncrypted: Boolean = false,
    @SerialName("total_tasks") val totalTasks: Int = 0,
    @SerialName("completed_tasks") val completedTasks: Int = 0,
    @SerialName("status") val status: String = "TODO",
    @SerialName("reminder_time") val reminderTime: Long? = null,
    @SerialName("reminder_type") val reminderType: String? = null,
    @SerialName("reminder_sound") val reminderSound: String? = null,
    @SerialName("is_reminder_done") val isReminderDone: Boolean = false,
    @SerialName("rich_blocks_json") val richBlocksJson: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L
)

@Serializable
data class SyncNoteLinkDto(
    @SerialName("sync_uuid") val syncUuid: String,
    @SerialName("source_sync_uuid") val sourceSyncUuid: String,
    @SerialName("target_sync_uuid") val targetSyncUuid: String,
    @SerialName("link_text") val linkText: String,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L
)

@Serializable
data class SyncStreamItemDto(
    @SerialName("sync_uuid") val syncUuid: String,
    @SerialName("content") val content: String,
    @SerialName("type") val type: String = "TEXT",
    @SerialName("audio_path") val audioPath: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("timestamp") val timestamp: Long = 0L,
    @SerialName("is_processed") val isProcessed: Boolean = false,
    @SerialName("merged_note_sync_uuid") val mergedNoteSyncUuid: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L
)

@Serializable
data class SyncAutomationRuleDto(
    @SerialName("sync_uuid") val syncUuid: String,
    @SerialName("title") val title: String,
    @SerialName("trigger_type") val triggerType: String,
    @SerialName("trigger_config") val triggerConfig: String? = null,
    @SerialName("action_type") val actionType: String,
    @SerialName("action_config") val actionConfig: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("sync_version") val syncVersion: Long = 1L
)

@Serializable
data class SyncDeltaPayload(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("device_id") val deviceId: String,
    @SerialName("emitted_at") val emittedAt: Long,
    @SerialName("categories") val categories: List<SyncCategoryDto> = emptyList(),
    @SerialName("notes") val notes: List<SyncNoteDto> = emptyList(),
    @SerialName("note_links") val noteLinks: List<SyncNoteLinkDto> = emptyList(),
    @SerialName("stream_items") val streamItems: List<SyncStreamItemDto> = emptyList(),
    @SerialName("automation_rules") val automationRules: List<SyncAutomationRuleDto> = emptyList()
) {
    fun isEmpty(): Boolean =
        categories.isEmpty() &&
            notes.isEmpty() &&
            noteLinks.isEmpty() &&
            streamItems.isEmpty() &&
            automationRules.isEmpty()
}

@Serializable
data class RemoteEncryptedChange(
    @SerialName("remote_id") val remoteId: String,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("modified_at") val modifiedAt: Long = 0L,
    @SerialName("removed") val isRemoteDeleted: Boolean = false,
    @SerialName("encrypted_envelope") val encryptedEnvelope: String? = null
)

@Serializable
data class RemoteSyncPullPage(
    @SerialName("changes") val changes: List<RemoteEncryptedChange>,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class EncryptedDeltaEnvelope(
    @SerialName("batch_id") val batchId: String,
    @SerialName("source_device_id") val sourceDeviceId: String,
    @SerialName("encrypted_payload") val encryptedPayload: String,
    @SerialName("base_cursor") val baseCursor: String? = null
)

@Serializable
data class RemoteSyncPushAck(
    @SerialName("remote_id") val remoteId: String,
    @SerialName("new_cursor") val newCursor: String,
    @SerialName("committed_at") val committedAt: Long
)

@Serializable
data class SyncCursorState(
    @SerialName("remote_cursor") val remoteCursor: String? = null,
    @SerialName("last_pushed_at") val lastPushedAt: Long = 0L,
    @SerialName("last_synced_at") val lastSyncedAt: Long = 0L,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("uploaded_media_keys") val uploadedMediaKeys: Set<String> = emptySet()
)

data class SyncSummary(
    val pulledCategories: Int = 0,
    val pulledNotes: Int = 0,
    val pulledLinks: Int = 0,
    val pulledStreamItems: Int = 0,
    val pulledRules: Int = 0,
    val pushedEntities: Int = 0,
    val conflictCopiesCreated: Int = 0,
    val mediaUploaded: Int = 0,
    val mediaDownloaded: Int = 0,
    val syncedAt: Long = 0L
)

fun Category.toSyncDto(): SyncCategoryDto = SyncCategoryDto(
    syncUuid = syncUuid,
    name = name,
    isPassword = isPassword,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun SyncCategoryDto.toDomain(): Category = Category(
    id = 0,
    name = name,
    isPassword = isPassword,
    syncUuid = syncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun Note.toSyncDto(): SyncNoteDto = SyncNoteDto(
    syncUuid = syncUuid,
    title = title,
    description = description,
    date = date,
    isPassword = isPassword,
    images = images,
    categorySyncUuid = categorySyncUuid,
    sounds = sounds,
    isPinned = isPinned,
    icon = icon,
    isEncrypted = isEncrypted,
    totalTasks = totalTasks,
    completedTasks = completedTasks,
    status = status,
    reminderTime = reminderTime,
    reminderType = reminderType,
    reminderSound = reminderSound,
    isReminderDone = isReminderDone,
    richBlocksJson = richBlocksJson,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun SyncNoteDto.toDomain(): Note = Note(
    id = 0,
    title = title,
    description = description,
    date = date,
    isPassword = isPassword,
    images = images,
    categoryId = null,
    sounds = sounds,
    isPinned = isPinned,
    icon = icon,
    isEncrypted = isEncrypted,
    totalTasks = totalTasks,
    completedTasks = completedTasks,
    status = status,
    reminderTime = reminderTime,
    reminderType = reminderType,
    reminderSound = reminderSound,
    isReminderDone = isReminderDone,
    richBlocksJson = richBlocksJson,
    syncUuid = syncUuid,
    categorySyncUuid = categorySyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun NoteLink.toSyncDto(): SyncNoteLinkDto = SyncNoteLinkDto(
    syncUuid = syncUuid,
    sourceSyncUuid = sourceSyncUuid,
    targetSyncUuid = targetSyncUuid,
    linkText = linkText,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun SyncNoteLinkDto.toDomain(): NoteLink = NoteLink(
    id = 0,
    sourceNoteId = 0,
    targetNoteId = 0,
    linkText = linkText,
    createdAt = createdAt,
    syncUuid = syncUuid,
    sourceSyncUuid = sourceSyncUuid,
    targetSyncUuid = targetSyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun StreamItem.toSyncDto(): SyncStreamItemDto = SyncStreamItemDto(
    syncUuid = syncUuid,
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = timestamp,
    isProcessed = isProcessed,
    mergedNoteSyncUuid = mergedNoteSyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun SyncStreamItemDto.toDomain(): StreamItem = StreamItem(
    id = 0,
    content = content,
    type = type,
    audioPath = audioPath,
    imagePath = imagePath,
    timestamp = timestamp,
    isProcessed = isProcessed,
    mergedNoteId = null,
    syncUuid = syncUuid,
    mergedNoteSyncUuid = mergedNoteSyncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun AutomationRule.toSyncDto(): SyncAutomationRuleDto = SyncAutomationRuleDto(
    syncUuid = syncUuid,
    title = title,
    triggerType = triggerType,
    triggerConfig = triggerConfig,
    actionType = actionType,
    actionConfig = actionConfig,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

fun SyncAutomationRuleDto.toDomain(): AutomationRule = AutomationRule(
    id = 0,
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
