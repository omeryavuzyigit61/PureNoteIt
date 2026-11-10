package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError

/**
 * Supplies OAuth 2.0 Bearer tokens for remote sync transports.
 * Implemented in Phase 3 via Chrome Custom Tabs (Android) and Loopback PKCE (Desktop).
 */
interface SyncTokenProvider {
    suspend fun getAccessToken(): AppResult<String, DataError.Network>

    suspend fun refreshAccessToken(expiredToken: String?): AppResult<String, DataError.Network>
}
