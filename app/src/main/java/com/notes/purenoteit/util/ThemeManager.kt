package com.notes.purenoteit.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode(val storageKey: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromStorageKey(key: String?): ThemeMode {
            return when (key?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                else -> SYSTEM
            }
        }
    }
}

/**
 * Manages App Theme Modes (System Default, Light, Dark)
 * and synchronizes with AppCompatDelegate and Android Starting Window.
 */
object ThemeManager {
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LEGACY_NIGHT = "night"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = SharedPrefencesUtil(context.applicationContext)
        val rawMode = prefs.get(KEY_THEME_MODE, "")
        if (rawMode.isNotBlank()) {
            return ThemeMode.fromStorageKey(rawMode)
        }
        // Fallback to legacy "night" boolean preference if present
        val legacyNight = prefs.getLocal(KEY_LEGACY_NIGHT, Boolean) as? Boolean
        return if (legacyNight != null) {
            if (legacyNight) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = SharedPrefencesUtil(context.applicationContext)
        prefs.addLocal(KEY_THEME_MODE, mode.storageKey)
        // Maintain legacy boolean compatibility
        val isDark = isDarkTheme(context, mode)
        prefs.addLocal(KEY_LEGACY_NIGHT, isDark)
        applyTheme(mode)
    }

    fun applyTheme(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun isDarkTheme(context: Context, mode: ThemeMode): Boolean {
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
