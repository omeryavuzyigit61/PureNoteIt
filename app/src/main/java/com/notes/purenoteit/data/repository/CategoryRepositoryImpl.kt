package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CategoryRepository {

    override fun getAllCategoriesFlow(): Flow<List<Category>> {
        return categoryDao.getAllCategoriesFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAllCategories(): List<Category> = withContext(ioDispatcher) {
        categoryDao.getAllCategoriesSuspend().map { it.toDomain() }
    }

    override suspend fun getCategoryById(id: Int): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryByIdSuspend(id)?.toDomain()
    }

    override suspend fun upsertCategory(category: Category) = withContext(ioDispatcher) {
        categoryDao.upsertCategorySuspend(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) = withContext(ioDispatcher) {
        categoryDao.updateCategorySuspend(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) = withContext(ioDispatcher) {
        categoryDao.deleteCategorySuspend(category.toEntity())
    }

    override suspend fun deleteCategoryById(id: Int) = withContext(ioDispatcher) {
        categoryDao.deleteCategoryByIdSuspend(id)
    }

    override suspend fun searchCategories(query: String): List<Category> = withContext(ioDispatcher) {
        categoryDao.searchCategorySuspend(query).map { it.toDomain() }
    }

    override suspend fun isCategoryExists(name: String): Boolean = withContext(ioDispatcher) {
        categoryDao.isCategoryExistsSuspend(name)
    }

    override suspend fun areDefaultCategoriesPresent(): Boolean = withContext(ioDispatcher) {
        categoryDao.areDefaultCategoriesPresentSuspend()
    }

    override suspend fun isPasswordSet(categoryName: String): Boolean? = withContext(ioDispatcher) {
        categoryDao.isPasswordSetSuspend(categoryName)
    }

    override suspend fun getCategoryIdByName(categoryName: String): Int? = withContext(ioDispatcher) {
        categoryDao.getCategoryIdByNameSuspend(categoryName)
    }
}
