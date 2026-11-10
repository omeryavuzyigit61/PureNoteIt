package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.RemoteSyncPullPage
import com.notes.purenoteit.sync.model.RemoteSyncPushAck

/**
 * Server-agnostic remote synchronization data source abstraction.
 * Allows swapping Google Drive (`appDataFolder`) with a custom backend or LAN WebSocket
 * without altering [SyncEngine] or conflict resolution logic.
 */
interface RemoteSyncDataSource {
    suspend fun pullChanges(sinceCursor: String?): AppResult<RemoteSyncPullPage, DataError.Network>

    suspend fun pushDelta(encryptedDelta: EncryptedDeltaEnvelope): AppResult<RemoteSyncPushAck, DataError.Network>

    suspend fun uploadMediaBlob(mediaKey: String, encryptedBytes: ByteArray): AppResult<Unit, DataError.Network>

    suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network>

    suspend fun readAppDataConfigFile(fileName: String): AppResult<String?, DataError.Network> =
        AppResult.Success(null)

    suspend fun getOrCreateAppDataConfigFile(fileName: String, content: String): AppResult<String, DataError.Network> =
        AppResult.Success(content)
}
