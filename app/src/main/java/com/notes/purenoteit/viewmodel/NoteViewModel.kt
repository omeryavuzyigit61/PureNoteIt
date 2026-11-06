package com.notes.purenoteit.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val categoryRepository: CategoryRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val automationEngine: AutomationEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    private val _noteList = MutableLiveData<List<NoteData>>()
    val noteList: LiveData<List<NoteData>> = _noteList

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
        Single.fromCallable {
            val initialList = repository.getAllNotes().blockingGet()
            var currentList = initialList
            if (!hasExecutedStartupMaintenance) {
                hasExecutedStartupMaintenance = true
                val trashPurged = purgeAllTrashNotesOnUpgradeSync(currentList)
                if (trashPurged) {
                    currentList = repository.getAllNotes().blockingGet()
                }
                val healed = healOrphanedSubPagesSync(currentList)
                if (healed) {
                    currentList = repository.getAllNotes().blockingGet()
                }
            }
            currentList
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _noteList.value = it
                Log.i("datas", "getAllNotes: ")
            }, {
                Log.e("NoteViewModel", "Error fetching notes: ${it.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun upsert(item: NoteData) {
        io.reactivex.Completable.fromAction {
            if (item.id > 0) {
                val previousNote = runCatching { repository.getNoteById(item.id).blockingGet() }.getOrNull()
                if (item.richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                    item.richBlocksJson = previousNote?.richBlocksJson
                }
                val previousStatus = previousNote?.status
                repository.update(item).blockingAwait()
                if (item.status == "TRASH") {
                    purgeDeletedNoteReferencesSync(item.id, item.title)
                } else if (previousStatus == "TRASH") {
                    restoreSubPageToParentSync(item)
                }
            } else {
                repository.upsert(item).blockingAwait()
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                triggerAutoSync()
                Log.i("NoteViewModel", "Note upserted successfully")
            }, { error ->
                Log.i("NoteViewModel", "Error upserting note: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun saveNote(item: NoteData, contentForLinks: String? = null, onSaved: ((Int) -> Unit)? = null) {
        Single.fromCallable {
            val previousNote = if (item.id > 0) {
                runCatching { repository.getNoteById(item.id).blockingGet() }.getOrNull()
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
                repository.update(item).blockingAwait()
                item.id
            } else {
                repository.insertNote(item).blockingGet().toInt()
            }
            item.id = actualId

            if (item.status == "TRASH") {
                purgeDeletedNoteReferencesSync(
                    deletedNoteId = actualId,
                    deletedRawTitle = item.title ?: previousTitle
                )
            } else {
                if (previousStatus == "TRASH") {
                    restoreSubPageToParentSync(item)
                }
                propagateTitleChangeToLinkedNotesSync(
                    targetNoteId = actualId,
                    newRawTitle = item.title ?: "",
                    previousRawTitle = previousTitle
                )
            }
            actualId
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ actualId ->
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                if (item.status != "TRASH" && !contentForLinks.isNullOrBlank()) {
                    syncNoteLinks(actualId, contentForLinks)
                }
                triggerAutoSync()
                onSaved?.invoke(actualId)
            }, { error ->
                Log.e("NoteViewModel", "Error saving note: ${error.message}")
            }).let { compositeDisposable.add(it) }
    }

    /**
     * Propagates title changes of [targetNoteId] across all notes that link to it via [[WikiLink]]
     * or SUB_PAGE blocks, and updates the linkText column in note_links so links never break.
     */
    private fun propagateTitleChangeToLinkedNotesSync(
        targetNoteId: Int,
        newRawTitle: String,
        previousRawTitle: String?
    ) {
        val newTitle = newRawTitle.trim()
        if (newTitle.isBlank()) return

        try {
            val backlinkRows = runCatching {
                knowledgeRepository.getBacklinks(targetNoteId).blockingGet()
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
                repository.getAllNotes().blockingGet()
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
                    runCatching { repository.update(updatedCandidate).blockingAwait() }
                }
            }

            if (backlinkRows.isNotEmpty() || oldTitles.isNotEmpty()) {
                runCatching {
                    knowledgeRepository.updateTargetLinkText(targetNoteId, newTitle).blockingAwait()
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Removes all [[WikiLink]] tags, SUB_PAGE blocks, and note_links entries referencing a deleted
     * or trashed note so that source notes never retain dead links or spawn duplicate notes.
     */
    private fun purgeDeletedNoteReferencesSync(
        deletedNoteId: Int,
        deletedRawTitle: String?
    ) {
        try {
            val backlinkRows = runCatching {
                knowledgeRepository.getBacklinks(deletedNoteId).blockingGet()
            }.getOrDefault(emptyList())

            val titlesToRemove = buildSet {
                deletedRawTitle?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                backlinkRows.forEach { row ->
                    val lt = row.linkText.trim()
                    if (lt.isNotBlank()) add(lt)
                }
            }

            val allNotes = runCatching {
                repository.getAllNotes().blockingGet()
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
                    runCatching { repository.update(updatedCandidate).blockingAwait() }
                }
            }

            runCatching {
                knowledgeRepository.deleteLinksForNote(deletedNoteId).blockingAwait()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Re-attaches a sub-page restored from Trash back into its parent note as a SUB_PAGE block,
     * or promotes it to a standalone root note if its parent note was deleted or is in Trash.
     */
    private fun restoreSubPageToParentSync(restoredNote: NoteData): Boolean {
        return try {
            val richJson = restoredNote.richBlocksJson ?: return false
            if (!richJson.contains("\"isSubPage\":true") && !richJson.contains("\"parentNoteId\"")) {
                return false
            }
            val parentIdMatch = Regex(""""parentNoteId"\s*:\s*(\d+)""").find(richJson)
            val parentId = parentIdMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

            val parentNote = if (parentId != null && parentId > 0) {
                runCatching { repository.getNoteById(parentId).blockingGet() }.getOrNull()
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
                    runCatching { repository.update(updatedParent).blockingAwait() }
                }

                runCatching {
                    val existingLinks = knowledgeRepository.getOutgoingLinks(parentNote.id).blockingGet()
                    if (existingLinks.none { it.targetNoteId == restoredNote.id }) {
                        val newLink = NoteLinkData(
                            sourceNoteId = parentNote.id,
                            targetNoteId = restoredNote.id,
                            linkText = restoredTitle
                        )
                        knowledgeRepository.insertAllLinks(listOf(newLink)).blockingAwait()
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
    private fun healOrphanedSubPagesSync(notes: List<NoteData>): Boolean {
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
                    if (restoreSubPageToParentSync(candidate)) {
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
    private fun purgeAllTrashNotesOnUpgradeSync(notes: List<NoteData>): Boolean {
        val prefs = context.getSharedPreferences("purenoteit_maintenance_prefs", Context.MODE_PRIVATE)
        val alreadyPurged = prefs.getBoolean("has_purged_trash_subpage_fix_v1", false)
        if (alreadyPurged) return false

        var anyDeleted = false
        try {
            val trashedNotes = notes.filter { it.status == "TRASH" }
            for (note in trashedNotes) {
                purgeDeletedNoteReferencesSync(note.id, note.title)
                repository.deleteNoteById(note.id).blockingAwait()
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
        io.reactivex.Completable.fromAction {
            purgeDeletedNoteReferencesSync(item.id, item.title)
            repository.delete(item).blockingAwait()
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                triggerAutoSync()
            }, {
                Log.e("NoteViewModel", "Error deleting note: ${it.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun deleteNoteById(id: Int) {
        io.reactivex.Completable.fromAction {
            val existingTitle = runCatching { repository.getNoteById(id).blockingGet()?.title }.getOrNull()
                ?: _noteList.value?.find { it.id == id }?.title
            purgeDeletedNoteReferencesSync(id, existingTitle)
            repository.deleteNoteById(id).blockingAwait()
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                triggerAutoSync()
                Log.i("NoteViewModel", "Note deleted successfully")
            }, { error ->
                Log.e("NoteViewModel", "Error deleting note: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun batchMoveToTrash(notes: List<NoteData>, onComplete: () -> Unit = {}) {
        io.reactivex.Completable.fromAction {
            for (note in notes) {
                val previousNote = runCatching { repository.getNoteById(note.id).blockingGet() }.getOrNull()
                val trashed = note.copy(status = "TRASH").apply {
                    id = note.id
                    if (richBlocksJson == null && !previousNote?.richBlocksJson.isNullOrBlank()) {
                        richBlocksJson = previousNote?.richBlocksJson
                    }
                }
                repository.update(trashed).blockingAwait()
                purgeDeletedNoteReferencesSync(trashed.id, trashed.title)
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                triggerAutoSync()
                onComplete()
            }, { error ->
                Log.e("NoteViewModel", "Error in batchMoveToTrash: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun batchToggleLock(
        context: Context,
        notes: List<NoteData>,
        shouldLock: Boolean,
        onComplete: () -> Unit = {}
    ) {
        io.reactivex.Completable.fromAction {
            for (note in notes) {
                val previousNote = runCatching { repository.getNoteById(note.id).blockingGet() }.getOrNull()
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
                repository.update(updatedNote).blockingAwait()
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                getAllNotes()
                getAllLinks()
                notifyWidgetSync()
                triggerAutoSync()
                onComplete()
            }, { error ->
                Log.e("NoteViewModel", "Error in batchToggleLock: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun togglePinStatus(id: Int, currentPinned: Boolean) {
        repository.updatePinStatus(id, !currentPinned)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                getAllNotes()
                notifyWidgetSync()
                Log.i("NoteViewModel", "Pin status toggled successfully")
            }, { error ->
                Log.e("NoteViewModel", "Error toggling pin: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun updateNoteStatus(note: NoteData, newStatus: String) {
        note.status = newStatus
        upsert(note)
    }

    fun getNotesBetweenDates(startDate: Long, endDate: Long) {
        repository.getNotesBetweenDates(startDate, endDate).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ dateDatas ->
                _noteList.value = dateDatas
            }, {
                Log.e("NoteViewModel", "Error fetching notes between dates: ${it.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun searchNotesByCategory(categoryName: String) {
        repository.getNotesByCategory(categoryName).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ notes ->
                _noteList.value = notes
            }, { error ->
                Log.e("NoteViewModel", "Error fetching notes by category: $error")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun searchNote(query: String) {
        repository.searchNote(query).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ notes ->
                _noteList.value = notes
            }, {
                Log.i("NoteViewModel", "Error searching notes: ${it.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun getNoteById(id: Int): Single<NoteData> {
        return repository.getNoteById(id).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    // --- Bi-directional Links & Graph Data ---
    fun getAllLinks() {
        knowledgeRepository.getAllLinks()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ links ->
                _allLinks.value = links
            }, {
                Log.e("NoteViewModel", "Error fetching note links: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    fun loadBacklinksForNote(targetNoteId: Int) {
        knowledgeRepository.getBacklinks(targetNoteId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ links ->
                val sourceIds = links.map { it.sourceNoteId }.toSet()
                val allCurrentNotes = _noteList.value ?: emptyList()
                _backlinks.value = allCurrentNotes.filter { it.id in sourceIds }
            }, {
                Log.e("NoteViewModel", "Error loading backlinks: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    fun syncNoteLinks(sourceNoteId: Int, contentText: String) {
        val linkRegex = Regex("""\[\[(.*?)\]\]""")
        val matchedTitles = linkRegex.findAll(contentText)
            .map { it.groupValues[1].trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        repository.getAllNotes()
            .subscribeOn(Schedulers.io())
            .flatMapCompletable { allNotes ->
                val existingOutgoing = runCatching {
                    knowledgeRepository.getOutgoingLinks(sourceNoteId).blockingGet()
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

                knowledgeRepository.deleteLinksFromSource(sourceNoteId)
                    .andThen(
                        if (targetLinks.isNotEmpty()) {
                            knowledgeRepository.insertAllLinks(targetLinks)
                        } else {
                            io.reactivex.Completable.complete()
                        }
                    )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                getAllLinks()
            }, {
                Log.e("NoteViewModel", "Error syncing note links: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    // --- Quick Thought Stream (Paket 4) ---
    fun loadStreamItems() {
        knowledgeRepository.getAllStreamItems()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ items ->
                _streamItems.value = items
            }, {
                Log.e("NoteViewModel", "Error loading stream items: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    fun addStreamItem(content: String, type: String = "TEXT", audioPath: String? = null, imagePath: String? = null) {
        val item = StreamItemData(
            content = content,
            type = type,
            audioPath = audioPath,
            imagePath = imagePath
        )
        knowledgeRepository.insertStreamItem(item)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                loadStreamItems()
            }, {
                Log.e("NoteViewModel", "Error adding stream item: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    fun deleteStreamItem(id: Int) {
        knowledgeRepository.deleteStreamItemById(id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                loadStreamItems()
            }, {
                Log.e("NoteViewModel", "Error deleting stream item: ${it.message}")
            }).let { compositeDisposable.add(it) }
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

        repository.insertNote(newNote)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ generatedId ->
                newNote.id = generatedId.toInt()
                knowledgeRepository.markStreamItemsProcessed(selectedIds, newNote.id)
                    .subscribeOn(Schedulers.io())
                    .onErrorComplete()
                    .subscribe()
                    .let { compositeDisposable.add(it) }
                getAllNotes()
                notifyWidgetSync()
                loadStreamItems()
                onSuccess(newNote)
            }, {
                Log.e("NoteViewModel", "Error converting stream to note: ${it.message}")
            }).let { compositeDisposable.add(it) }
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

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
