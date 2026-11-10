package com.notes.purenoteit.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.notes.purenoteit.R
import com.notes.purenoteit.data.security.AndroidOAuthPkceManager
import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.ui.mvi.CloudSyncUiEffect
import com.notes.purenoteit.ui.mvi.CloudSyncUiEvent
import com.notes.purenoteit.ui.mvi.CloudSyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
class CloudSyncViewModel @Inject constructor(
    private val oauthPkceManager: AndroidOAuthPkceManager,
    private val configProvider: OAuthClientConfigProvider,
    private val tokenStorage: SecureTokenStorage,
    private val syncEngine: SyncEngine,
    private val autoSyncKeyCoordinator: AutoSyncKeyCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudSyncUiState())
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CloudSyncUiEffect>(Channel.BUFFERED)
    val effects: Flow<CloudSyncUiEffect> = _effects.receiveAsFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val hasToken = (tokenStorage.getTokenBundle() as? AppResult.Success)?.data != null
            val passRes = tokenStorage.getSyncPassphrase()
            val hasPass = when (passRes) {
                is AppResult.Success -> {
                    val chars = passRes.data
                    val exists = chars != null && chars.isNotEmpty()
                    chars?.fill('\u0000')
                    exists
                }
                is AppResult.Failure -> false
            }
            _uiState.update {
                it.copy(
                    isDriveConnected = hasToken,
                    hasStoredPassphrase = hasPass
                )
            }
        }
    }

    fun onEvent(event: CloudSyncUiEvent) {
        when (event) {
            is CloudSyncUiEvent.ConnectGoogleDrive -> connectDrive(event.activityContext)
            CloudSyncUiEvent.TriggerSyncNow -> syncNow()
            CloudSyncUiEvent.DisconnectGoogleDrive -> disconnect()
        }
    }

    private fun connectDrive(activityContext: Context) {
        val config = configProvider.getConfig()
        if (config == null) {
            _effects.trySend(CloudSyncUiEffect.ShowMessage(R.string.cloud_sync_client_unconfigured))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                when (val authRes = oauthPkceManager.authorizeWithCustomTab(activityContext, config)) {
                    is AppResult.Failure -> {
                        _effects.trySend(CloudSyncUiEffect.ShowMessage(mapErrorToRes(authRes.error)))
                    }
                    is AppResult.Success -> {
                        _uiState.update { it.copy(isDriveConnected = true) }
                        _effects.trySend(CloudSyncUiEffect.ShowMessage(R.string.cloud_sync_connected_toast))
                        executeAutoKeySyncLocked()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                executeAutoKeySyncLocked()
            } catch (e: CancellationException) {
                throw e
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private suspend fun executeAutoKeySyncLocked() {
        val passChars = when (val keyRes = autoSyncKeyCoordinator.resolveOrBootstrapPassphrase()) {
            is AppResult.Failure -> {
                _effects.trySend(CloudSyncUiEffect.ShowMessage(mapErrorToRes(keyRes.error)))
                return
            }
            is AppResult.Success -> keyRes.data
        }
        _uiState.update { it.copy(hasStoredPassphrase = true) }
        try {
            runSyncWithPassphraseLocked(passChars)
        } finally {
            passChars.fill('\u0000')
        }
    }

    private suspend fun runSyncWithPassphraseLocked(passChars: CharArray) {
        when (val res = syncEngine.synchronize(passChars)) {
            is AppResult.Success -> {
                val s = res.data
                _uiState.update {
                    it.copy(
                        lastSyncPushed = s.pushedEntities,
                        lastSyncPulled = s.pulledNotes
                    )
                }
                _effects.trySend(
                    CloudSyncUiEffect.ShowMessage(
                        messageRes = R.string.cloud_sync_completed_summary,
                        arg1 = s.pushedEntities,
                        arg2 = s.pulledNotes
                    )
                )
            }
            is AppResult.Failure -> {
                _effects.trySend(CloudSyncUiEffect.ShowMessage(mapErrorToRes(res.error)))
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            tokenStorage.clearAll()
            _uiState.update {
                it.copy(
                    isDriveConnected = false,
                    hasStoredPassphrase = false,
                    lastSyncPushed = null,
                    lastSyncPulled = null
                )
            }
            _effects.trySend(CloudSyncUiEffect.ShowMessage(R.string.cloud_sync_disconnected_toast))
        }
    }

    private fun mapErrorToRes(error: AppError): Int = when (error) {
        DataError.Security.DECRYPTION_FAILED -> R.string.cloud_sync_error_decrypt
        DataError.Security.KEY_REQUIRED -> R.string.cloud_sync_passphrase_required
        DataError.Security.KEYSTORE_UNAVAILABLE,
        DataError.Security.UNAUTHORIZED -> R.string.cloud_sync_error_security
        DataError.Network.UNAUTHORIZED -> R.string.cloud_sync_error_auth
        DataError.Network.NO_CONNECTION,
        DataError.Network.TIMEOUT -> R.string.cloud_sync_error_network
        DataError.Network.TLS_ERROR -> R.string.cloud_sync_error_tls
        else -> R.string.cloud_sync_error_generic
    }
}
