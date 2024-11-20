package com.notes.purenoteit.di

import android.content.Context
import androidx.room.Room
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
        Room.databaseBuilder(context, NoteDatabase::class.java, "notes").build()
    @Provides
    @Singleton
    fun provideDao(db: NoteDatabase) = db.getNoteDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: NoteDatabase) = db.getCategoryDao()
}
