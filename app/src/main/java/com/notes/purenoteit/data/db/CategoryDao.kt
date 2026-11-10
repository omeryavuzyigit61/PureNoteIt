package com.notes.purenoteit.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsertCategorySuspend(category: CategoryData)

    @Update
    suspend fun updateCategorySuspend(category: CategoryData)

    @Query(
        """
        UPDATE categories
        SET deletedAt = :deletedAt,
            updatedAt = :updatedAt,
            syncVersion = syncVersion + 1
        WHERE id = :id AND deletedAt IS NULL
        """
    )
    suspend fun softDeleteWithTombstoneSuspend(id: Int, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryData>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name ASC")
    suspend fun getAllCategoriesSuspend(): List<CategoryData>

    @Query("SELECT * FROM categories WHERE id = :id AND deletedAt IS NULL")
    suspend fun getCategoryByIdSuspend(id: Int): CategoryData?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryByIdIncludingDeletedSuspend(id: Int): CategoryData?

    @Query("SELECT * FROM categories WHERE syncUuid = :syncUuid LIMIT 1")
    suspend fun getCategoryBySyncUuidIncludingDeletedSuspend(syncUuid: String): CategoryData?

    @Query("SELECT * FROM categories WHERE updatedAt > :sinceMillis ORDER BY updatedAt ASC")
    suspend fun getCategoriesModifiedSinceSuspend(sinceMillis: Long): List<CategoryData>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%'")
    suspend fun searchCategorySuspend(query: String): List<CategoryData>

    @Query("SELECT COUNT(*) > 0 FROM categories WHERE deletedAt IS NULL AND name = :name")
    suspend fun isCategoryExistsSuspend(name: String): Boolean

    @Query("SELECT (SELECT COUNT(*) FROM categories WHERE deletedAt IS NULL AND name IN ('Not', 'Önemli', 'Günlük')) = 3")
    suspend fun areDefaultCategoriesPresentSuspend(): Boolean

    @Query("SELECT isPassword FROM categories WHERE deletedAt IS NULL AND name = :categoryName LIMIT 1")
    suspend fun isPasswordSetSuspend(categoryName: String): Boolean?

    @Query("SELECT id FROM categories WHERE deletedAt IS NULL AND name = :categoryName LIMIT 1")
    suspend fun getCategoryIdByNameSuspend(categoryName: String): Int?
}
