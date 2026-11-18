package com.notes.purenoteit.desktop.data.security

import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.SyncedVaultSecurityDocument
import com.notes.purenoteit.security.VaultPinSyncProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Manages the per-note 6-digit PIN Vault on Desktop:
 * - Persists ONLY PBKDF2-HMAC-SHA256 verifier metadata and PIN-wrapped key envelope (`SyncedVaultSecurityDocument`)
 *   to disk (never plaintext PIN or plaintext note content key).
 * - Unwraps and holds the note encryption key purely in volatile RAM while unlocked,
 *   zeroizing all key buffers on lock, auto-lock timeout, remote PIN rotation, or shutdown.
 */
internal class DesktopNoteVaultManager(
    private val pinSecurityFile: File,
    private val vaultCrypto: VaultCrypto,
    private val clock: AppClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val deviceId: String = "desktop-local"
) : AutoCloseable {

    private val lock = Mutex()
    private val pinProtocol = VaultPinSyncProtocol(vaultCrypto)

    @Volatile
    private var unlockedVaultKeyChars: CharArray? = null

    private var failedAttempts: Int = 0
    private var lockedUntilMillis: Long = 0L

    fun isPinSetSync(): Boolean = readDocumentLocked()?.hasPin == true

    fun isUnlocked(): Boolean = unlockedVaultKeyChars != null

    suspend fun getPinStatusSnapshot(): NoteVaultSnapshot = withContext(ioDispatcher) {
        lock.withLock {
            NoteVaultSnapshot(
                isPinSet = readDocumentLocked()?.hasPin == true,
                isUnlocked = isUnlocked(),
                cooldownSeconds = remainingCooldownSecondsLocked()
            )
        }
    }

    suspend fun currentCooldownSeconds(): Int = withContext(ioDispatcher) {
        lock.withLock { remainingCooldownSecondsLocked() }
    }

    suspend fun exportPinDocument(): SyncedVaultSecurityDocument? = withContext(ioDispatcher) {
        lock.withLock { readDocumentLocked() }
    }

    /**
     * Applies a newer [SyncedVaultSecurityDocument] pulled from Drive / Peer.
     * Returns `true` if the PIN hash changed (and locks active session so the new PIN takes effect immediately).
     */
    suspend fun applySyncedDocument(remoteDoc: SyncedVaultSecurityDocument): Boolean = withContext(ioDispatcher) {
        if (!remoteDoc.hasPin || remoteDoc.pinHashB64.isNullOrBlank() || remoteDoc.pinSaltB64.isNullOrBlank()) {
            return@withContext false
        }
        lock.withLock {
            val localDoc = readDocumentLocked()
            val shouldApply = localDoc == null ||
                remoteDoc.pinUpdatedAt > localDoc.pinUpdatedAt ||
                (localDoc.pinHashB64 == null && !remoteDoc.pinHashB64.isNullOrBlank()) ||
                (localDoc.wrappedVaultKeyEnvelope.isNullOrBlank() && !remoteDoc.wrappedVaultKeyEnvelope.isNullOrBlank())
            if (!shouldApply) return@withLock false

            val hashChanged = localDoc?.pinHashB64 != null && localDoc.pinHashB64 != remoteDoc.pinHashB64
            if (hashChanged) {
                clearSessionKeyLocked()
                failedAttempts = 0
                lockedUntilMillis = 0L
            }
            writeDocumentLocked(remoteDoc)
            true
        }
    }

    suspend fun verifyPinAndUnlock(pinChars: CharArray): AppResult<NoteVaultSnapshot, DataError.Security> =
        withContext(ioDispatcher) {
            val pinCopy = pinChars.copyOf()
            pinChars.fill('\u0000')
            lock.withLock {
                try {
                    if (remainingCooldownSecondsLocked() > 0) {
                        return@withLock AppResult.Failure(DataError.Security.UNAUTHORIZED)
                    }
                    val doc = readDocumentLocked()
                        ?: return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                    when (val res = pinProtocol.verifyPinAndUnwrapKey(pinCopy, doc)) {
                        is AppResult.Success -> {
                            failedAttempts = 0
                            lockedUntilMillis = 0L
                            setSessionKeyLocked(res.data)
                            res.data.fill('\u0000')
                            AppResult.Success(
                                NoteVaultSnapshot(
                                    isPinSet = true,
                                    isUnlocked = true,
                                    cooldownSeconds = 0
                                )
                            )
                        }
                        is AppResult.Failure -> {
                            recordFailedAttemptLocked()
                            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    pinCopy.fill('\u0000')
                }
            }
        }

    suspend fun setOrChangePin(
        currentPinChars: CharArray?,
        newPinChars: CharArray
    ): AppResult<Pair<SyncedVaultSecurityDocument, NoteVaultSnapshot>, DataError.Security> =
        withContext(ioDispatcher) {
            val currCopy = currentPinChars?.copyOf()
            currentPinChars?.fill('\u0000')
            val newCopy = newPinChars.copyOf()
            newPinChars.fill('\u0000')
            lock.withLock {
                try {
                    if (newCopy.size != 6 || !newCopy.all { it.isDigit() }) {
                        return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                    }
                    val existingDoc = readDocumentLocked()
                    var preservedKeyChars: CharArray? = unlockedVaultKeyChars?.copyOf()
                    if (existingDoc?.hasPin == true) {
                        if (remainingCooldownSecondsLocked() > 0) {
                            preservedKeyChars?.fill('\u0000')
                            return@withLock AppResult.Failure(DataError.Security.UNAUTHORIZED)
                        }
                        val curr = currCopy ?: run {
                            preservedKeyChars?.fill('\u0000')
                            return@withLock AppResult.Failure(DataError.Security.KEY_REQUIRED)
                        }
                        when (val verifyRes = pinProtocol.verifyPinAndUnwrapKey(curr, existingDoc)) {
                            is AppResult.Failure -> {
                                recordFailedAttemptLocked()
                                preservedKeyChars?.fill('\u0000')
                                return@withLock AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                            }
                            is AppResult.Success -> {
                                preservedKeyChars?.fill('\u0000')
                                preservedKeyChars = verifyRes.data
                            }
                        }
                    }

                    val created = pinProtocol.createPinDocument(
                        pinChars = newCopy,
                        existingVaultKeyChars = preservedKeyChars,
                        currentDoc = existingDoc,
                        nowMillis = clock.nowMillis(),
                        deviceId = deviceId
                    )
                    preservedKeyChars?.fill('\u0000')
                    when (created) {
                        is AppResult.Failure -> return@withLock created
                        is AppResult.Success -> {
                            val (doc, keyChars) = created.data
                            try {
                                writeDocumentLocked(doc)
                                setSessionKeyLocked(keyChars)
                                failedAttempts = 0
                                lockedUntilMillis = 0L
                                return@withLock AppResult.Success(
                                    doc to NoteVaultSnapshot(
                                        isPinSet = true,
                                        isUnlocked = true,
                                        cooldownSeconds = 0
                                    )
                                )
                            } finally {
                                keyChars.fill('\u0000')
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } finally {
                    currCopy?.fill('\u0000')
                    newCopy.fill('\u0000')
                }
            }
        }

    fun lockSessionSync() {
        clearSessionKeyLocked()
    }

    suspend fun lockSession(): NoteVaultSnapshot = withContext(ioDispatcher) {
        lock.withLock {
            clearSessionKeyLocked()
            NoteVaultSnapshot(
                isPinSet = readDocumentLocked()?.hasPin == true,
                isUnlocked = false,
                cooldownSeconds = remainingCooldownSecondsLocked()
            )
        }
    }

    fun encryptNoteDescription(plainText: String): AppResult<String, DataError.Security> {
        if (plainText.isEmpty() || vaultCrypto.isEncryptedPayload(plainText)) {
            return AppResult.Success(plainText)
        }
        val keyCopy = unlockedVaultKeyChars?.copyOf()
            ?: return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        return try {
            vaultCrypto.encryptWithPassphrase(plainText, keyCopy)
        } finally {
            keyCopy.fill('\u0000')
        }
    }

    fun decryptNoteDescription(encryptedText: String): AppResult<String, DataError.Security> {
        if (!vaultCrypto.isEncryptedPayload(encryptedText)) {
            return AppResult.Success(encryptedText)
        }
        if (encryptedText.startsWith("ENC_V2:") || encryptedText.startsWith("ENC:")) {
            return AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
        }
        val keyCopy = unlockedVaultKeyChars?.copyOf()
            ?: return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        return try {
            vaultCrypto.decryptEnvelope(
                encryptedText = encryptedText,
                passphrase = keyCopy,
                legacyFallbackSecret = null
            )
        } finally {
            keyCopy.fill('\u0000')
        }
    }

    private fun setSessionKeyLocked(keyChars: CharArray) {
        unlockedVaultKeyChars?.fill('\u0000')
        unlockedVaultKeyChars = keyChars.copyOf()
    }

    private fun clearSessionKeyLocked() {
        unlockedVaultKeyChars?.fill('\u0000')
        unlockedVaultKeyChars = null
    }

    private fun readDocumentLocked(): SyncedVaultSecurityDocument? {
        if (!pinSecurityFile.isFile) return null
        return pinProtocol.decodeDocumentJson(
            try {
                pinSecurityFile.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        )
    }

    private fun writeDocumentLocked(doc: SyncedVaultSecurityDocument) {
        pinSecurityFile.parentFile?.mkdirs()
        val tmp = File(pinSecurityFile.parentFile, "${pinSecurityFile.name}.tmp")
        tmp.writeText(pinProtocol.encodeDocumentJson(doc), Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), pinSecurityFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), pinSecurityFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun recordFailedAttemptLocked() {
        failedAttempts++
        if (failedAttempts >= 3) {
            val shift = (failedAttempts - 3).coerceAtMost(5)
            val delayMs = (1_000L shl shift).coerceAtMost(30_000L)
            lockedUntilMillis = clock.nowMillis() + delayMs
        }
    }

    private fun remainingCooldownSecondsLocked(): Int {
        val rem = lockedUntilMillis - clock.nowMillis()
        return if (rem <= 0L) 0 else ((rem + 999L) / 1000L).toInt()
    }

    override fun close() {
        clearSessionKeyLocked()
    }
}

data class NoteVaultSnapshot(
    val isPinSet: Boolean,
    val isUnlocked: Boolean,
    val cooldownSeconds: Int
)
