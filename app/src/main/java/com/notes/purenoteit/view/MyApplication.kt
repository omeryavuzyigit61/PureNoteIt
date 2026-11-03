package com.notes.purenoteit.view

import android.app.Application
import com.notes.purenoteit.util.ThemeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Theme Mode at Application startup before any Activity window is created.
        val mode = ThemeManager.getThemeMode(this)
        ThemeManager.applyTheme(mode)
    }
}
