package com.notes.purenoteit.util

import android.content.Context
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

        val localeList = if (langCode == LANG_AUTO) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
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
}
