package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

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
        Index(value = ["targetNoteId"])
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
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
