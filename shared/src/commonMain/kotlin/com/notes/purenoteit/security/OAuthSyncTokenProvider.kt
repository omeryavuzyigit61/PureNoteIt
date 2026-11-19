package com.notes.purenoteit.security

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.sync.SyncTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Shared [SyncTokenProvider] integrating [SecureTokenStorage] with [OAuthPkceProtocol].
 * Uses an in-memory access-token cache (avoiding per-request Keyring/DataStore lock contention
 * during parallel Drive transfers), performs Mutex-guarded single-flight token refresh, and
 * wipes revoked sessions on `invalid_grant`.
 */
class OAuthSyncTokenProvider(
    private val tokenStorage: SecureTokenStorage,
    private val pkceProtocol: OAuthPkceProtocol,
    private val configProvider: OAuthClientConfigProvider,
    private val clock: AppClock
) : SyncTokenProvider {

    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()

    @Volatile
    private var cachedBundle: OAuthTokenBundle? = null

    override fun invalidateCache() {
        cachedBundle = null
    }

    override suspend fun getAccessToken(): AppResult<String, DataError.Network> {
        val now = clock.nowMillis()
        cachedBundle?.let { cached ->
            if (isUsable(cached, now)) {
                return AppResult.Success(cached.accessToken)
            }
        }

        val expiredTokenToRefresh = loadMutex.withLock {
            val nowLocked = clock.nowMillis()
            cachedBundle?.let { cached ->
                if (isUsable(cached, nowLocked)) {
                    return AppResult.Success(cached.accessToken)
                }
            }
            val bundle = when (val read = tokenStorage.getTokenBundle()) {
                is AppResult.Failure -> {
                    cachedBundle = null
                    return AppResult.Failure(DataError.Network.UNAUTHORIZED)
                }
                is AppResult.Success -> read.data ?: run {
                    cachedBundle = null
                    return AppResult.Failure(DataError.Network.UNAUTHORIZED)
                }
            }
            if (isUsable(bundle, nowLocked)) {
                cachedBundle = bundle
                return AppResult.Success(bundle.accessToken)
            }
            bundle.accessToken
        }

        return refreshAccessToken(expiredTokenToRefresh)
    }

    override suspend fun refreshAccessToken(expiredToken: String?): AppResult<String, DataError.Network> {
        return refreshMutex.withLock {
            try {
                val now = clock.nowMillis()
                cachedBundle?.let { cached ->
                    if (expiredToken != null &&
                        cached.accessToken != expiredToken &&
                        isUsable(cached, now)
                    ) {
                        return@withLock AppResult.Success(cached.accessToken)
                    }
                }

                val currentBundle = when (val read = tokenStorage.getTokenBundle()) {
                    is AppResult.Failure -> {
                        cachedBundle = null
                        return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    }
                    is AppResult.Success -> read.data ?: run {
                        cachedBundle = null
                        return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    }
                }

                if (expiredToken != null &&
                    currentBundle.accessToken != expiredToken &&
                    isUsable(currentBundle, now)
                ) {
                    cachedBundle = currentBundle
                    return@withLock AppResult.Success(currentBundle.accessToken)
                }

                val refreshToken = currentBundle.refreshToken?.takeIf { it.isNotBlank() }
                    ?: run {
                        cachedBundle = null
                        return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    }
                val config = configProvider.getConfig()
                    ?: return@withLock AppResult.Failure(DataError.Network.UNAUTHORIZED)

                val refreshRes = pkceProtocol.refreshAccessToken(config, refreshToken)
                when (refreshRes) {
                    is AppResult.Failure -> {
                        cachedBundle = null
                        if (refreshRes.error == DataError.Network.UNAUTHORIZED) {
                            tokenStorage.clearAll()
                        }
                        refreshRes
                    }
                    is AppResult.Success -> {
                        when (tokenStorage.saveTokenBundle(refreshRes.data)) {
                            is AppResult.Failure -> {
                                cachedBundle = null
                                AppResult.Failure(DataError.Network.UNAUTHORIZED)
                            }
                            is AppResult.Success -> {
                                cachedBundle = refreshRes.data
                                AppResult.Success(refreshRes.data.accessToken)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun isUsable(bundle: OAuthTokenBundle, nowMillis: Long): Boolean =
        bundle.accessToken.isNotBlank() && nowMillis < bundle.expiresAtEpochMillis - EXPIRY_SKEW_MS

    companion object {
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
