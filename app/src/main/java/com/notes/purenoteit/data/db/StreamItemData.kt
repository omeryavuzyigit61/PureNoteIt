package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable
import java.util.UUID

@Entity(
    tableName = "stream_items",
    indices = [Index(value = ["syncUuid"], unique = true)]
)
data class StreamItemData(
    @ColumnInfo(name = "content")
    var content: String,
    @ColumnInfo(name = "type")
    var type: String = "TEXT", // TEXT, VOICE, PHOTO
    @ColumnInfo(name = "audioPath")
    var audioPath: String? = null,
    @ColumnInfo(name = "imagePath")
    var imagePath: String? = null,
    @ColumnInfo(name = "timestamp")
    var timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "isProcessed", defaultValue = "0")
    var isProcessed: Boolean = false,
    @ColumnInfo(name = "mergedNoteId")
    var mergedNoteId: Int? = null,
    @ColumnInfo(name = "syncUuid", defaultValue = "''")
    var syncUuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "mergedNoteSyncUuid")
    var mergedNoteSyncUuid: String? = null,
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
