package com.notes.purenoteit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.notes.purenoteit.automation.AutomationEngine
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
import com.notes.purenoteit.viewmodel.NoteViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * PureNoteIt - NoteViewModel Saf Coroutines & StateFlow Testi.
 * Bu test getAllNotes() çağrısının StateFlow üzerinden reaktif olarak
 * UI'a veri akıttığını ve Main thread bloklaması olmadığını doğrular.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteViewModelCoroutinesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: NoteRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var knowledgeRepository: KnowledgeRepository
    private lateinit var automationEngine: AutomationEngine
    private lateinit var context: Context
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var viewModel: NoteViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        repository = mockk(relaxed = true)
        categoryRepository = mockk(relaxed = true)
        knowledgeRepository = mockk(relaxed = true)
        automationEngine = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPrefs = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        every { sharedPrefs.getBoolean(any(), any()) } returns true

        viewModel = NoteViewModel(
            repository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            automationEngine = automationEngine,
            context = context,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getAllNotes cagrildiginda noteListFlow guncellenmelidir`() = runTest(testDispatcher) {
        val sampleNotes = listOf(
            NoteData(title = "Kasa Notu", description = "Gizli", date = 1000L),
            NoteData(title = "İkinci Not", description = "Açık", date = 2000L)
        )
        coEvery { repository.getAllNotesSuspend() } returns sampleNotes

        assertEquals(0, viewModel.noteListFlow.value.size)

        viewModel.getAllNotes()
        testScheduler.advanceUntilIdle()

        val list = viewModel.noteListFlow.value
        assertEquals(2, list.size)
        assertEquals("Kasa Notu", list[0].title)
        assertEquals("İkinci Not", list[1].title)
    }
}
