package com.notes.purenoteit.di

import com.notes.purenoteit.data.repository.CategoryRepositoryImpl
import com.notes.purenoteit.data.repository.KnowledgeRepositoryImpl
import com.notes.purenoteit.data.repository.NoteRepositoryImpl
import com.notes.purenoteit.data.repository.OfflineCodeExecutionRepositoryImpl
import com.notes.purenoteit.data.security.AndroidSecureTokenStorageImpl
import com.notes.purenoteit.data.storage.AndroidMediaPathResolverImpl
import com.notes.purenoteit.data.sync.AndroidLocalSyncStoreImpl
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.CodeExecutionRepository
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.LocalSyncStore
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

    @Binds
    @Singleton
    internal abstract fun bindCodeExecutionRepository(
        impl: OfflineCodeExecutionRepositoryImpl
    ): CodeExecutionRepository

    @Binds
    @Singleton
    internal abstract fun bindMediaPathResolver(
        impl: AndroidMediaPathResolverImpl
    ): MediaPathResolver

    @Binds
    @Singleton
    internal abstract fun bindLocalSyncStore(
        impl: AndroidLocalSyncStoreImpl
    ): LocalSyncStore

    @Binds
    @Singleton
    internal abstract fun bindSecureTokenStorage(
        impl: AndroidSecureTokenStorageImpl
    ): SecureTokenStorage
}
