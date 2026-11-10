package com.notes.purenoteit.ui.mvi

import androidx.compose.runtime.Immutable
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput

@Immutable
data class BlockExecutionUiState(
    val isRunning: Boolean = false,
    val output: CodeExecutionOutput? = null,
    val error: CodeExecutionError? = null
)

@Immutable
data class CodePlaygroundUiState(
    val blockStates: Map<String, BlockExecutionUiState> = emptyMap()
)

sealed interface CodePlaygroundUiEvent {
    data class RunCode(
        val blockId: String,
        val language: String,
        val code: String
    ) : CodePlaygroundUiEvent

    data class ClearOutput(val blockId: String) : CodePlaygroundUiEvent
}

sealed interface CodePlaygroundUiEffect {
    data class OpenExternalPlayground(val url: String) : CodePlaygroundUiEffect
}
