package com.notes.purenoteit.domain.model

data class Note(
    val id: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val date: Long = 0L,
    val isPassword: Boolean? = null,
    val images: List<String>? = null,
    val categoryId: Int? = null,
    val sounds: List<String>? = null,
    val isPinned: Boolean = false,
    val icon: String? = null,
    val isEncrypted: Boolean = false,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val status: String = "TODO",
    val reminderTime: Long? = null,
    val reminderType: String? = null,
    val reminderSound: String? = null,
    val isReminderDone: Boolean = false,
    val richBlocksJson: String? = null,
    val syncUuid: String = "",
    val categorySyncUuid: String? = null,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncVersion: Long = 1L
)
