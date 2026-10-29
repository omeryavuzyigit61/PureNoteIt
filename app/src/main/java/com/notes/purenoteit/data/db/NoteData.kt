package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = CategoryData::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["categoryId"])]
)
data class NoteData(
    @ColumnInfo(name = "title")
    var title: String? = null,
    @ColumnInfo(name = "description")
    var description: String? = null,
    @ColumnInfo(name = "date")
    var date: Long,
    @ColumnInfo(name = "isPassword")
    var isPassword: Boolean? = null,
    @ColumnInfo(name = "images")
    var images: List<String>? = null,
    @ColumnInfo(name = "categoryId")
    var categoryId: Int? = null,
    @ColumnInfo(name = "isSound")
    var sounds: List<String>? = null,
    @ColumnInfo(name = "isPinned", defaultValue = "0")
    var isPinned: Boolean = false,
    @ColumnInfo(name = "icon")
    var icon: String? = null,
    @ColumnInfo(name = "isEncrypted", defaultValue = "0")
    var isEncrypted: Boolean = false,
    @ColumnInfo(name = "totalTasks", defaultValue = "0")
    var totalTasks: Int = 0,
    @ColumnInfo(name = "completedTasks", defaultValue = "0")
    var completedTasks: Int = 0,
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
