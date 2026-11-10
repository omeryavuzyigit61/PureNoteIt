package com.notes.purenoteit.crypto

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError

/**
 * Portable end-to-end encryption contract (`ENC_P3_PWD` text envelope & `PVM3` binary media format)
 * backed by 120,000-iteration PBKDF2-HMAC-SHA256 and AES-256-GCM.
 */
interface VaultCrypto {
    fun isEncryptedPayload(text: String?): Boolean

    /** Encrypts [plainText] using [passphrase] (`ENC_P3_PWD:`). Requires non-empty passphrase. */
    fun encryptWithPassphrase(plainText: String, passphrase: CharArray): AppResult<String, DataError.Security>

    /** Encrypts [plainText] with fallback secret (`ENC_P3:`) for local `.purevault` compatibility. */
    fun encryptLegacyFallback(plainText: String, fallbackSecret: CharArray): String

    /** Safely decrypts `ENC_P3_PWD:`, `ENC_P3:`, and `ENC_P2:` envelopes. Never returns plaintext on error. */
    fun decryptEnvelope(
        encryptedText: String,
        passphrase: CharArray? = null,
        legacyFallbackSecret: CharArray? = null
    ): AppResult<String, DataError.Security>

    /** Encrypts binary media using [passphrase] into `PVM3` AES-256-GCM format. */
    fun encryptMediaBytes(plainBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security>

    /** Strictly decrypts `PVM3`-framed cloud/sync media blobs; rejects unauthenticated headers. */
    fun decryptMediaBytesStrict(encryptedBytes: ByteArray, passphrase: CharArray): AppResult<ByteArray, DataError.Security>

    /** Local backup helper encoding `PVM3` bytes with passphrase or fallback secret. */
    fun encryptLegacyMediaBytes(plainBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray

    /**
     * Legacy backup decryption helper preserving `GeneralSecurityException` on GCM tag tampering
     * and transparent passthrough when the `PVM3` magic header is absent.
     */
    fun decryptMediaBytesLegacyCompat(encryptedBytes: ByteArray, secretOrPassphrase: CharArray): ByteArray
}
