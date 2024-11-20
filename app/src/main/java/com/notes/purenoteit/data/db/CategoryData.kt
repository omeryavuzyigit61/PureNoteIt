package com.notes.purenoteit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "categories")
data class CategoryData(
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "isPassword")
    var isPassword: Boolean? = null
) : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
