package com.notes.purenoteit.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NoteDatabase =
        Room.databaseBuilder(context, NoteDatabase::class.java, "notes")
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
