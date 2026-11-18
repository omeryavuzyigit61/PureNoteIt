package com.notes.purenoteit.data.storage

import android.content.Context
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.data.security.AndroidMediaKeyManager
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.storage.EncryptedMediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Hilt binding of the media store. Media files live under `context.filesDir/media` and are encrypted at
 * rest with a Keystore-wrapped AES-256-GCM key (see `:shared` `EncryptedMediaPathStorage`); decrypted copies for
 * Coil/MediaPlayer/export are created on demand in `context.cacheDir/media_plain` and wiped at app start.
 */
@Singleton
internal class AndroidMediaPathResolverImpl @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : EncryptedMediaStore by SharedStorageFactory.createEncryptedMediaStore(
    rootFilesDir = context.filesDir,
    cacheDir = context.cacheDir,
    keyProvider = { AndroidMediaKeyManager.getOrCreateMediaKey(context) },
    ioDispatcher = ioDispatcher
)
