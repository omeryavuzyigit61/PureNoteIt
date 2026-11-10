package com.notes.purenoteit.security

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

internal interface OsKeyringClient : AutoCloseable {
    fun getPassword(service: String, account: String): String?
    fun setPassword(service: String, account: String, password: String)
    fun deletePassword(service: String, account: String)
}

internal class JavaKeyringClient : OsKeyringClient {
    private val keyring: Keyring = Keyring.create()

    override fun getPassword(service: String, account: String): String? {
        return try {
            keyring.getPassword(service, account)
        } catch (e: PasswordAccessException) {
            if (isNotFoundError(e)) null else throw e
        }
    }

    override fun setPassword(service: String, account: String, password: String) {
        keyring.setPassword(service, account, password)
    }

    override fun deletePassword(service: String, account: String) {
        try {
            keyring.deletePassword(service, account)
        } catch (e: PasswordAccessException) {
            if (!isNotFoundError(e)) throw e
        }
    }

    override fun close() {
        try {
            keyring.close()
        } catch (_: Exception) {
        }
    }

    private fun isNotFoundError(e: PasswordAccessException): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("not found", ignoreCase = true) ||
            msg.contains("Element not found", ignoreCase = true) ||
            msg.contains("1168") // Windows ERROR_NOT_FOUND
    }
}

/**
 * Desktop OS Keychain / Windows Credential Manager (DPAPI) implementation of [SecureTokenStorage].
 * Stores serialized OAuth tokens and the sync passphrase strictly inside the OS credential vault
 * (never in plaintext files). Clears corrupt data cleanly if OS keychain retrieval fails.
 */
internal class DesktopSecureTokenStorageImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val keyringFactory: () -> OsKeyringClient = { JavaKeyringClient() }
) : SecureTokenStorage {

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun saveTokenBundle(bundle: OAuthTokenBundle): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val encoded = json.encodeToString(bundle)
                    keyringFactory().use { kr ->
                        kr.setPassword(SERVICE_NAME, ACCOUNT_OAUTH_BUNDLE, encoded)
                    }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: BackendNotSupportedException) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    override suspend fun getTokenBundle(): AppResult<OAuthTokenBundle?, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val raw = keyringFactory().use { kr ->
                        kr.getPassword(SERVICE_NAME, ACCOUNT_OAUTH_BUNDLE)
                    } ?: return@withLock AppResult.Success(null)

                    if (raw.isBlank()) return@withLock AppResult.Success(null)
                    val bundle = json.decodeFromString<OAuthTokenBundle>(raw)
                    AppResult.Success(bundle)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: SerializationException) {
                    deleteKeySilently(ACCOUNT_OAUTH_BUNDLE)
                    AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                } catch (_: BackendNotSupportedException) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    override suspend fun saveSyncPassphrase(passphrase: CharArray): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            val copy = passphrase.copyOf()
            mutex.withLock {
                try {
                    if (copy.isEmpty()) {
                        return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                    }
                    val charBuffer = java.nio.CharBuffer.wrap(copy)
                    val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
                    val encodedString = String(byteBuffer.array(), 0, byteBuffer.limit(), StandardCharsets.UTF_8)
                    byteBuffer.array().fill(0)
                    keyringFactory().use { kr ->
                        kr.setPassword(SERVICE_NAME, ACCOUNT_SYNC_PASSPHRASE, encodedString)
                    }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                } finally {
                    copy.fill('\u0000')
                }
            }
        }

    override suspend fun getSyncPassphrase(): AppResult<CharArray?, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val raw = keyringFactory().use { kr ->
                        kr.getPassword(SERVICE_NAME, ACCOUNT_SYNC_PASSPHRASE)
                    } ?: return@withLock AppResult.Success(null)
                    AppResult.Success(if (raw.isEmpty()) null else raw.toCharArray())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    override suspend fun clearAll(): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    keyringFactory().use { kr ->
                        kr.deletePassword(SERVICE_NAME, ACCOUNT_OAUTH_BUNDLE)
                        kr.deletePassword(SERVICE_NAME, ACCOUNT_SYNC_PASSPHRASE)
                    }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    private fun deleteKeySilently(account: String) {
        try {
            keyringFactory().use { kr -> kr.deletePassword(SERVICE_NAME, account) }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SERVICE_NAME = "com.purenoteit.desktop"
        const val ACCOUNT_OAUTH_BUNDLE = "oauth_drive_bundle_v1"
        const val ACCOUNT_SYNC_PASSPHRASE = "e2ee_sync_passphrase_v1"
    }
}

/**
 * Clean public bridge exposing desktop OS Keychain (`java-keyring`) slot operations
 * for wrapping local SQLite/media auto-keys while keeping credential internals `internal`.
 */
interface DesktopOsKeyringVault {
    suspend fun saveSecret(account: String, secretChars: CharArray): AppResult<Unit, DataError.Security>
    suspend fun readSecret(account: String): AppResult<CharArray?, DataError.Security>
    suspend fun deleteSecret(account: String): AppResult<Unit, DataError.Security>
}

internal class DesktopOsKeyringVaultImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val keyringFactory: () -> OsKeyringClient = { JavaKeyringClient() }
) : DesktopOsKeyringVault {

    private val mutex = Mutex()

    override suspend fun saveSecret(account: String, secretChars: CharArray): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            val copy = secretChars.copyOf()
            mutex.withLock {
                try {
                    if (copy.isEmpty() || account.isBlank()) {
                        return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                    }
                    val charBuf = java.nio.CharBuffer.wrap(copy)
                    val byteBuf = StandardCharsets.UTF_8.encode(charBuf)
                    val str = String(byteBuf.array(), 0, byteBuf.limit(), StandardCharsets.UTF_8)
                    byteBuf.array().fill(0)
                    keyringFactory().use { kr ->
                        kr.setPassword(DesktopSecureTokenStorageImpl.SERVICE_NAME, account, str)
                    }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                } finally {
                    copy.fill('\u0000')
                }
            }
        }

    override suspend fun readSecret(account: String): AppResult<CharArray?, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val raw = keyringFactory().use { kr ->
                        kr.getPassword(DesktopSecureTokenStorageImpl.SERVICE_NAME, account)
                    } ?: return@withLock AppResult.Success(null)
                    AppResult.Success(if (raw.isEmpty()) null else raw.toCharArray())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    override suspend fun deleteSecret(account: String): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    keyringFactory().use { kr ->
                        kr.deletePassword(DesktopSecureTokenStorageImpl.SERVICE_NAME, account)
                    }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }
}
