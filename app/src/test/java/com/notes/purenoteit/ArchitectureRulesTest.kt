package com.notes.purenoteit

import android.util.Base64
import com.notes.purenoteit.data.security.SqlCipherDatabaseManager
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.util.SecurityUtil
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.experimental.xor

/**
 * Mimari sınır (Clean Architecture) ve Defansif Güvenlik (AES-256-GCM / PBKDF2-120K / Zeroization)
 * kurallarını her derlemede otomatik olarak doğrulayan bekçi birim testleri.
 */
class ArchitectureRulesTest {

    @Before
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun resolveMainJavaDir(): File {
        val direct = File("src/main/java")
        if (direct.exists()) return direct
        val nested = File("app/src/main/java")
        if (nested.exists()) return nested
        error("src/main/java dizini bulunamadı: ${File(".").absolutePath}")
    }

    @Test
    fun `domain katmani hicbir framework room hilt compose gson veya dis katman bagimliligi icermemelidir`() {
        val domainDir = File(resolveMainJavaDir(), "com/notes/purenoteit/domain")
        assertTrue("domain dizini mevcut olmalıdır", domainDir.exists())

        val forbiddenTokens = listOf(
            "import android.",
            "import androidx.",
            "import dagger.",
            "import javax.inject.",
            "import com.google.gson.",
            "import com.notes.purenoteit.data.",
            "import com.notes.purenoteit.ui.",
            "import com.notes.purenoteit.viewmodel.",
            "import com.notes.purenoteit.util.",
            "System.currentTimeMillis("
        )

        val violations = mutableListOf<String>()
        domainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            forbiddenTokens.forEach { token ->
                if (content.contains(token)) {
                    violations += "${file.name}: '$token'"
                }
            }
        }

        assertTrue("Domain mimari ihlalleri bulundu: $violations", violations.isEmpty())
    }

    @Test
    fun `kod tabaninda hicbir LiveData MutableLiveData veya observeAsState bagimliligi kalmamalidir`() {
        val mainDir = resolveMainJavaDir()
        val forbiddenReactive = listOf(
            "androidx.lifecycle.LiveData",
            "androidx.lifecycle.MutableLiveData",
            "androidx.compose.runtime.livedata.observeAsState"
        )

        val violations = mutableListOf<String>()
        mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            forbiddenReactive.forEach { forbidden ->
                if (content.contains(forbidden)) {
                    violations += "${file.name}: '$forbidden'"
                }
            }
        }

        assertTrue("Yasaklı LiveData kullanımı bulundu: $violations", violations.isEmpty())
    }

    @Test
    fun `eski duplike repository paketi tamamen kaldirilmis olmalidir`() {
        val legacyRepoDir = File(resolveMainJavaDir(), "com/notes/purenoteit/repository")
        val remainingKotlinFiles = if (legacyRepoDir.exists()) {
            legacyRepoDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        } else {
            emptyList()
        }
        assertTrue("Eski duplike repository dosyaları kalmamalıdır: $remainingKotlinFiles", remainingKotlinFiles.isEmpty())
    }

    @Test
    fun `data katmanindaki tum RepositoryImpl ve Mapper dosyalari internal gorunurlukte olmalidir`() {
        val dataDir = File(resolveMainJavaDir(), "com/notes/purenoteit/data")
        val implFiles = File(dataDir, "repository").listFiles { f -> f.name.endsWith("Impl.kt") }.orEmpty()
        val mapperFiles = File(dataDir, "mapper").listFiles { f -> f.name.endsWith("Mapper.kt") }.orEmpty()

        assertTrue("En az 3 RepositoryImpl bulunmalıdır", implFiles.size >= 3)
        assertTrue("En az 3 Mapper bulunmalıdır", mapperFiles.size >= 3)

        implFiles.forEach { file ->
            val text = file.readText()
            assertTrue("${file.name} internal class olmalıdır", text.contains("internal class "))
        }

        mapperFiles.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} icinde public donusum fonksiyonu bulunmamalidir", Regex("""(?m)^\s*fun\s+""").containsMatchIn(text))
            assertTrue("${file.name} internal fun icermelidir", text.contains("internal fun "))
        }
    }

    @Test
    fun `Hilt modullerinde RepositoryModule Binds kullanmali ve UseCaseModule Singleton icermemelidir`() {
        val diDir = File(resolveMainJavaDir(), "com/notes/purenoteit/di")
        val repoModule = File(diDir, "RepositoryModule.kt").readText()
        val useCaseModule = File(diDir, "UseCaseModule.kt").readText()

        assertTrue("RepositoryModule @Binds kullanmalıdır", repoModule.contains("@Binds"))
        assertFalse("UseCaseModule durumsuz UseCase'leri @Singleton yapmamalıdır", useCaseModule.contains("@Singleton"))
    }

    @Test
    fun `SecurityUtil ENC_P3 ve ikili PVM3 sifreleme cozme ile hata maskeleme yasagini dogrulamalidir`() {
        val secretNote = " Gizli [[Baglanacak Not]] ve Alt Sayfa İçeriği 2026 "
        val pass = "948271".toCharArray()

        // 1. Parola tabanlı ENC_P3_PWD şifreleme + çözme
        val encryptedWithPass = SecurityUtil.encryptPortable(secretNote, pass.copyOf())
        assertTrue(encryptedWithPass.startsWith("ENC_P3_PWD:"))

        val wrongPassResult = SecurityUtil.decryptSafe(encryptedWithPass, "000000".toCharArray())
        assertTrue("Yanlış parolada düz metin yerine Failure dönmelidir", wrongPassResult is AppResult.Failure)
        assertEquals(DataError.Security.DECRYPTION_FAILED, (wrongPassResult as AppResult.Failure).error)

        val rightPassResult = SecurityUtil.decryptSafe(encryptedWithPass, pass)
        assertTrue(rightPassResult is AppResult.Success)
        assertEquals(secretNote, (rightPassResult as AppResult.Success).data)

        // 2. İkili medya şifreleme (PVM3 AES-256-GCM) + bütünlük bozulması testi
        val rawMediaBytes = ByteArray(256) { idx -> (idx * 7).toByte() }
        val encryptedMedia = SecurityUtil.encryptPortableBytes(rawMediaBytes)
        assertFalse(rawMediaBytes.contentEquals(encryptedMedia))

        val decryptedMedia = SecurityUtil.decryptPortableBytes(encryptedMedia)
        assertArrayEquals(rawMediaBytes, decryptedMedia)

        // GCM Tag bütünlüğü bozulduğunda GeneralSecurityException fırlatılarak bozuk medyanın kabulü engellenmelidir
        val tamperedMedia = encryptedMedia.copyOf()
        tamperedMedia[tamperedMedia.lastIndex] = tamperedMedia.last() xor 0x5A.toByte()
        var threwSecurityException = false
        try {
            SecurityUtil.decryptPortableBytes(tamperedMedia)
        } catch (_: java.security.GeneralSecurityException) {
            threwSecurityException = true
        }
        assertTrue("Bozulmuş şifreli medya GeneralSecurityException fırlatmalıdır", threwSecurityException)
    }

    @Test
    fun `SQLCipher tam veritabani sifrelemesi DatabaseModule icinde bagli olmali ve duz SQLite basligini dogru ayirt etmelidir`() {
        val mainDir = resolveMainJavaDir()
        val dbModuleText = File(mainDir, "com/notes/purenoteit/di/DatabaseModule.kt").readText()
        val sqlCipherMgrText = File(mainDir, "com/notes/purenoteit/data/security/SqlCipherDatabaseManager.kt").readText()

        assertTrue(
            "DatabaseModule openHelperFactory ile SqlCipherDatabaseManager kullanmalıdır",
            dbModuleText.contains(".openHelperFactory(SqlCipherDatabaseManager.createSupportFactory(")
        )
        assertTrue(
            "SqlCipherDatabaseManager internal görünürlükte olmalıdır",
            sqlCipherMgrText.contains("internal object SqlCipherDatabaseManager")
        )
        assertTrue(
            "SqlCipherDatabaseManager atomik sqlcipher_export göçünü içermelidir",
            sqlCipherMgrText.contains("sqlcipher_export('main', 'plaintext')")
        )

        val tempPlain = File.createTempFile("plain_sqlite_test", ".db")
        val tempEncrypted = File.createTempFile("encrypted_sqlcipher_test", ".db")
        try {
            tempPlain.writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84))
            tempEncrypted.writeBytes(ByteArray(100) { idx -> ((idx * 37) xor 0xA5).toByte() })

            assertTrue(
                "Şifresiz SQLite 3 başlığı doğru tespit edilmelidir",
                SqlCipherDatabaseManager.isPlaintextSqliteHeader(tempPlain)
            )
            assertFalse(
                "SQLCipher rastgele tuzlu başlık düz SQLite sayılmamalıdır",
                SqlCipherDatabaseManager.isPlaintextSqliteHeader(tempEncrypted)
            )
        } finally {
            tempPlain.delete()
            tempEncrypted.delete()
        }
    }

    @Test
    fun `Compose navigasyonu resmi tip guvenli Serializable AppRoute kullanmali ve string rota sablonu barindirmamalidir`() {
        val mainDir = resolveMainJavaDir()
        val appRouteFile = File(mainDir, "com/notes/purenoteit/ui/navigation/AppRoute.kt")
        assertTrue("AppRoute.kt dosyası mevcut olmalıdır", appRouteFile.exists())
        val appRouteText = appRouteFile.readText()
        assertTrue("AppRoute @Serializable içermelidir", appRouteText.contains("@Serializable"))
        assertTrue("AppRoute @SerialName içermelidir", appRouteText.contains("@SerialName("))

        val mainActivityText = File(mainDir, "com/notes/purenoteit/view/MainActivity.kt").readText()
        assertTrue(
            "MainActivity tip güvenli composable<AppRoute.NoteDetail> kullanmalıdır",
            mainActivityText.contains("composable<AppRoute.NoteDetail>")
        )
        assertFalse(
            "MainActivity kırılgan navArgument string şablonu kullanmamalıdır",
            mainActivityText.contains("navArgument(")
        )
    }
}
