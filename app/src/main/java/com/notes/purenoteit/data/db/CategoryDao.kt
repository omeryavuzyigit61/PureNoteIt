package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Completable
import io.reactivex.Single

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCategory(category: CategoryData): Completable

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Single<List<CategoryData>>

    @Delete
    fun deleteCategory(category: CategoryData): Completable

    @Query("DELETE FROM categories WHERE id = :id")
    fun deleteNoteById(id: Int): Completable

    @Query("SELECT * FROM categories WHERE name LIKE '%' || :query || '%'")
    fun searchCategory(query: String): Single<List<CategoryData>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getNoteById(id: Int): Single<CategoryData>

    @Query("SELECT COUNT(*) > 0 FROM categories WHERE name = :name")
    fun isCategoryExists(name: String): Single<Boolean>

    @Query("SELECT (SELECT COUNT(*) FROM categories WHERE name IN ('Not', 'Önemli', 'Günlük')) = 3")
    fun areDefaultCategoriesPresent(): Single<Boolean>

    @Query("SELECT isPassword FROM categories WHERE name = :categoryName")
    fun isPasswordSet(categoryName: String): Single<Boolean>
}