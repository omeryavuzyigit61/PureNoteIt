package com.notes.purenoteit.domain.usecase

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.repository.CodeExecutionRepository

class ExecuteOfflineCodeUseCase(
    private val codeExecutionRepository: CodeExecutionRepository
) {
    suspend operator fun invoke(
        language: String,
        code: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError> {
        if (code.isBlank()) {
            return AppResult.Failure(CodeExecutionError.EmptyCode)
        }
        return codeExecutionRepository.executeOffline(language = language, code = code)
    }
}
