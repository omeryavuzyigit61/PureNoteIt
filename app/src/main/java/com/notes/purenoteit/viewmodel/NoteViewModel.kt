package com.notes.purenoteit.viewmodel

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
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val automationEngine: AutomationEngine
) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    private val _noteList = MutableLiveData<List<NoteData>>()
    val noteList: LiveData<List<NoteData>> = _noteList

    private val _allLinks = MutableLiveData<List<NoteLinkData>>()
    val allLinks: LiveData<List<NoteLinkData>> = _allLinks

    private val _backlinks = MutableLiveData<List<NoteData>>()
    val backlinks: LiveData<List<NoteData>> = _backlinks

    private val _streamItems = MutableLiveData<List<StreamItemData>>()
    val streamItems: LiveData<List<StreamItemData>> = _streamItems

    fun getAllNotes() {
        repository.getNotes()
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
        repository.upsert(item).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
                Log.i("NoteViewModel", "Note upserted successfully")
            }, { error ->
                Log.i("NoteViewModel", "Error upserting note: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun delete(item: NoteData) {
        repository.delete(item).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
            }, {
                Log.e("NoteViewModel", "Error deleting note: ${it.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun deleteNoteById(id: Int) {
        repository.deleteNoteById(id).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllNotes()
                Log.i("NoteViewModel", "Note deleted successfully")
            }, { error ->
                Log.e("NoteViewModel", "Error deleting note: ${error.message}")
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

        repository.getNotes()
            .subscribeOn(Schedulers.io())
            .flatMapCompletable { allNotes ->
                val targetLinks = allNotes
                    .filter { it.id != sourceNoteId && it.title?.trim()?.lowercase() in matchedTitles }
                    .map { target ->
                        NoteLinkData(
                            sourceNoteId = sourceNoteId,
                            targetNoteId = target.id,
                            linkText = target.title ?: ""
                        )
                    }

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
                    .subscribe()
                getAllNotes()
                loadStreamItems()
                onSuccess(newNote)
            }, {
                Log.e("NoteViewModel", "Error converting stream to note: ${it.message}")
            }).let { compositeDisposable.add(it) }
    }

    // --- Automation Triggers ---
    fun createDailyAgenda(onSuccess: (NoteData) -> Unit) {
        automationEngine.createDailyAgendaNote { note ->
            getAllNotes()
            onSuccess(note)
        }
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
