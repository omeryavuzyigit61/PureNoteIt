package com.notes.purenoteit.data.storage

import android.content.Context
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.di.IoDispatcher
import com.notes.purenoteit.domain.storage.MediaPathResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

/**
 * Android Hilt binding implementation of [MediaPathResolver] delegating to `:shared`
 * content-addressed storage rooted under `context.filesDir` and `context.cacheDir`.
 */
internal class AndroidMediaPathResolverImpl @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher ioDispatcher: CoroutineDispatcher
) : MediaPathResolver {

    private val storage = SharedStorageFactory.createMediaPathResolver(
        rootFilesDir = context.filesDir,
        cacheDir = context.cacheDir,
        ioDispatcher = ioDispatcher
    )

    override fun isCanonicalRelativePath(rawPath: String?): Boolean =
        storage.isCanonicalRelativePath(rawPath)

    override fun resolveToReadablePath(rawPath: String?): String? =
        storage.resolveToReadablePath(rawPath)

    override suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String = storage.storeMediaBytes(bytes, subFolder, extension)

    override suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean
    ): String? = storage.importExistingFile(sourcePath, subFolder, defaultExtension, deleteSource)

    override suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String? =
        storage.migrateToCanonicalRelativeIfNeeded(rawPath)

    override fun createStagingFilePath(prefix: String, extension: String): String =
        storage.createStagingFilePath(prefix, extension)
}
