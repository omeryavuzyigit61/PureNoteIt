package com.notes.purenoteit.security

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.sync.SyncTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared [SyncTokenProvider] integrating [SecureTokenStorage] with [OAuthPkceProtocol].
 * Performs Mutex-guarded single-flight token refresh and wipes revoked sessions on `invalid_grant`.
 */
class OAuthSyncTokenProvider(
    private val tokenStorage: SecureTokenStorage,
    private val pkceProtocol: OAuthPkceProtocol,
    private val configProvider: OAuthClientConfigProvider,
    private val clock: AppClock
) : SyncTokenProvider {

    private val refreshMutex = Mutex()

    override suspend fun getAccessToken(): AppResult<String, DataError.Network> {
        val bundle = when (val read = tokenStorage.getTokenBundle()) {
            is AppResult.Failure -> return AppResult.Failure(DataError.Network.UNAUTHORIZED)
            is AppResult.Success -> read.data ?: return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }
        val now = clock.nowMillis()
        if (bundle.accessToken.isNotBlank() && now < bundle.expiresAtEpochMillis - EXPIRY_SKEW_MS) {
            return AppResult.Success(bundle.accessToken)
        }
        return refreshAccessToken(bundle.accessToken)
    }

    override suspend fun refreshAccessToken(expiredToken: String?): AppResult<String, DataError.Network> {
        return refreshMutex.withLock {
            try {
                val currentBundle = when (val read = tokenStorage.getTokenBundle()) {
                    is AppResult.Failure -> return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    is AppResult.Success -> read.data ?: return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                }

                val now = clock.nowMillis()
                if (expiredToken != null &&
                    currentBundle.accessToken != expiredToken &&
                    now < currentBundle.expiresAtEpochMillis - EXPIRY_SKEW_MS
                ) {
                    return@withLock AppResult.Success(currentBundle.accessToken)
                }

                val refreshToken = currentBundle.refreshToken?.takeIf { it.isNotBlank() }
                    ?: return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                val config = configProvider.getConfig()
                    ?: return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)

                val refreshRes = pkceProtocol.refreshAccessToken(config, refreshToken)
                when (refreshRes) {
                    is AppResult.Failure -> {
                        if (refreshRes.error == DataError.Network.UNAUTHORIZED) {
                            tokenStorage.clearAll()
                        }
                        refreshRes
                    }
                    is AppResult.Success -> {
                        when (tokenStorage.saveTokenBundle(refreshRes.data)) {
                            is AppResult.Failure -> AppResult.Failure(DataError.Network.UNAUTHORIZED)
                            is AppResult.Success -> AppResult.Success(refreshRes.data.accessToken)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    companion object {
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
