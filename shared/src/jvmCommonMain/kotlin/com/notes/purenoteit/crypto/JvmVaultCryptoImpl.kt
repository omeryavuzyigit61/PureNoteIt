package com.notes.purenoteit.crypto

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Shared JVM (`:app` + `:desktopApp`) cryptographic envelope engine using
 * 120,000-iteration PBKDF2-HMAC-SHA256 + AES-256-GCM (`ENC_P3_PWD`, `ENC_P3`, `PVM3`).
 * Explicitly zeroizes copied key/passphrase buffers in `finally` blocks.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class JvmVaultCryptoImpl : VaultCrypto {

    private val randomGen = SecureRandom()

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
            if (passCopy.isEmpty()) {
                return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            }
            if (plainText.isEmpty()) {
                return AppResult.Success(plainText)
            }
            AppResult.Success(encryptEnvelopeInternal(plainText, passCopy, PREFIX_P3_PWD))
        } catch (_: GeneralSecurityException) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun encryptLegacyFallback(plainText: String, fallbackSecret: CharArray): String {
        if (plainText.isEmpty()) return plainText
        val secretCopy = fallbackSecret.copyOf()
        return try {
            encryptEnvelopeInternal(plainText, secretCopy, PREFIX_P3)
        } finally {
            secretCopy.fill('\u0000')
        }
    }

    override fun decryptEnvelope(
        encryptedText: String,
        passphrase: CharArray?,
        legacyFallbackSecret: CharArray?
    ): AppResult<String, DataError.Security> {
        if (!isEncryptedPayload(encryptedText)) {
            return AppResult.Success(encryptedText)
        }
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

        val salt = ByteArray(PORTABLE_SALT_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        return try {
            val rawBase64 = encryptedText.removePrefix(prefix)
            val combined = Base64.decode(rawBase64)
            val headerLen = PORTABLE_SALT_LENGTH + GCM_IV_LENGTH
            if (combined.size <= headerLen) {
                return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }

            System.arraycopy(combined, 0, salt, 0, PORTABLE_SALT_LENGTH)
            System.arraycopy(combined, PORTABLE_SALT_LENGTH, iv, 0, GCM_IV_LENGTH)

            val cipherText = ByteArray(combined.size - headerLen)
            System.arraycopy(combined, headerLen, cipherText, 0, cipherText.size)

            val key = deriveAesKey(salt, iterations, effectiveSecret)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val original = cipher.doFinal(cipherText)
            val decoded = String(original, Charsets.UTF_8)
            original.fill(0)
            AppResult.Success(decoded)
        } catch (_: GeneralSecurityException) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } catch (_: Exception) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } finally {
            salt.fill(0)
            iv.fill(0)
            effectiveSecret.fill('\u0000')
        }
    }

    override fun encryptMediaBytes(plainBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security> {
        val passCopy = passphrase.copyOf()
        return try {
            if (passCopy.isEmpty()) {
                return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            }
            AppResult.Success(encryptPvm3Internal(plainBytes, passCopy))
        } catch (_: GeneralSecurityException) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun decryptMediaBytesStrict(encryptedBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security> {
        val passCopy = passphrase.copyOf()
        return try {
            if (passCopy.isEmpty()) {
                return AppResult.Failure(DataError.Security.KEY_REQUIRED)
            }
            if (!hasPvm3Header(encryptedBytes)) {
                return AppResult.Failure(DataError.Security.INTEGRITY_MISMATCH)
            }
            AppResult.Success(decryptPvm3Internal(encryptedBytes, passCopy))
        } catch (_: GeneralSecurityException) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } catch (_: Exception) {
            AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
        } finally {
            passCopy.fill('\u0000')
        }
    }

    override fun encryptLegacyMediaBytes(plainBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray {
        val secretCopy = secretOrPassphrase.copyOf()
        return try {
            encryptPvm3Internal(plainBytes, secretCopy)
        } finally {
            secretCopy.fill('\u0000')
        }
    }

    override fun decryptMediaBytesLegacyCompat(encryptedBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray {
        if (!hasPvm3Header(encryptedBytes)) return encryptedBytes
        val secretCopy = secretOrPassphrase.copyOf()
        return try {
            decryptPvm3Internal(encryptedBytes, secretCopy)
        } finally {
            secretCopy.fill('\u0000')
        }
    }

    private fun encryptEnvelopeInternal(plainText: String, secretChars: CharArray, prefix: String): String {
        val salt = ByteArray(PORTABLE_SALT_LENGTH).also { randomGen.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { randomGen.nextBytes(it) }
        return try {
            val key = deriveAesKey(salt, PBKDF2_ITERATIONS_V3, secretChars)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(salt.size + iv.size + cipherText.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

            prefix + Base64.encode(combined)
        } finally {
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun encryptPvm3Internal(plainBytes: ByteArray, secretChars: CharArray): ByteArray {
        val salt = ByteArray(PORTABLE_SALT_LENGTH).also { randomGen.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { randomGen.nextBytes(it) }
        return try {
            val key = deriveAesKey(salt, PBKDF2_ITERATIONS_V3, secretChars)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherBytes = cipher.doFinal(plainBytes)

            val out = ByteArray(MEDIA_MAGIC_V3.size + salt.size + iv.size + cipherBytes.size)
            var offset = 0
            System.arraycopy(MEDIA_MAGIC_V3, 0, out, offset, MEDIA_MAGIC_V3.size)
            offset += MEDIA_MAGIC_V3.size
            System.arraycopy(salt, 0, out, offset, salt.size)
            offset += salt.size
            System.arraycopy(iv, 0, out, offset, iv.size)
            offset += iv.size
            System.arraycopy(cipherBytes, 0, out, offset, cipherBytes.size)
            out
        } finally {
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun decryptPvm3Internal(encryptedBytes: ByteArray, secretChars: CharArray): ByteArray {
        val salt = ByteArray(PORTABLE_SALT_LENGTH)
        val iv = ByteArray(GCM_IV_LENGTH)
        return try {
            var offset = MEDIA_MAGIC_V3.size
            System.arraycopy(encryptedBytes, offset, salt, 0, PORTABLE_SALT_LENGTH)
            offset += PORTABLE_SALT_LENGTH
            System.arraycopy(encryptedBytes, offset, iv, 0, GCM_IV_LENGTH)
            offset += GCM_IV_LENGTH
            val cipherLen = encryptedBytes.size - offset

            val key = deriveAesKey(salt, PBKDF2_ITERATIONS_V3, secretChars)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(encryptedBytes, offset, cipherLen)
        } finally {
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

    private fun deriveAesKey(salt: ByteArray, iterations: Int, secretChars: CharArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secretChars, salt, iterations, PBKDF2_KEY_BITS)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val PREFIX_P3_PWD = "ENC_P3_PWD:"
        const val PREFIX_P3 = "ENC_P3:"
        const val PREFIX_P2 = "ENC_P2:"
        private const val PBKDF2_ITERATIONS_V2 = 60_000
        private const val PBKDF2_ITERATIONS_V3 = 120_000
        private const val PBKDF2_KEY_BITS = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PORTABLE_SALT_LENGTH = 16
        private val MEDIA_MAGIC_V3 = byteArrayOf(0x50, 0x56, 0x4D, 0x33) // "PVM3"
    }
}
