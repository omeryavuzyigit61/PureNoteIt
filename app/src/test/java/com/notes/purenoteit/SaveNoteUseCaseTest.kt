package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.usecase.SaveNoteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveNoteUseCaseTest {

    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val saveNoteUseCase = SaveNoteUseCase(noteRepository)

    @Test
    fun `tamamen bos not kaydedilmeye calisildiginda dogrulama hatasi donmelidir`() = runTest {
        val emptyNote = Note(title = "   ", description = "   ", richBlocksJson = null)

        val result = saveNoteUseCase(emptyNote)

        assertTrue(result is AppResult.Failure)
        assertEquals(DataError.Local.VALIDATION_FAILED, (result as AppResult.Failure).error)
        coVerify(exactly = 0) { noteRepository.upsertNote(any()) }
    }

    @Test
    fun `gecerli not kaydedildiginde basariyla upsert edilmelidir`() = runTest {
        val validNote = Note(id = 1, title = "  Baslik  ", description = "Icerik", date = 5000L)
        coEvery { noteRepository.upsertNote(any()) } returns Unit

        val result = saveNoteUseCase(validNote)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) {
            noteRepository.upsertNote(match {
                it.id == 1 && it.title == "Baslik" && it.description == "Icerik"
            })
        }
    }
}
