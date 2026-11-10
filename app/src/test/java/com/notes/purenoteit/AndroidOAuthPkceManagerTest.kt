package com.notes.purenoteit

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.notes.purenoteit.data.security.AndroidOAuthPkceManager
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI

class AndroidOAuthPkceManagerTest {

    private val clock = AppClock { 1_760_000_000_000L }
    private val config = OAuthPkceConfig(
        clientId = "android-client-id.apps.googleusercontent.com",
        redirectUri = AndroidOAuthPkceManager.ANDROID_REDIRECT_URI
    )

    private lateinit var storage: InMemoryAndroidTokenStorage
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        storage = InMemoryAndroidTokenStorage()
        mockContext = mockk(relaxed = true)
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers { mockUri(firstArg()) }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `authorizeWithCustomTab gecerli deep link ile token kaydedip intent datasini temizlemelidir`() =
        runBlocking {
            val protocol = OAuthPkceProtocol(
                httpEngine = MockEngine {
                    respond(
                        content = """{"access_token":"android-acc","refresh_token":"android-ref","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                },
                clock = clock
            )
            val manager = AndroidOAuthPkceManager(protocol, storage)

            lateinit var redirectIntent: Intent
            val authDeferred = async {
                manager.authorizeWithCustomTab(
                    activityContext = mockContext,
                    config = config,
                    timeoutMs = 5_000L,
                    customTabLauncher = { _, launchedUri ->
                        val parsed = Url(launchedUri.toString())
                        val state = parsed.parameters["state"]!!
                        assertEquals("S256", parsed.parameters["code_challenge_method"])
                        assertEquals(AndroidOAuthPkceManager.ANDROID_REDIRECT_URI, parsed.parameters["redirect_uri"])
                        val callbackUri = mockUri("com.purenoteit.app:/oauth2redirect?code=android-code-7&state=$state")
                        redirectIntent = mockIntent(callbackUri)
                        async {
                            val consumed = manager.handleRedirectIntent(redirectIntent)
                            assertTrue(consumed)
                        }
                    }
                )
            }

            val result = authDeferred.await()
            assertTrue("Android PKCE basarili olmali: $result", result is AppResult.Success)
            val bundle = (result as AppResult.Success).data
            assertEquals("android-acc", bundle.accessToken)
            assertEquals("android-ref", bundle.refreshToken)
            assertEquals(bundle, (storage.getTokenBundle() as AppResult.Success).data)

            // Replay protection checks: intent.data wiped + second invocation rejected
            assertNull(redirectIntent.data)
            assertFalse(manager.handleRedirectIntent(mockIntent(mockUri("com.purenoteit.app:/oauth2redirect?code=android-code-7&state=replayed"))))
        }

    @Test
    fun `bekleyen oturum yokken veya yanlis scheme path geldiginde intent reddedilmelidir`() = runTest {
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = clock
        )
        val manager = AndroidOAuthPkceManager(protocol, storage)

        val unsolicited = mockIntent(mockUri("com.purenoteit.app:/oauth2redirect?code=fake&state=fake"))
        assertFalse(manager.handleRedirectIntent(unsolicited))

        val wrongScheme = mockIntent(mockUri("https://evil.example/oauth2redirect?code=x&state=y"))
        assertFalse(manager.handleRedirectIntent(wrongScheme))

        val wrongPath = mockIntent(mockUri("com.purenoteit.app:/evil?code=x&state=y"))
        assertFalse(manager.handleRedirectIntent(wrongPath))
        assertNull((storage.getTokenBundle() as AppResult.Success).data)
    }

    @Test
    fun `hatali CSRF state iceren callback intenti oturumu UNAUTHORIZED ile sonlandirmalidir`() =
        runBlocking {
            val protocol = OAuthPkceProtocol(
                httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
                clock = clock
            )
            val manager = AndroidOAuthPkceManager(protocol, storage)

            val result = manager.authorizeWithCustomTab(
                activityContext = mockContext,
                config = config,
                timeoutMs = 5_000L,
                customTabLauncher = { _, _ ->
                    val tamperedIntent = mockIntent(mockUri("com.purenoteit.app:/oauth2redirect?code=code-1&state=tampered"))
                    async {
                        assertTrue(manager.handleRedirectIntent(tamperedIntent))
                        assertNull(tamperedIntent.data)
                    }
                }
            )

            assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), result)
            assertNull((storage.getTokenBundle() as AppResult.Success).data)
        }

    @Test
    fun `Custom Tab zaman asiminda TIMEOUT donup aktif oturumu temizlemelidir`() = runBlocking {
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = clock
        )
        val manager = AndroidOAuthPkceManager(protocol, storage)

        lateinit var capturedState: String
        val result = manager.authorizeWithCustomTab(
            activityContext = mockContext,
            config = config,
            timeoutMs = 150L,
            customTabLauncher = { _, launchedUri ->
                capturedState = Url(launchedUri.toString()).parameters["state"]!!
            }
        )

        assertEquals(AppResult.Failure(DataError.Network.TIMEOUT), result)
        val lateIntent = mockIntent(mockUri("com.purenoteit.app:/oauth2redirect?code=late&state=$capturedState"))
        assertFalse(manager.handleRedirectIntent(lateIntent))
    }

    private fun mockUri(raw: String): Uri {
        val parsed = URI(raw)
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns parsed.scheme
        every { uri.host } returns parsed.host
        every { uri.path } returns parsed.path
        every { uri.encodedQuery } returns parsed.rawQuery
        every { uri.toString() } returns raw
        return uri
    }

    private fun mockIntent(initialUri: Uri?): Intent {
        var currentData: Uri? = initialUri
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } answers { currentData }
        every { intent.setData(isNull()) } answers {
            currentData = null
            intent
        }
        every { intent.setData(any()) } answers {
            currentData = firstArg()
            intent
        }
        return intent
    }

    private class InMemoryAndroidTokenStorage : SecureTokenStorage {
        private var bundle: OAuthTokenBundle? = null
        private var passphrase: CharArray? = null

        override suspend fun saveTokenBundle(bundle: OAuthTokenBundle): AppResult<Unit, DataError.Security> {
            this.bundle = bundle
            return AppResult.Success(Unit)
        }

        override suspend fun getTokenBundle(): AppResult<OAuthTokenBundle?, DataError.Security> =
            AppResult.Success(bundle)

        override suspend fun saveSyncPassphrase(passphrase: CharArray): AppResult<Unit, DataError.Security> {
            this.passphrase = passphrase.copyOf()
            return AppResult.Success(Unit)
        }

        override suspend fun getSyncPassphrase(): AppResult<CharArray?, DataError.Security> =
            AppResult.Success(passphrase?.copyOf())

        override suspend fun clearAll(): AppResult<Unit, DataError.Security> {
            bundle = null
            passphrase?.fill('\u0000')
            passphrase = null
            return AppResult.Success(Unit)
        }
    }
}
