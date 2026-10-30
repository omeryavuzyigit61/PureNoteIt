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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteDatabase =
        Room.databaseBuilder(context, NoteDatabase::class.java, "notes")
            .addMigrations(MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (1, 'Not', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (2, 'Günlük', 0)")
                    db.execSQL("INSERT OR IGNORE INTO categories (id, name, isPassword) VALUES (3, 'Önemli', 0)")
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
}
