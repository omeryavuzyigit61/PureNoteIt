package com.notes.purenoteit.domain.usecase

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.CancellationException

class SaveNoteUseCase(
    private val noteRepository: NoteRepository,
    private val clock: AppClock
) {
    suspend operator fun invoke(note: Note): AppResult<Unit, DataError.Local> {
        val trimmedTitle = note.title?.trim()
        val trimmedDesc = note.description?.trim()

        if (trimmedTitle.isNullOrBlank() && trimmedDesc.isNullOrBlank() && note.richBlocksJson.isNullOrBlank()) {
            return AppResult.Failure(DataError.Local.VALIDATION_FAILED)
        }

        return try {
            val sanitized = note.copy(
                title = trimmedTitle,
                description = trimmedDesc,
                date = if (note.date <= 0L) clock.nowMillis() else note.date
            )
            noteRepository.upsertNote(sanitized)
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Local.UNKNOWN)
        }
    }
}
