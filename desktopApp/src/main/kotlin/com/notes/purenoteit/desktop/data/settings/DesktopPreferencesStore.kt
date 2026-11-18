package com.notes.purenoteit.desktop.data.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class DesktopPreferencesData(
    @SerialName("language") val language: String = "tr",
    @SerialName("trash_retention_days") val trashRetentionDays: Int = 30,
    @SerialName("is_dark_theme") val isDarkTheme: Boolean = true,
    @SerialName("hide_locked_notes") val hideLockedNotes: Boolean = false,
    @SerialName("settings_updated_at") val settingsUpdatedAt: Long = 0L
)

internal class DesktopPreferencesStore(
    private val preferencesFile: File
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Synchronized
    fun load(): DesktopPreferencesData {
        if (!preferencesFile.isFile) return DesktopPreferencesData()
        return try {
            val raw = preferencesFile.readText(Charsets.UTF_8)
            val parsed = json.decodeFromString<DesktopPreferencesData>(raw)
            parsed.copy(
                language = normalizeLanguage(parsed.language),
                trashRetentionDays = parsed.trashRetentionDays.coerceIn(1, 365)
            )
        } catch (_: Exception) {
            DesktopPreferencesData()
        }
    }

    @Synchronized
    fun update(transform: (DesktopPreferencesData) -> DesktopPreferencesData): DesktopPreferencesData {
        val updated = transform(load()).let {
            it.copy(
                language = normalizeLanguage(it.language),
                trashRetentionDays = it.trashRetentionDays.coerceIn(1, 365)
            )
        }
        saveAtomic(updated)
        return updated
    }

    private fun saveAtomic(data: DesktopPreferencesData) {
        preferencesFile.parentFile?.mkdirs()
        val tmp = File(preferencesFile.parentFile, "${preferencesFile.name}.tmp")
        tmp.writeText(json.encodeToString(data), Charsets.UTF_8)
        try {
            Files.move(tmp.toPath(), preferencesFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), preferencesFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        fun normalizeLanguage(raw: String?): String =
            if (raw.orEmpty().trim().lowercase().startsWith("en")) "en" else "tr"
    }
}
