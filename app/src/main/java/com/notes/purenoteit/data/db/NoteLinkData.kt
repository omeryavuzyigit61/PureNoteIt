package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "note_links",
    foreignKeys = [
        ForeignKey(
            entity = NoteData::class,
            parentColumns = ["id"],
            childColumns = ["sourceNoteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NoteData::class,
            parentColumns = ["id"],
            childColumns = ["targetNoteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceNoteId"]),
        Index(value = ["targetNoteId"]),
        Index(value = ["syncUuid"], unique = true)
    ]
)
data class NoteLinkData(
    @ColumnInfo(name = "sourceNoteId")
    val sourceNoteId: Int,
    @ColumnInfo(name = "targetNoteId")
    val targetNoteId: Int,
    @ColumnInfo(name = "linkText")
    val linkText: String,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "syncUuid", defaultValue = "''")
    var syncUuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "sourceSyncUuid", defaultValue = "''")
    var sourceSyncUuid: String = "",
    @ColumnInfo(name = "targetSyncUuid", defaultValue = "''")
    var targetSyncUuid: String = "",
    @ColumnInfo(name = "updatedAt", defaultValue = "0")
    var updatedAt: Long = 0L,
    @ColumnInfo(name = "deletedAt")
    var deletedAt: Long? = null,
    @ColumnInfo(name = "syncVersion", defaultValue = "1")
    var syncVersion: Long = 1L
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
