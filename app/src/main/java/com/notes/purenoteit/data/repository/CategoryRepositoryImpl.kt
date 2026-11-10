package com.notes.purenoteit.data.repository

import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.mapper.defaultCategorySyncUuid
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.time.AppClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val noteDao: NoteDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val clock: AppClock
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
        categoryDao.upsertCategorySuspend(prepareEntityForWrite(category))
    }

    override suspend fun updateCategory(category: Category) = withContext(ioDispatcher) {
        categoryDao.updateCategorySuspend(prepareEntityForWrite(category))
    }

    override suspend fun deleteCategory(category: Category) = withContext(ioDispatcher) {
        if (category.id > 0) {
            softDeleteById(category.id)
        } else if (category.syncUuid.isNotBlank()) {
            categoryDao.getCategoryBySyncUuidIncludingDeletedSuspend(category.syncUuid)?.let {
                softDeleteById(it.id)
            }
        }
    }

    override suspend fun deleteCategoryById(id: Int) = withContext(ioDispatcher) {
        if (id > 0) {
            softDeleteById(id)
        }
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

    override suspend fun getCategoryBySyncUuid(syncUuid: String): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryBySyncUuidIncludingDeletedSuspend(syncUuid)
            ?.takeIf { it.deletedAt == null }
            ?.toDomain()
    }

    override suspend fun getCategoriesModifiedSince(sinceMillis: Long): List<Category> = withContext(ioDispatcher) {
        categoryDao.getCategoriesModifiedSinceSuspend(sinceMillis).map { it.toDomain() }
    }

    private suspend fun softDeleteById(id: Int) {
        val now = clock.nowMillis()
        categoryDao.softDeleteWithTombstoneSuspend(id = id, deletedAt = now, updatedAt = now)
        noteDao.softDeleteNotesByCategorySuspend(categoryId = id, deletedAt = now, updatedAt = now)
    }

    private suspend fun prepareEntityForWrite(category: Category): CategoryData {
        val now = clock.nowMillis()
        val existing = if (category.id > 0) {
            categoryDao.getCategoryByIdIncludingDeletedSuspend(category.id)?.takeIf { it.id > 0 }
        } else null

        val resolvedUuid = category.syncUuid.ifBlank {
            existing?.syncUuid?.ifBlank { null }
                ?: defaultCategorySyncUuid(category.id, category.name)
                ?: UUID.randomUUID().toString()
        }
        val resolvedVersion = if (existing != null) {
            maxOf(category.syncVersion, existing.syncVersion) + 1L
        } else {
            category.syncVersion.coerceAtLeast(1L)
        }
        return category.copy(
            syncUuid = resolvedUuid,
            updatedAt = now,
            syncVersion = resolvedVersion
        ).toEntity()
    }
}
