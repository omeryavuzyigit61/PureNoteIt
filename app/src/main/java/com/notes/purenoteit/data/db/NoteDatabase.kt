package com.notes.purenoteit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.notes.purenoteit.util.Converters

@Database(entities = [NoteData::class, CategoryData::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun getNoteDao(): NoteDao
    abstract fun getCategoryDao(): CategoryDao
}
