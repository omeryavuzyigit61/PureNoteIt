package com.notes.purenoteit

import app.cash.turbine.test
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.usecase.GetNotesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetNotesUseCaseTest {

    private val noteRepository: NoteRepository = mockk()
    private val getNotesUseCase = GetNotesUseCase(noteRepository)

    @Test
    fun `cop kutusundaki notlar varsayilan olarak filtrelenmelidir`() = runTest {
        val notes = listOf(
            Note(id = 1, title = "Aktif Not", status = "TODO", date = 1000L),
            Note(id = 2, title = "Silinen Not", status = "TRASH", date = 2000L),
            Note(id = 3, title = "Diger Not", status = "DONE", date = 3000L)
        )
        every { noteRepository.getAllNotesFlow() } returns flowOf(notes)

        getNotesUseCase().test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Aktif Not", result[0].title)
            assertEquals("Diger Not", result[1].title)
            awaitComplete()
        }
    }

    @Test
    fun `kategoriye gore filtreleme basariyla calismalidir`() = runTest {
        val notes = listOf(
            Note(id = 1, title = "Is Notu", categoryId = 5, status = "TODO", date = 1000L),
            Note(id = 2, title = "Kisisel Not", categoryId = 10, status = "TODO", date = 2000L)
        )
        every { noteRepository.getAllNotesFlow() } returns flowOf(notes)

        getNotesUseCase(categoryId = 5).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Is Notu", result[0].title)
            awaitComplete()
        }
    }
}
