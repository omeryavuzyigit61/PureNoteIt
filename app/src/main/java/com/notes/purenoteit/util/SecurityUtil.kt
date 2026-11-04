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
import android.security.keystore.KeyProperties
import android.util.Base64
import com.notes.purenoteit.security.ObfuscatedSecrets
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY
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
    private const val PBKDF2_ITERATIONS = 60_000
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
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val PORTABLE_SALT_LENGTH = 16

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

    // --- Salted PBKDF2-HMAC-SHA256 PIN Hashing ---

    private fun derivePinHashV2(pin: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun legacyHash(pin: String): String {
        val input = "$APP_SALT$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isPasswordSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_HASH_V2) ||
                prefs.contains(KEY_PIN_HASH) ||
                (prefs.getInt(KEY_LEGACY_PIN, -1) != -1)
    }

    fun verifyPassword(context: Context, enteredPin: String): Boolean {
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
            return try {
                val saltBytes = Base64.decode(savedSaltV2Base64, Base64.NO_WRAP)
                val expectedHash = Base64.decode(savedHashV2Base64, Base64.NO_WRAP)
                val candidateHash = derivePinHashV2(enteredPin, saltBytes)
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
            }
        }

        // 2. Legacy V1 SHA-256 hash migration
        val savedLegacyHash = prefs.getString(KEY_PIN_HASH, null)
        if (savedLegacyHash != null) {
            val valid = MessageDigest.isEqual(
                savedLegacyHash.toByteArray(Charsets.UTF_8),
                legacyHash(enteredPin).toByteArray(Charsets.UTF_8)
            )
            if (valid) {
                // Transparently upgrade to V2 Salted PBKDF2
                savePassword(context, enteredPin)
                resetFailedAttempts(context)
                unlockVault()
            } else {
                recordFailedAttempt(context)
            }
            return valid
        }

        // 3. Legacy V0 plaintext integer PIN migration
        val legacyPin = prefs.getInt(KEY_LEGACY_PIN, -1)
        if (legacyPin != -1) {
            val valid = enteredPin.toIntOrNull() == legacyPin
            if (valid) {
                savePassword(context, enteredPin)
                resetFailedAttempts(context)
                unlockVault()
            } else {
                recordFailedAttempt(context)
            }
            return valid
        }

        return false
    }

    fun savePassword(context: Context, newPin: String) {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        val randomSalt = ByteArray(16)
        SecureRandom().nextBytes(randomSalt)
        val derivedHash = derivePinHashV2(newPin, randomSalt)

        prefs.edit()
            .putString(KEY_PIN_SALT_V2, Base64.encodeToString(randomSalt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH_V2, Base64.encodeToString(derivedHash, Base64.NO_WRAP))
            .remove(KEY_PIN_HASH)
            .remove(KEY_LEGACY_PIN)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL_MS)
            .apply()
        unlockVault()
    }

    // --- Hardware-Backed AndroidKeyStore AES-256-GCM Encryption (with V1 Legacy Fallback) ---

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
        val spec = PBEKeySpec(APP_SALT.toCharArray(), AES_KEY_SALT.toByteArray(), 1000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun derivePortableKeyV2(randomSalt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val compositeSecret = (APP_SALT + "_" + AES_KEY_SALT).toCharArray()
        val spec = PBEKeySpec(compositeSecret, randomSalt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            val tmp = factory.generateSecret(spec)
            SecretKeySpec(tmp.encoded, "AES")
        } finally {
            spec.clearPassword()
            compositeSecret.fill('\u0000')
        }
    }

    fun isEncryptedPayload(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.startsWith("ENC_V2:") || text.startsWith("ENC_P2:") || text.startsWith("ENC:")
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

        // 2. Fallback to 60,000-iteration random-salted AES-256-GCM (ENC_P2:)
        return encryptPortable(plainText)
    }

    /**
     * Portable AES-256-GCM encryption (V2) using per-payload 16-byte random salt
     * and 60,000 PBKDF2-HMAC-SHA256 iterations for cross-device .purevault archives.
     */
    fun encryptPortable(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        return try {
            val randomGen = SecureRandom()
            val salt = ByteArray(PORTABLE_SALT_LENGTH).also { randomGen.nextBytes(it) }
            val iv = ByteArray(GCM_IV_LENGTH).also { randomGen.nextBytes(it) }
            val key = derivePortableKeyV2(salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(salt.size + iv.size + cipherText.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

            "ENC_P2:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.startsWith("ENC_V2:")) {
            return try {
                val rawBase64 = encryptedText.removePrefix("ENC_V2:")
                val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
                if (combined.size < 2) return encryptedText

                val ivLength = combined[0].toInt() and 0xFF
                if (ivLength <= 0 || combined.size <= 1 + ivLength) return encryptedText

                val iv = ByteArray(ivLength)
                System.arraycopy(combined, 1, iv, 0, ivLength)

                val cipherText = ByteArray(combined.size - 1 - ivLength)
                System.arraycopy(combined, 1 + ivLength, cipherText, 0, cipherText.size)

                val hardwareKey = getOrCreateHardwareKey() ?: return encryptedText
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, hardwareKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                val original = cipher.doFinal(cipherText)
                String(original, Charsets.UTF_8)
            } catch (_: Exception) {
                encryptedText
            }
        }

        if (encryptedText.startsWith("ENC_P2:")) {
            return try {
                val rawBase64 = encryptedText.removePrefix("ENC_P2:")
                val combined = Base64.decode(rawBase64, Base64.NO_WRAP)
                val headerLen = PORTABLE_SALT_LENGTH + GCM_IV_LENGTH
                if (combined.size <= headerLen) return encryptedText

                val salt = ByteArray(PORTABLE_SALT_LENGTH)
                System.arraycopy(combined, 0, salt, 0, PORTABLE_SALT_LENGTH)

                val iv = ByteArray(GCM_IV_LENGTH)
                System.arraycopy(combined, PORTABLE_SALT_LENGTH, iv, 0, GCM_IV_LENGTH)

                val cipherText = ByteArray(combined.size - headerLen)
                System.arraycopy(combined, headerLen, cipherText, 0, cipherText.size)

                val key = derivePortableKeyV2(salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                val original = cipher.doFinal(cipherText)
                String(original, Charsets.UTF_8)
            } catch (_: Exception) {
                encryptedText
            }
        }

        if (!encryptedText.startsWith("ENC:")) return encryptedText
        return try {
            val rawBase64 = encryptedText.removePrefix("ENC:")
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)

            if (combined.size < GCM_IV_LENGTH) return encryptedText

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val key = deriveLegacyKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val original = cipher.doFinal(cipherText)
            String(original, Charsets.UTF_8)
        } catch (_: Exception) {
            encryptedText
        }
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
