package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = CategoryData::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE
    )])
data class NoteData(
    @ColumnInfo(name = "title")
    var title: String? = null,
    @ColumnInfo(name = "description")
    var description: String? = null,
    @ColumnInfo(name = "category")
    var category: String? = null,
    @ColumnInfo(name = "date")
    var date: Long,
    @ColumnInfo(name = "isPassword")
    var isPassword: Boolean? = null,
    @ColumnInfo(name = "images")
    var images: List<String>? = null,
    @ColumnInfo(name = "categoryId")
    var categoryId: Int? = null
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}