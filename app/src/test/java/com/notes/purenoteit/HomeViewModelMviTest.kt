package com.notes.purenoteit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import app.cash.turbine.test
import com.notes.purenoteit.automation.AutomationEngine
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
import com.notes.purenoteit.ui.mvi.HomeUiEffect
import com.notes.purenoteit.ui.mvi.HomeUiEvent
import com.notes.purenoteit.ui.screens.NotionViewMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelMviTest {

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
    fun `kategori secildiginde HomeUiState guncellenmelidir`() = runTest(testDispatcher) {
        assertEquals(null, viewModel.homeUiState.value.selectedCategory)

        viewModel.onHomeEvent(HomeUiEvent.SelectCategory("Is"))
        assertEquals("Is", viewModel.homeUiState.value.selectedCategory)

        viewModel.onHomeEvent(HomeUiEvent.SelectCategory(null))
        assertEquals(null, viewModel.homeUiState.value.selectedCategory)
    }

    @Test
    fun `not secildiginde coklu secim modu aktif olmali ve temizlenebilmelidir`() = runTest(testDispatcher) {
        assertFalse(viewModel.homeUiState.value.isSelectionMode)

        viewModel.onHomeEvent(HomeUiEvent.ToggleSelectNote(101))
        assertTrue(viewModel.homeUiState.value.isSelectionMode)
        assertTrue(viewModel.homeUiState.value.selectedNoteIds.contains(101))

        viewModel.onHomeEvent(HomeUiEvent.ToggleSelectNote(102))
        assertEquals(2, viewModel.homeUiState.value.selectedNoteIds.size)

        viewModel.onHomeEvent(HomeUiEvent.ClearSelection)
        assertFalse(viewModel.homeUiState.value.isSelectionMode)
        assertEquals(0, viewModel.homeUiState.value.selectedNoteIds.size)
    }

    @Test
    fun `gorunum modu degistirildiginde HomeUiState reaktif olarak guncellenmelidir`() = runTest(testDispatcher) {
        assertEquals(NotionViewMode.GRID, viewModel.homeUiState.value.viewMode)

        viewModel.onHomeEvent(HomeUiEvent.ChangeViewMode(NotionViewMode.LIST))
        assertEquals(NotionViewMode.LIST, viewModel.homeUiState.value.viewMode)

        viewModel.onHomeEvent(HomeUiEvent.ChangeViewMode(NotionViewMode.CALENDAR))
        assertEquals(NotionViewMode.CALENDAR, viewModel.homeUiState.value.viewMode)
    }

    @Test
    fun `not silme olayi tetiklendiginde effect kanali tek seferlik bildirim yaymalidir`() = runTest(testDispatcher) {
        val sampleNote = NoteData(title = "Silinecek", date = 1000L).apply { id = 99 }
        coEvery { repository.deleteSuspend(any()) } returns Unit

        viewModel.homeEffect.test {
            viewModel.onHomeEvent(HomeUiEvent.DeleteNote(sampleNote))
            testScheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is HomeUiEffect.ShowToast)
            assertEquals("Not silindi", (effect as HomeUiEffect.ShowToast).message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
