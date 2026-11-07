package com.notes.purenoteit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.usecase.GetCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    private val getCategoriesUseCase: GetCategoriesUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _categoriesDomainFlow = MutableStateFlow<List<Category>>(emptyList())
    val categoriesDomainFlow: StateFlow<List<Category>> = _categoriesDomainFlow.asStateFlow()

    private val _categoryListFlow = MutableStateFlow<List<CategoryData>>(emptyList())
    val categoryListFlow: StateFlow<List<CategoryData>> = _categoryListFlow.asStateFlow()

    private inline fun <T> suspendSafeOrNull(block: () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            try {
                getCategoriesUseCase().collect { domainCategories ->
                    val entities = domainCategories.map { it.toEntity() }
                    withContext(Dispatchers.Main) {
                        _categoriesDomainFlow.value = domainCategories
                        _categoryListFlow.value = entities
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun seedDefaultCategories() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val present = suspendSafeOrNull { repository.areDefaultCategoriesPresent() } ?: false
                if (!present) {
                    repository.upsertCategory(Category(id = 1, name = "Not", isPassword = false))
                    repository.upsertCategory(Category(id = 2, name = "Günlük", isPassword = false))
                    repository.upsertCategory(Category(id = 3, name = "Önemli", isPassword = false))
                }
                getAllCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                suspendSafeOrNull {
                    repository.upsertCategory(Category(id = 1, name = "Not", isPassword = false))
                    repository.upsertCategory(Category(id = 2, name = "Günlük", isPassword = false))
                    repository.upsertCategory(Category(id = 3, name = "Önemli", isPassword = false))
                }
                getAllCategories()
            }
        }
    }

    fun getAllCategories() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val domainList = repository.getAllCategories()
                val entityList = domainList.map { it.toEntity() }
                withContext(Dispatchers.Main) {
                    _categoriesDomainFlow.value = domainList
                    _categoryListFlow.value = entityList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun upsert(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.upsertCategory(item.toDomain())
                getAllCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun update(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.updateCategory(item.toDomain())
                getAllCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun delete(item: CategoryData) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.deleteCategory(item.toDomain())
                getAllCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun deleteCategoryById(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.deleteCategoryById(id)
                getAllCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun searchCategory(query: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val domainList = repository.searchCategories(query)
                val entityList = domainList.map { it.toEntity() }
                withContext(Dispatchers.Main) {
                    _categoriesDomainFlow.value = domainList
                    _categoryListFlow.value = entityList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    suspend fun getCategoryById(id: Int): CategoryData? = withContext(ioDispatcher) {
        repository.getCategoryById(id)?.toEntity()
    }

    suspend fun isCategoryExists(name: String): Boolean = withContext(ioDispatcher) {
        repository.isCategoryExists(name)
    }

    suspend fun isPasswordSet(categoryName: String): Boolean = withContext(ioDispatcher) {
        repository.isPasswordSet(categoryName) ?: false
    }

    suspend fun areDefaultCategoriesPresent(): Boolean = withContext(ioDispatcher) {
        repository.areDefaultCategoriesPresent()
    }

    suspend fun getCategoryIdByName(categoryName: String): Int? = withContext(ioDispatcher) {
        repository.getCategoryIdByName(categoryName)
    }
}
