package com.notes.purenoteit.data.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed Android [SecureTokenStorage] powered by:
 * - Android Keystore MasterKey (`android-keystore://purenoteit_oauth_master_v1`)
 * - Google Tink AEAD (`AES256_GCM`) with bound Associated Authenticated Data (AAD)
 * - Jetpack DataStore (`noBackupFilesDir/datastore/purenote_secure_auth.preferences_pb`)
 *
 * Security invariants:
 * - Zero plaintext fallback on Keystore invalidation or decryption failure; corrupted auth data is wiped.
 * - All token/key files are excluded from cloud backups in `backup_rules.xml` & `data_extraction_rules.xml`.
 */
@Singleton
internal class AndroidSecureTokenStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SecureTokenStorage {

    private val lock = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = {
                File(context.noBackupFilesDir, "datastore/$AUTH_DATASTORE_FILE").apply {
                    parentFile?.mkdirs()
                }
            }
        )
    }

    @Volatile
    private var cachedAead: Aead? = null

    override suspend fun saveTokenBundle(bundle: OAuthTokenBundle): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            lock.withLock {
                encryptAndPut(KEY_OAUTH_BUNDLE, json.encodeToString(bundle).toByteArray(Charsets.UTF_8))
            }
        }

    override suspend fun getTokenBundle(): AppResult<OAuthTokenBundle?, DataError.Security> =
        withContext(ioDispatcher) {
            lock.withLock {
                when (val dec = readAndDecrypt(KEY_OAUTH_BUNDLE)) {
                    is AppResult.Failure -> dec
                    is AppResult.Success -> {
                        val plainBytes = dec.data ?: return@withLock AppResult.Success(null)
                        try {
                            val decoded = String(plainBytes, Charsets.UTF_8)
                            AppResult.Success(json.decodeFromString<OAuthTokenBundle>(decoded))
                        } catch (_: SerializationException) {
                            wipeAllLocked()
                            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                        } finally {
                            plainBytes.fill(0)
                        }
                    }
                }
            }
        }

    override suspend fun saveSyncPassphrase(passphrase: CharArray): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            val copy = passphrase.copyOf()
            lock.withLock {
                if (copy.isEmpty()) {
                    copy.fill('\u0000')
                    return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                }
                val charBuf = java.nio.CharBuffer.wrap(copy)
                val byteBuf = Charsets.UTF_8.encode(charBuf)
                val plainBytes = ByteArray(byteBuf.remaining())
                byteBuf.get(plainBytes)
                byteBuf.array().fill(0)
                try {
                    encryptAndPut(KEY_SYNC_PASSPHRASE, plainBytes)
                } finally {
                    copy.fill('\u0000')
                    plainBytes.fill(0)
                }
            }
        }

    override suspend fun getSyncPassphrase(): AppResult<CharArray?, DataError.Security> =
        withContext(ioDispatcher) {
            lock.withLock {
                when (val dec = readAndDecrypt(KEY_SYNC_PASSPHRASE)) {
                    is AppResult.Failure -> dec
                    is AppResult.Success -> {
                        val plainBytes = dec.data ?: return@withLock AppResult.Success(null)
                        try {
                            val charBuf = Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(plainBytes))
                            val out = CharArray(charBuf.remaining())
                            charBuf.get(out)
                            if (charBuf.hasArray()) charBuf.array().fill('\u0000')
                            AppResult.Success(if (out.isEmpty()) null else out)
                        } finally {
                            plainBytes.fill(0)
                        }
                    }
                }
            }
        }

    override suspend fun clearAll(): AppResult<Unit, DataError.Security> =
        withContext(ioDispatcher) {
            lock.withLock {
                try {
                    dataStore.edit { it.clear() }
                    AppResult.Success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
            }
        }

    private suspend fun encryptAndPut(
        prefKey: Preferences.Key<String>,
        plainBytes: ByteArray
    ): AppResult<Unit, DataError.Security> {
        return try {
            val aead = acquireAeadOrResetLocked()
                ?: return AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
            val ciphertext = aead.encrypt(plainBytes, ASSOCIATED_DATA)
            val b64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            dataStore.edit { prefs -> prefs[prefKey] = b64 }
            AppResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: KeyPermanentlyInvalidatedException) {
            wipeAllAndResetKeysetLocked()
            AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
        } catch (_: GeneralSecurityException) {
            wipeAllAndResetKeysetLocked()
            AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
        } catch (_: Exception) {
            AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
        } finally {
            plainBytes.fill(0)
        }
    }

    private suspend fun readAndDecrypt(
        prefKey: Preferences.Key<String>
    ): AppResult<ByteArray?, DataError.Security> {
        return try {
            val encoded = dataStore.data.first()[prefKey] ?: return AppResult.Success(null)
            val ciphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val aead = acquireAeadOrResetLocked()
                ?: return AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
            val plain = aead.decrypt(ciphertext, ASSOCIATED_DATA)
            AppResult.Success(plain)
        } catch (e: CancellationException) {
            throw e
        } catch (_: KeyPermanentlyInvalidatedException) {
            wipeAllAndResetKeysetLocked()
            AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
        } catch (_: GeneralSecurityException) {
            wipeAllLocked()
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } catch (_: Exception) {
            wipeAllLocked()
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        }
    }

    private suspend fun acquireAeadOrResetLocked(): Aead? {
        return try {
            getOrCreateAead()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Covers Keystore invalidation and corrupted/unreadable Tink protobuf keysets with zero plaintext fallback.
            wipeAllAndResetKeysetLocked()
            null
        }
    }

    private fun getOrCreateAead(): Aead {
        cachedAead?.let { return it }
        synchronized(this) {
            cachedAead?.let { return it }
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, TINK_KEYSET_NAME, TINK_KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            return keysetHandle.getPrimitive(Aead::class.java).also { cachedAead = it }
        }
    }

    private suspend fun wipeAllLocked() {
        try {
            dataStore.edit { it.clear() }
        } catch (_: Exception) {
        }
    }

    private suspend fun wipeAllAndResetKeysetLocked() {
        cachedAead = null
        wipeAllLocked()
        try {
            context.getSharedPreferences(TINK_KEYSET_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {
        }
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (ks.containsAlias(MASTER_KEY_ALIAS)) {
                ks.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val AUTH_DATASTORE_FILE = "purenote_secure_auth.preferences_pb"
        private const val TINK_KEYSET_NAME = "purenoteit_tink_token_keyset"
        private const val TINK_KEYSET_PREFS = "purenoteit_tink_token_keyset"
        private const val MASTER_KEY_ALIAS = "purenoteit_oauth_master_v1"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        private val ASSOCIATED_DATA = "purenoteit_secure_auth_v1".toByteArray(Charsets.UTF_8)

        private val KEY_OAUTH_BUNDLE = stringPreferencesKey("enc_oauth_bundle")
        private val KEY_SYNC_PASSPHRASE = stringPreferencesKey("enc_sync_passphrase")
    }
}
