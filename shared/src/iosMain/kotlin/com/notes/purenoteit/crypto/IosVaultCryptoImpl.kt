package com.notes.purenoteit.crypto

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.generateSecureRandomBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Native Apple CryptoKit / CommonCrypto bridge implemented on the iOS Swift host.
 * - [aesGcmEncrypt] returns `ciphertext || 16-byte tag` (no IV prepended).
 * - [aesGcmDecrypt] accepts `ciphertext || 16-byte tag` and returns plaintext bytes or null on auth failure.
 */
interface IosNativeCryptoBridge {
    fun derivePbkdf2HmacSha256(
        passphrase: String,
        salt: ByteArray,
        iterations: Int,
        keyByteLength: Int
    ): ByteArray?

    fun aesGcmEncrypt(plainBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray?

    fun aesGcmDecrypt(cipherAndTagBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray?
}

/**
 * iOS implementation of [VaultCrypto] matching `JvmVaultCryptoImpl`'s portable envelope format
 * (`ENC_P3_PWD:`, `ENC_P3:`, `ENC_P2:`, `PVM3`) with PBKDF2-HMAC-SHA256 + AES-256-GCM.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class IosVaultCryptoImpl(
    private val bridge: IosNativeCryptoBridge
) : VaultCrypto {

    override fun isEncryptedPayload(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.startsWith("ENC_V2:") ||
            text.startsWith(PREFIX_P3_PWD) ||
            text.startsWith(PREFIX_P3) ||
            text.startsWith(PREFIX_P2) ||
            text.startsWith("ENC:")
    }

    override fun encryptWithPassphrase(plainText: String, passphrase: CharArray): AppResult<String, DataError.Security> {
        val passCopy = passphrase.copyOf()
        return try {
            if (passCopy.isEmpty()) return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            if (plainText.isEmpty()) return AppResult.Success(plainText)
            val passStr = passCopy.concatToString()
            val encoded = encryptEnvelopeInternal(plainText, passStr, PREFIX_P3_PWD)
                ?: return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            AppResult.Success(encoded)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun encryptLegacyFallback(plainText: String, fallbackSecret: CharArray): String {
        if (plainText.isEmpty()) return plainText
        val secretCopy = fallbackSecret.copyOf()
        return try {
            encryptEnvelopeInternal(plainText, secretCopy.concatToString(), PREFIX_P3) ?: ""
        } finally {
            secretCopy.fill('\u0000')
        }
    }

    override fun decryptEnvelope(
        encryptedText: String,
        passphrase: CharArray?,
        legacyFallbackSecret: CharArray?
    ): AppResult<String, DataError.Security> {
        if (!isEncryptedPayload(encryptedText)) return AppResult.Success(encryptedText)

        val (prefix, iterations) = when {
            encryptedText.startsWith(PREFIX_P3_PWD) -> PREFIX_P3_PWD to PBKDF2_ITERATIONS_V3
            encryptedText.startsWith(PREFIX_P3) -> PREFIX_P3 to PBKDF2_ITERATIONS_V3
            encryptedText.startsWith(PREFIX_P2) -> PREFIX_P2 to PBKDF2_ITERATIONS_V2
            else -> return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        }

        val effectiveSecret = when (prefix) {
            PREFIX_P3_PWD -> passphrase?.takeIf { it.isNotEmpty() }?.copyOf()
                ?: return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            else -> legacyFallbackSecret?.takeIf { it.isNotEmpty() }?.copyOf()
                ?: passphrase?.takeIf { it.isNotEmpty() }?.copyOf()
                ?: return AppResult.Failure(DataError.Security.KEY_REQUIRED)
        }

        return try {
            val combined = Base64.decode(encryptedText.removePrefix(prefix))
            val headerLen = PORTABLE_SALT_LENGTH + GCM_IV_LENGTH
            if (combined.size <= headerLen) {
                return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }
            val salt = combined.copyOfRange(0, PORTABLE_SALT_LENGTH)
            val iv = combined.copyOfRange(PORTABLE_SALT_LENGTH, headerLen)
            val cipherText = combined.copyOfRange(headerLen, combined.size)

            val key = bridge.derivePbkdf2HmacSha256(
                passphrase = effectiveSecret.concatToString(),
                salt = salt,
                iterations = iterations,
                keyByteLength = AES_KEY_BYTES
            ) ?: return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)

            try {
                val plain = bridge.aesGcmDecrypt(cipherText, key, iv)
                    ?: return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                val decoded = plain.decodeToString()
                plain.fill(0)
                AppResult.Success(decoded)
            } finally {
                key.fill(0)
                salt.fill(0)
                iv.fill(0)
            }
        } catch (_: Exception) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } finally {
            effectiveSecret.fill('\u0000')
        }
    }

    override fun encryptMediaBytes(plainBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security> {
        val passCopy = passphrase.copyOf()
        return try {
            if (passCopy.isEmpty()) return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            val encoded = encryptPvm3Internal(plainBytes, passCopy.concatToString())
                ?: return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            AppResult.Success(encoded)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun decryptMediaBytesStrict(encryptedBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security> {
        val passCopy = passphrase.copyOf()
        return try {
            if (passCopy.isEmpty()) return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            if (!hasPvm3Header(encryptedBytes)) return AppResult.Failure(DataError.Security.INTEGRITY_MISMATCH)
            val plain = decryptPvm3Internal(encryptedBytes, passCopy.concatToString())
                ?: return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            AppResult.Success(plain)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun encryptLegacyMediaBytes(plainBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray {
        val copy = secretOrPassphrase.copyOf()
        return try {
            encryptPvm3Internal(plainBytes, copy.concatToString()) ?: ByteArray(0)
        } finally {
            copy.fill('\u0000')
        }
    }

    override fun decryptMediaBytesLegacyCompat(encryptedBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray {
        if (!hasPvm3Header(encryptedBytes)) return encryptedBytes
        val copy = secretOrPassphrase.copyOf()
        return try {
            decryptPvm3Internal(encryptedBytes, copy.concatToString())
                ?: throw IllegalStateException("PVM3 decryption failed")
        } finally {
            copy.fill('\u0000')
        }
    }

    private fun encryptEnvelopeInternal(plainText: String, secret: String, prefix: String): String? {
        val salt = generateSecureRandomBytes(PORTABLE_SALT_LENGTH)
        val iv = generateSecureRandomBytes(GCM_IV_LENGTH)
        val key = bridge.derivePbkdf2HmacSha256(secret, salt, PBKDF2_ITERATIONS_V3, AES_KEY_BYTES) ?: return null
        return try {
            val cipher = bridge.aesGcmEncrypt(plainText.encodeToByteArray(), key, iv) ?: return null
            val combined = ByteArray(salt.size + iv.size + cipher.size)
            salt.copyInto(combined, 0)
            iv.copyInto(combined, salt.size)
            cipher.copyInto(combined, salt.size + iv.size)
            prefix + Base64.encode(combined)
        } finally {
            key.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun encryptPvm3Internal(plainBytes: ByteArray, secret: String): ByteArray? {
        val salt = generateSecureRandomBytes(PORTABLE_SALT_LENGTH)
        val iv = generateSecureRandomBytes(GCM_IV_LENGTH)
        val key = bridge.derivePbkdf2HmacSha256(secret, salt, PBKDF2_ITERATIONS_V3, AES_KEY_BYTES) ?: return null
        return try {
            val cipher = bridge.aesGcmEncrypt(plainBytes, key, iv) ?: return null
            val out = ByteArray(MEDIA_MAGIC_V3.size + salt.size + iv.size + cipher.size)
            var offset = 0
            MEDIA_MAGIC_V3.copyInto(out, offset); offset += MEDIA_MAGIC_V3.size
            salt.copyInto(out, offset); offset += salt.size
            iv.copyInto(out, offset); offset += iv.size
            cipher.copyInto(out, offset)
            out
        } finally {
            key.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun decryptPvm3Internal(encryptedBytes: ByteArray, secret: String): ByteArray? {
        var offset = MEDIA_MAGIC_V3.size
        val salt = encryptedBytes.copyOfRange(offset, offset + PORTABLE_SALT_LENGTH); offset += PORTABLE_SALT_LENGTH
        val iv = encryptedBytes.copyOfRange(offset, offset + GCM_IV_LENGTH); offset += GCM_IV_LENGTH
        val cipher = encryptedBytes.copyOfRange(offset, encryptedBytes.size)
        val key = bridge.derivePbkdf2HmacSha256(secret, salt, PBKDF2_ITERATIONS_V3, AES_KEY_BYTES) ?: return null
        return try {
            bridge.aesGcmDecrypt(cipher, key, iv)
        } finally {
            key.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun hasPvm3Header(bytes: ByteArray): Boolean {
        val minSize = MEDIA_MAGIC_V3.size + PORTABLE_SALT_LENGTH + GCM_IV_LENGTH
        if (bytes.size <= minSize) return false
        for (i in MEDIA_MAGIC_V3.indices) {
            if (bytes[i] != MEDIA_MAGIC_V3[i]) return false
        }
        return true
    }

    companion object {
        private const val PREFIX_P3_PWD = "ENC_P3_PWD:"
        private const val PREFIX_P3 = "ENC_P3:"
        private const val PREFIX_P2 = "ENC_P2:"
        private const val PBKDF2_ITERATIONS_V2 = 60_000
        private const val PBKDF2_ITERATIONS_V3 = 120_000
        private const val AES_KEY_BYTES = 32
        private const val GCM_IV_LENGTH = 12
        private const val PORTABLE_SALT_LENGTH = 16
        private val MEDIA_MAGIC_V3 = byteArrayOf(0x50, 0x56, 0x4D, 0x33) // "PVM3"
    }
}
