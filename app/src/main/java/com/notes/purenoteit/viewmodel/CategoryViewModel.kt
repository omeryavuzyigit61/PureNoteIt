package com.notes.purenoteit.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val _categoryList = MutableLiveData<List<CategoryData>>()
    val categoryList: LiveData<List<CategoryData>> = _categoryList

    // Modern StateFlow API (Compose collectAsStateWithLifecycle için)
    private val _categoryListFlow = MutableStateFlow<List<CategoryData>>(emptyList())
    val categoryListFlow: StateFlow<List<CategoryData>> = _categoryListFlow.asStateFlow()

    fun seedDefaultCategories() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val present = runCatching { repository.areDefaultCategoriesPresentSuspend() }.getOrDefault(false)
                if (!present) {
                    repository.categoryUpsertSuspend(CategoryData("Not", false, id = 1))
                    repository.categoryUpsertSuspend(CategoryData("Günlük", false, id = 2))
                    repository.categoryUpsertSuspend(CategoryData("Önemli", false, id = 3))
                }
                getAllCategories()
            } catch (_: Exception) {
                runCatching {
                    repository.categoryUpsertSuspend(CategoryData("Not", false, id = 1))
                    repository.categoryUpsertSuspend(CategoryData("Günlük", false, id = 2))
                    repository.categoryUpsertSuspend(CategoryData("Önemli", false, id = 3))
                }
                getAllCategories()
            }
        }
    }

    fun getAllCategories() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val list = repository.getAllCategoriesSuspend()
                withContext(Dispatchers.Main) {
                    _categoryListFlow.value = list
                    runCatching { _categoryList.value = list }
                }
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error fetching categories: ${e.message}")
            }
        }
    }

    fun upsert(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.categoryUpsertSuspend(item)
                getAllCategories()
                Log.i("CategoryViewModel", "Category upserted successfully via Coroutines")
            } catch (error: Exception) {
                Log.e("CategoryViewModel", "Error upserting category: ${error.message}")
            }
        }
    }

    fun update(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.categoryUpdateSuspend(item)
                getAllCategories()
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error updating category: ${e.message}")
            }
        }
    }

    fun delete(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.categoryDeleteSuspend(item)
                getAllCategories()
            } catch (e: Exception) {
                Log.e("CategoryViewModel", "Error deleting category: ${e.message}")
            }
        }
    }

    fun deleteCategoryById(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.deleteCategoryByIdSuspend(id)
                getAllCategories()
                Log.i("CategoryViewModel", "Category deleted successfully via Coroutines")
            } catch (error: Exception) {
                Log.e("CategoryViewModel", "Error deleting category: ${error.message}")
            }
        }
    }

    fun searchCategory(query: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val list = repository.searchCategorySuspend(query)
                withContext(Dispatchers.Main) {
                    _categoryListFlow.value = list
                    runCatching { _categoryList.value = list }
                }
            } catch (error: Exception) {
                Log.e("CategoryViewModel", "Error searching categories: ${error.message}")
            }
        }
    }

    suspend fun getCategoryById(id: Int): CategoryData? = withContext(ioDispatcher) {
        repository.getCategoryByIdSuspend(id)
    }

    suspend fun isCategoryExists(name: String): Boolean = withContext(ioDispatcher) {
        repository.isCategoryExistsSuspend(name)
    }

    suspend fun isPasswordSet(categoryName: String): Boolean = withContext(ioDispatcher) {
        repository.isPasswordSetSuspend(categoryName) ?: false
    }

    suspend fun areDefaultCategoriesPresent(): Boolean = withContext(ioDispatcher) {
        repository.areDefaultCategoriesPresentSuspend()
    }

    suspend fun getCategoryIdByName(categoryName: String): Int? = withContext(ioDispatcher) {
        repository.getCategoryIdByNameSuspend(categoryName)
    }
}
