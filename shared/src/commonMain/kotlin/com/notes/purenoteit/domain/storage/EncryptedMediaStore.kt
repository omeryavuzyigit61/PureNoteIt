package com.notes.purenoteit.domain.storage

/**
 * Optional capability of a [MediaPathResolver] that keeps media files encrypted at rest.
 * Both functions are blocking and must be called from a background (IO) thread.
 */
interface MediaAtRestEncryptor {
    /**
     * Encrypts any media file under `media/` that is still stored in plaintext (legacy files,
     * freshly restored backups). Returns the number of files converted.
     */
    fun encryptPendingPlaintextMedia(): Int

    /** Deletes every temporary decrypted copy created for UI/player/export access. */
    fun clearDecryptedCache()
}

/** A media resolver whose files are encrypted at rest and decrypted on demand into a private cache. */
interface EncryptedMediaStore : MediaPathResolver, MediaAtRestEncryptor
