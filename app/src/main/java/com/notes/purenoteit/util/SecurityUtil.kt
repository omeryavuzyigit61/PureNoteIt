package com.notes.purenoteit.util

import android.content.Context
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY
import java.security.MessageDigest

object SecurityUtil {
    private const val KEY_PIN_HASH = "pin_hash_key"
    private const val KEY_LEGACY_PIN = "password_key"
    private const val APP_SALT = "PureNoteIt_Secure_Salt_2026"

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
            return savedHash == hash(enteredPin)
        }

        // Backward compatibility: If an old plaintext integer PIN exists, verify & migrate
        val legacyPin = prefs.getInt(KEY_LEGACY_PIN, -1)
        if (legacyPin != -1 && enteredPin.toIntOrNull() == legacyPin) {
            savePassword(context, enteredPin)
            prefs.edit().remove(KEY_LEGACY_PIN).apply()
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
    }
}
