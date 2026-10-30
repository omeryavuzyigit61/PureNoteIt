package com.notes.purenoteit.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(private val repository: NoteRepository) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    private val _noteList = MutableLiveData<List<NoteData>>()
    val noteList: LiveData<List<NoteData>> = _noteList

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

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
