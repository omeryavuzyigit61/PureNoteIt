package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["syncUuid"], unique = true)]
)
data class CategoryData(
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "isPassword")
    var isPassword: Boolean? = null,
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    @ColumnInfo(name = "syncUuid", defaultValue = "''")
    var syncUuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "updatedAt", defaultValue = "0")
    var updatedAt: Long = 0L,
    @ColumnInfo(name = "deletedAt")
    var deletedAt: Long? = null,
    @ColumnInfo(name = "syncVersion", defaultValue = "1")
    var syncVersion: Long = 1L
) : Serializable
