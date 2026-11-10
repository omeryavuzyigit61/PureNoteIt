package com.notes.purenoteit.desktop.data.storage

import com.notes.purenoteit.domain.storage.MediaPathResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Transparent PC disk-encryption wrapper over `:shared`'s [MediaPathResolver].
 * Stores files at canonical SHA-256 paths (`media/<subFolder>/<sha256(plain)>.<ext>`)
 * encrypted with AES-256-GCM (`PDM1` envelope + relative-path AAD) while exposing decrypted
 * bytes purely in RAM to the UI and `SyncEngine`.
 */
internal class DesktopEncryptedMediaStorage(
    private val delegate: MediaPathResolver,
    private val mediaKeyProvider: () -> ByteArray?,
    private val rootFilesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaPathResolver {

    private val secureRandom = SecureRandom()

    override fun isCanonicalRelativePath(rawPath: String?): Boolean =
        delegate.isCanonicalRelativePath(rawPath)

    override fun resolveToReadablePath(rawPath: String?): String? =
        delegate.resolveToReadablePath(rawPath)

    override suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String = withContext(ioDispatcher) {
        val canonicalPath = delegate.storeMediaBytes(bytes, subFolder, extension)
        val keyBytes = mediaKeyProvider()
        if (keyBytes != null) {
            val targetPath = delegate.resolveToReadablePath(canonicalPath)
            if (targetPath != null) {
                val targetFile = File(targetPath)
                val encrypted = encryptForDisk(bytes, keyBytes, canonicalPath)
                writeFileAtomically(targetFile, encrypted)
            }
        }
        canonicalPath
    }

    override suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean
    ): String? = withContext(ioDispatcher) {
        val src = File(sourcePath)
        if (!src.isFile) return@withContext null
        val plain = src.readBytes()
        val canonical = storeMediaBytes(plain, subFolder, defaultExtension)
        if (deleteSource) {
            runCatching { src.delete() }
        }
        canonical
    }

    override suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String? =
        delegate.migrateToCanonicalRelativeIfNeeded(rawPath)

    override fun createStagingFilePath(prefix: String, extension: String): String =
        delegate.createStagingFilePath(prefix, extension)

    suspend fun readDecryptedBytes(rawPath: String?): ByteArray? = withContext(ioDispatcher) {
        if (rawPath.isNullOrBlank()) return@withContext null
        val resolved = delegate.resolveToReadablePath(rawPath) ?: return@withContext null
        val file = File(resolved)
        if (!file.exists() || !file.isFile) return@withContext null
        val diskBytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return@withContext null
        }

        if (!hasEncryptedHeader(diskBytes)) {
            return@withContext diskBytes
        }

        val keyBytes = mediaKeyProvider() ?: return@withContext null
        decryptFromDisk(diskBytes, keyBytes, rawPath)
    }

    suspend fun reencryptAllStoredMedia(
        oldKey: ByteArray?,
        newKey: ByteArray
    ) = withContext(ioDispatcher) {
        val mediaRoot = File(rootFilesDir, "media")
        if (!mediaRoot.isDirectory) return@withContext
        val allFiles = mediaRoot.walkTopDown().filter { it.isFile }.toList()
        for (file in allFiles) {
            val relPath = "media/" + file.relativeTo(mediaRoot).invariantSeparatorsPath
            val diskBytes = try {
                file.readBytes()
            } catch (_: Exception) {
                continue
            }
            val plainBytes = if (hasEncryptedHeader(diskBytes)) {
                val okKey = oldKey ?: continue
                decryptFromDisk(diskBytes, okKey, relPath)
                    ?: throw IllegalStateException("Existing disk media decryption failed during key rotation")
            } else {
                diskBytes
            }
            val reencrypted = encryptForDisk(plainBytes, newKey, relPath)
            plainBytes.fill(0)
            writeFileAtomically(file, reencrypted)
        }
    }

    private fun hasEncryptedHeader(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size + IV_LEN + TAG_BYTES) return false
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) return false
        }
        return true
    }

    private fun encryptForDisk(plainBytes: ByteArray, keyBytes: ByteArray, aadPath: String): ByteArray {
        val keyCopy = keyBytes.copyOf(32)
        val iv = ByteArray(IV_LEN).also { secureRandom.nextBytes(it) }
        val keySpec = SecretKeySpec(keyCopy, "AES")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(normalizeAad(aadPath))
            val encrypted = cipher.doFinal(plainBytes)
            ByteArray(MAGIC.size + IV_LEN + encrypted.size).apply {
                System.arraycopy(MAGIC, 0, this, 0, MAGIC.size)
                System.arraycopy(iv, 0, this, MAGIC.size, IV_LEN)
                System.arraycopy(encrypted, 0, this, MAGIC.size + IV_LEN, encrypted.size)
            }
        } finally {
            keyCopy.fill(0)
        }
    }

    private fun decryptFromDisk(diskBytes: ByteArray, keyBytes: ByteArray, aadPath: String): ByteArray? {
        if (!hasEncryptedHeader(diskBytes)) return diskBytes
        val keyCopy = keyBytes.copyOf(32)
        val iv = diskBytes.copyOfRange(MAGIC.size, MAGIC.size + IV_LEN)
        val cipherData = diskBytes.copyOfRange(MAGIC.size + IV_LEN, diskBytes.size)
        val keySpec = SecretKeySpec(keyCopy, "AES")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(normalizeAad(aadPath))
            cipher.doFinal(cipherData)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            keyCopy.fill(0)
        }
    }

    private fun normalizeAad(rawPath: String): ByteArray =
        rawPath.replace('\\', '/').trimStart('/').toByteArray(Charsets.UTF_8)

    private fun writeFileAtomically(targetFile: File, data: ByteArray) {
        targetFile.parentFile?.mkdirs()
        val tmp = File(targetFile.parentFile, "${targetFile.name}.enc.tmp")
        tmp.writeBytes(data)
        try {
            Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'D'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())
        private const val IV_LEN = 12
        private const val TAG_BYTES = 16
        private const val GCM_TAG_BITS = 128

        fun isEncryptedMediaFile(file: File): Boolean {
            if (!file.isFile || file.length() < MAGIC.size + IV_LEN + TAG_BYTES) return false
            val head = ByteArray(MAGIC.size)
            file.inputStream().use { ins ->
                if (ins.read(head) < MAGIC.size) return false
            }
            return MessageDigest.isEqual(head, MAGIC)
        }
    }
}
