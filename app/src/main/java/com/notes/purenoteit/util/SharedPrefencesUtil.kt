package com.notes.purenoteit.util

import android.content.Context
import android.content.SharedPreferences
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY

class SharedPrefencesUtil(context: Context) {
    private val local: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)

    fun addLocal(key: String, data: Any) {
        local.edit().apply {
            when (data) {
                is String -> putString(key, data)
                is Boolean -> putBoolean(key, data)
                is Int -> putInt(key, data)
                is Long -> putLong(key, data)
                is Float -> putFloat(key, data)
            }
        }.apply()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, defaultValue: T): T {
        return when (defaultValue) {
            is String -> (local.getString(key, defaultValue) ?: defaultValue) as T
            is Boolean -> local.getBoolean(key, defaultValue) as T
            is Int -> local.getInt(key, defaultValue) as T
            is Long -> local.getLong(key, defaultValue) as T
            is Float -> local.getFloat(key, defaultValue) as T
            else -> defaultValue
        }
    }

    fun getLocal(key: String, dataType: Any): Any {
        return when (dataType) {
            String -> local.getString(key, "") ?: ""
            Boolean -> local.getBoolean(key, false)
            Int -> local.getInt(key, 0)
            else -> false
        }
    }
}
