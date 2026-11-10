package com.notes.purenoteit.di

import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.CodeExecutionRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.DeleteNoteUseCase
import com.notes.purenoteit.domain.usecase.ExecuteOfflineCodeUseCase
import com.notes.purenoteit.domain.usecase.GetCategoriesUseCase
import com.notes.purenoteit.domain.usecase.GetNotesUseCase
import com.notes.purenoteit.domain.usecase.SaveNoteUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetNotesUseCase(noteRepository: NoteRepository): GetNotesUseCase {
        return GetNotesUseCase(noteRepository)
    }

    @Provides
    fun provideSaveNoteUseCase(
        noteRepository: NoteRepository,
        clock: AppClock
    ): SaveNoteUseCase {
        return SaveNoteUseCase(noteRepository, clock)
    }

    @Provides
    fun provideDeleteNoteUseCase(noteRepository: NoteRepository): DeleteNoteUseCase {
        return DeleteNoteUseCase(noteRepository)
    }

    @Provides
    fun provideGetCategoriesUseCase(categoryRepository: CategoryRepository): GetCategoriesUseCase {
        return GetCategoriesUseCase(categoryRepository)
    }

    @Provides
    fun provideExecuteOfflineCodeUseCase(
        codeExecutionRepository: CodeExecutionRepository
    ): ExecuteOfflineCodeUseCase {
        return ExecuteOfflineCodeUseCase(codeExecutionRepository)
    }
}
