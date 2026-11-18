package com.notes.purenoteit

import com.notes.purenoteit.data.storage.MediaDiskCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom

class EncryptedMediaStorageTest {

    private lateinit var root: File
    private lateinit var files: File
    private lateinit var cache: File
    private val key = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("pn_media_test").toFile()
        files = File(root, "files").apply { mkdirs() }
        cache = File(root, "cache").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store(keyProvider: () -> ByteArray? = { key }) =
        SharedStorageFactory.createEncryptedMediaStore(files, cache, keyProvider, Dispatchers.Unconfined)

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    @Test
    fun cipherRoundTripsAcrossChunkBoundaries() {
        for (size in listOf(0, 1, 100, MediaDiskCipher.CHUNK_BYTES - 1, MediaDiskCipher.CHUNK_BYTES,
            MediaDiskCipher.CHUNK_BYTES + 1, MediaDiskCipher.CHUNK_BYTES * 3 + 17)) {
            val plain = File(root, "p_$size.bin").apply { writeBytes(randomBytes(size)) }
            val enc = File(root, "e_$size.bin")
            val dec = File(root, "d_$size.bin")
            MediaDiskCipher.encryptFile(plain, enc, key, "media/images/x.jpg")
            assertTrue(MediaDiskCipher.isEncrypted(enc))
            MediaDiskCipher.decryptFile(enc, dec, key, "media/images/x.jpg")
            assertArrayEquals("size=$size", plain.readBytes(), dec.readBytes())
        }
    }

    @Test
    fun cipherRejectsWrongKeyWrongPathAndTampering() {
        val plain = File(root, "p.bin").apply { writeBytes(randomBytes(MediaDiskCipher.CHUNK_BYTES * 2 + 5)) }
        val enc = File(root, "e.bin")
        MediaDiskCipher.encryptFile(plain, enc, key, "media/images/a.jpg")
        val out = File(root, "out.bin")

        val wrongKey = randomBytes(32)
        assertFailsDecrypt { MediaDiskCipher.decryptFile(enc, out, wrongKey, "media/images/a.jpg") }
        assertFalse("partial output must be deleted", out.exists())
        assertFailsDecrypt { MediaDiskCipher.decryptFile(enc, out, key, "media/images/other.jpg") }

        val tampered = enc.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        File(root, "t.bin").writeBytes(tampered)
        assertFailsDecrypt { MediaDiskCipher.decryptFile(File(root, "t.bin"), out, key, "media/images/a.jpg") }

        val truncated = enc.readBytes().copyOf(enc.length().toInt() - 20)
        File(root, "tr.bin").writeBytes(truncated)
        assertFailsDecrypt { MediaDiskCipher.decryptFile(File(root, "tr.bin"), out, key, "media/images/a.jpg") }
    }

    private fun assertFailsDecrypt(block: () -> Unit) {
        try {
            block()
            fail("decryption should have failed")
        } catch (_: GeneralSecurityException) {
        } catch (_: java.io.IOException) {
        }
    }

    @Test
    fun storedMediaIsEncryptedOnDiskButReadableThroughResolver() = runBlocking {
        val s = store()
        val plain = randomBytes(5000)
        val canonical = s.storeMediaBytes(plain, "images", "jpg")

        val onDisk = File(files, canonical)
        assertTrue(onDisk.isFile)
        assertTrue(MediaDiskCipher.isEncrypted(onDisk))
        assertFalse("plaintext must not appear on disk", onDisk.readBytes().toList().windowed(32).any { w -> w == plain.take(32) })

        val readable = s.resolveToReadablePath(canonical)
        assertNotNull(readable)
        assertTrue(File(readable!!).path.startsWith(cache.path))
        assertArrayEquals(plain, File(readable).readBytes())
        assertTrue(readable.endsWith(".jpg"))
    }

    @Test
    fun sameBytesDeduplicateAndStayEncrypted() = runBlocking {
        val s = store()
        val plain = randomBytes(2048)
        val first = s.storeMediaBytes(plain, "audio", "m4a")
        val second = s.storeMediaBytes(plain, "audio", "m4a")
        assertEquals(first, second)
        assertTrue(MediaDiskCipher.isEncrypted(File(files, first)))
        assertArrayEquals(plain, File(s.resolveToReadablePath(first)!!).readBytes())
    }

    @Test
    fun importEncryptsAndOptionallyDeletesSource() = runBlocking {
        val s = store()
        val src = File(root, "src.png").apply { writeBytes(randomBytes(3000)) }
        val original = src.readBytes()
        val canonical = s.importExistingFile(src.path, "drawings", "png", deleteSource = true)
        assertNotNull(canonical)
        assertFalse(src.exists())
        assertTrue(MediaDiskCipher.isEncrypted(File(files, canonical!!)))
        assertArrayEquals(original, File(s.resolveToReadablePath(canonical)!!).readBytes())
    }

    @Test
    fun sweepEncryptsLegacyPlaintextAndIsIdempotent() {
        val s = store()
        val legacy = File(files, "media/images/legacy.jpg").apply { parentFile!!.mkdirs(); writeBytes(randomBytes(777)) }
        val original = legacy.readBytes()
        val temp = File(files, "media/images/.tmp_in_progress.jpg").apply { writeBytes(randomBytes(10)) }

        assertEquals(1, s.encryptPendingPlaintextMedia())
        assertTrue(MediaDiskCipher.isEncrypted(legacy))
        assertFalse("in-progress temp files are skipped", MediaDiskCipher.isEncrypted(temp))
        assertEquals(0, s.encryptPendingPlaintextMedia())
        assertArrayEquals(original, File(s.resolveToReadablePath("media/images/legacy.jpg")!!).readBytes())
    }

    @Test
    fun plaintextFileIsStillReadableBeforeSweep() {
        val s = store()
        val legacy = File(files, "media/images/p.jpg").apply { parentFile!!.mkdirs(); writeBytes(randomBytes(64)) }
        assertEquals(legacy.canonicalPath, File(s.resolveToReadablePath("media/images/p.jpg")!!).canonicalPath)
    }

    @Test
    fun wrongKeyResolvesToNullNeverToCiphertext() = runBlocking {
        val canonical = store().storeMediaBytes(randomBytes(500), "images", "jpg")
        val wrong = randomBytes(32)
        val other = store { wrong }
        assertNull(other.resolveToReadablePath(canonical))
        val noKey = store { null }
        assertNull(noKey.resolveToReadablePath(canonical))
    }

    @Test
    fun clearDecryptedCacheRemovesPlainCopies() = runBlocking {
        val s = store()
        val canonical = s.storeMediaBytes(randomBytes(500), "images", "jpg")
        val readable = File(s.resolveToReadablePath(canonical)!!)
        assertTrue(readable.exists())
        s.clearDecryptedCache()
        assertFalse(readable.exists())
        // still resolvable afterwards (re-decrypts on demand)
        assertTrue(File(s.resolveToReadablePath(canonical)!!).exists())
    }

    @Test
    fun movedCiphertextCannotBeDecryptedUnderAnotherPath() = runBlocking {
        val s = store()
        val canonical = s.storeMediaBytes(randomBytes(500), "images", "jpg")
        val moved = File(files, "media/images/ffffffff.jpg")
        File(files, canonical).copyTo(moved)
        assertNull(s.resolveToReadablePath("media/images/ffffffff.jpg"))
    }
}
