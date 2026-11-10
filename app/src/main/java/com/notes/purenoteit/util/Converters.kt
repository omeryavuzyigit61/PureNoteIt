package com.notes.purenoteit.util

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(value)
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromListString(list: List<String>?): String? {
        return json.encodeToString(list ?: emptyList())
    }
}
