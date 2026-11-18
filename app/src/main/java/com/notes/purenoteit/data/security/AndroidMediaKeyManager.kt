package com.notes.purenoteit.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the 256-bit key that encrypts media files at rest ([com.notes.purenoteit.data.storage.MediaDiskCipher]).
 *
 * The raw key is random, never leaves the process unwrapped and is persisted only as an AES-256-GCM blob wrapped by
 * a non-exportable Android Keystore key (`noBackupFilesDir`, excluded from cloud backup/transfer). If the Keystore
 * key is lost or invalidated the blob cannot be unwrapped: a fresh key is generated and previously encrypted media
 * becomes unreadable (it resolves to "missing" in the UI) -- there is never a plaintext fallback.
 */
internal object AndroidMediaKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "purenoteit_media_master_v1"
    private const val WRAPPED_KEY_FILENAME = "purenote_media_key.bin"
    private const val RAW_KEY_BYTES = 32
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val lock = Any()

    @Volatile
    private var cachedKey: ByteArray? = null

    /** Returns the 32-byte media key, creating and persisting it on first use. */
    fun getOrCreateMediaKey(context: Context): ByteArray {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }
            val keyFile = wrappedKeyFile(context)
            val loaded = if (keyFile.isFile) unwrapOrNull(keyFile) else null
            val key = loaded ?: createAndPersist(keyFile)
            cachedKey = key
            return key
        }
    }

    private fun unwrapOrNull(keyFile: File): ByteArray? {
        return try {
            val blob = keyFile.readBytes()
            if (blob.size <= GCM_IV_LENGTH) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateMasterKey(),
                GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, GCM_IV_LENGTH))
            )
            val raw = cipher.doFinal(blob, GCM_IV_LENGTH, blob.size - GCM_IV_LENGTH)
            if (raw.size == RAW_KEY_BYTES) raw else null
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun createAndPersist(keyFile: File): ByteArray {
        deleteMasterKeyQuietly()
        val raw = ByteArray(RAW_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val blob = cipher.iv + cipher.doFinal(raw)
        val temp = File(keyFile.parentFile, "$WRAPPED_KEY_FILENAME.tmp")
        temp.writeBytes(blob)
        Files.move(
            temp.toPath(),
            keyFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
        return raw
    }

    private fun wrappedKeyFile(context: Context): File {
        val dir = ContextCompat.getNoBackupFilesDir(context) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return File(dir, WRAPPED_KEY_FILENAME)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun deleteMasterKeyQuietly() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) keyStore.deleteEntry(MASTER_KEY_ALIAS)
        } catch (_: Exception) {
        }
    }
}
