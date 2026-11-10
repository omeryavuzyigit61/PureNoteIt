package com.notes.purenoteit.data.mapper

import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.domain.model.Category
import java.util.UUID

internal fun CategoryData.toDomain(): Category = Category(
    id = id,
    name = name,
    isPassword = isPassword,
    syncUuid = syncUuid,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion
)

internal fun Category.toEntity(): CategoryData = CategoryData(
    name = name,
    isPassword = isPassword,
    id = id,
    syncUuid = syncUuid.ifBlank { defaultCategorySyncUuid(id, name) ?: UUID.randomUUID().toString() },
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncVersion = syncVersion.coerceAtLeast(1L)
)

internal fun defaultCategorySyncUuid(id: Int, name: String): String? = when {
    id == 1 || name == "Not" -> "cat-default-1"
    id == 2 || name == "Günlük" -> "cat-default-2"
    id == 3 || name == "Önemli" -> "cat-default-3"
    else -> null
}
