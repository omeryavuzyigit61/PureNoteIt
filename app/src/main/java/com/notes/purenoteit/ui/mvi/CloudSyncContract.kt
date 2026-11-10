package com.notes.purenoteit.ui.mvi

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class CloudSyncUiState(
    val isDriveConnected: Boolean = false,
    val hasStoredPassphrase: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncPushed: Int? = null,
    val lastSyncPulled: Int? = null
)

sealed interface CloudSyncUiEvent {
    data class ConnectGoogleDrive(val activityContext: Context) : CloudSyncUiEvent
    data object TriggerSyncNow : CloudSyncUiEvent
    data object DisconnectGoogleDrive : CloudSyncUiEvent
}

sealed interface CloudSyncUiEffect {
    data class ShowMessage(
        @StringRes val messageRes: Int,
        val arg1: Int = 0,
        val arg2: Int = 0
    ) : CloudSyncUiEffect
}
