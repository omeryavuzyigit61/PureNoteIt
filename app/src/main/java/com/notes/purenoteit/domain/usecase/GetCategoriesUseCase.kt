package com.notes.purenoteit.domain.usecase

import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

class GetCategoriesUseCase(
    private val categoryRepository: CategoryRepository
) {
    operator fun invoke(): Flow<List<Category>> {
        return categoryRepository.getAllCategoriesFlow()
    }
}
