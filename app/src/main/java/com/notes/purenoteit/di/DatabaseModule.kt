package com.notes.purenoteit.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.NoteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN reminderTime INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN reminderType TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN reminderSound TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE notes ADD COLUMN isReminderDone INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add richBlocksJson to notes table
            db.execSQL("ALTER TABLE notes ADD COLUMN richBlocksJson TEXT DEFAULT NULL")

            // Create note_links table for bi-directional linking and Graph View
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS note_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceNoteId INTEGER NOT NULL,
                    targetNoteId INTEGER NOT NULL,
                    linkText TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(sourceNoteId) REFERENCES notes(id) ON DELETE CASCADE,
                    FOREIGN KEY(targetNoteId) REFERENCES notes(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_targetNoteId ON note_links(targetNoteId)")

            // Create automation_rules table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS automation_rules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    triggerType TEXT NOT NULL,
                    triggerConfig TEXT,
                    actionType TEXT NOT NULL,
                    actionConfig TEXT,
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create stream_items table for Quick Inbox Stream
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stream_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    type TEXT NOT NULL,
                    audioPath TEXT,
                    imagePath TEXT,
                    timestamp INTEGER NOT NULL,
                    isProcessed INTEGER NOT NULL DEFAULT 0,
                    mergedNoteId INTEGER
                )
                """.trimIndent()
            )

            // Seed default automation rules
            val now = System.currentTimeMillis()
            db.execSQL("INSERT OR IGNORE INTO automation_rules (id, title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt) VALUES (1, 'Tüm Görevler Bitince Tamamlandı Yap', 'ALL_TASKS_COMPLETED', NULL, 'SET_STATUS_DONE', 'DONE', 1, $now)")
            db.execSQL("INSERT OR IGNORE INTO automation_rules (id, title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt) VALUES (2, 'Hafta İçi Günlük Plan Şablonu', 'DAILY_SCHEDULED', '08:30', 'CREATE_TEMPLATE', 'Günün Planı', 1, $now)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteDatabase =
        Room.databaseBuilder(context, NoteDatabase::class.java, "notes")
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (1, 'Not', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (2, 'Günlük', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (3, 'Önemli', 0)")
                    val now = System.currentTimeMillis()
                    db.execSQL("INSERT OR IGNORE INTO automation_rules (id, title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt) VALUES (1, 'Tüm Görevler Bitince Tamamlandı Yap', 'ALL_TASKS_COMPLETED', NULL, 'SET_STATUS_DONE', 'DONE', 1, $now)")
                    db.execSQL("INSERT OR IGNORE INTO automation_rules (id, title, triggerType, triggerConfig, actionType, actionConfig, isEnabled, createdAt) VALUES (2, 'Hafta İçi Günlük Plan Şablonu', 'DAILY_SCHEDULED', '08:30', 'CREATE_TEMPLATE', 'Günün Planı', 1, $now)")
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (1, 'Not', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (2, 'Günlük', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (3, 'Önemli', 0)")
                }
            })
            .build()

    @Provides
    @Singleton
    fun provideDao(db: NoteDatabase) = db.getNoteDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: NoteDatabase) = db.getCategoryDao()

    @Provides
    @Singleton
    fun provideNoteLinkDao(db: NoteDatabase) = db.getNoteLinkDao()

    @Provides
    @Singleton
    fun provideAutomationRuleDao(db: NoteDatabase) = db.getAutomationRuleDao()

    @Provides
    @Singleton
    fun provideStreamItemDao(db: NoteDatabase) = db.getStreamItemDao()
}
