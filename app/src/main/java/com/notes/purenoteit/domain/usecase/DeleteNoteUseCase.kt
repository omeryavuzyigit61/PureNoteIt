package com.notes.purenoteit.domain.usecase

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException

class DeleteNoteUseCase(
    private val noteRepository: NoteRepository
) {
    suspend operator fun invoke(noteId: Int): AppResult<Unit, DataError.Local> {
        if (noteId <= 0) {
            return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        }

        return try {
            noteRepository.deleteNoteById(noteId)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }
}
