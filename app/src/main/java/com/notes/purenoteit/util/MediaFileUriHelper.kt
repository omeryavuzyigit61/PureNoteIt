package com.notes.purenoteit.util

import android.content.Context
import com.notes.purenoteit.domain.storage.MediaPathResolver
import java.io.File

/**
 * Shared path resolution helper for Android UI & export layers.
 * Resolves canonical relative `media/<subFolder>/<sha256>.<ext>` paths as well as legacy absolute paths
 * while enforcing strict CWE-22 canonical boundary verification inside `context.filesDir`.
 */
object MediaFileUriHelper {

    fun resolveReadablePath(
        context: Context,
        rawPath: String?,
        mediaPathResolver: MediaPathResolver? = null
    ): String? {
        if (rawPath.isNullOrBlank()) return null
        if (mediaPathResolver != null) {
            return mediaPathResolver.resolveToReadablePath(rawPath)
        }

        val trimmed = rawPath.trim()
        if (trimmed.indexOf('\u0000') >= 0) return null
        if (trimmed.startsWith("content://", ignoreCase = true)) return trimmed

        val normalized = trimmed.replace('\\', '/').trimStart('/')
        if (normalized.startsWith("media/")) {
            val parts = normalized.split('/')
            if (parts.size < 3 || parts.any { it == ".." || it == "." || it.isEmpty() }) {
                return null
            }
            return try {
                val filesCanonical = context.filesDir.canonicalFile
                val candidate = File(filesCanonical, normalized).canonicalFile
                val prefix = if (filesCanonical.path.endsWith(File.separator)) {
                    filesCanonical.path
                } else {
                    filesCanonical.path + File.separator
                }
                if (candidate.path.startsWith(prefix)) candidate.path else null
            } catch (_: Exception) {
                null
            }
        }

        return trimmed
    }

    fun resolveReadableFile(
        context: Context,
        rawPath: String?,
        mediaPathResolver: MediaPathResolver? = null
    ): File? = resolveReadablePath(context, rawPath, mediaPathResolver)?.let { File(it) }
}
