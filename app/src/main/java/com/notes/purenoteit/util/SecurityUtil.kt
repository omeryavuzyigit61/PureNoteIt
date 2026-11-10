package com.notes.purenoteit.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.ObfuscatedSecrets
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtil {
    // V2 PBKDF2-HMAC-SHA256 Salted PIN Storage Keys (XOR Obfuscated)
    private val KEY_PIN_HASH_V2 by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_PIN_HASH_V2) }
    private val KEY_PIN_SALT_V2 by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_PIN_SALT_V2) }
    private const val PBKDF2_ITERATIONS_V2 = 60_000
    private const val PBKDF2_ITERATIONS_V3 = 120_000
    private const val PBKDF2_KEY_BITS = 256

    // Brute-Force Rate Limiting Keys (XOR Obfuscated)
    private val KEY_FAILED_ATTEMPTS by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_FAILED_ATTEMPTS) }
    private val KEY_LOCKOUT_UNTIL_MS by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_LOCKOUT_UNTIL_MS) }
    private const val MAX_FAILED_ATTEMPTS = 5
    private const val MAX_LOCKOUT_CAP_MS = 900_000L // 15 minutes max cap

    @Volatile
    private var monotonicLockoutUntilMs: Long = 0L

    // Legacy PIN Keys (XOR Obfuscated, kept strictly for transparent migration)
    private val KEY_PIN_HASH by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_PIN_HASH_LEGACY) }
    private val KEY_LEGACY_PIN by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEY_LEGACY_PIN) }
    private val APP_SALT by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.APP_SALT) }
    private val AES_KEY_SALT by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.AES_KEY_SALT) }

    // Hardware AndroidKeyStore Constants (XOR Obfuscated)
    private val ANDROID_KEYSTORE by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.ANDROID_KEYSTORE) }
    private val KEYSTORE_ALIAS by lazy { ObfuscatedSecrets.decode(ObfuscatedSecrets.KEYSTORE_ALIAS) }
    private const val BIOMETRIC_KEYSTORE_ALIAS = "purenoteit_biometric_gate_v1"
    private val BIOMETRIC_CHALLENGE = byteArrayOf(0x50, 0x4E, 0x49, 0x5F, 0x42, 0x49, 0x4F, 0x5F, 0x56, 0x31)

    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PORTABLE_SALT_LENGTH = 16
    private val MEDIA_MAGIC_V3 = byteArrayOf(0x50, 0x56, 0x4D, 0x33) // "PVM3"

    // Vault Session Lock State
    @Volatile
    var isVaultUnlocked: Boolean = false
        private set

    fun unlockVault() {
        isVaultUnlocked = true
    }

    fun lockVault() {
        isVaultUnlocked = false
    }

    // --- Brute-Force Protection (Monotonic Clock + Exponential Backoff) ---

    private fun calculateLockoutDurationMs(attempts: Int): Long {
        return when {
            attempts < MAX_FAILED_ATTEMPTS -> 0L
            attempts <= 6 -> 30_000L       // 30s
            attempts <= 9 -> 60_000L       // 1 min
            attempts <= 14 -> 300_000L     // 5 min
            else -> MAX_LOCKOUT_CAP_MS     // 15 min
        }
    }

    fun isPinLockedOut(context: Context): Boolean {
        return getRemainingLockoutSeconds(context) > 0
    }

    fun getRemainingLockoutSeconds(context: Context): Int {
        val elapsedRemaining = monotonicLockoutUntilMs - SystemClock.elapsedRealtime()
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        val wallNow = System.currentTimeMillis()
        var wallLockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)

        // Guard against backward system clock manipulation freezing lockout indefinitely
        if (wallLockoutUntil - wallNow > MAX_LOCKOUT_CAP_MS) {
            wallLockoutUntil = wallNow + MAX_LOCKOUT_CAP_MS
            prefs.edit().putLong(KEY_LOCKOUT_UNTIL_MS, wallLockoutUntil).apply()
        }

        val wallRemaining = wallLockoutUntil - wallNow
        val effectiveRemainingMs = maxOf(elapsedRemaining, wallRemaining)
        return if (effectiveRemainingMs > 0) ((effectiveRemainingMs + 999) / 1000).toInt() else 0
    }

    private fun recordFailedAttempt(context: Context) {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        val penaltyMs = calculateLockoutDurationMs(attempts)
        if (penaltyMs > 0L) {
            monotonicLockoutUntilMs = SystemClock.elapsedRealtime() + penaltyMs
            editor.putLong(KEY_LOCKOUT_UNTIL_MS, System.currentTimeMillis() + penaltyMs)
        }
        editor.apply()
    }

    private fun resetFailedAttempts(context: Context) {
        monotonicLockoutUntilMs = 0L
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_FAILED_ATTEMPTS) || prefs.contains(KEY_LOCKOUT_UNTIL_MS)) {
            prefs.edit()
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKOUT_UNTIL_MS)
                .apply()
        }
    }

    // --- Salted PBKDF2-HMAC-SHA256 PIN Hashing (CharArray Zeroization Compliant) ---

    private fun derivePinHashV2(pinChars: CharArray, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS_V2, PBKDF2_KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun legacyHash(pinChars: CharArray): String {
        val saltChars = APP_SALT.toCharArray()
        val combined = CharArray(saltChars.size + pinChars.size)
        System.arraycopy(saltChars, 0, combined, 0, saltChars.size)
        System.arraycopy(pinChars, 0, combined, saltChars.size, pinChars.size)
        val utf8Bytes = String(combined).toByteArray(Charsets.UTF_8)
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(utf8Bytes)
            try {
                digest.joinToString("") { "%02x".format(it) }
            } finally {
                digest.fill(0)
            }
        } finally {
            saltChars.fill('\u0000')
            combined.fill('\u0000')
            utf8Bytes.fill(0)
        }
    }

    fun isPasswordSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_HASH_V2) ||
            prefs.contains(KEY_PIN_HASH) ||
            (prefs.getInt(KEY_LEGACY_PIN, -1) != -1)
    }

    /**
     * Verifies [enteredPin] using constant-time digest comparison and zeroizes [enteredPin]
     * plus all derived key material in memory before returning.
     */
    fun verifyPassword(context: Context, enteredPin: CharArray): Boolean {
        try {
            if (isPinLockedOut(context)) {
                val remaining = getRemainingLockoutSeconds(context)
                context.showToastShort("Çok fazla hatalı deneme! Lütfen $remaining sn bekleyin.")
                return false
            }

            val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)

            // 1. Check V2 PBKDF2-HMAC-SHA256 with per-device random salt & constant-time comparison
            val savedHashV2Base64 = prefs.getString(KEY_PIN_HASH_V2, null)
            val savedSaltV2Base64 = prefs.getString(KEY_PIN_SALT_V2, null)
            if (savedHashV2Base64 != null && savedSaltV2Base64 != null) {
                var saltBytes: ByteArray? = null
                var expectedHash: ByteArray? = null
                var candidateHash: ByteArray? = null
                return try {
                    saltBytes = Base64.decode(savedSaltV2Base64, Base64.NO_WRAP)
                    expectedHash = Base64.decode(savedHashV2Base64, Base64.NO_WRAP)
                    candidateHash = derivePinHashV2(enteredPin, saltBytes)
                    val valid = MessageDigest.isEqual(expectedHash, candidateHash)
                    if (valid) {
                        resetFailedAttempts(context)
                        unlockVault()
                    } else {
                        recordFailedAttempt(context)
                    }
                    valid
                } catch (_: Exception) {
                    false
                } finally {
                    saltBytes?.fill(0)
                    expectedHash?.fill(0)
                    candidateHash?.fill(0)
                }
            }

            // 2. Legacy V1 SHA-256 hash transparent migration
            val savedLegacyHash = prefs.getString(KEY_PIN_HASH, null)
            if (savedLegacyHash != null) {
                val expectedBytes = savedLegacyHash.toByteArray(Charsets.UTF_8)
                val candidateBytes = legacyHash(enteredPin).toByteArray(Charsets.UTF_8)
                val valid = try {
                    MessageDigest.isEqual(expectedBytes, candidateBytes)
                } finally {
                    expectedBytes.fill(0)
                    candidateBytes.fill(0)
                }
                if (valid) {
                    savePassword(context, enteredPin.copyOf())
                    resetFailedAttempts(context)
                    unlockVault()
                } else {
                    recordFailedAttempt(context)
                }
                return valid
            }

            // 3. Legacy V0 plaintext integer PIN transparent migration
            val legacyPin = prefs.getInt(KEY_LEGACY_PIN, -1)
            if (legacyPin != -1) {
                val parsedInt = String(enteredPin).toIntOrNull()
                val valid = parsedInt == legacyPin
                if (valid) {
                    savePassword(context, enteredPin.copyOf())
                    resetFailedAttempts(context)
                    unlockVault()
                } else {
                    recordFailedAttempt(context)
                }
                return valid
            }

            return false
        } finally {
            enteredPin.fill('\u0000')
        }
    }

    /**
     * Derives a random-salted PBKDF2-HMAC-SHA256 verifier from [newPin] and zeroizes [newPin]
     * and intermediate byte arrays immediately after persistence.
     */
    fun savePassword(context: Context, newPin: CharArray) {
        val randomSalt = ByteArray(16)
        var derivedHash: ByteArray? = null
        try {
            val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
            SecureRandom().nextBytes(randomSalt)
            derivedHash = derivePinHashV2(newPin, randomSalt)

            prefs.edit()
                .putString(KEY_PIN_SALT_V2, Base64.encodeToString(randomSalt, Base64.NO_WRAP))
                .putString(KEY_PIN_HASH_V2, Base64.encodeToString(derivedHash, Base64.NO_WRAP))
                .remove(KEY_PIN_HASH)
                .remove(KEY_LEGACY_PIN)
                .remove(KEY_FAILED_ATTEMPTS)
                .remove(KEY_LOCKOUT_UNTIL_MS)
                .apply()
            unlockVault()
        } finally {
            newPin.fill('\u0000')
            randomSalt.fill(0)
            derivedHash?.fill(0)
        }
    }

    // --- Crypto-Bound Biometric Authentication (BiometricPrompt.CryptoObject) ---

    private fun deleteBiometricGateKey() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(BIOMETRIC_KEYSTORE_ALIAS)) {
                ks.deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
            }
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateBiometricGateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(BIOMETRIC_KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                return existing.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    fun createBiometricCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val key = getOrCreateBiometricGateKey() ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            BiometricPrompt.CryptoObject(cipher)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteBiometricGateKey()
            // Re-create once after biometric enrollment change so future prompts bind to current biometrics
            try {
                val freshKey = getOrCreateBiometricGateKey() ?: return null
                val freshCipher = Cipher.getInstance("AES/GCM/NoPadding")
                freshCipher.init(Cipher.ENCRYPT_MODE, freshKey)
                BiometricPrompt.CryptoObject(freshCipher)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Authenticates the user using hardware-bound [BiometricPrompt.CryptoObject] (`BIOMETRIC_STRONG`).
     * Never grants Vault access on a raw callback without completing the hardware-gated cipher operation.
     * Falls back cleanly to PIN entry ([onFallbackToPin]) if strong crypto biometrics are unavailable or invalidated.
     */
    fun authenticateWithCryptoBiometric(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onUnlocked: () -> Unit,
        onFallbackToPin: () -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        val canStrongAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canStrongAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onFallbackToPin()
            return
        }

        val cryptoObject = createBiometricCryptoObject()
        if (cryptoObject == null) {
            onFallbackToPin()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val unlockedCipher = result.cryptoObject?.cipher
                    if (unlockedCipher == null) {
                        lockVault()
                        onFallbackToPin()
                        return
                    }
                    try {
                        val proof = unlockedCipher.doFinal(BIOMETRIC_CHALLENGE)
                        val validProof = proof != null && proof.isNotEmpty()
                        proof?.fill(0)
                        if (validProof) {
                            unlockVault()
                            onUnlocked()
                        } else {
                            lockVault()
                            onFallbackToPin()
                        }
                    } catch (_: GeneralSecurityException) {
                        deleteBiometricGateKey()
                        lockVault()
                        onFallbackToPin()
                    } catch (_: Exception) {
                        lockVault()
                        onFallbackToPin()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallbackToPin()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeButtonText)
            .build()

        prompt.authenticate(promptInfo, cryptoObject)
    }

    // --- Hardware-Backed AndroidKeyStore AES-256-GCM Encryption (with Portable & Legacy Support) ---

    private fun getOrCreateHardwareKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                return existingEntry.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keyGenSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveLegacyKey(): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val chars = APP_SALT.toCharArray()
        val spec = PBEKeySpec(chars, AES_KEY_SALT.toByteArray(), 1000, 256)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun derivePortableKey(randomSalt: ByteArray, iterations: Int, passphrase: CharArray? = null): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretChars = if (passphrase != null && passphrase.isNotEmpty()) {
            passphrase.copyOf()
        } else {
            (APP_SALT + "_" + AES_KEY_SALT).toCharArray()
        }
        val spec = PBEKeySpec(secretChars, randomSalt, iterations, PBKDF2_KEY_BITS)
        return try {
            val encoded = factory.generateSecret(spec).encoded
            try {
                SecretKeySpec(encoded, "AES")
            } finally {
                encoded.fill(0)
            }
        } finally {
            spec.clearPassword()
            secretChars.fill('\u0000')
        }
    }

    private val SHARED_CRYPTO by lazy { com.notes.purenoteit.SharedCryptoFactory.createVaultCrypto() }

    private fun defaultFallbackChars(): CharArray = (APP_SALT + "_" + AES_KEY_SALT).toCharArray()

    fun isEncryptedPayload(text: String?): Boolean {
        return SHARED_CRYPTO.isEncryptedPayload(text)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty() || isEncryptedPayload(plainText)) return plainText
        // 1. Prefer Hardware-Backed AndroidKeyStore (ENC_V2:)
        try {
            val hardwareKey = getOrCreateHardwareKey()
            if (hardwareKey != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, hardwareKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

                val combined = ByteArray(1 + iv.size + cipherText.size)
                combined[0] = iv.size.toByte()
                System.arraycopy(iv, 0, combined, 1, iv.size)
                System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)

                return "ENC_V2:" + Base64.encodeToString(combined, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
        }

        // 2. Fallback to 120,000-iteration random-salted AES-256-GCM (ENC_P3:)
        return encryptPortable(plainText)
    }

    /**
     * Portable AES-256-GCM encryption (V3) delegated to `:shared` [com.notes.purenoteit.crypto.VaultCrypto]
     * using per-payload 16-byte random salt and 120,000 PBKDF2-HMAC-SHA256 iterations.
     */
    fun encryptPortable(plainText: String, passphrase: CharArray? = null): String {
        if (plainText.isEmpty()) return plainText
        return if (passphrase != null && passphrase.isNotEmpty()) {
            when (val res = SHARED_CRYPTO.encryptWithPassphrase(plainText, passphrase)) {
                is AppResult.Success -> res.data
                is AppResult.Failure -> throw GeneralSecurityException("Portable encryption failed: ${res.error}")
            }
        } else {
            val fallback = defaultFallbackChars()
            try {
                SHARED_CRYPTO.encryptLegacyFallback(plainText, fallback)
            } finally {
                fallback.fill('\u0000')
            }
        }
    }

    /**
     * Encrypts binary media files (photos, drawings, voice recordings) with AES-256-GCM (120k PBKDF2)
     * so `.purevault` archives never expose raw media in cloud storage or share targets.
     */
    fun encryptPortableBytes(plainBytes: ByteArray, passphrase: CharArray? = null): ByteArray {
        val secret = if (passphrase != null && passphrase.isNotEmpty()) passphrase.copyOf() else defaultFallbackChars()
        return try {
            SHARED_CRYPTO.encryptLegacyMediaBytes(plainBytes, secret)
        } finally {
            secret.fill('\u0000')
        }
    }

    /**
     * Decrypts binary media payloads created by [encryptPortableBytes]; transparently passes through
     * legacy unencrypted bytes if the PVM3 header is absent.
     */
    fun decryptPortableBytes(encryptedBytes: ByteArray, passphrase: CharArray? = null): ByteArray {
        val secret = if (passphrase != null && passphrase.isNotEmpty()) passphrase.copyOf() else defaultFallbackChars()
        return try {
            SHARED_CRYPTO.decryptMediaBytesLegacyCompat(encryptedBytes, secret)
        } finally {
            secret.fill('\u0000')
        }
    }

    /**
     * Safe decryption returning [AppResult] without ever falling back to plaintext or silently
     * returning corrupted data on [KeyPermanentlyInvalidatedException] / [GeneralSecurityException].
     */
    fun decryptSafe(encryptedText: String, passphrase: CharArray? = null): AppResult<String, DataError.Security> {
        if (!isEncryptedPayload(encryptedText)) {
            return AppResult.Success(encryptedText)
        }

        if (encryptedText.startsWith("ENC_V2:")) {
            return try {
                val rawBase64 = encryptedText.removePrefix("ENC_V2:")
                val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
                if (combined.size < 2) {
                    lockVault()
                    return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }

                val ivLength = combined[0].toInt() and 0xFF
                if (ivLength <= 0 || combined.size <= 1 + ivLength) {
                    lockVault()
                    return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }

                val iv = ByteArray(ivLength)
                System.arraycopy(combined, 1, iv, 0, ivLength)

                val cipherText = ByteArray(combined.size - 1 - ivLength)
                System.arraycopy(combined, 1 + ivLength, cipherText, 0, cipherText.size)

                val hardwareKey = getOrCreateHardwareKey() ?: run {
                    lockVault()
                    return AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
                }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, hardwareKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                val original = cipher.doFinal(cipherText)
                AppResult.Success(String(original, Charsets.UTF_8))
            } catch (_: KeyPermanentlyInvalidatedException) {
                lockVault()
                AppResult.Failure(DataError.Security.KEYSTORE_UNAVAILABLE)
            } catch (_: GeneralSecurityException) {
                lockVault()
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            } catch (_: Exception) {
                lockVault()
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }
        }

        if (encryptedText.startsWith("ENC_P3_PWD:") || encryptedText.startsWith("ENC_P3:") || encryptedText.startsWith("ENC_P2:")) {
            val fallback = defaultFallbackChars()
            return try {
                SHARED_CRYPTO.decryptEnvelope(
                    encryptedText = encryptedText,
                    passphrase = passphrase,
                    legacyFallbackSecret = fallback
                )
            } finally {
                fallback.fill('\u0000')
            }
        }

        if (encryptedText.startsWith("ENC:")) {
            return try {
                val rawBase64 = encryptedText.removePrefix("ENC:")
                val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
                if (combined.size < GCM_IV_LENGTH) {
                    return AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
                }

                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

                val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                val key = deriveLegacyKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                val original = cipher.doFinal(cipherText)
                AppResult.Success(String(original, Charsets.UTF_8))
            } catch (_: GeneralSecurityException) {
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            } catch (_: Exception) {
                AppResult.Failure(DataError.Security.DECRYPTION_FAILED)
            }
        }

        return AppResult.Success(encryptedText)
    }

    // --- Clipboard Auto-Clear with Android 13+ Sensitive Flag ---

    fun copyToClipboardWithAutoClear(
        context: Context,
        text: String,
        label: String = "PureNoteIt Secret",
        timeoutSeconds: Long = 30
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        context.showToastShort("Panoya kopyalandı ($timeoutSeconds sn sonra temizlenecek)")

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {
            }
        }, timeoutSeconds * 1000)
    }
}
