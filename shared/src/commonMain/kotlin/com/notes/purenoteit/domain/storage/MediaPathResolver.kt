package com.notes.purenoteit.domain.storage

/**
 * Pure domain contract for platform-independent, content-addressed relative media paths
 * (`media/<subFolder>/<sha256>.<ext>`) with transparent legacy absolute-path compatibility.
 */
interface MediaPathResolver {
    /** Returns true if [rawPath] already uses the canonical `media/...` relative format without traversal. */
    fun isCanonicalRelativePath(rawPath: String?): Boolean

    /**
     * Resolves a canonical relative path (`media/...`) or a legacy app-local path into a safe,
     * sandbox-validated readable filesystem path on the current device.
     * Returns `null` for blank inputs, path-traversal attempts (`..`), or paths escaping app storage.
     */
    fun resolveToReadablePath(rawPath: String?): String?

    /**
     * Persists [bytes] into content-addressed storage (`media/<subFolder>/<sha256>.<ext>`)
     * and returns the canonical relative path. Deduplicates identical payloads automatically.
     */
    suspend fun storeMediaBytes(
        bytes: ByteArray,
        subFolder: String,
        extension: String
    ): String

    /**
     * Streams an existing file from [sourcePath] into content-addressed storage
     * (`media/<subFolder>/<sha256>.<ext>`) and returns the canonical relative path,
     * or `null` if [sourcePath] is unreadable or missing.
     */
    suspend fun importExistingFile(
        sourcePath: String,
        subFolder: String,
        defaultExtension: String,
        deleteSource: Boolean = false
    ): String?

    /**
     * Converts a legacy local path into its canonical `media/...` path if the file exists locally.
     * Already-canonical paths are normalized and returned directly. Missing files return [rawPath].
     */
    suspend fun migrateToCanonicalRelativeIfNeeded(rawPath: String?): String?

    /** Allocates a temporary staging file path inside the app's cache directory (e.g. for audio capture). */
    fun createStagingFilePath(prefix: String, extension: String): String
}
