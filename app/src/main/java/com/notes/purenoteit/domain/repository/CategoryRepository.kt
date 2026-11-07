package com.notes.purenoteit.domain.repository

import com.notes.purenoteit.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAllCategoriesFlow(): Flow<List<Category>>
    suspend fun getAllCategories(): List<Category>
    suspend fun getCategoryById(id: Int): Category?
    suspend fun upsertCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(category: Category)
    suspend fun deleteCategoryById(id: Int)
    suspend fun searchCategories(query: String): List<Category>
    suspend fun isCategoryExists(name: String): Boolean
    suspend fun areDefaultCategoriesPresent(): Boolean
    suspend fun isPasswordSet(categoryName: String): Boolean?
    suspend fun getCategoryIdByName(categoryName: String): Int?
}
