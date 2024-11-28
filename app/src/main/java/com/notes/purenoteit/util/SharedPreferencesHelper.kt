package com.notes.purenoteit.util

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

    fun saveFilePath(filePath: String) {
        sharedPreferences.edit().putString("last_audio_path", filePath).apply()
    }

    fun getFilePath(): String? {
        return sharedPreferences.getString("last_audio_path", null)
    }
}