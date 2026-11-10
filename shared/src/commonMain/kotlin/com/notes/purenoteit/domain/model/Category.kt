package com.notes.purenoteit.domain.model

data class Category(
    val id: Int = 0,
    val name: String,
    val isPassword: Boolean? = null,
    val syncUuid: String = "",
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncVersion: Long = 1L
)
