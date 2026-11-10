package com.notes.purenoteit.data.storage

import com.notes.purenoteit.domain.storage.MediaPathResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * JVM content-addressed media storage implementation backed by `<rootFilesDir>/media/<subFolder>/<sha256>.<ext>`.
 * Enforces strict CWE-22 canonical boundary checks and resolves legacy absolute paths across app versions.
 */
internal class LocalMediaPathStorage(
    private val rootFilesDir: File,
    private val cacheDir: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : MediaPathResolver {

    private val mediaRootDir: File by lazy {
        File(rootFilesDir, MEDIA_ROOT).apply { if (!exists()) mkdirs() }
    }

    override fun isCanonicalRelativePath(rawPath: String?): Boolean {
        val normalized = normalizeRelativeCandidate(rawPath) ?: return false
        return CANONICAL_PATH_REGEX.matches(normalized) ||
            (normalized.startsWith("$MEDIA_ROOT/") && hasNoUnsafeSegments(normalized))
    }

    override fun resolveToReadablePath(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val trimmed = rawPath.trim()
        if (trimmed.indexOf('\u0000') >= 0) return null

        // Content URIs are temporary Android picker handles prior to copy.
        if (trimmed.startsWith("content://", ignoreCase = true)) {
            return trimmed
        }

        val withoutFileScheme = if (trimmed.startsWith("file://", ignoreCase = true)) {
            trimmed.removePrefix("file://").removePrefix("FILE://")
        } else {
            trimmed
        }

        val normalizedRelative = withoutFileScheme.replace('\\', '/')
        if (normalizedRelative.startsWith("$MEDIA_ROOT/")) {
            if (!hasNoUnsafeSegments(normalizedRelative)) return null
            if (normalizedRelative.split('/').size < 3) return null
            val candidate = File(rootFilesDir, normalizedRelative)
            val safe = ensureInsideDirectory(mediaRootDir, candidate) ?: return null
            return safe.canonicalPath
        }

        val fileObj = File(withoutFileScheme)
        val isAbsoluteAnyOs = fileObj.isAbsolute ||
            normalizedRelative.startsWith("/") ||
            WINDOWS_DRIVE_REGEX.containsMatchIn(normalizedRelative)
        if (isAbsoluteAnyOs) {
            if (fileObj.isAbsolute) {
                ensureInsideDirectory(rootFilesDir, fileObj)?.takeIf { it.exists() && it.isFile }?.let {
                    return it.canonicalPath
                }
                cacheDir?.let { cache ->
                    ensureInsideDirectory(cache, fileObj)?.takeIf { it.exists() && it.isFile }?.let {
                        return it.canonicalPath
                    }
                }
            }
            return resolveLegacyAliasInsideRoot(normalizedRelative)
        }

        // Relative legacy path (e.g. "drawings/drawing_1.png")
        if (!hasNoUnsafeSegments(normalizedRelative)) return null
        val legacyCandidate = File(rootFilesDir, normalizedRelative)
        return ensureInsideDirectory(rootFilesDir, legacyCandidate)
            ?.takeIf { it.exists() && it.isFile }
            ?.canonicalPath
            ?: resolveLegacyAliasInsideRoot(normalizedRelative)
    }

    override suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String = withContext(ioDispatcher) {
        val safeFolder = sanitizeSubFolder(subFolder)
        val safeExt = sanitizeExtension(extension)
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val targetDir = File(mediaRootDir, safeFolder).apply { if (!exists()) mkdirs() }
        val targetFile = ensureInsideDirectory(mediaRootDir, File(targetDir, "$sha256.$safeExt"))
            ?: throw SecurityException("Invalid canonical target media path")

        if (!targetFile.exists() || targetFile.length() != bytes.size.toLong()) {
            val tempFile = File(targetDir, ".tmp_${UUID.randomUUID()}.$safeExt")
            try {
                tempFile.writeBytes(bytes)
                if (!targetFile.exists()) {
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                    }
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
        "$MEDIA_ROOT/$safeFolder/$sha256.$safeExt"
    }

    override suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean
    ): String? = withContext(ioDispatcher) {
        if (sourcePath.isBlank() || sourcePath.indexOf('\u0000') >= 0) return@withContext null
        val normalizedSrc = if (sourcePath.startsWith("file://", ignoreCase = true)) {
            sourcePath.substring(7)
        } else {
            sourcePath
        }
        val sourceFile = File(normalizedSrc)
        if (!sourceFile.exists() || !sourceFile.isFile) return@withContext null

        val safeFolder = sanitizeSubFolder(subFolder)
        val extFromFile = sourceFile.extension.takeIf { it.isNotBlank() } ?: defaultExtension
        val safeExt = sanitizeExtension(extFromFile)

        val targetDir = File(mediaRootDir, safeFolder).apply { if (!exists()) mkdirs() }
        val stagingTemp = File(targetDir, ".import_${UUID.randomUUID()}.tmp")

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            DigestInputStream(sourceFile.inputStream().buffered(), digest).use { dis ->
                stagingTemp.outputStream().buffered().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = dis.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        totalBytes += read
                    }
                }
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val targetFile = ensureInsideDirectory(mediaRootDir, File(targetDir, "$sha256.$safeExt"))
                ?: throw SecurityException("Invalid canonical target media path")

            if (!targetFile.exists() || targetFile.length() != totalBytes) {
                if (!stagingTemp.renameTo(targetFile)) {
                    stagingTemp.copyTo(targetFile, overwrite = true)
                }
            }
            if (deleteSource && sourceFile.canonicalPath != targetFile.canonicalPath) {
                sourceFile.delete()
            }
            "$MEDIA_ROOT/$safeFolder/$sha256.$safeExt"
        } finally {
            if (stagingTemp.exists()) stagingTemp.delete()
        }
    }

    override suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return rawPath
        if (isCanonicalRelativePath(rawPath)) {
            return normalizeRelativeCandidate(rawPath)
        }
        val readable = resolveToReadablePath(rawPath) ?: return rawPath
        if (readable.startsWith("content://", ignoreCase = true)) return rawPath
        val subFolder = inferSubFolder(rawPath)
        val defaultExt = when (subFolder) {
            FOLDER_AUDIO -> "m4a"
            FOLDER_DRAWINGS -> "png"
            FOLDER_IMAGES -> "jpg"
            else -> "bin"
        }
        return importExistingFile(readable, subFolder, defaultExt, deleteSource = false) ?: rawPath
    }

    override fun createStagingFilePath(prefix: String, extension: String): String {
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "stage" }
        val safeExt = sanitizeExtension(extension)
        val stagingRoot = cacheDir ?: File(rootFilesDir, "tmp_staging").apply { if (!exists()) mkdirs() }
        if (!stagingRoot.exists()) stagingRoot.mkdirs()
        return File(stagingRoot, "${safePrefix}_${UUID.randomUUID()}.$safeExt").canonicalPath
    }

    private fun resolveLegacyAliasInsideRoot(normalizedPath: String): String? {
        val segments = normalizedPath.split('/').filter { it.isNotBlank() }
        if (segments.any { it == ".." || it == "." || it.indexOf('\u0000') >= 0 }) return null
        val rawName = segments.lastOrNull() ?: return null

        val candidateDirs = buildList {
            for ((legacyName, canonicalSub) in LEGACY_FOLDER_MAP) {
                if (segments.any { it.equals(legacyName, ignoreCase = true) }) {
                    add(File(rootFilesDir, "$legacyName/$rawName"))
                    add(File(mediaRootDir, "$canonicalSub/$rawName"))
                }
            }
            add(File(mediaRootDir, "${inferSubFolder(normalizedPath)}/$rawName"))
        }

        for (candidate in candidateDirs) {
            val safe = ensureInsideDirectory(rootFilesDir, candidate) ?: continue
            if (safe.exists() && safe.isFile) {
                return safe.canonicalPath
            }
        }
        return null
    }

    private fun inferSubFolder(rawPath: String): String {
        val lower = rawPath.replace('\\', '/').lowercase()
        return when {
            lower.contains("/$FOLDER_DRAWINGS/") || lower.contains("drawing_") -> FOLDER_DRAWINGS
            lower.contains("/recordings/") || lower.contains("/$FOLDER_AUDIO/") ||
                lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") -> FOLDER_AUDIO
            lower.contains("/shared_images/") || lower.contains("/$FOLDER_IMAGES/") ||
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") -> FOLDER_IMAGES
            else -> FOLDER_FILES
        }
    }

    private fun normalizeRelativeCandidate(rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val cleaned = rawPath.trim().replace('\\', '/')
        if (cleaned.indexOf('\u0000') >= 0) return null
        return cleaned.trimStart('/')
    }

    private fun hasNoUnsafeSegments(normalizedPath: String): Boolean {
        if (normalizedPath.indexOf('\u0000') >= 0) return false
        val parts = normalizedPath.split('/')
        return parts.none { it == ".." || it == "." || it.isEmpty() }
    }

    private fun ensureInsideDirectory(baseDir: File, candidate: File): File? {
        return try {
            val baseCanonical = baseDir.canonicalPath
            val targetCanonical = candidate.canonicalPath
            val prefix = if (baseCanonical.endsWith(File.separator)) {
                baseCanonical
            } else {
                baseCanonical + File.separator
            }
            if (targetCanonical == baseCanonical || targetCanonical.startsWith(prefix)) {
                candidate
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeSubFolder(subFolder: String): String {
        val normalized = subFolder.trim().lowercase()
        return if (normalized in ALLOWED_SUBFOLDERS) normalized else FOLDER_FILES
    }

    private fun sanitizeExtension(extension: String): String {
        val clean = extension.trim().lowercase().trimStart('.')
            .replace(Regex("[^a-z0-9]"), "")
            .take(8)
        return clean.ifBlank { "bin" }
    }

    companion object {
        const val MEDIA_ROOT = "media"
        const val FOLDER_IMAGES = "images"
        const val FOLDER_DRAWINGS = "drawings"
        const val FOLDER_AUDIO = "audio"
        const val FOLDER_FILES = "files"

        private val ALLOWED_SUBFOLDERS = setOf(
            FOLDER_IMAGES,
            FOLDER_DRAWINGS,
            FOLDER_AUDIO,
            FOLDER_FILES
        )

        private val LEGACY_FOLDER_MAP = mapOf(
            "drawings" to FOLDER_DRAWINGS,
            "shared_images" to FOLDER_IMAGES,
            "recordings" to FOLDER_AUDIO,
            "images" to FOLDER_IMAGES,
            "audio" to FOLDER_AUDIO
        )

        private val CANONICAL_PATH_REGEX =
            Regex("^media/(images|drawings|audio|files)/[a-f0-9]{64}\\.[a-z0-9]{1,8}$")

        private val WINDOWS_DRIVE_REGEX = Regex("^[a-zA-Z]:/")
    }
}
