package com.notes.purenoteit.view

import android.app.Application
import com.notes.purenoteit.di.ApplicationScope
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.storage.EncryptedMediaStore
import com.notes.purenoteit.util.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    internal lateinit var encryptedMediaStore: EncryptedMediaStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        // Initialize Theme Mode at Application startup before any Activity window is created.
        val mode = ThemeManager.getThemeMode(this)
        ThemeManager.applyTheme(mode)

        // Media at rest: wipe leftover decrypted copies from the previous process, then encrypt any legacy or
        // freshly restored plaintext media files.
        applicationScope.launch {
            withContext(ioDispatcher) {
                runCatching {
                    encryptedMediaStore.clearDecryptedCache()
                    encryptedMediaStore.encryptPendingPlaintextMedia()
                }
            }
        }
    }
}
