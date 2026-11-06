package com.notes.purenoteit

import app.cash.turbine.test
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.repository.NoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * PureNoteIt - NoteRepository Saf Coroutines & Flow Birim Testi.
 * Bu test, Repository katmanının DAO'ya giden suspend ve Flow çağrılarını
 * hiçbir SQL şema değişikliği olmadan, kayıpsız ve asenkron yürüttüğünü doğrular.
 */
class NoteRepositoryCoroutinesTest {

    private lateinit var mockDao: NoteDao
    private lateinit var repository: NoteRepository

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        repository = NoteRepository(mockDao)
    }

    @Test
    fun `getAllNotesFlow reaktif olarak not listesini yaymalidir`() = runTest {
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
    fun `getNoteByIdSuspend tekil notu dondurmelidir`() = runTest {
        val fakeNote = NoteData(title = "Hedef Not", description = "Detay", date = 5000L)
        coEvery { mockDao.getNoteByIdSuspend(42) } returns fakeNote

        val result = repository.getNoteByIdSuspend(42)
        assertNotNull(result)
        assertEquals("Hedef Not", result?.title)
        coVerify(exactly = 1) { mockDao.getNoteByIdSuspend(42) }
    }

    @Test
    fun `upsertSuspend kaydetme cagrisini dao katmanina iletmelidir`() = runTest {
        val newNote = NoteData(title = "Yeni", description = "Test", date = 9999L)
        coEvery { mockDao.upsertNote(newNote) } returns Unit

        repository.upsertSuspend(newNote)

        coVerify(exactly = 1) { mockDao.upsertNote(newNote) }
    }
}
