package com.notes.purenoteit.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decoupled signal bus connecting local note/category/stream mutations with background Drive sync
 * and notifying ViewModels when remote changes are applied locally.
 */
@Singleton
class CloudSyncTriggerBus @Inject constructor() {

    private val _localChangeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val localChangeRequests: SharedFlow<Unit> = _localChangeRequests.asSharedFlow()

    private val _immediateChangeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val immediateChangeRequests: SharedFlow<Unit> = _immediateChangeRequests.asSharedFlow()

    private val _remoteAppliedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val remoteAppliedEvents: SharedFlow<Unit> = _remoteAppliedEvents.asSharedFlow()

    fun requestAutoSync() {
        _localChangeRequests.tryEmit(Unit)
    }

    fun requestImmediateSync() {
        _immediateChangeRequests.tryEmit(Unit)
    }

    fun notifyRemoteApplied() {
        _remoteAppliedEvents.tryEmit(Unit)
    }
}
