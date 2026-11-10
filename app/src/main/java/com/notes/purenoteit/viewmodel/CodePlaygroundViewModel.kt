package com.notes.purenoteit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeLanguageCapabilities
import com.notes.purenoteit.domain.usecase.ExecuteOfflineCodeUseCase
import com.notes.purenoteit.ui.mvi.BlockExecutionUiState
import com.notes.purenoteit.ui.mvi.CodePlaygroundUiEffect
import com.notes.purenoteit.ui.mvi.CodePlaygroundUiEvent
import com.notes.purenoteit.ui.mvi.CodePlaygroundUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CodePlaygroundViewModel @Inject constructor(
    private val executeOfflineCodeUseCase: ExecuteOfflineCodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodePlaygroundUiState())
    val uiState: StateFlow<CodePlaygroundUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CodePlaygroundUiEffect>(Channel.BUFFERED)
    val effects: Flow<CodePlaygroundUiEffect> = _effects.receiveAsFlow()

    private val runningJobs = mutableMapOf<String, Job>()

    fun onEvent(event: CodePlaygroundUiEvent) {
        when (event) {
            is CodePlaygroundUiEvent.RunCode -> runBlockCode(event.blockId, event.language, event.code)
            is CodePlaygroundUiEvent.ClearOutput -> clearBlock(event.blockId)
        }
    }

    fun requestExternalPlayground(language: String) {
        val url = CodeLanguageCapabilities.externalPlaygroundUrl(language) ?: return
        viewModelScope.launch {
            _effects.send(CodePlaygroundUiEffect.OpenExternalPlayground(url))
        }
    }

    private fun runBlockCode(blockId: String, language: String, code: String) {
        runningJobs[blockId]?.cancel()
        val job = viewModelScope.launch {
            _uiState.update { state ->
                val updated = state.blockStates + (
                    blockId to BlockExecutionUiState(isRunning = true)
                )
                state.copy(blockStates = updated)
            }

            try {
                when (val result = executeOfflineCodeUseCase(language = language, code = code)) {
                    is AppResult.Success -> {
                        _uiState.update { state ->
                            val updated = state.blockStates + (
                                blockId to BlockExecutionUiState(
                                    isRunning = false,
                                    output = result.data,
                                    error = null
                                )
                            )
                            state.copy(blockStates = updated)
                        }
                    }

                    is AppResult.Failure -> {
                        _uiState.update { state ->
                            val updated = state.blockStates + (
                                blockId to BlockExecutionUiState(
                                    isRunning = false,
                                    output = null,
                                    error = result.error
                                )
                            )
                            state.copy(blockStates = updated)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                runningJobs.remove(blockId)
            }
        }
        runningJobs[blockId] = job
    }

    private fun clearBlock(blockId: String) {
        runningJobs.remove(blockId)?.cancel()
        _uiState.update { state ->
            state.copy(blockStates = state.blockStates - blockId)
        }
    }
}
