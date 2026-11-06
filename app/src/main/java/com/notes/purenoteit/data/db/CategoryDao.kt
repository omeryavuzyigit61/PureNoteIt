package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategorySuspend(category: CategoryData)

    @Update
    suspend fun updateCategorySuspend(category: CategoryData)

    @Delete
    suspend fun deleteCategorySuspend(category: CategoryData)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryByIdSuspend(id: Int)

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryData>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesSuspend(): List<CategoryData>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryByIdSuspend(id: Int): CategoryData?

    @Query("SELECT * FROM categories WHERE name LIKE '%' || :query || '%'")
    suspend fun searchCategorySuspend(query: String): List<CategoryData>

    @Query("SELECT COUNT(*) > 0 FROM categories WHERE name = :name")
    suspend fun isCategoryExistsSuspend(name: String): Boolean

    @Query("SELECT (SELECT COUNT(*) FROM categories WHERE name IN ('Not', 'Önemli', 'Günlük')) = 3")
    suspend fun areDefaultCategoriesPresentSuspend(): Boolean

    @Query("SELECT isPassword FROM categories WHERE name = :categoryName")
    suspend fun isPasswordSetSuspend(categoryName: String): Boolean?

    @Query("SELECT id FROM categories WHERE name = :categoryName")
    suspend fun getCategoryIdByNameSuspend(categoryName: String): Int?
}
