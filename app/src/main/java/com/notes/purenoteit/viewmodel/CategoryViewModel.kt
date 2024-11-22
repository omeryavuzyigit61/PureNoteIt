package com.notes.purenoteit.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(private val repository: CategoryRepository) :
    ViewModel() {
    private val compositeDisposable = CompositeDisposable()
    private val _categoryList = MutableLiveData<List<CategoryData>>()
    val categoryList: LiveData<List<CategoryData>> = _categoryList
    fun getAllCategories() {
        repository.getAllCategories()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                _categoryList.value = it
            }, {

            }).let {
                compositeDisposable.add(it)
            }
    }

    fun upsert(item: CategoryData) {
        repository.categoryUpsert(item).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllCategories()
                Log.i("CategoryViewModel", "Note upserted successfully")
            }, { error ->
                Log.i("CategoryViewModel", "Error upserting note: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }

    }

    fun delete(item: CategoryData) {
        repository.categoryDelete(item).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({

            }, {

            }).let {
                compositeDisposable.add(it)
            }
    }

    fun deleteNoteById(id: Int) {
        repository.deleteNoteById(id).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({
                getAllCategories()
                Log.i("NoteViewModel", "Note deleted successfully")
            }, { error ->
                Log.e("NoteViewModel", "Error deleting note: ${error.message}")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun searchCategory(query: String) {
        repository.searchCategory(query).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribe({ notes ->
                _categoryList.value = notes
            }, { error ->
                Log.e("NoteViewModel", "Error fetching notes by category: $error")
            }).let {
                compositeDisposable.add(it)
            }
    }

    fun getNoteById(id: Int): Single<CategoryData> {
        return repository.getNoteById(id).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    fun isCategoryExists(name: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        repository.isCategoryExists(name)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ exists ->
                result.value = exists
            }, {

            }).let {
                compositeDisposable.add(it)
            }
        return result
    }

    fun isPasswordSet(categoryName: String): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        repository.isPasswordSet(categoryName)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ exists ->
                result.value = exists
                Log.i("CategoryViewModel", "Şifre ayarlandı mı?: $result")
            }, {
                Log.i("CategoryViewModel", "Şifre ayarlandı mı?: $result")
            }).let {
                compositeDisposable.add(it)
            }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}