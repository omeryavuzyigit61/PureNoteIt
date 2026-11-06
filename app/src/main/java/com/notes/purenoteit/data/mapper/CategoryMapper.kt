package com.notes.purenoteit.data.mapper

import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.domain.model.Category

internal fun CategoryData.toDomain(): Category = Category(
    id = id,
    name = name,
    isPassword = isPassword
)

internal fun Category.toEntity(): CategoryData = CategoryData(
    name = name,
    isPassword = isPassword,
    id = id
)
