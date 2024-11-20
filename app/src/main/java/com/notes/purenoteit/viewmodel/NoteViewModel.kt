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

            }).let {
                compositeDisposable.add(it)
            }
    }

    fun upsert(item: NoteData) {
        repository.upsert(item).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
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

            }, {

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

    fun getNotesBetweenDates(startDate: Long, endDate: Long) {
        return repository.getNotesBetweenDates(startDate, endDate).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ dateDatas ->
                _noteList.value = dateDatas
            }, {
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun searchNotesByCategory(category: String) {
        repository.searchByCategory(category).subscribeOn(Schedulers.io())
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