package com.notes.purenoteit.util

import android.content.Context
import android.content.SharedPreferences
import com.notes.purenoteit.util.Constants.SHARED_PREFENCES_KEY

class SharedPrefencesUtil(context: Context) {
    private var local: SharedPreferences

    init {
        local = context.getSharedPreferences(SHARED_PREFENCES_KEY, Context.MODE_PRIVATE)
    }

    fun addLocal(key: String, data: Any) {
        local.edit().apply {
            when (data) {
                is String -> {
                    this.putString(key, data)
                }
                is Boolean -> {
                    this.putBoolean(key, data)
                }
                is Int -> {
                    this.putInt(key, data)
                }
                else -> {

                }
            }
        }.apply()
    }

    fun getLocal(key: String, dataType: Any): Any {
        return when (dataType) {
            String -> {
                local.getString(key, "")!!
            }
            Boolean -> {
                local.getBoolean(key, false)
            }
            Int -> {
                local.getInt(key, 0)
            }
            else -> {
            }
        }
    }
}