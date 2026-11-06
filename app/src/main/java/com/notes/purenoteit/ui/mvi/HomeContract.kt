package com.notes.purenoteit.ui.mvi

import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.ui.screens.NotionViewMode

data class HomeUiState(
    val notes: List<NoteData> = emptyList(),
    val categories: List<CategoryData> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val viewMode: NotionViewMode = NotionViewMode.GRID,
    val selectedNoteIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isSelectionMode: Boolean get() = selectedNoteIds.isNotEmpty()
}

sealed interface HomeUiEvent {
    data class SelectCategory(val categoryName: String?) : HomeUiEvent
    data class UpdateSearchQuery(val query: String) : HomeUiEvent
    data class ChangeViewMode(val viewMode: NotionViewMode) : HomeUiEvent
    data class ToggleSelectNote(val noteId: Int) : HomeUiEvent
    data object ClearSelection : HomeUiEvent
    data class DeleteNote(val note: NoteData) : HomeUiEvent
    data class TogglePin(val noteId: Int, val isPinned: Boolean) : HomeUiEvent
    data object BatchDeleteSelected : HomeUiEvent
    data object Refresh : HomeUiEvent
}

sealed interface HomeUiEffect {
    data class ShowSnackbar(val message: String) : HomeUiEffect
    data class NavigateToNote(val noteId: Int) : HomeUiEffect
    data class ShowToast(val message: String) : HomeUiEffect
}
