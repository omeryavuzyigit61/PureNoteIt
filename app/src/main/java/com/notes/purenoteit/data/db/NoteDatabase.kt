package com.notes.purenoteit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.notes.purenoteit.util.Converters

@Database(
    entities = [
        NoteData::class,
        CategoryData::class,
        NoteLinkData::class,
        AutomationRuleData::class,
        StreamItemData::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun getNoteDao(): NoteDao
    abstract fun getCategoryDao(): CategoryDao
    abstract fun getNoteLinkDao(): NoteLinkDao
    abstract fun getAutomationRuleDao(): AutomationRuleDao
    abstract fun getStreamItemDao(): StreamItemDao
}
