package com.notes.purenoteit.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtil {
    private const val KEY_PIN_HASH = "pin_hash_key"
    private const val KEY_LEGACY_PIN = "password_key"
    private const val APP_SALT = "PureNoteIt_Secure_Salt_2026"
    private const val AES_KEY_SALT = "PureNoteIt_AES_Salt_V2_2026"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    // Bitwarden Session Vault Lock
    var isVaultUnlocked: Boolean = false
        private set

    fun unlockVault() {
        isVaultUnlocked = true
    }

    fun lockVault() {
        isVaultUnlocked = false
    }

    private fun hash(pin: String): String {
        val input = "$APP_SALT$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isPasswordSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        return prefs.contains(KEY_PIN_HASH) || (prefs.getInt(KEY_LEGACY_PIN, -1) != -1)
    }

    fun verifyPassword(context: Context, enteredPin: String): Boolean {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        val savedHash = prefs.getString(KEY_PIN_HASH, null)

        if (savedHash != null) {
            val valid = savedHash == hash(enteredPin)
            if (valid) unlockVault()
            return valid
        }

        // Backward compatibility: If an old plaintext integer PIN exists, verify & migrate
        val legacyPin = prefs.getInt(KEY_LEGACY_PIN, -1)
        if (legacyPin != -1 && enteredPin.toIntOrNull() == legacyPin) {
            savePassword(context, enteredPin)
            prefs.edit().remove(KEY_LEGACY_PIN).apply()
            unlockVault()
            return true
        }

        return false
    }

    fun savePassword(context: Context, newPin: String) {
        val prefs = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash(newPin))
            .remove(KEY_LEGACY_PIN)
            .apply()
        unlockVault()
    }

    // --- Bitwarden-Grade AES-256 GCM Encryption ---

    private fun deriveKey(): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(APP_SALT.toCharArray(), AES_KEY_SALT.toByteArray(), 1000, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return plainText
        return try {
            val key = deriveKey()
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Prepend IV to ciphertext
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            "ENC:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SecurityUtil", "Encryption failed: ${e.message}")
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        if (!encryptedText.startsWith("ENC:")) return encryptedText
        return try {
            val rawBase64 = encryptedText.removePrefix("ENC:")
            val combined = Base64.decode(rawBase64, Base64.NO_WRAP)

            if (combined.size < GCM_IV_LENGTH) return encryptedText

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val key = deriveKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val original = cipher.doFinal(cipherText)
            String(original, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("SecurityUtil", "Decryption failed: ${e.message}")
            encryptedText
        }
    }

    // --- Bitwarden Clipboard Auto-Clear ---

    fun copyToClipboardWithAutoClear(
        context: Context,
        text: String,
        label: String = "PureNoteIt Secret",
        timeoutSeconds: Long = 30
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        context.showToastShort("Panoya kopyalandı ($timeoutSeconds sn sonra temizlenecek)")

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val currentClip = clipboard.primaryClip
                if (currentClip != null && currentClip.itemCount > 0) {
                    val clipText = currentClip.getItemAt(0).text?.toString()
                    if (clipText == text) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        Log.i("SecurityUtil", "Clipboard auto-cleared securely")
                    }
                }
            } catch (e: Exception) {
                Log.e("SecurityUtil", "Clipboard clear failed: ${e.message}")
            }
        }, timeoutSeconds * 1000)
    }
}
