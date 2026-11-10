package com.notes.purenoteit.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.purenoteit.automation.AutomationEngine
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemData
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.domain.usecase.DeleteNoteUseCase
import com.notes.purenoteit.domain.usecase.GetNotesUseCase
import com.notes.purenoteit.domain.usecase.SaveNoteUseCase
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.mvi.HomeUiEffect
import com.notes.purenoteit.ui.mvi.HomeUiEvent
import com.notes.purenoteit.ui.mvi.HomeUiState
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.VaultBackupManager
import com.notes.purenoteit.widget.PureNoteWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val getNotesUseCase: GetNotesUseCase,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
    private val automationEngine: AutomationEngine,
    private val clock: AppClock,
    val mediaPathResolver: MediaPathResolver,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _noteListFlow = MutableStateFlow<List<NoteData>>(emptyList())
    val noteListFlow: StateFlow<List<NoteData>> = _noteListFlow.asStateFlow()

    private val _allLinksFlow = MutableStateFlow<List<NoteLinkData>>(emptyList())
    val allLinksFlow: StateFlow<List<NoteLinkData>> = _allLinksFlow.asStateFlow()

    private val _backlinksFlow = MutableStateFlow<List<NoteData>>(emptyList())
    val backlinksFlow: StateFlow<List<NoteData>> = _backlinksFlow.asStateFlow()

    private val _streamItemsFlow = MutableStateFlow<List<StreamItemData>>(emptyList())
    val streamItemsFlow: StateFlow<List<StreamItemData>> = _streamItemsFlow.asStateFlow()

    // --- MVI Architecture State & Effect Contracts (android_native_rules.md) ---
    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _homeEffect = Channel<HomeUiEffect>(Channel.BUFFERED)
    val homeEffect: Flow<HomeUiEffect> = _homeEffect.receiveAsFlow()

    private var hasExecutedStartupMaintenance = false

    private inline fun <T> suspendSafeOrNull(block: () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

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
                deleteNoteById(event.noteId)
                viewModelScope.launch(ioDispatcher) {
                    _homeEffect.send(HomeUiEffect.ShowToast("Not silindi"))
                }
            }
            is HomeUiEvent.TogglePin -> {
                togglePinStatus(event.noteId, event.isPinned)
            }
            is HomeUiEvent.BatchDeleteSelected -> {
                val ids = _homeUiState.value.selectedNoteIds
                val toDelete = _noteListFlow.value.filter { it.id in ids }
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
        } catch (_: Exception) {
        }
    }

    fun getAllNotes() {
        viewModelScope.launch(ioDispatcher) {
            try {
                var domainList = repository.getAllNotes()
                if (!hasExecutedStartupMaintenance) {
                    hasExecutedStartupMaintenance = true
                    val trashPurged = purgeAllTrashNotesOnUpgrade(domainList)
                    if (trashPurged) {
                        domainList = repository.getAllNotes()
                    }
                    val healed = healOrphanedSubPages(domainList)
                    if (healed) {
                        domainList = repository.getAllNotes()
                    }
                }
                val entities = domainList.map { it.toEntity() }
                val categories = suspendSafeOrNull { categoryRepository.getAllCategories() }.orEmpty()
                withContext(Dispatchers.Main) {
                    _noteListFlow.value = entities
                    _homeUiState.update {
                        it.copy(
                            notes = domainList,
                            categories = categories,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _homeUiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun upsert(item: NoteData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                if (item.id > 0) {
                    val previousNote = suspendSafeOrNull { repository.getNoteById(item.id) }
                    if (item.richBlocksJson == null && previousNote != null && !previousNote.richBlocksJson.isNullOrBlank()) {
                        item.richBlocksJson = previousNote.richBlocksJson
                    }
                    val previousStatus = previousNote?.status
                    repository.updateNote(item.toDomain())
                    if (item.status == "TRASH") {
                        purgeDeletedNoteReferences(item.id, item.title)
                    } else if (previousStatus == "TRASH") {
                        restoreSubPageToParent(item.toDomain())
                    }
                } else {
                    saveNoteUseCase(item.toDomain())
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun saveNote(item: NoteData, contentForLinks: String? = null, onSaved: ((Int) -> Unit)? = null) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val previousNote = if (item.id > 0) {
                    suspendSafeOrNull { repository.getNoteById(item.id) }
                        ?: _noteListFlow.value.find { it.id == item.id }?.toDomain()
                } else {
                    null
                }
                if (item.richBlocksJson == null && previousNote != null && !previousNote.richBlocksJson.isNullOrBlank()) {
                    item.richBlocksJson = previousNote.richBlocksJson
                }
                val previousTitle = previousNote?.title
                val previousStatus = previousNote?.status

                val domainItem = item.toDomain().let {
                    if (it.date <= 0L) it.copy(date = clock.nowMillis()) else it
                }

                val actualId = if (domainItem.id > 0) {
                    repository.updateNote(domainItem)
                    domainItem.id
                } else {
                    repository.insertNote(domainItem).toInt()
                }
                item.id = actualId

                if (item.status == "TRASH") {
                    purgeDeletedNoteReferences(
                        deletedNoteId = actualId,
                        deletedRawTitle = item.title ?: previousTitle
                    )
                } else {
                    if (previousStatus == "TRASH") {
                        restoreSubPageToParent(domainItem.copy(id = actualId))
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
            val backlinkRows = suspendSafeOrNull { knowledgeRepository.getBacklinks(targetNoteId) }.orEmpty()

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

            val allNotes = suspendSafeOrNull { repository.getAllNotes() }.orEmpty()

            for (candidate in allNotes) {
                if (candidate.id == targetNoteId) continue
                val candDesc = candidate.description
                val wasEncrypted = candidate.isEncrypted ||
                    (candidate.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(candDesc)
                val rawDesc = if (wasEncrypted && !candDesc.isNullOrBlank()) {
                    when (val dec = SecurityUtil.decryptSafe(candDesc)) {
                        is AppResult.Success -> dec.data
                        is AppResult.Failure -> continue // Never overwrite undecryptable encrypted notes
                    }
                } else {
                    candDesc ?: ""
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
                            oldTitles.any { block.content.trim().equals(it, ignoreCase = true) }
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
                    suspendSafeOrNull { repository.updateNote(candidate.copy(description = finalDesc)) }
                }
            }

            if (backlinkRows.isNotEmpty() || oldTitles.isNotEmpty()) {
                suspendSafeOrNull { knowledgeRepository.updateTargetLinkText(targetNoteId, newTitle) }
            }
        } catch (e: CancellationException) {
            throw e
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
            val backlinkRows = suspendSafeOrNull { knowledgeRepository.getBacklinks(deletedNoteId) }.orEmpty()

            val titlesToRemove = buildSet {
                deletedRawTitle?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                backlinkRows.forEach { row ->
                    val lt = row.linkText.trim()
                    if (lt.isNotBlank()) add(lt)
                }
            }

            val allNotes = suspendSafeOrNull { repository.getAllNotes() }.orEmpty()

            for (candidate in allNotes) {
                if (candidate.id == deletedNoteId) continue
                val candDesc = candidate.description
                val wasEncrypted = candidate.isEncrypted ||
                    (candidate.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(candDesc)
                val rawDesc = if (wasEncrypted && !candDesc.isNullOrBlank()) {
                    when (val dec = SecurityUtil.decryptSafe(candDesc)) {
                        is AppResult.Success -> dec.data
                        is AppResult.Failure -> continue
                    }
                } else {
                    candDesc ?: ""
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
                                titlesToRemove.any { block.content.trim().equals(it, ignoreCase = true) }
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
                            block = block.copy(content = pattern.replace(block.content, "").trimEnd())
                            blockChanged = true
                        }
                        if (pattern.containsMatchIn(block.toggleBody)) {
                            block = block.copy(toggleBody = pattern.replace(block.toggleBody, "").trimEnd())
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
                    suspendSafeOrNull { repository.updateNote(candidate.copy(description = finalDesc)) }
                }
            }

            suspendSafeOrNull { knowledgeRepository.deleteLinksForNote(deletedNoteId) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Re-attaches a sub-page restored from Trash back into its parent note as a SUB_PAGE block.
     */
    private suspend fun restoreSubPageToParent(restoredNote: Note): Boolean {
        return try {
            val richJson = restoredNote.richBlocksJson ?: return false
            val meta = BlockParser.parseRichMetadata(richJson)
            if (meta?.isSubPage != true && !richJson.contains("\"isSubPage\":true") && !richJson.contains("\"parentNoteId\"")) {
                return false
            }
            val parentId = meta?.parentNoteId
                ?: Regex(""""parentNoteId"\s*:\s*(\d+)""").find(richJson)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val parentSyncUuid = meta?.parentSyncUuid?.takeIf { it.isNotBlank() }

            val parentNote = when {
                parentSyncUuid != null -> suspendSafeOrNull { repository.getNoteBySyncUuid(parentSyncUuid) }
                    ?: if (parentId != null && parentId > 0) suspendSafeOrNull { repository.getNoteById(parentId) } else null
                parentId != null && parentId > 0 -> suspendSafeOrNull { repository.getNoteById(parentId) }
                else -> null
            }

            if (parentNote != null && parentNote.status != "TRASH") {
                val parentDesc = parentNote.description
                val wasEncrypted = parentNote.isEncrypted ||
                    (parentNote.isPassword == true) ||
                    SecurityUtil.isEncryptedPayload(parentDesc)
                val rawDesc = if (wasEncrypted && !parentDesc.isNullOrBlank()) {
                    when (val dec = SecurityUtil.decryptSafe(parentDesc)) {
                        is AppResult.Success -> dec.data
                        is AppResult.Failure -> return false
                    }
                } else {
                    parentDesc ?: ""
                }

                val parsedBlocks = if (rawDesc.isBlank()) {
                    mutableListOf()
                } else {
                    BlockParser.parse(rawDesc).toMutableList()
                }

                val restoredTitle = restoredNote.title?.trim()?.takeIf { it.isNotBlank() } ?: "Yeni Alt Sayfa"
                val existingIdx = parsedBlocks.indexOfFirst {
                    it.type == BlockType.SUB_PAGE &&
                        (it.subPageNoteId == restoredNote.id ||
                            (restoredNote.syncUuid.isNotBlank() && it.subPageSyncUuid == restoredNote.syncUuid))
                }

                var parentModified = false
                if (existingIdx == -1) {
                    parsedBlocks.add(
                        NoteBlock(
                            type = BlockType.SUB_PAGE,
                            content = restoredTitle,
                            subPageNoteId = restoredNote.id,
                            subPageSyncUuid = restoredNote.syncUuid.ifBlank { null }
                        )
                    )
                    parentModified = true
                } else {
                    val curBlock = parsedBlocks[existingIdx]
                    if (curBlock.content != restoredTitle || curBlock.subPageNoteId != restoredNote.id ||
                        (restoredNote.syncUuid.isNotBlank() && curBlock.subPageSyncUuid != restoredNote.syncUuid)
                    ) {
                        parsedBlocks[existingIdx] = curBlock.copy(
                            content = restoredTitle,
                            subPageNoteId = restoredNote.id,
                            subPageSyncUuid = restoredNote.syncUuid.ifBlank { curBlock.subPageSyncUuid }
                        )
                        parentModified = true
                    }
                }

                if (parentModified) {
                    val serialized = BlockParser.serialize(parsedBlocks)
                    val finalDesc = if (wasEncrypted) SecurityUtil.encrypt(serialized) else serialized
                    suspendSafeOrNull { repository.updateNote(parentNote.copy(description = finalDesc)) }
                }

                suspendSafeOrNull {
                    val existingLinks = knowledgeRepository.getOutgoingLinks(parentNote.id)
                    if (existingLinks.none { it.targetNoteId == restoredNote.id }) {
                        val newLink = NoteLink(
                            sourceNoteId = parentNote.id,
                            targetNoteId = restoredNote.id,
                            linkText = restoredTitle,
                            createdAt = clock.nowMillis()
                        )
                        knowledgeRepository.insertAllLinks(listOf(newLink))
                    }
                }
                parentModified
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun healOrphanedSubPages(notes: List<Note>): Boolean {
        var anyModified = false
        try {
            val now = clock.nowMillis()
            val activeNotes = notes.filter { it.status != "TRASH" }
            val referencedSubPageIds = activeNotes.flatMap { note ->
                suspendSafeOrNull {
                    val noteDesc = note.description
                    val wasEncrypted = note.isEncrypted ||
                        (note.isPassword == true) ||
                        SecurityUtil.isEncryptedPayload(noteDesc)
                    val rawDesc = if (wasEncrypted && !noteDesc.isNullOrBlank()) {
                        when (val dec = SecurityUtil.decryptSafe(noteDesc)) {
                            is AppResult.Success -> dec.data
                            is AppResult.Failure -> ""
                        }
                    } else {
                        noteDesc ?: ""
                    }
                    if (rawDesc.isBlank()) emptyList()
                    else BlockParser.parse(rawDesc)
                        .filter { it.type == BlockType.SUB_PAGE }
                        .mapNotNull { it.subPageNoteId }
                }.orEmpty()
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return anyModified
    }

    private suspend fun purgeAllTrashNotesOnUpgrade(notes: List<Note>): Boolean {
        val prefs = context.getSharedPreferences("purenoteit_maintenance_prefs", Context.MODE_PRIVATE)
        val alreadyPurged = prefs.getBoolean("has_purged_trash_subpage_fix_v1", false)
        if (alreadyPurged) return false

        var anyDeleted = false
        try {
            val trashedNotes = notes.filter { it.status == "TRASH" }
            for (note in trashedNotes) {
                purgeDeletedNoteReferences(note.id, note.title)
                deleteNoteUseCase(note.id)
                anyDeleted = true
            }
            prefs.edit().putBoolean("has_purged_trash_subpage_fix_v1", true).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return anyDeleted
    }

    fun delete(item: NoteData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                purgeDeletedNoteReferences(item.id, item.title)
                if (item.id > 0) {
                    deleteNoteUseCase(item.id)
                } else {
                    repository.deleteNote(item.toDomain())
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun deleteNoteById(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val existingTitle = suspendSafeOrNull { repository.getNoteById(id)?.title }
                    ?: _noteListFlow.value.find { it.id == id }?.title
                purgeDeletedNoteReferences(id, existingTitle)
                deleteNoteUseCase(id)
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun batchMoveToTrash(notes: List<NoteData>, onComplete: () -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            try {
                for (note in notes) {
                    val previousNote = suspendSafeOrNull { repository.getNoteById(note.id) }
                    val richJson = note.richBlocksJson ?: previousNote?.richBlocksJson
                    val trashedDomain = note.toDomain().copy(
                        status = "TRASH",
                        richBlocksJson = richJson
                    )
                    repository.updateNote(trashedDomain)
                    purgeDeletedNoteReferences(trashedDomain.id, trashedDomain.title)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    onComplete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
                    val previousNote = suspendSafeOrNull { repository.getNoteById(note.id) }
                    val currentDesc = note.description ?: ""
                    val updatedDesc = if (shouldLock) {
                        if (note.isEncrypted || SecurityUtil.isEncryptedPayload(currentDesc)) {
                            currentDesc
                        } else {
                            SecurityUtil.encrypt(currentDesc)
                        }
                    } else {
                        if (note.isEncrypted || SecurityUtil.isEncryptedPayload(currentDesc)) {
                            when (val dec = SecurityUtil.decryptSafe(currentDesc)) {
                                is AppResult.Success -> dec.data
                                is AppResult.Failure -> continue
                            }
                        } else {
                            currentDesc
                        }
                    }

                    val richJson = note.richBlocksJson ?: previousNote?.richBlocksJson
                    val updatedDomain = note.toDomain().copy(
                        isPassword = shouldLock,
                        isEncrypted = shouldLock,
                        description = updatedDesc,
                        richBlocksJson = richJson
                    )
                    repository.updateNote(updatedDomain)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    getAllLinks()
                    notifyWidgetSync()
                    triggerAutoSync()
                    onComplete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun togglePinStatus(id: Int, currentPinned: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.updatePinStatus(id, !currentPinned)
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    notifyWidgetSync()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
                val domainNotes = repository.getNotesBetweenDates(startDate, endDate)
                val entities = domainNotes.map { it.toEntity() }
                withContext(Dispatchers.Main) {
                    _noteListFlow.value = entities
                    _homeUiState.update { it.copy(notes = domainNotes) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun searchNotesByCategory(categoryName: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.getNotesByCategoryFlow(categoryName).collect { domainNotes ->
                    val entities = domainNotes.map { it.toEntity() }
                    withContext(Dispatchers.Main) {
                        _noteListFlow.value = entities
                        _homeUiState.update { it.copy(notes = domainNotes) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun searchNote(query: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.searchNotesFlow(query).collect { domainNotes ->
                    val entities = domainNotes.map { it.toEntity() }
                    withContext(Dispatchers.Main) {
                        _noteListFlow.value = entities
                        _homeUiState.update { it.copy(notes = domainNotes) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    suspend fun getNoteByIdSuspend(id: Int): NoteData? = withContext(ioDispatcher) {
        repository.getNoteById(id)?.toEntity()
    }

    // --- Bi-directional Links & Graph Data ---
    fun getAllLinks() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val links = knowledgeRepository.getAllLinks().map { it.toEntity() }
                withContext(Dispatchers.Main) {
                    _allLinksFlow.value = links
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun loadBacklinksForNote(targetNoteId: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val links = knowledgeRepository.getBacklinks(targetNoteId)
                val sourceIds = links.map { it.sourceNoteId }.toSet()
                val allCurrentNotes = _noteListFlow.value.ifEmpty {
                    repository.getAllNotes().map { it.toEntity() }
                }
                withContext(Dispatchers.Main) {
                    _backlinksFlow.value = allCurrentNotes.filter { it.id in sourceIds }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
                val allNotes = repository.getAllNotes()
                val existingOutgoing = suspendSafeOrNull {
                    knowledgeRepository.getOutgoingLinks(sourceNoteId)
                }.orEmpty()

                val resolvedTargets = mutableMapOf<Int, NoteLink>()
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
                        resolvedTargets[targetNote.id] = NoteLink(
                            sourceNoteId = sourceNoteId,
                            targetNoteId = targetNote.id,
                            linkText = targetNote.title?.trim() ?: matchedLower,
                            createdAt = clock.nowMillis()
                        )
                    }
                }
                val targetLinks = resolvedTargets.values.toList()

                knowledgeRepository.deleteLinksFromSource(sourceNoteId)
                if (targetLinks.isNotEmpty()) {
                    knowledgeRepository.insertAllLinks(targetLinks)
                }

                withContext(Dispatchers.Main) {
                    getAllLinks()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // --- Quick Thought Stream ---
    fun loadStreamItems() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val items = knowledgeRepository.getAllStreamItems().map { it.toEntity() }
                withContext(Dispatchers.Main) {
                    _streamItemsFlow.value = items
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun addStreamItem(content: String, type: String = "TEXT", audioPath: String? = null, imagePath: String? = null) {
        val item = StreamItem(
            content = content,
            type = type,
            audioPath = audioPath,
            imagePath = imagePath,
            timestamp = clock.nowMillis()
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                knowledgeRepository.insertStreamItem(item)
                withContext(Dispatchers.Main) {
                    loadStreamItems()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun deleteStreamItem(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                knowledgeRepository.deleteStreamItemById(id)
                withContext(Dispatchers.Main) {
                    loadStreamItems()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun convertStreamItemsToNote(selectedIds: List<Int>, title: String, onSuccess: (NoteData) -> Unit) {
        val currentItems = _streamItemsFlow.value
        val selected = currentItems.filter { it.id in selectedIds }
        if (selected.isEmpty()) return

        val blocks = mutableListOf<NoteBlock>()
        blocks.add(NoteBlock(type = BlockType.HEADING_2, content = "Hızlı Fikirler Akışı"))

        for (item in selected) {
            when (item.type) {
                "AUDIO" -> blocks.add(NoteBlock(type = BlockType.AUDIO, mediaPath = item.audioPath))
                "IMAGE" -> blocks.add(NoteBlock(type = BlockType.IMAGE, mediaPath = item.imagePath))
                else -> blocks.add(NoteBlock(type = BlockType.BULLET_LIST, content = item.content))
            }
        }

        val domainNote = Note(
            title = if (title.isBlank()) "Fikir Notu (${selected.size} Madde)" else title.trim(),
            description = BlockParser.serialize(blocks),
            date = clock.nowMillis(),
            icon = "⚡"
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                val generatedId = repository.insertNote(domainNote).toInt()
                val savedEntity = domainNote.copy(id = generatedId).toEntity()
                suspendSafeOrNull {
                    knowledgeRepository.markStreamItemsProcessed(selectedIds, generatedId)
                }
                withContext(Dispatchers.Main) {
                    getAllNotes()
                    notifyWidgetSync()
                    loadStreamItems()
                    onSuccess(savedEntity)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    // --- Vault Backup & Restore (Domain Repository Layer) ---
    fun backupVault(context: Context, onSuccess: (File) -> Unit, onError: (Exception) -> Unit) {
        VaultBackupManager.createBackup(
            context = context,
            noteRepository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onSuccess = onSuccess,
            onError = onError,
            mediaPathResolver = mediaPathResolver
        )
    }

    fun restoreVault(context: Context, uri: Uri, onSuccess: (Int, Int) -> Unit, onError: (Exception) -> Unit) {
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
            onError = onError,
            mediaPathResolver = mediaPathResolver
        )
    }

    fun triggerAutoSync() {
        if (VaultBackupManager.isAutoSyncEnabled(context)) {
            VaultBackupManager.scheduleDebouncedAutoSync(
                context = context,
                noteRepository = repository,
                categoryRepository = categoryRepository,
                knowledgeRepository = knowledgeRepository,
                mediaPathResolver = mediaPathResolver
            )
        }
    }

    fun syncVaultNow(onComplete: (Boolean) -> Unit) {
        VaultBackupManager.performAutoSync(
            context = context,
            noteRepository = repository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onComplete = onComplete,
            mediaPathResolver = mediaPathResolver
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
