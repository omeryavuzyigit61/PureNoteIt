package com.notes.purenoteit.data.storage

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked AES-256-GCM file encryption for media at rest (`PNM1` envelope).
 *
 * Layout: `"PNM1" | nonce(8) | plaintextLength(8, big-endian) | chunk*`, where every chunk is the GCM output
 * (ciphertext + 16-byte tag) of up to [CHUNK_BYTES] plaintext bytes. Each chunk uses IV `nonce | chunkIndex(4)` and
 * authenticates `aad | chunkIndex | isLastChunk | plaintextLength`, so reordering, truncation, extension and moving a
 * file to another path are all detected. Chunking keeps memory flat for large audio/image files (Android's GCM
 * implementation buffers the whole message otherwise).
 */
object MediaDiskCipher {
    const val KEY_BYTES = 32
    const val CHUNK_BYTES = 64 * 1024

    private val MAGIC = byteArrayOf('P'.code.toByte(), 'N'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())
    private const val NONCE_BYTES = 8
    private const val TAG_BYTES = 16
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + NONCE_BYTES + 8
    private val secureRandom = SecureRandom()

    /** True if [file] starts with the `PNM1` envelope header. */
    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < HEADER_BYTES) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(MAGIC.size)
                var read = 0
                while (read < magic.size) {
                    val n = input.read(magic, read, magic.size - read)
                    if (n < 0) return false
                    read += n
                }
                magic.contentEquals(MAGIC)
            }
        } catch (_: IOException) {
            false
        }
    }

    /** Encrypts [source] into [target] (overwritten). [aad] binds the ciphertext to its logical path. */
    fun encryptFile(source: File, target: File, key: ByteArray, aad: String) {
        val keySpec = keySpecOf(key)
        val plainLength = source.length()
        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val chunkCount = chunkCountOf(plainLength)
        target.parentFile?.mkdirs()
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(target))).use { out ->
                out.write(MAGIC)
                out.write(nonce)
                out.writeLong(plainLength)
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(CHUNK_BYTES)
                    for (index in 0 until chunkCount) {
                        val expected = chunkPlainSize(plainLength, index)
                        readFully(input, buffer, expected)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, ivOf(nonce, index)))
                        cipher.updateAAD(aadOf(aad, index, index == chunkCount - 1, plainLength))
                        out.write(cipher.doFinal(buffer, 0, expected))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    /**
     * Decrypts [source] into [target] (overwritten). Throws [GeneralSecurityException] if the key, [aad] or
     * data is wrong/tampered; a partially written [target] is deleted before the exception propagates.
     */
    fun decryptFile(source: File, target: File, key: ByteArray, aad: String) {
        val keySpec = keySpecOf(key)
        target.parentFile?.mkdirs()
        try {
            DataInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) throw IOException("Not a PNM1 media envelope")
                val nonce = ByteArray(NONCE_BYTES)
                input.readFully(nonce)
                val plainLength = input.readLong()
                if (plainLength < 0) throw GeneralSecurityException("Invalid media length")
                val chunkCount = chunkCountOf(plainLength)
                val expectedFileSize = HEADER_BYTES.toLong() + plainLength + chunkCount.toLong() * TAG_BYTES
                if (source.length() != expectedFileSize) {
                    throw GeneralSecurityException("Media envelope size mismatch")
                }
                FileOutputStream(target).use { fos ->
                    val buffer = ByteArray(CHUNK_BYTES + TAG_BYTES)
                    for (index in 0 until chunkCount) {
                        val cipherLen = chunkPlainSize(plainLength, index) + TAG_BYTES
                        try {
                            input.readFully(buffer, 0, cipherLen)
                        } catch (_: EOFException) {
                            throw GeneralSecurityException("Truncated media envelope")
                        }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, ivOf(nonce, index)))
                        cipher.updateAAD(aadOf(aad, index, index == chunkCount - 1, plainLength))
                        val plain = cipher.doFinal(buffer, 0, cipherLen)
                        fos.write(plain)
                        plain.fill(0)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            if (e is GeneralSecurityException || e is IOException) throw e
            throw GeneralSecurityException("Media decryption failed", e)
        }
    }

    /** Atomically replaces plaintext [file] with its encrypted form. No-op if already encrypted. */
    fun encryptInPlace(file: File, key: ByteArray, aad: String) {
        if (isEncrypted(file)) return
        val temp = File(file.parentFile, ".enc_${file.name}.tmp")
        try {
            encryptFile(file, temp, key, aad)
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun keySpecOf(key: ByteArray): SecretKeySpec {
        require(key.size == KEY_BYTES) { "Media key must be $KEY_BYTES bytes" }
        return SecretKeySpec(key, "AES")
    }

    private fun chunkCountOf(plainLength: Long): Int =
        ((plainLength + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()

    private fun chunkPlainSize(plainLength: Long, index: Int): Int =
        minOf(CHUNK_BYTES.toLong(), plainLength - index.toLong() * CHUNK_BYTES).toInt()

    private fun ivOf(nonce: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(12).put(nonce).putInt(index).array()

    private fun aadOf(aad: String, index: Int, isLast: Boolean, plainLength: Long): ByteArray {
        val path = aad.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(path.size + 1 + 4 + 1 + 8)
            .put(path)
            .put(0)
            .putInt(index)
            .put(if (isLast) 1 else 0)
            .putLong(plainLength)
            .array()
    }

    private fun readFully(input: FileInputStream, buffer: ByteArray, length: Int) {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) throw IOException("Source file shrank while encrypting")
            read += n
        }
    }
}
