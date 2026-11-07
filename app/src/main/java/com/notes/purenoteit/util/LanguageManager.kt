package com.notes.purenoteit.util

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    const val LANG_AUTO = "system"
    const val LANG_TR = "tr"
    const val LANG_EN = "en"

    data class LanguageOption(
        val code: String,
        val displayName: String,
        val nativeName: String,
        val flag: String
    )

    val supportedLanguages = listOf(
        LanguageOption(LANG_AUTO, "Sistem Dili (System Default)", "Cihazın varsayılan dili", "📱"),
        LanguageOption(LANG_TR, "Türkçe", "Türkçe", "🇹🇷"),
        LanguageOption(LANG_EN, "English", "English", "🇬🇧")
    )

    fun setLanguage(context: Context, langCode: String) {
        val prefs = SharedPrefencesUtil(context)
        prefs.addLocal("app_language", langCode)

        // 1. Update AppCompatDelegate for Android 13+ and backward compatibility
        val localeList = if (langCode == LANG_AUTO) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)

        // 2. Set default Java Locale
        val targetLocale = if (langCode == LANG_AUTO) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(langCode)
        }
        Locale.setDefault(targetLocale)

        // 3. Update resources configuration directly for immediate in-process update
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(targetLocale)
        config.setLayoutDirection(targetLocale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)

        // 4. Recreate Activity so all Compose and Activity views reload immediately
        findActivity(context)?.recreate()
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun getSelectedLanguage(context: Context): String {
        val prefs = SharedPrefencesUtil(context)
        val saved = prefs.get("app_language", "")
        if (saved.isNotBlank()) return saved

        val current = AppCompatDelegate.getApplicationLocales()
        return if (current.isEmpty) LANG_AUTO else current.toLanguageTags()
    }

    fun getLanguageLabel(context: Context): String {
        val code = getSelectedLanguage(context)
        return supportedLanguages.find { it.code == code }?.displayName ?: "Türkçe 🇹🇷"
    }

    fun wrapContext(context: Context): Context {
        val savedLang = getSelectedLanguage(context)
        if (savedLang == LANG_AUTO) {
            return context
        }
        val locale = Locale.forLanguageTag(savedLang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
