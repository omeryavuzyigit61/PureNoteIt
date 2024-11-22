package com.notes.purenoteit.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import javax.inject.Inject

class CategoryRepository @Inject constructor(private val categoryDao: CategoryDao) {
    fun categoryUpsert(category: CategoryData) = categoryDao.upsertCategory(category)
    fun categoryDelete(category: CategoryData) = categoryDao.deleteCategory(category)
    fun getAllCategories() = categoryDao.getAllCategories()
    fun deleteNoteById(id: Int) = categoryDao.deleteNoteById(id)
    fun searchCategory(query: String) = categoryDao.searchCategory(query)
    fun getNoteById(id: Int) = categoryDao.getNoteById(id)
    fun isCategoryExists(name: String) = categoryDao.isCategoryExists(name)
    fun isPasswordSet(categoryName: String) = categoryDao.isPasswordSet(categoryName)
}