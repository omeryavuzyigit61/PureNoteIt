package com.notes.purenoteit.domain.repository

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput

interface CodeExecutionRepository {
    suspend fun executeOffline(
        language: String,
        code: String
    ): AppResult<CodeExecutionOutput, CodeExecutionError>
}
