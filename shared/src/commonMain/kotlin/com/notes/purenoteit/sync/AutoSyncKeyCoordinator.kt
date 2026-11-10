package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.security.generateSecureRandomBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Automatically resolves or provisions the cross-device E2EE synchronization key
 * in OS Keyring / Android Keystore and Google Drive `appDataFolder` so end users
 * never have to type or remember an extra sync passphrase during 1-click Drive login.
 */
@OptIn(ExperimentalEncodingApi::class)
class AutoSyncKeyCoordinator(
    private val remoteDataSource: RemoteSyncDataSource,
    private val tokenStorage: SecureTokenStorage,
    private val clock: AppClock
) {
    private val keyMutex = Mutex()
    private val urlSafeNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun resolveOrBootstrapPassphrase(): AppResult<CharArray, AppError> = keyMutex.withLock {
        when (val existing = tokenStorage.getSyncPassphrase()) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> {
                val cached = existing.data
                if (cached != null && cached.isNotEmpty()) {
                    return AppResult.Success(cached)
                }
            }
        }

        val randomBytes = generateSecureRandomBytes(KEY_BYTE_LENGTH)
        val generatedKey = urlSafeNoPadding.encode(randomBytes)
        randomBytes.fill(0)

        val candidateJson = json.encodeToString(
            AutoSyncKeyDocument(
                version = 1,
                key = generatedKey,
                createdAt = clock.nowMillis()
            )
        )

        val remoteContent = when (
            val res = remoteDataSource.getOrCreateAppDataConfigFile(AUTO_KEY_FILE_NAME, candidateJson)
        ) {
            is AppResult.Failure -> return res
            is AppResult.Success -> res.data
        }

        val resolvedString = try {
            json.decodeFromString<AutoSyncKeyDocument>(remoteContent).key.trim()
        } catch (_: Exception) {
            return AppResult.Failure(DataError.Network.SERIALIZATION)
        }
        if (resolvedString.isEmpty()) {
            return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        }

        val resolvedChars = resolvedString.toCharArray()
        return when (val saveRes = tokenStorage.saveSyncPassphrase(resolvedChars)) {
            is AppResult.Failure -> {
                resolvedChars.fill('\u0000')
                saveRes
            }
            is AppResult.Success -> AppResult.Success(resolvedChars)
        }
    }

    @Serializable
    private data class AutoSyncKeyDocument(
        @SerialName("version") val version: Int = 1,
        @SerialName("key") val key: String,
        @SerialName("created_at") val createdAt: Long = 0L
    )

    companion object {
        internal const val AUTO_KEY_FILE_NAME = "purenoteit_auto_sync_key_v1.json"
        private const val KEY_BYTE_LENGTH = 32
    }
}
