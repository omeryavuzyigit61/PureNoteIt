package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.usecase.DeleteNoteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteNoteUseCaseTest {

    private val noteRepository: NoteRepository = mockk(relaxed = true)
    private val deleteNoteUseCase = DeleteNoteUseCase(noteRepository)

    @Test
    fun `gecersiz id ile silme denendiginde dogrulama hatasi vermelidir`() = runTest {
        val result = deleteNoteUseCase(0)

        assertTrue(result is AppResult.Failure)
        assertEquals(DataError.Local.VALIDATION_FAILED, (result as AppResult.Failure).error)
        coVerify(exactly = 0) { noteRepository.deleteNoteById(any()) }
    }

    @Test
    fun `gecerli id ile silme islemi repositorye iletilmelidir`() = runTest {
        coEvery { noteRepository.deleteNoteById(42) } returns Unit

        val result = deleteNoteUseCase(42)

        assertTrue(result is AppResult.Success)
        coVerify(exactly = 1) { noteRepository.deleteNoteById(42) }
    }
}
