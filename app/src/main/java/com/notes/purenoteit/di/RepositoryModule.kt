package com.notes.purenoteit.di

import com.notes.purenoteit.data.repository.CategoryRepositoryImpl
import com.notes.purenoteit.data.repository.KnowledgeRepositoryImpl
import com.notes.purenoteit.data.repository.NoteRepositoryImpl
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    internal abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    internal abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    internal abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository
}
