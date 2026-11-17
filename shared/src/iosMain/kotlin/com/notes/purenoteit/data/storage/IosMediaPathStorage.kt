package com.notes.purenoteit.data.storage

import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.sync.sha256Hex
import com.notes.purenoteit.sync.sharedRandomUuid
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * iOS content-addressed media storage (`<rootFilesPath>/media/<subFolder>/<sha256>.<ext>`).
 * Enforces strict CWE-22 path traversal checks inside sandboxed app directories.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosMediaPathStorage(
    private val rootFilesPath: String,
    private val cachePath: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaPathResolver {

    private val fileManager = NSFileManager.defaultManager
    private val normalizedRoot = rootFilesPath.trimEnd('/')
    private val normalizedCache = cachePath?.trimEnd('/')
    private val mediaRoot = "$normalizedRoot/$MEDIA_ROOT"

    override fun isCanonicalRelativePath(rawPath: String?): Boolean {
        val trimmed = rawPath?.trim()?.replace('\\', '/') ?: return false
        return trimmed.startsWith("$MEDIA_ROOT/") && hasNoUnsafeSegments(trimmed)
    }

    override fun resolveToReadablePath(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val trimmed = rawPath.trim()
        if (trimmed.indexOf('\u0000') >= 0) return null
        val cleaned = trimmed.removePrefix("file://").removePrefix("FILE://").replace('\\', '/')

        if (cleaned.startsWith("$MEDIA_ROOT/")) {
            if (!hasNoUnsafeSegments(cleaned)) return null
            if (cleaned.split('/').size < 3) return null
            return "$normalizedRoot/$cleaned"
        }

        if (!hasNoUnsafeSegments(cleaned)) return null
        if (cleaned.startsWith("/")) {
            val isAllowed = cleaned.startsWith("$normalizedRoot/") ||
                (normalizedCache != null && cleaned.startsWith("$normalizedCache/"))
            return if (isAllowed && fileManager.fileExistsAtPath(cleaned)) cleaned else null
        }

        val candidate = "$normalizedRoot/$cleaned"
        return if (fileManager.fileExistsAtPath(candidate)) candidate else null
    }

    override suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String = withContext(ioDispatcher) {
        val safeFolder = sanitizeSegment(subFolder, "files")
        val safeExt = sanitizeSegment(extension, "bin")
        val hash = sha256Hex(bytes)
        val folderPath = "$mediaRoot/$safeFolder"
        ensureDir(folderPath)
        val relPath = "$MEDIA_ROOT/$safeFolder/$hash.$safeExt"
        val fullPath = "$normalizedRoot/$relPath"
        if (!fileManager.fileExistsAtPath(fullPath)) {
            bytes.toNSData().writeToFile(fullPath, atomically = true)
        }
        relPath
    }

    override suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean
    ): String? = withContext(ioDispatcher) {
        val cleanSource = sourcePath.removePrefix("file://")
        if (!fileManager.fileExistsAtPath(cleanSource)) return@withContext null
        val data = NSData.dataWithContentsOfFile(cleanSource) ?: return@withContext null
        val bytes = data.toByteArray()
        val ext = cleanSource.substringAfterLast('.', defaultExtension)
        val canonical = storeMediaBytes(bytes, subFolder, ext)
        if (deleteSource) {
            fileManager.removeItemAtPath(cleanSource, error = null)
        }
        canonical
    }

    override suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return rawPath
        if (isCanonicalRelativePath(rawPath)) return rawPath.trim().replace('\\', '/')
        val resolved = resolveToReadablePath(rawPath) ?: return rawPath
        return importExistingFile(resolved, "migrated", "bin", deleteSource = false) ?: rawPath
    }

    override fun createStagingFilePath(prefix: String, extension: String): String {
        val dir = normalizedCache ?: "$normalizedRoot/cache"
        ensureDir(dir)
        val safePrefix = sanitizeSegment(prefix, "stage")
        val safeExt = sanitizeSegment(extension, "tmp")
        return "$dir/${safePrefix}_${sharedRandomUuid()}.$safeExt"
    }

    private fun ensureDir(path: String) {
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }

    private fun hasNoUnsafeSegments(path: String): Boolean {
        val segments = path.split('/')
        return segments.none { it == ".." || it == "." || it.isEmpty() }
    }

    private fun sanitizeSegment(raw: String, fallback: String): String {
        val clean = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
        return clean.ifEmpty { fallback }
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val len = length.toInt()
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
        return out
    }

    companion object {
        private const val MEDIA_ROOT = "media"
    }
}
