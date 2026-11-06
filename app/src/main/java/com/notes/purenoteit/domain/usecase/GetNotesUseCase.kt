package com.notes.purenoteit.domain.usecase

import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetNotesUseCase(
    private val noteRepository: NoteRepository
) {
    operator fun invoke(categoryId: Int? = null, filterTrash: Boolean = true): Flow<List<Note>> {
        return noteRepository.getAllNotesFlow().map { notes ->
            notes.filter { note ->
                val matchesCategory = categoryId == null || note.categoryId == categoryId
                val matchesStatus = if (filterTrash) note.status != "TRASH" else true
                matchesCategory && matchesStatus
            }
        }
    }
}
