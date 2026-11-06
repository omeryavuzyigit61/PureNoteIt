package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val categoryDao: CategoryDao) {
    // --- Modern Coroutines & Flow API (Main-safe, Zero SQL schema changes) ---
    suspend fun categoryUpsertSuspend(category: CategoryData) = categoryDao.upsertCategorySuspend(category)
    suspend fun categoryUpdateSuspend(category: CategoryData) = categoryDao.updateCategorySuspend(category)
    suspend fun categoryDeleteSuspend(category: CategoryData) = categoryDao.deleteCategorySuspend(category)
    suspend fun deleteCategoryByIdSuspend(id: Int) = categoryDao.deleteCategoryByIdSuspend(id)
    fun getAllCategoriesFlow(): Flow<List<CategoryData>> = categoryDao.getAllCategoriesFlow()
    suspend fun getAllCategoriesSuspend(): List<CategoryData> = categoryDao.getAllCategoriesSuspend()
    suspend fun getCategoryByIdSuspend(id: Int): CategoryData? = categoryDao.getCategoryByIdSuspend(id)
    suspend fun searchCategorySuspend(query: String): List<CategoryData> = categoryDao.searchCategorySuspend(query)
    suspend fun isCategoryExistsSuspend(name: String): Boolean = categoryDao.isCategoryExistsSuspend(name)
    suspend fun areDefaultCategoriesPresentSuspend(): Boolean = categoryDao.areDefaultCategoriesPresentSuspend()
    suspend fun isPasswordSetSuspend(categoryName: String): Boolean? = categoryDao.isPasswordSetSuspend(categoryName)
    suspend fun getCategoryIdByNameSuspend(categoryName: String): Int? = categoryDao.getCategoryIdByNameSuspend(categoryName)
}
