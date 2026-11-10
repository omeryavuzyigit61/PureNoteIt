package com.notes.purenoteit

import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.domain.storage.MediaPathResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPathResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var resolver: MediaPathResolver
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        resolver = SharedStorageFactory.createMediaPathResolver(filesDir, cacheDir, testDispatcher)
    }

    @Test
    fun `storeMediaBytes SHA-256 tabanli bagil yol dondurmeli ve ayni baytlari tekil saklamalidir`() = runTest(testDispatcher) {
        val payload = "purenoteit-drawing-png-bytes".toByteArray()

        val firstPath = resolver.storeMediaBytes(payload, "drawings", "png")
        val secondPath = resolver.storeMediaBytes(payload, "drawings", "png")

        assertEquals(firstPath, secondPath)
        assertTrue(firstPath.startsWith("media/drawings/"))
        assertTrue(firstPath.endsWith(".png"))
        assertTrue(resolver.isCanonicalRelativePath(firstPath))

        val drawingsDir = File(filesDir, "media/drawings")
        assertEquals(1, drawingsDir.listFiles()?.size)

        val resolvedAbs = resolver.resolveToReadablePath(firstPath)
        assertNotNull(resolvedAbs)
        assertTrue(File(resolvedAbs!!).exists())
        assertEquals(payload.decodeToString(), File(resolvedAbs).readText())
    }

    @Test
    fun `CWE-22 path traversal ve null byte girisimleri kesinlikle engellenmelidir`() {
        assertNull(resolver.resolveToReadablePath("../../etc/passwd"))
        assertNull(resolver.resolveToReadablePath("media/images/../../secret.jpg"))
        assertNull(resolver.resolveToReadablePath("media/../../etc/hosts"))
        assertNull(resolver.resolveToReadablePath("media/images/bad\u0000file.png"))
        assertNull(resolver.resolveToReadablePath("media/"))
        assertFalse(resolver.isCanonicalRelativePath("media/../etc/passwd"))
        assertFalse(resolver.isCanonicalRelativePath("/data/user/0/app/files/media/a.png"))
    }

    @Test
    fun `eski mutlak cihaz yollari geriye donuk olarak yeni yerel dosya sistemine cozulmelidir`() = runTest(testDispatcher) {
        val legacyDir = File(filesDir, "drawings").apply { mkdirs() }
        val legacyFile = File(legacyDir, "sketch_12345.png").apply { writeText("legacy-sketch") }

        // Başka cihaz/kurulum mutlak yolu simülasyonu
        val foreignAbsolutePath = "/data/user/0/com.old.device/files/drawings/sketch_12345.png"
        val resolved = resolver.resolveToReadablePath(foreignAbsolutePath)
        assertEquals(legacyFile.canonicalPath, resolved)

        val migrated = resolver.migrateToCanonicalRelativeIfNeeded(foreignAbsolutePath)
        assertNotNull(migrated)
        assertTrue(migrated!!.startsWith("media/drawings/"))
        assertTrue(resolver.isCanonicalRelativePath(migrated))
    }

    @Test
    fun `importExistingFile deleteSource true ile gecici kaydi tasiyip dogru uzantida bagil yol uretmelidir`() = runTest(testDispatcher) {
        val stagingPath = resolver.createStagingFilePath("rec", "m4a")
        File(stagingPath).writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val imported = resolver.importExistingFile(
            sourcePath = stagingPath,
            subFolder = "audio",
            defaultExtension = "m4a",
            deleteSource = true
        )

        assertNotNull(imported)
        assertTrue(imported!!.startsWith("media/audio/"))
        assertTrue(imported.endsWith(".m4a"))
        assertFalse("Geçici staging dosyası taşınma sonrası silinmeliydi", File(stagingPath).exists())

        val readable = resolver.resolveToReadablePath(imported)
        assertNotNull(readable)
        assertTrue(File(readable!!).exists())
    }
}
