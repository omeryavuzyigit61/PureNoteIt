package com.notes.purenoteit.domain.model

data class NoteLink(
    val id: Int = 0,
    val sourceNoteId: Int,
    val targetNoteId: Int,
    val linkText: String,
    val createdAt: Long = 0L,
    val syncUuid: String = "",
    val sourceSyncUuid: String = "",
    val targetSyncUuid: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncVersion: Long = 1L
)
