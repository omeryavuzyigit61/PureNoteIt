package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import javax.inject.Inject

class CategoryRepository @Inject constructor(private val categoryDao: CategoryDao) {
    fun categoryUpsert(category: CategoryData) = categoryDao.upsertCategory(category)
    fun categoryUpdate(category: CategoryData) = categoryDao.updateCategory(category)
    fun categoryDelete(category: CategoryData) = categoryDao.deleteCategory(category)
    fun getAllCategories() = categoryDao.getAllCategories()
    fun deleteCategoryById(id: Int) = categoryDao.deleteCategoryById(id)
    fun searchCategory(query: String) = categoryDao.searchCategory(query)
    fun getNoteById(id: Int) = categoryDao.getNoteById(id)
    fun isCategoryExists(name: String) = categoryDao.isCategoryExists(name)
    fun isPasswordSet(categoryName: String) = categoryDao.isPasswordSet(categoryName)
    fun areDefaultCategoriesPresent() = categoryDao.areDefaultCategoriesPresent()
    fun getCategoryIdByName(categoryName: String) = categoryDao.getCategoryIdByName(categoryName)
}