package com.notes.purenoteit.security

import com.notes.purenoteit.crypto.VaultCrypto
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Synchronized Vault security & workspace preferences document.
 *
 * Zero-Knowledge Invariant:
 * - Never contains plaintext PIN or plaintext note content key.
 * - Stores only a 120,000-iteration salted PBKDF2-HMAC-SHA256 PIN verifier (`pinSaltB64`, `pinHashB64`)
 *   and the shared 256-bit vault note key encrypted via AES-256-GCM under the user's PIN (`wrappedVaultKeyEnvelope`).
 * - When transported over Google Drive `appDataFolder`, the serialized document is additionally wrapped inside
 *   an outer `ENC_P3_PWD` envelope encrypted with the device sync passphrase.
 */
@Serializable
data class SyncedVaultSecurityDocument(
    @SerialName("version") val version: Int = 1,
    @SerialName("has_pin") val hasPin: Boolean = false,
    @SerialName("pin_salt_b64") val pinSaltB64: String? = null,
    @SerialName("pin_hash_b64") val pinHashB64: String? = null,
    @SerialName("kdf_iterations") val kdfIterations: Int = DEFAULT_PIN_KDF_ITERATIONS,
    @SerialName("wrapped_vault_key_envelope") val wrappedVaultKeyEnvelope: String? = null,
    @SerialName("pin_updated_at") val pinUpdatedAt: Long = 0L,
    @SerialName("pin_updated_by_device_id") val pinUpdatedByDeviceId: String = "",
    @SerialName("trash_retention_days") val trashRetentionDays: Int = DEFAULT_TRASH_RETENTION_DAYS,
    @SerialName("settings_updated_at") val settingsUpdatedAt: Long = 0L
) {
    companion object {
        const val DEFAULT_PIN_KDF_ITERATIONS = 120_000
        const val DEFAULT_TRASH_RETENTION_DAYS = 30
    }
}

@Serializable
private data class EncryptedSettingsTransportEnvelope(
    @SerialName("version") val version: Int = 1,
    @SerialName("envelope") val envelope: String
)

/**
 * Cross-platform cryptographic protocol for syncing the 6-digit Note Vault PIN verifier,
 * wrapped note encryption key, and trash retention policy without ever exposing plaintext secrets.
 */
@OptIn(ExperimentalEncodingApi::class)
class VaultPinSyncProtocol(
    private val vaultCrypto: VaultCrypto
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun createPinDocument(
        pinChars: CharArray,
        existingVaultKeyChars: CharArray? = null,
        currentDoc: SyncedVaultSecurityDocument? = null,
        nowMillis: Long,
        deviceId: String
    ): AppResult<Pair<SyncedVaultSecurityDocument, CharArray>, DataError.Security> {
        val pinCopy = pinChars.copyOf()
        val salt = generateSecureRandomBytes(PIN_SALT_BYTES)
        var pinDigest: ByteArray? = null
        val vaultKeyChars: CharArray = if (existingVaultKeyChars != null && existingVaultKeyChars.isNotEmpty()) {
            existingVaultKeyChars.copyOf()
        } else {
            val rawKey = generateSecureRandomBytes(VAULT_KEY_BYTES)
            try {
                bytesToHexChars(rawKey)
            } finally {
                rawKey.fill(0)
            }
        }

        return try {
            if (pinCopy.isEmpty()) {
                vaultKeyChars.fill('\u0000')
                return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            }
            pinDigest = pbkdf2HmacSha256(
                password = pinCopy,
                salt = salt,
                iterations = SyncedVaultSecurityDocument.DEFAULT_PIN_KDF_ITERATIONS,
                keyBitLength = 256
            )
            val keyString = vaultKeyChars.concatToString()
            val wrappedRes = vaultCrypto.encryptWithPassphrase(keyString, pinCopy)
            val wrappedEnvelope = when (wrappedRes) {
                is AppResult.Failure -> {
                    vaultKeyChars.fill('\u0000')
                    return wrappedRes
                }
                is AppResult.Success -> wrappedRes.data
            }

            val base = currentDoc ?: SyncedVaultSecurityDocument()
            val updatedDoc = base.copy(
                version = 1,
                hasPin = true,
                pinSaltB64 = Base64.encode(salt),
                pinHashB64 = Base64.encode(pinDigest),
                kdfIterations = SyncedVaultSecurityDocument.DEFAULT_PIN_KDF_ITERATIONS,
                wrappedVaultKeyEnvelope = wrappedEnvelope,
                pinUpdatedAt = nowMillis.coerceAtLeast(base.pinUpdatedAt + 1L),
                pinUpdatedByDeviceId = deviceId
            )
            AppResult.Success(updatedDoc to vaultKeyChars)
        } finally {
            pinCopy.fill('\u0000')
            salt.fill(0)
            pinDigest?.fill(0)
        }
    }

    fun verifyPinAndUnwrapKey(
        pinChars: CharArray,
        doc: SyncedVaultSecurityDocument
    ): AppResult<CharArray, DataError.Security> {
        val pinCopy = pinChars.copyOf()
        val saltB64 = doc.pinSaltB64
        val hashB64 = doc.pinHashB64
        val wrapped = doc.wrappedVaultKeyEnvelope
        if (!doc.hasPin || saltB64.isNullOrBlank() || hashB64.isNullOrBlank() || wrapped.isNullOrBlank()) {
            pinCopy.fill('\u0000')
            return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        }

        val salt = try {
            Base64.decode(saltB64)
        } catch (_: Exception) {
            pinCopy.fill('\u0000')
            return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        }
        val expectedHash = try {
            Base64.decode(hashB64)
        } catch (_: Exception) {
            pinCopy.fill('\u0000')
            salt.fill(0)
            return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        }

        var candidateHash: ByteArray? = null
        return try {
            val iterations = doc.kdfIterations.coerceAtLeast(60_000)
            candidateHash = pbkdf2HmacSha256(
                password = pinCopy,
                salt = salt,
                iterations = iterations,
                keyBitLength = expectedHash.size * 8
            )
            if (!constantTimeEqualsBytes(expectedHash, candidateHash)) {
                return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }
            when (val unwrapped = vaultCrypto.decryptEnvelope(wrapped, passphrase = pinCopy)) {
                is AppResult.Failure -> unwrapped
                is AppResult.Success -> {
                    val keyChars = unwrapped.data.toCharArray()
                    if (keyChars.isEmpty()) {
                        AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                    } else {
                        AppResult.Success(keyChars)
                    }
                }
            }
        } finally {
            pinCopy.fill('\u0000')
            salt.fill(0)
            expectedHash.fill(0)
            candidateHash?.fill(0)
        }
    }

    fun updateRetentionDays(
        currentDoc: SyncedVaultSecurityDocument?,
        retentionDays: Int,
        nowMillis: Long
    ): SyncedVaultSecurityDocument {
        val base = currentDoc ?: SyncedVaultSecurityDocument()
        val normalized = retentionDays.coerceIn(1, 365)
        return base.copy(
            trashRetentionDays = normalized,
            settingsUpdatedAt = nowMillis.coerceAtLeast(base.settingsUpdatedAt + 1L)
        )
    }

    fun mergeDocuments(
        local: SyncedVaultSecurityDocument?,
        remote: SyncedVaultSecurityDocument?
    ): SyncedVaultSecurityDocument? {
        if (local == null) return remote
        if (remote == null) return local

        val pinSource = if (remote.pinUpdatedAt > local.pinUpdatedAt && remote.hasPin) remote else local
        val settingsSource = if (remote.settingsUpdatedAt > local.settingsUpdatedAt) remote else local

        return SyncedVaultSecurityDocument(
            version = maxOf(local.version, remote.version),
            hasPin = pinSource.hasPin,
            pinSaltB64 = pinSource.pinSaltB64,
            pinHashB64 = pinSource.pinHashB64,
            kdfIterations = pinSource.kdfIterations,
            wrappedVaultKeyEnvelope = pinSource.wrappedVaultKeyEnvelope,
            pinUpdatedAt = pinSource.pinUpdatedAt,
            pinUpdatedByDeviceId = pinSource.pinUpdatedByDeviceId,
            trashRetentionDays = settingsSource.trashRetentionDays,
            settingsUpdatedAt = settingsSource.settingsUpdatedAt
        )
    }

    fun encryptDocumentForDrive(
        doc: SyncedVaultSecurityDocument,
        syncPassphrase: CharArray
    ): AppResult<String, DataError.Security> {
        val plainJson = json.encodeToString(doc)
        return when (val enc = vaultCrypto.encryptWithPassphrase(plainJson, syncPassphrase)) {
            is AppResult.Failure -> enc
            is AppResult.Success -> {
                val wrapper = EncryptedSettingsTransportEnvelope(version = 1, envelope = enc.data)
                AppResult.Success(json.encodeToString(wrapper))
            }
        }
    }

    fun decryptDocumentFromDrive(
        rawPayload: String,
        syncPassphrase: CharArray
    ): AppResult<SyncedVaultSecurityDocument, DataError.Security> {
        val wrapper = try {
            json.decodeFromString<EncryptedSettingsTransportEnvelope>(rawPayload)
        } catch (_: Exception) {
            return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        }
        val decRes = vaultCrypto.decryptEnvelope(
            encryptedText = wrapper.envelope,
            passphrase = syncPassphrase,
            legacyFallbackSecret = null
        )
        return when (decRes) {
            is AppResult.Failure -> decRes
            is AppResult.Success -> try {
                AppResult.Success(json.decodeFromString<SyncedVaultSecurityDocument>(decRes.data))
            } catch (_: Exception) {
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }
        }
    }

    fun encodeDocumentJson(doc: SyncedVaultSecurityDocument): String = json.encodeToString(doc)

    fun decodeDocumentJson(raw: String?): SyncedVaultSecurityDocument? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString<SyncedVaultSecurityDocument>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun bytesToHexChars(bytes: ByteArray): CharArray {
        val hexDigits = "0123456789abcdef"
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = hexDigits[v ushr 4]
            out[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return out
    }

    companion object {
        private const val PIN_SALT_BYTES = 16
        private const val VAULT_KEY_BYTES = 32
    }
}
