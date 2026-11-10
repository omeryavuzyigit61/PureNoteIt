package com.notes.purenoteit.domain.model

data class StreamItem(
    val id: Int = 0,
    val content: String,
    val type: String = "TEXT",
    val audioPath: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = 0L,
    val isProcessed: Boolean = false,
    val mergedNoteId: Int? = null,
    val syncUuid: String = "",
    val mergedNoteSyncUuid: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncVersion: Long = 1L
)
