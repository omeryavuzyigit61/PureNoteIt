package com.notes.purenoteit.data.storage

import com.notes.purenoteit.domain.storage.EncryptedMediaStore
import com.notes.purenoteit.domain.storage.MediaPathResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Keeps every file under `<rootFilesDir>/media/` encrypted at rest ([MediaDiskCipher], `PNM1` envelope) while
 * remaining a drop-in [MediaPathResolver]:
 *  - writes (`storeMediaBytes`, `importExistingFile`, `migrateToCanonicalRelativeIfNeeded`) encrypt in place right away;
 *  - `resolveToReadablePath` returns a path to a decrypted copy inside [cacheDir] (`media_plain/`) for encrypted
 *    files, so Coil, MediaPlayer, BitmapFactory, export and sync keep working with plain `File` paths;
 *  - an undecryptable file resolves to `null` (never to ciphertext, never to a plaintext fallback);
 *  - [encryptPendingPlaintextMedia] converts legacy/restored plaintext files, [clearDecryptedCache] wipes the copies.
 */
internal class EncryptedMediaPathStorage(
    private val delegate: MediaPathResolver,
    rootFilesDir: File,
    cacheDir: File,
    private val keyProvider: () -> ByteArray?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EncryptedMediaStore {

    private val filesRoot: File = rootFilesDir.canonicalFile
    private val mediaRoot: File = File(filesRoot, MEDIA_ROOT)
    private val plainCacheDir: File = File(cacheDir, PLAIN_CACHE_DIR)
    private val lock = Any()

    override fun isCanonicalRelativePath(rawPath: String?): Boolean =
        delegate.isCanonicalRelativePath(rawPath)

    override fun resolveToReadablePath(rawPath: String?): String? {
        val resolved = delegate.resolveToReadablePath(rawPath) ?: return null
        return readableViewOf(resolved)
    }

    override suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String = withContext(ioDispatcher) {
        val canonical = delegate.storeMediaBytes(bytes, subFolder, extension)
        protectOrThrow(canonical)
        canonical
    }

    override suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean
    ): String? = withContext(ioDispatcher) {
        val canonical = delegate.importExistingFile(sourcePath, subFolder, defaultExtension, deleteSource)
        if (canonical != null) protectOrThrow(canonical)
        canonical
    }

    override suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String? =
        withContext(ioDispatcher) {
            val result = delegate.migrateToCanonicalRelativeIfNeeded(rawPath)
            if (result != null && delegate.isCanonicalRelativePath(result)) {
                runCatching { protectOrThrow(result) }
            }
            result
        }

    override fun createStagingFilePath(prefix: String, extension: String): String =
        delegate.createStagingFilePath(prefix, extension)

    override fun encryptPendingPlaintextMedia(): Int {
        if (!mediaRoot.isDirectory) return 0
        val key = keyProvider() ?: return 0
        var converted = 0
        mediaRoot.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                val logical = logicalPathOf(file) ?: return@forEach
                try {
                    if (!MediaDiskCipher.isEncrypted(file)) {
                        MediaDiskCipher.encryptInPlace(file, key, logical)
                        converted++
                    }
                } catch (_: Exception) {
                    // Leave the file untouched; it is retried on the next sweep.
                }
            }
        return converted
    }

    override fun clearDecryptedCache() {
        synchronized(lock) {
            if (plainCacheDir.isDirectory) plainCacheDir.deleteRecursively()
        }
    }

    private fun protectOrThrow(canonicalOrPath: String) {
        val path = delegate.resolveToReadablePath(canonicalOrPath) ?: return
        val file = File(path)
        val logical = logicalPathOf(file) ?: return
        if (!file.isFile || MediaDiskCipher.isEncrypted(file)) return
        val key = keyProvider() ?: throw IllegalStateException("Media encryption key unavailable")
        MediaDiskCipher.encryptInPlace(file, key, logical)
    }

    private fun readableViewOf(path: String): String? {
        if (path.startsWith("content://", ignoreCase = true)) return path
        val file = File(path)
        val logical = logicalPathOf(file) ?: return path
        if (!MediaDiskCipher.isEncrypted(file)) return path
        val key = keyProvider() ?: return null
        synchronized(lock) {
            plainCacheDir.mkdirs()
            val cached = File(plainCacheDir, logical.replace("/", "__"))
            if (cached.isFile && cached.lastModified() >= file.lastModified()) return cached.path
            val temp = File(plainCacheDir, ".dec_${UUID.randomUUID()}.tmp")
            return try {
                MediaDiskCipher.decryptFile(file, temp, key, logical)
                Files.move(
                    temp.toPath(),
                    cached.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
                cached.path
            } catch (_: Exception) {
                null
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    /** Logical path (`media/<sub>/<name>`) of [file] if it lives inside the media root, else null. */
    private fun logicalPathOf(file: File): String? {
        return try {
            val canonical = file.canonicalFile
            val rootPrefix = mediaRoot.path + File.separator
            if (!canonical.path.startsWith(rootPrefix)) return null
            canonical.relativeTo(filesRoot).invariantSeparatorsPath
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MEDIA_ROOT = "media"
        const val PLAIN_CACHE_DIR = "media_plain"
    }
}
