package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "stream_items")
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
    var mergedNoteId: Int? = null
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
