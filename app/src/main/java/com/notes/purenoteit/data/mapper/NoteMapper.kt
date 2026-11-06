package com.notes.purenoteit.data.mapper

import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.domain.model.Note

internal fun NoteData.toDomain(): Note = Note(
    id = id,
    title = title,
    description = description,
    date = date,
    isPassword = isPassword,
    images = images,
    categoryId = categoryId,
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
    richBlocksJson = richBlocksJson
)

internal fun Note.toEntity(): NoteData = NoteData(
    title = title,
    description = description,
    date = date,
    isPassword = isPassword,
    images = images,
    categoryId = categoryId,
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
    richBlocksJson = richBlocksJson
).also { it.id = this.id }
