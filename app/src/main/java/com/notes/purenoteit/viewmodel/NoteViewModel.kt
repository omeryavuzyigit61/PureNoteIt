package com.notes.purenoteit.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.notes.purenoteit.ui.mvi.HomeUiEffect
import com.notes.purenoteit.ui.mvi.HomeUiEvent
import com.notes.purenoteit.ui.mvi.HomeUiState
import com.notes.purenoteit.automation.AutomationEngine
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.VaultBackupManager
import com.notes.purenoteit.widget.PureNoteWidgetProvider
import com.notes.purenoteit.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val automationEngine: AutomationEngine,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _noteList = MutableLiveData<List<NoteData>>()
    val noteList: LiveData<List<NoteData>> = _noteList

    private val _noteListFlow = MutableStateFlow<List<NoteData>>(emptyList())
    val noteListFlow: StateFlow<List<NoteData>> = _noteListFlow.asStateFlow()

    // --- MVI Architecture State & Effect Contracts (android_native_rules.md) ---
    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _homeEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val homeEffect: Flow<HomeUiEffect> = _homeEffect.receiveAsFlow()

    fun onHomeEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.SelectCategory -> {
                _homeUiState.update { it.copy(selectedCategory = event.categoryName) }
            }
            is HomeUiEvent.UpdateSearchQuery -> {
                _homeUiState.update { it.copy(searchQuery = event.query) }
                if (event.query.isNotBlank()) {
                    searchNote(event.query)
                } else {
                    getAllNotes()
                }
            }
            is HomeUiEvent.ChangeViewMode -> {
                _homeUiState.update { it.copy(viewMode = event.viewMode) }
            }
            is HomeUiEvent.ToggleSelectNote -> {
                _homeUiState.update { state ->
                    val current = state.selectedNoteIds
                    val updated = if (current.contains(event.noteId)) {
                        current - event.noteId
                    } else {
                        current + event.noteId
                    }
                    state.copy(selectedNoteIds = updated)
                }
            }
            is HomeUiEvent.ClearSelection -> {
                _homeUiState.update { it.copy(selectedNoteIds = emptySet()) }
            }
            is HomeUiEvent.DeleteNote -> {
                delete(event.note)
                viewModelScope.launch(ioDispatcher) {
                    _homeEffect.send(HomeUiEffect.ShowToast("Not silindi"))
                }
            }
            is HomeUiEvent.TogglePin -> {
                togglePinStatus(event.noteId, event.isPinned)
            }
            is HomeUiEvent.BatchDeleteSelected -> {
                val ids = _homeUiState.value.selectedNoteIds
                val toDelete = _homeUiState.value.notes.filter { it.id in ids }
                if (toDelete.isNotEmpty()) {
                    batchMoveToTrash(toDelete) {
                        _homeUiState.update { it.copy(selectedNoteIds = emptySet()) }
                        viewModelScope.launch(ioDispatcher) {
                            _homeEffect.send(HomeUiEffect.ShowToast("${toDelete.size} not çöp kutusuna taşındı"))
                        }
                    }
                }
            }
            is HomeUiEvent.Refresh -> {
                _homeUiState.update { it.copy(isLoading = true) }
                getAllNotes()
            }
        }
    }

    private fun notifyWidgetSync() {
        try {
            PureNoteWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error notifying widget sync: ${e.message}")
        }
    }

    private val _allLinks = MutableLiveData<List<NoteLinkData>>()
    val allLinks: LiveData<List<NoteLinkData>> = _allLinks

    private val _backlinks = MutableLiveData<List<NoteData>>()
    val backlinks: LiveData<List<NoteData>> = _backlinks

    private val _streamItems = MutableLiveData<List<StreamItemData>>()
    val streamItems: LiveData<List<StreamItemData>> = _streamItems

    private var hasExecutedStartupMaintenance = false

    fun getAllNotes() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val initialList = repository.getAllNotesSuspend()
                var currentList = initialList
                if (!hasExecutedStartupMaintenance) {
                    hasExecutedStartupMaintenance = true
                    val trashPurged = purgeAllTrashNotesOnUpgrade(currentList)
                    if (trashPurged) {
                        currentList = repository.getAllNotesSuspend()
                    }
                    val healed = healOrphanedSubPages(currentList)
                    if (healed) {
                        currentList = repository.getAllNotesSuspend()
                    }
                }
                withContext(Dispatchers.Main) {
                    _noteListFlow.value = currentList
                    _homeUiState.update { it.copy(notes = currentList, isLoading = false) }
                    runCatching { _noteList.value = currentList }
                    Log.i("datas", "getAllNotes: coroutines flow updated (${currentList.size} notes)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("NoteViewModel", "Error fetching notes: ${e.message}")
            }
        }
    }

    fun upsert(item: NoteData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                if (item.id > 0) {
                    val previousNote = runCatching { repository.getNoteByIdSuspend(item.id) }.getOrNull()
                    if (item.richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                        item.richBlocksJson = previousNote?.richBlocksJson
                    }
                    val previousStatus = previousNote?.status
                    repository.updateSuspend(item)
                    if (item.status == "TRASH") {
                        purgeDeletedNoteReferences(item.id, item.title)
                    } else if (previousStatus == "TRASH") {
                        restoreSubPageToParent(item)
                    }
                } else {
                    repository.upsertSuspend(item)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    Log.i("NoteViewModel", "Note upserted successfully via Coroutines")
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error upserting note: ${error.message}")
            }
        }
    }

    fun saveNote(item: NoteData, contentForLinks: String? = null, onSaved: ((Int) -> Unit)? = null) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val previousNote = if (item.id > 0) {
                    runCatching { repository.getNoteByIdSuspend(item.id) }.getOrNull()
                        ?: _noteList.value?.find { it.id == item.id }
                } else {
                    null
                }
                if (item.richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                    item.richBlocksJson = previousNote?.richBlocksJson
                }
                val previousTitle = previousNote?.title
                val previousStatus = previousNote?.status

                val actualId = if (item.id > 0) {
                    repository.updateSuspend(item)
                    item.id
                } else {
                    repository.insertNoteSuspend(item).toInt()
                }
                item.id = actualId

                if (item.status == "TRASH") {
                    purgeDeletedNoteReferences(
                        deletedNoteId = actualId,
                        deletedRawTitle = item.title ?: previousTitle
                    )
                } else {
                    if (previousStatus == "TRASH") {
                        restoreSubPageToParent(item)
                    }
                    propagateTitleChangeToLinkedNotes(
                        targetNoteId = actualId,
                        newRawTitle = item.title ?: "",
                        previousRawTitle = previousTitle
                    )
                }

                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    if (item.status != "TRASH" && !contentForLinks.isNullOrBlank()) {
                        syncNoteLinks(actualId, contentForLinks)
                    }
                    triggerAutoSync()
                    onSaved?.invoke(actualId)
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error saving note: ${error.message}")
            }
        }
    }

    /**
     * Propagates title changes of [targetNoteId] across all notes that link to it via [[WikiLink]]
     * or SUB_PAGE blocks, and updates the linkText column in note_links so links never break.
     */
    private suspend fun propagateTitleChangeToLinkedNotes(
        targetNoteId: Int,
        newRawTitle: String,
        previousRawTitle: String?
    ) {
        val newTitle = newRawTitle.trim()
        if (newTitle.isBlank()) return

        try {
            val backlinkRows = runCatching {
                knowledgeRepository.getBacklinksSuspend(targetNoteId)
            }.getOrDefault(emptyList())

            val oldTitles = buildSet {
                previousRawTitle?.trim()?.takeIf {
                    it.isNotBlank() && !it.equals(newTitle, ignoreCase = true)
                }?.let { add(it) }
                backlinkRows.forEach { row ->
                    val lt = row.linkText.trim()
                    if (lt.isNotBlank() && !lt.equals(newTitle, ignoreCase = true)) {
                        add(lt)
                    }
                }
            }

            if (oldTitles.isEmpty() && backlinkRows.isEmpty()) return

            val allNotes = runCatching {
                repository.getAllNotesSuspend()
            }.getOrDefault(emptyList())

            for (candidate in allNotes) {
                if (candidate.id == targetNoteId) continue
                val wasEncrypted = candidate.isEncrypted ||
                    (candidate.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(candidate.description)
                val rawDesc = if (wasEncrypted && !candidate.description.isNullOrBlank()) {
                    SecurityUtil.decrypt(candidate.description!!)
                } else {
                    candidate.description ?: ""
                }
                if (rawDesc.isBlank()) continue

                val parsedBlocks = BlockParser.parse(rawDesc).toMutableList()
                var noteModified = false

                for (idx in parsedBlocks.indices) {
                    var block = parsedBlocks[idx]
                    var blockChanged = false

                    if (block.type == BlockType.SUB_PAGE) {
                        val isMatch = if (block.subPageNoteId != null) {
                            block.subPageNoteId == targetNoteId
                        } else {
                            oldTitles.any {
                                block.content.trim().equals(it, ignoreCase = true)
                            }
                        }
                        if (isMatch) {
                            if (block.content != newTitle || block.subPageNoteId != targetNoteId) {
                                block = block.copy(content = newTitle, subPageNoteId = targetNoteId)
                                blockChanged = true
                            }
                        }
                        if (blockChanged) {
                            parsedBlocks[idx] = block
                            noteModified = true
                        }
                        continue
                    }

                    for (oldT in oldTitles) {
                        val pattern = Regex("""\[\[\s*${Regex.escape(oldT)}\s*\]\]""", RegexOption.IGNORE_CASE)
                        if (pattern.containsMatchIn(block.content)) {
                            block = block.copy(content = pattern.replace(block.content, "[[$newTitle]]"))
                            blockChanged = true
                        }
                        if (pattern.containsMatchIn(block.toggleBody)) {
                            block = block.copy(toggleBody = pattern.replace(block.toggleBody, "[[$newTitle]]"))
                            blockChanged = true
                        }
                    }

                    if (blockChanged) {
                        parsedBlocks[idx] = block
                        noteModified = true
                    }
                }

                if (noteModified) {
                    val serialized = BlockParser.serialize(parsedBlocks)
                    val finalDesc = if (wasEncrypted) SecurityUtil.encrypt(serialized) else serialized
                    val updatedCandidate = candidate.copy(description = finalDesc).also {
                        it.id = candidate.id
                    }
                    runCatching { repository.updateSuspend(updatedCandidate) }
                }
            }

            if (backlinkRows.isNotEmpty() || oldTitles.isNotEmpty()) {
                runCatching {
                    knowledgeRepository.updateTargetLinkTextSuspend(targetNoteId, newTitle)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Removes all [[WikiLink]] tags, SUB_PAGE blocks, and note_links entries referencing a deleted
     * or trashed note so that source notes never retain dead links or spawn duplicate notes.
     */
    private suspend fun purgeDeletedNoteReferences(
        deletedNoteId: Int,
        deletedRawTitle: String?
    ) {
        try {
            val backlinkRows = runCatching {
                knowledgeRepository.getBacklinksSuspend(deletedNoteId)
            }.getOrDefault(emptyList())

            val titlesToRemove = buildSet {
                deletedRawTitle?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                backlinkRows.forEach { row ->
                    val lt = row.linkText.trim()
                    if (lt.isNotBlank()) add(lt)
                }
            }

            val allNotes = runCatching {
                repository.getAllNotesSuspend()
            }.getOrDefault(emptyList())

            for (candidate in allNotes) {
                if (candidate.id == deletedNoteId) continue
                val wasEncrypted = candidate.isEncrypted ||
                    (candidate.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(candidate.description)
                val rawDesc = if (wasEncrypted && !candidate.description.isNullOrBlank()) {
                    SecurityUtil.decrypt(candidate.description!!)
                } else {
                    candidate.description ?: ""
                }
                if (rawDesc.isBlank()) continue

                val parsedBlocks = BlockParser.parse(rawDesc).toMutableList()
                val blocksToRemove = mutableListOf<NoteBlock>()
                var noteModified = false

                for (idx in parsedBlocks.indices) {
                    var block = parsedBlocks[idx]
                    var blockChanged = false

                    if (block.type == BlockType.SUB_PAGE) {
                        val isMatch = if (block.subPageNoteId != null) {
                            block.subPageNoteId == deletedNoteId
                        } else {
                            block.content.isNotBlank() &&
                                !block.content.trim().equals("Yeni Alt Sayfa", ignoreCase = true) &&
                                titlesToRemove.any {
                                    block.content.trim().equals(it, ignoreCase = true)
                                }
                        }
                        if (isMatch) {
                            blocksToRemove.add(block)
                            noteModified = true
                        }
                        continue
                    }

                    for (deadTitle in titlesToRemove) {
                        val pattern = Regex("""\[\[\s*${Regex.escape(deadTitle)}\s*\]\]\s*""", RegexOption.IGNORE_CASE)
                        if (pattern.containsMatchIn(block.content)) {
                            val cleanedContent = pattern.replace(block.content, "").trimEnd()
                            block = block.copy(content = cleanedContent)
                            blockChanged = true
                        }
                        if (pattern.containsMatchIn(block.toggleBody)) {
                            val cleanedBody = pattern.replace(block.toggleBody, "").trimEnd()
                            block = block.copy(toggleBody = cleanedBody)
                            blockChanged = true
                        }
                    }

                    if (blockChanged) {
                        if (block.type == BlockType.PARAGRAPH &&
                            block.content.isBlank() &&
                            block.toggleBody.isBlank() &&
                            block.mediaPath == null
                        ) {
                            blocksToRemove.add(block)
                        } else {
                            parsedBlocks[idx] = block
                        }
                        noteModified = true
                    }
                }

                if (blocksToRemove.isNotEmpty()) {
                    parsedBlocks.removeAll(blocksToRemove)
                    if (parsedBlocks.isEmpty()) {
                        parsedBlocks.add(NoteBlock())
                    }
                }

                if (noteModified) {
                    val serialized = BlockParser.serialize(parsedBlocks)
                    val finalDesc = if (wasEncrypted) SecurityUtil.encrypt(serialized) else serialized
                    val updatedCandidate = candidate.copy(description = finalDesc).also {
                        it.id = candidate.id
                    }
                    runCatching { repository.updateSuspend(updatedCandidate) }
                }
            }

            runCatching {
                knowledgeRepository.deleteLinksForNoteSuspend(deletedNoteId)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Re-attaches a sub-page restored from Trash back into its parent note as a SUB_PAGE block,
     * or promotes it to a standalone root note if its parent note was deleted or is in Trash.
     */
    private suspend fun restoreSubPageToParent(restoredNote: NoteData): Boolean {
        return try {
            val richJson = restoredNote.richBlocksJson ?: return false
            if (!richJson.contains("\"isSubPage\":true") && !richJson.contains("\"parentNoteId\"")) {
                return false
            }
            val parentIdMatch = Regex(""""parentNoteId"\s*:\s*(\d+)""").find(richJson)
            val parentId = parentIdMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

            val parentNote = if (parentId != null && parentId > 0) {
                runCatching { repository.getNoteByIdSuspend(parentId) }.getOrNull()
            } else {
                null
            }

            if (parentNote != null && parentNote.status != "TRASH") {
                val wasEncrypted = parentNote.isEncrypted ||
                    (parentNote.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(parentNote.description)
                val rawDesc = if (wasEncrypted && !parentNote.description.isNullOrBlank()) {
                    SecurityUtil.decrypt(parentNote.description!!)
                } else {
                    parentNote.description ?: ""
                }

                val parsedBlocks = if (rawDesc.isBlank()) {
                    mutableListOf()
                } else {
                    BlockParser.parse(rawDesc).toMutableList()
                }

                val restoredTitle = restoredNote.title?.trim()?.takeIf { it.isNotBlank() } ?: "Yeni Alt Sayfa"
                val existingIdx = parsedBlocks.indexOfFirst {
                    it.type == BlockType.SUB_PAGE && it.subPageNoteId == restoredNote.id
                }

                var parentModified = false
                if (existingIdx == -1) {
                    parsedBlocks.add(
                        NoteBlock(
                            type = BlockType.SUB_PAGE,
                            content = restoredTitle,
                            subPageNoteId = restoredNote.id
                        )
                    )
                    parentModified = true
                } else if (parsedBlocks[existingIdx].content != restoredTitle) {
                    parsedBlocks[existingIdx] = parsedBlocks[existingIdx].copy(content = restoredTitle)
                    parentModified = true
                }

                if (parentModified) {
                    val serialized = BlockParser.serialize(parsedBlocks)
                    val finalDesc = if (wasEncrypted) SecurityUtil.encrypt(serialized) else serialized
                    val updatedParent = parentNote.copy(description = finalDesc).also {
                        it.id = parentNote.id
                    }
                    runCatching { repository.updateSuspend(updatedParent) }
                }

                runCatching {
                    val existingLinks = knowledgeRepository.getOutgoingLinksSuspend(parentNote.id)
                    if (existingLinks.none { it.targetNoteId == restoredNote.id }) {
                        val newLink = NoteLinkData(
                            sourceNoteId = parentNote.id,
                            targetNoteId = restoredNote.id,
                            linkText = restoredTitle
                        )
                        knowledgeRepository.insertAllLinksSuspend(listOf(newLink))
                    }
                }
                parentModified
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One-time startup self-healing pass for any sub-page that was restored from Trash previously
     * and lost its SUB_PAGE block inside its parent note.
     */
    private suspend fun healOrphanedSubPages(notes: List<NoteData>): Boolean {
        var anyModified = false
        try {
            val now = System.currentTimeMillis()
            val activeNotes = notes.filter { it.status != "TRASH" }
            val referencedSubPageIds = activeNotes.flatMap { note ->
                runCatching {
                    val wasEncrypted = note.isEncrypted ||
                        (note.isPassword == true) ||
                        SecurityUtil.isEncryptedPayload(note.description)
                    val rawDesc = if (wasEncrypted && !note.description.isNullOrBlank()) {
                        SecurityUtil.decrypt(note.description!!)
                    } else {
                        note.description ?: ""
                    }
                    if (rawDesc.isBlank()) emptyList()
                    else BlockParser.parse(rawDesc)
                        .filter { it.type == BlockType.SUB_PAGE }
                        .mapNotNull { it.subPageNoteId }
                }.getOrDefault(emptyList())
            }.toSet()

            for (candidate in activeNotes) {
                if (now - candidate.date < 3000L) continue
                val richJson = candidate.richBlocksJson ?: continue
                val isSubPageMeta = richJson.contains("\"isSubPage\":true") || richJson.contains("\"parentNoteId\"")
                if (isSubPageMeta && candidate.id !in referencedSubPageIds) {
                    if (restoreSubPageToParent(candidate)) {
                        anyModified = true
                    }
                }
            }
        } catch (_: Exception) {
        }
        return anyModified
    }

    /**
     * One-time maintenance pass executed on all devices receiving this update:
     * Permanently purges all notes currently in the Trash bin and cleanly eliminates all
     * references, backlinks, and child sub-page pointers so no corrupted/bugged notes
     * can ever be restored.
     */
    private suspend fun purgeAllTrashNotesOnUpgrade(notes: List<NoteData>): Boolean {
        val prefs = context.getSharedPreferences("purenoteit_maintenance_prefs", Context.MODE_PRIVATE)
        val alreadyPurged = prefs.getBoolean("has_purged_trash_subpage_fix_v1", false)
        if (alreadyPurged) return false

        var anyDeleted = false
        try {
            val trashedNotes = notes.filter { it.status == "TRASH" }
            for (note in trashedNotes) {
                purgeDeletedNoteReferences(note.id, note.title)
                repository.deleteNoteByIdSuspend(note.id)
                anyDeleted = true
            }
            prefs.edit().putBoolean("has_purged_trash_subpage_fix_v1", true).apply()
            Log.i("NoteViewModel", "Successfully purged ${trashedNotes.size} trashed notes during update maintenance.")
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Error purging trash notes on update: ${e.message}")
        }
        return anyDeleted
    }

    fun delete(item: NoteData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                purgeDeletedNoteReferences(item.id, item.title)
                repository.deleteSuspend(item)
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error deleting note: ${e.message}")
            }
        }
    }

    fun deleteNoteById(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val existingTitle = runCatching { repository.getNoteByIdSuspend(id)?.title }.getOrNull()
                    ?: _noteList.value?.find { it.id == id }?.title
                purgeDeletedNoteReferences(id, existingTitle)
                repository.deleteNoteByIdSuspend(id)
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    Log.i("NoteViewModel", "Note deleted successfully via Coroutines")
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error deleting note: ${error.message}")
            }
        }
    }

    fun batchMoveToTrash(notes: List<NoteData>, onComplete: () -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            try {
                for (note in notes) {
                    val previousNote = runCatching { repository.getNoteByIdSuspend(note.id) }.getOrNull()
                    val trashed = note.copy(status = "TRASH").apply {
                        id = note.id
                        if (richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                            richBlocksJson = previousNote?.richBlocksJson
                        }
                    }
                    repository.updateSuspend(trashed)
                    purgeDeletedNoteReferences(trashed.id, trashed.title)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    onComplete()
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error in batchMoveToTrash: ${error.message}")
            }
        }
    }

    fun batchToggleLock(
        context: Context,
        notes: List<NoteData>,
        shouldLock: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                for (note in notes) {
                    val previousNote = runCatching { repository.getNoteByIdSuspend(note.id) }.getOrNull()
                    val currentDesc = note.description ?: ""
                    val updatedDesc = if (shouldLock) {
                        if (note.isEncrypted || SecurityUtil.isEncryptedPayload(currentDesc)) currentDesc else SecurityUtil.encrypt(currentDesc)
                    } else {
                        if (note.isEncrypted || SecurityUtil.isEncryptedPayload(currentDesc)) SecurityUtil.decrypt(currentDesc) else currentDesc
                    }

                    val updatedNote = note.copy(
                        isPassword = shouldLock,
                        isEncrypted = shouldLock,
                        description = updatedDesc
                    ).apply {
                        id = note.id
                        if (richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                            richBlocksJson = previousNote?.richBlocksJson
                        }
                    }
                    repository.updateSuspend(updatedNote)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    onComplete()
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error in batchToggleLock: ${error.message}")
            }
        }
    }

    fun togglePinStatus(id: Int, currentPinned: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.updatePinStatusSuspend(id, !currentPinned)
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    notifyWidgetSync()
                    Log.i("NoteViewModel", "Pin status toggled successfully via Coroutines")
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error toggling pin: ${error.message}")
            }
        }
    }

    fun updateNoteStatus(note: NoteData, newStatus: String) {
        note.status = newStatus
        upsert(note)
    }

    fun getNotesBetweenDates(startDate: Long, endDate: Long) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val dateDatas = repository.getNotesBetweenDatesSuspend(startDate, endDate)
                withContext(Dispatchers.Main) {
                    _noteListFlow.value = dateDatas
                    runCatching { _noteList.value = dateDatas }
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error fetching notes between dates: ${e.message}")
            }
        }
    }

    fun searchNotesByCategory(categoryName: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.getNotesByCategoryFlow(categoryName).collect { notes ->
                    withContext(Dispatchers.Main) {
                        _noteListFlow.value = notes
                        runCatching { _noteList.value = notes }
                    }
                }
            } catch (error: Exception) {
                Log.e("NoteViewModel", "Error fetching notes by category: $error")
            }
        }
    }

    fun searchNote(query: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.searchNoteFlow(query).collect { notes ->
                    withContext(Dispatchers.Main) {
                        _noteListFlow.value = notes
                        runCatching { _noteList.value = notes }
                    }
                }
            } catch (e: Exception) {
                Log.i("NoteViewModel", "Error searching notes: ${e.message}")
            }
        }
    }

    suspend fun getNoteByIdSuspend(id: Int): NoteData? {
        return repository.getNoteByIdSuspend(id)
    }

    // --- Bi-directional Links & Graph Data ---
    fun getAllLinks() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val links = knowledgeRepository.getAllLinksSuspend()
                withContext(Dispatchers.Main) {
                    _allLinks.value = links
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error fetching note links: ${e.message}")
            }
        }
    }

    fun loadBacklinksForNote(targetNoteId: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val links = knowledgeRepository.getBacklinksSuspend(targetNoteId)
                val sourceIds = links.map { it.sourceNoteId }.toSet()
                val allCurrentNotes = _noteList.value ?: emptyList()
                withContext(Dispatchers.Main) {
                    _backlinks.value = allCurrentNotes.filter { it.id in sourceIds }
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error loading backlinks: ${e.message}")
            }
        }
    }

    fun syncNoteLinks(sourceNoteId: Int, contentText: String) {
        val linkRegex = Regex("""\[\[(.*?)\]\]""")
        val matchedTitles = linkRegex.findAll(contentText)
            .map { it.groupValues[1].trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        viewModelScope.launch(ioDispatcher) {
            try {
                val allNotes = repository.getAllNotesSuspend()
                val existingOutgoing = runCatching {
                    knowledgeRepository.getOutgoingLinksSuspend(sourceNoteId)
                }.getOrDefault(emptyList())

                val resolvedTargets = mutableMapOf<Int, NoteLinkData>()
                for (matchedLower in matchedTitles) {
                    val byCurrentTitle = allNotes.find {
                        it.id != sourceNoteId &&
                            it.status != "TRASH" &&
                            it.title?.trim()?.lowercase() == matchedLower
                    }
                    val targetNote = byCurrentTitle ?: existingOutgoing.find {
                        it.linkText.trim().lowercase() == matchedLower
                    }?.let { oldLink ->
                        allNotes.find { it.id == oldLink.targetNoteId && it.status != "TRASH" }
                    }

                    if (targetNote != null) {
                        resolvedTargets[targetNote.id] = NoteLinkData(
                            sourceNoteId = sourceNoteId,
                            targetNoteId = targetNote.id,
                            linkText = targetNote.title?.trim() ?: matchedLower
                        )
                    }
                }
                val targetLinks = resolvedTargets.values.toList()

                knowledgeRepository.deleteLinksFromSourceSuspend(sourceNoteId)
                if (targetLinks.isNotEmpty()) {
                    knowledgeRepository.insertAllLinksSuspend(targetLinks)
                }

                withContext(Dispatchers.Main) {
                    getAllLinks()
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error syncing note links: ${e.message}")
            }
        }
    }

    // --- Quick Thought Stream (Paket 4) ---
    fun loadStreamItems() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val items = knowledgeRepository.getAllStreamItemsSuspend()
                withContext(Dispatchers.Main) {
                    _streamItems.value = items
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error loading stream items: ${e.message}")
            }
        }
    }

    fun addStreamItem(content: String, type: String = "TEXT", audioPath: String? = null, imagePath: String? = null) {
        val item = StreamItemData(
            content = content,
            type = type,
            audioPath = audioPath,
            imagePath = imagePath
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                knowledgeRepository.insertStreamItemSuspend(item)
                withContext(Dispatchers.Main) {
                    loadStreamItems()
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error adding stream item: ${e.message}")
            }
        }
    }

    fun deleteStreamItem(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                knowledgeRepository.deleteStreamItemByIdSuspend(id)
                withContext(Dispatchers.Main) {
                    loadStreamItems()
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error deleting stream item: ${e.message}")
            }
        }
    }

    fun convertStreamItemsToNote(selectedIds: List<Int>, title: String, onSuccess: (NoteData) -> Unit) {
        val currentItems = _streamItems.value ?: emptyList()
        val selected = currentItems.filter { it.id in selectedIds }
        if (selected.isEmpty()) return

        val blocks = mutableListOf<NoteBlock>()
        blocks.add(NoteBlock(type = BlockType.HEADING_2, content = "Hızlı Fikirler Akışı"))

        for (item in selected) {
            when (item.type) {
                "AUDIO" -> {
                    blocks.add(NoteBlock(type = BlockType.AUDIO, mediaPath = item.audioPath))
                }
                "IMAGE" -> {
                    blocks.add(NoteBlock(type = BlockType.IMAGE, mediaPath = item.imagePath))
                }
                else -> {
                    blocks.add(NoteBlock(type = BlockType.BULLET_LIST, content = item.content))
                }
            }
        }

        val newNote = NoteData(
            title = if (title.isBlank()) "Fikir Notu (${selected.size} Madde)" else title.trim(),
            description = BlockParser.serialize(blocks),
            date = System.currentTimeMillis(),
            icon = "⚡"
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                val generatedId = repository.insertNoteSuspend(newNote)
                newNote.id = generatedId.toInt()
                runCatching {
                    knowledgeRepository.markStreamItemsProcessedSuspend(selectedIds, newNote.id)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    notifyWidgetSync()
                    loadStreamItems()
                    onSuccess(newNote)
                }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Error converting stream to note: ${e.message}")
            }
        }
    }

    // --- Vault Backup & Restore (Feature 5 - Clean Repository Layer) ---
    fun backupVault(context: android.content.Context, onSuccess: (java.io.File) -> Unit, onError: (Exception) -> Unit) {
        VaultBackupManager.createBackup(
            context = context,
            noteRepository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun restoreVault(context: android.content.Context, uri: android.net.Uri, onSuccess: (Int, Int) -> Unit, onError: (Exception) -> Unit) {
        VaultBackupManager.restoreBackup(
            context = context,
            backupUri = uri,
            noteRepository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onSuccess = { notesCount, catsCount ->
                getAllNotes()
                getAllLinks()
                loadStreamItems()
                notifyWidgetSync()
                onSuccess(notesCount, catsCount)
            },
            onError = onError
        )
    }

    fun triggerAutoSync() {
        if (VaultBackupManager.isAutoSyncEnabled(context)) {
            VaultBackupManager.scheduleDebouncedAutoSync(
                context = context,
                noteRepository = repository,
                categoryRepository = categoryRepository,
                knowledgeRepository = knowledgeRepository
            )
        }
    }

    fun syncVaultNow(onComplete: (Boolean) -> Unit) {
        VaultBackupManager.performAutoSync(
            context = context,
            noteRepository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onComplete = onComplete
        )
    }

    // --- Automation Triggers ---
    fun createDailyAgenda(onSuccess: (NoteData) -> Unit) {
        automationEngine.createDailyAgendaNote { note ->
            getAllNotes()
            notifyWidgetSync()
            onSuccess(note)
        }
    }
}
