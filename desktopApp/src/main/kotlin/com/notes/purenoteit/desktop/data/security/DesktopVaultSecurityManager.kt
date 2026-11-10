package com.notes.purenoteit.desktop.data.security

import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.DesktopOsKeyringVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
internal data class DesktopVaultSecurityMetadata(
    @SerialName("version") val version: Int = 1,
    @SerialName("master_lock_enabled") val masterLockEnabled: Boolean = false,
    @SerialName("salt_b64") val saltB64: String? = null,
    @SerialName("verifier_hmac_b64") val verifierHmacB64: String? = null,
    @SerialName("kdf_iterations") val kdfIterations: Int = 120_000,
    @SerialName("auto_lock_minutes") val autoLockMinutes: Int = 5
)

internal data class VaultSecuritySnapshot(
    val isLocked: Boolean,
    val isMasterLockEnabled: Boolean,
    val autoLockMinutes: Int,
    val cooldownSeconds: Int
)

/**
 * Coordinates PC local vault encryption (`SQLite3 Multiple Ciphers SQLCipher v4` +
 * `AES-256-GCM` disk media), Master Password PBKDF2-HMAC-SHA256 verification, OS Keychain
 * auto-key cleanup on Master Password activation, brute-force cooldowns, and RAM auto-lock wiping.
 */
internal class DesktopVaultSecurityManager(
    private val securityMetadataFile: File,
    private val keyringVault: DesktopOsKeyringVault,
    private val dbManagerProvider: () -> DesktopSqliteDatabaseManager,
    private val encryptedMediaStorageProvider: () -> DesktopEncryptedMediaStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val lock = Mutex()
    private val secureRandom = SecureRandom()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Volatile
    private var activeDbKey: ByteArray? = null

    @Volatile
    private var activeMediaKey: ByteArray? = null

    private var failedAttempts: Int = 0
    private var lockedUntilMillis: Long = 0L

    fun currentDbKeyCopy(): ByteArray? = activeDbKey?.copyOf()

    fun currentMediaKeyCopy(): ByteArray? = activeMediaKey?.copyOf()

    fun isVaultUnlocked(): Boolean = activeDbKey != null && activeMediaKey != null

    suspend fun initializeOnStartup(): AppResult<VaultSecuritySnapshot, DataError.Security> =
        withContext(ioDispatcher) {
            lock.withLock {
                try {
                    val meta = readMetadataLocked()
                    if (meta.masterLockEnabled) {
                        clearActiveKeysLocked()
                        return@withLock AppResult.Success(
                            VaultSecuritySnapshot(
                                isLocked = true,
                                isMasterLockEnabled = true,
                                autoLockMinutes = meta.autoLockMinutes,
                                cooldownSeconds = remainingCooldownSecondsLocked()
                            )
                        )
                    }

                    // Mode A: Auto OS Keychain mode (DB & media are still 100% encrypted on disk)
                    val rootBytes = loadOrCreateAutoRootKeyLocked()
                        ?: return@withLock AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                    try {
                        setSessionKeysFrom64ByteRootLocked(rootBytes)
                    } finally {
                        rootBytes.fill(0)
                    }
                    dbManagerProvider().ensureInitializedWithCurrentKey()
                    if (!securityMetadataFile.exists()) {
                        writeMetadataLocked(meta)
                    }

                    AppResult.Success(
                        VaultSecuritySnapshot(
                            isLocked = false,
                            isMasterLockEnabled = false,
                            autoLockMinutes = meta.autoLockMinutes,
                            cooldownSeconds = 0
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    clearActiveKeysLocked()
                    AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }
            }
        }

    suspend fun unlockWithMasterPassword(passphrase: CharArray): AppResult<VaultSecuritySnapshot, DataError.Security> =
        withContext(ioDispatcher) {
            val passCopy = passphrase.copyOf()
            passphrase.fill('\u0000')
            lock.withLock {
                try {
                    val cooldown = remainingCooldownSecondsLocked()
                    if (cooldown > 0) {
                        return@withLock AppResult.Failure(DataError.Security.UNAUTHORIZED)
                    }
                    if (passCopy.isEmpty()) {
                        return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                    }

                    val meta = readMetadataLocked()
                    val salt = meta.saltB64?.let { Base64.getDecoder().decode(it) }
                    val expectedVerifier = meta.verifierHmacB64?.let { Base64.getDecoder().decode(it) }
                    if (!meta.masterLockEnabled || salt == null || expectedVerifier == null) {
                        return@withLock AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                    }

                    val derived64 = derive64Bytes(passCopy, salt, meta.kdfIterations)
                    try {
                        val candidateDb = derived64.copyOfRange(0, 32)
                        val candidateMedia = derived64.copyOfRange(32, 64)
                        val computedVerifier = computeVerifierHmac(candidateMedia)
                        if (!MessageDigest.isEqual(computedVerifier, expectedVerifier)) {
                            candidateDb.fill(0)
                            candidateMedia.fill(0)
                            recordFailedAttemptLocked()
                            return@withLock AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                        }

                        setSessionKeysLocked(candidateDb, candidateMedia)
                        candidateDb.fill(0)
                        candidateMedia.fill(0)

                        try {
                            dbManagerProvider().ensureInitializedWithCurrentKey()
                        } catch (_: Exception) {
                            clearActiveKeysLocked()
                            recordFailedAttemptLocked()
                            return@withLock AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                        }

                        failedAttempts = 0
                        lockedUntilMillis = 0L
                        AppResult.Success(
                            VaultSecuritySnapshot(
                                isLocked = false,
                                isMasterLockEnabled = true,
                                autoLockMinutes = meta.autoLockMinutes,
                                cooldownSeconds = 0
                            )
                        )
                    } finally {
                        derived64.fill(0)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    clearActiveKeysLocked()
                    AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                } finally {
                    passCopy.fill('\u0000')
                }
            }
        }

    suspend fun enableMasterPassword(
        newPassphrase: CharArray,
        autoLockMinutes: Int = 5
    ): AppResult<VaultSecuritySnapshot, DataError.Security> = withContext(ioDispatcher) {
        val passCopy = newPassphrase.copyOf()
        newPassphrase.fill('\u0000')
        lock.withLock {
            if (passCopy.size < MIN_MASTER_PASS_LEN) {
                passCopy.fill('\u0000')
                return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
            }
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val derived64 = derive64Bytes(passCopy, salt, DEFAULT_KDF_ITERATIONS)
            val newDbKey = derived64.copyOfRange(0, 32)
            val newMediaKey = derived64.copyOfRange(32, 64)
            derived64.fill(0)
            passCopy.fill('\u0000')

            val oldMediaKey = activeMediaKey?.copyOf()
            try {
                // 1. Re-key SQLCipher DB and local encrypted media first
                dbManagerProvider().rekeyDatabase(newDbKey)
                encryptedMediaStorageProvider().reencryptAllStoredMedia(oldMediaKey, newMediaKey)

                // 2. Write non-secret KDF metadata + HMAC verifier
                val verifier = computeVerifierHmac(newMediaKey)
                val nextMeta = DesktopVaultSecurityMetadata(
                    version = 1,
                    masterLockEnabled = true,
                    saltB64 = Base64.getEncoder().encodeToString(salt),
                    verifierHmacB64 = Base64.getEncoder().encodeToString(verifier),
                    kdfIterations = DEFAULT_KDF_ITERATIONS,
                    autoLockMinutes = autoLockMinutes.coerceIn(0, 120)
                )
                writeMetadataLocked(nextMeta)

                // 3. Remove wrapped auto-key from OS Keychain / DPAPI so zero key exists off-RAM
                keyringVault.deleteSecret(ACCOUNT_LOCAL_AUTO_ROOT)

                setSessionKeysLocked(newDbKey, newMediaKey)
                AppResult.Success(
                    VaultSecuritySnapshot(
                        isLocked = false,
                        isMasterLockEnabled = true,
                        autoLockMinutes = nextMeta.autoLockMinutes,
                        cooldownSeconds = 0
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            } finally {
                newDbKey.fill(0)
                newMediaKey.fill(0)
                oldMediaKey?.fill(0)
            }
        }
    }

    suspend fun disableMasterPassword(
        currentPassphrase: CharArray
    ): AppResult<VaultSecuritySnapshot, DataError.Security> = withContext(ioDispatcher) {
        val passCopy = currentPassphrase.copyOf()
        currentPassphrase.fill('\u0000')
        lock.withLock {
            try {
                val meta = readMetadataLocked()
                if (!meta.masterLockEnabled) {
                    return@withLock AppResult.Success(
                        VaultSecuritySnapshot(false, false, meta.autoLockMinutes, 0)
                    )
                }
                val salt = meta.saltB64?.let { Base64.getDecoder().decode(it) }
                val expectedVerifier = meta.verifierHmacB64?.let { Base64.getDecoder().decode(it) }
                if (salt == null || expectedVerifier == null) {
                    return@withLock AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }

                val check64 = derive64Bytes(passCopy, salt, meta.kdfIterations)
                val checkMedia = check64.copyOfRange(32, 64)
                val checkHmac = computeVerifierHmac(checkMedia)
                val verified = MessageDigest.isEqual(checkHmac, expectedVerifier)
                check64.fill(0)
                checkMedia.fill(0)
                if (!verified) {
                    return@withLock AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }

                val freshRoot64 = ByteArray(64).also { secureRandom.nextBytes(it) }
                val autoDbKey = freshRoot64.copyOfRange(0, 32)
                val autoMediaKey = freshRoot64.copyOfRange(32, 64)
                val oldMediaKey = activeMediaKey?.copyOf()
                try {
                    val encodedChars = bytesToHex(freshRoot64).toCharArray()
                    val savedRes = keyringVault.saveSecret(ACCOUNT_LOCAL_AUTO_ROOT, encodedChars)
                    encodedChars.fill('\u0000')
                    if (savedRes is AppResult.Failure) return@withLock savedRes

                    dbManagerProvider().rekeyDatabase(autoDbKey)
                    encryptedMediaStorageProvider().reencryptAllStoredMedia(oldMediaKey, autoMediaKey)

                    val nextMeta = meta.copy(
                        masterLockEnabled = false,
                        saltB64 = null,
                        verifierHmacB64 = null
                    )
                    writeMetadataLocked(nextMeta)
                    setSessionKeysLocked(autoDbKey, autoMediaKey)
                    AppResult.Success(
                        VaultSecuritySnapshot(false, false, nextMeta.autoLockMinutes, 0)
                    )
                } finally {
                    freshRoot64.fill(0)
                    autoDbKey.fill(0)
                    autoMediaKey.fill(0)
                    oldMediaKey?.fill(0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            } finally {
                passCopy.fill('\u0000')
            }
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int): VaultSecuritySnapshot = withContext(ioDispatcher) {
        lock.withLock {
            val clean = minutes.coerceIn(0, 120)
            val updated = readMetadataLocked().copy(autoLockMinutes = clean)
            writeMetadataLocked(updated)
            VaultSecuritySnapshot(
                isLocked = !isVaultUnlocked(),
                isMasterLockEnabled = updated.masterLockEnabled,
                autoLockMinutes = updated.autoLockMinutes,
                cooldownSeconds = remainingCooldownSecondsLocked()
            )
        }
    }

    suspend fun lockVault(): VaultSecuritySnapshot = withContext(ioDispatcher) {
        lock.withLock {
            dbManagerProvider().lockAndClose()
            clearActiveKeysLocked()
            val meta = readMetadataLocked()
            VaultSecuritySnapshot(
                isLocked = true,
                isMasterLockEnabled = meta.masterLockEnabled,
                autoLockMinutes = meta.autoLockMinutes,
                cooldownSeconds = remainingCooldownSecondsLocked()
            )
        }
    }

    suspend fun currentCooldownSeconds(): Int = withContext(ioDispatcher) {
        lock.withLock { remainingCooldownSecondsLocked() }
    }

    private suspend fun loadOrCreateAutoRootKeyLocked(): ByteArray? {
        val readRes = keyringVault.readSecret(ACCOUNT_LOCAL_AUTO_ROOT)
        if (readRes is AppResult.Failure) return null
        val existingChars = (readRes as AppResult.Success).data
        if (existingChars != null) {
            val parsed = hexToBytes(existingChars)
            existingChars.fill('\u0000')
            if (parsed != null && parsed.size == 64) {
                return parsed
            }
            parsed?.fill(0)
        }

        val freshRoot = ByteArray(64).also { secureRandom.nextBytes(it) }
        val chars = bytesToHex(freshRoot).toCharArray()
        val saveRes = keyringVault.saveSecret(ACCOUNT_LOCAL_AUTO_ROOT, chars)
        chars.fill('\u0000')
        if (saveRes is AppResult.Failure) {
            freshRoot.fill(0)
            return null
        }
        return freshRoot
    }

    private fun setSessionKeysFrom64ByteRootLocked(root64: ByteArray) {
        val dbK = root64.copyOfRange(0, 32)
        val medK = root64.copyOfRange(32, 64)
        setSessionKeysLocked(dbK, medK)
        dbK.fill(0)
        medK.fill(0)
    }

    private fun setSessionKeysLocked(dbKey: ByteArray, mediaKey: ByteArray) {
        activeDbKey?.fill(0)
        activeMediaKey?.fill(0)
        activeDbKey = dbKey.copyOf(32)
        activeMediaKey = mediaKey.copyOf(32)
    }

    private fun clearActiveKeysLocked() {
        activeDbKey?.fill(0)
        activeMediaKey?.fill(0)
        activeDbKey = null
        activeMediaKey = null
    }

    private fun readMetadataLocked(): DesktopVaultSecurityMetadata {
        if (!securityMetadataFile.isFile) return DesktopVaultSecurityMetadata()
        return try {
            json.decodeFromString<DesktopVaultSecurityMetadata>(securityMetadataFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            DesktopVaultSecurityMetadata()
        }
    }

    private fun writeMetadataLocked(meta: DesktopVaultSecurityMetadata) {
        securityMetadataFile.parentFile?.mkdirs()
        securityMetadataFile.writeText(json.encodeToString(meta), Charsets.UTF_8)
    }

    private fun derive64Bytes(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, 512)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return raw
    }

    private fun computeVerifierHmac(subKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(subKey, "HmacSHA256"))
        return mac.doFinal(VERIFIER_CONTEXT)
    }

    private fun recordFailedAttemptLocked() {
        failedAttempts++
        if (failedAttempts >= 3) {
            val shift = (failedAttempts - 3).coerceAtMost(5)
            val delayMs = (1_000L shl shift).coerceAtMost(30_000L)
            lockedUntilMillis = System.currentTimeMillis() + delayMs
        }
    }

    private fun remainingCooldownSecondsLocked(): Int {
        val rem = lockedUntilMillis - System.currentTimeMillis()
        return if (rem <= 0L) 0 else ((rem + 999L) / 1000L).toInt()
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(chars: CharArray): ByteArray? {
        if (chars.size % 2 != 0) return null
        val out = ByteArray(chars.size / 2)
        for (i in out.indices) {
            val hi = Character.digit(chars[i * 2], 16)
            val lo = Character.digit(chars[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) {
                out.fill(0)
                return null
            }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    companion object {
        const val ACCOUNT_LOCAL_AUTO_ROOT = "local_vault_auto_root_v1"
        private const val SALT_BYTES = 16
        private const val DEFAULT_KDF_ITERATIONS = 120_000
        private const val MIN_MASTER_PASS_LEN = 6
        private val VERIFIER_CONTEXT = "PURE_DESKTOP_VAULT_VERIFIER_V1".toByteArray(Charsets.UTF_8)
    }
}
