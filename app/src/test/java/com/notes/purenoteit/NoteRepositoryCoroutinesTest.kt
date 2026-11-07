package com.notes.purenoteit

import app.cash.turbine.test
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.repository.NoteRepositoryImpl
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteRepositoryCoroutinesTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockDao: NoteDao
    private lateinit var repository: NoteRepository

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        repository = NoteRepositoryImpl(mockDao, testDispatcher)
    }

    @Test
    fun `getAllNotesFlow reaktif olarak domain Note listesini yaymalidir`() = runTest(testDispatcher) {
        val fakeNotes = listOf(
            NoteData(title = "Gizli Not", description = "İçerik", date = 1000L),
            NoteData(title = "İkinci Not", description = "İçerik 2", date = 2000L)
        )
        every { mockDao.getAllNotesFlow() } returns flowOf(fakeNotes)

        repository.getAllNotesFlow().test {
            val emission = awaitItem()
            assertEquals(2, emission.size)
            assertEquals("Gizli Not", emission[0].title)
            assertEquals("İkinci Not", emission[1].title)
            awaitComplete()
        }
    }

    @Test
    fun `getNoteById tekil domain notunu dondurmelidir`() = runTest(testDispatcher) {
        val fakeNote = NoteData(title = "Hedef Not", description = "Detay", date = 5000L).apply { id = 42 }
        coEvery { mockDao.getNoteByIdSuspend(42) } returns fakeNote

        val result = repository.getNoteById(42)
        assertNotNull(result)
        assertEquals(42, result?.id)
        assertEquals("Hedef Not", result?.title)
        coVerify(exactly = 1) { mockDao.getNoteByIdSuspend(42) }
    }

    @Test
    fun `upsertNote kaydetme cagrisini entity donusumuyle dao katmanina iletmelidir`() = runTest(testDispatcher) {
        val newNote = Note(id = 7, title = "Yeni", description = "Test", date = 9999L)
        coEvery { mockDao.upsertNote(any()) } returns Unit

        repository.upsertNote(newNote)

        coVerify(exactly = 1) {
            mockDao.upsertNote(match {
                it.id == 7 && it.title == "Yeni" && it.description == "Test" && it.date == 9999L
            })
        }
    }
}
