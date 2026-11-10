package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.DesktopOAuthPkceManager
import com.notes.purenoteit.security.DesktopSecureTokenStorageImpl
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthSyncTokenProvider
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.OsKeyringClient
import com.notes.purenoteit.security.SecureTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OAuthPkceAndDesktopAuthTest {

    private var clockMillis = 1_760_000_000_000L
    private val testClock = AppClock { clockMillis }

    private val defaultConfig = OAuthPkceConfig(
        clientId = "test-desktop-client-id.apps.googleusercontent.com",
        redirectUri = "http://127.0.0.1/oauth2redirect"
    )

    @Test
    fun `PKCE S256 challenge ve verifier benzersiz ve RFC 7636 uyumlu uretilmelidir`() {
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = testClock
        )

        val res1 = protocol.createPkceSession(defaultConfig) as AppResult.Success
        val res2 = protocol.createPkceSession(defaultConfig) as AppResult.Success

        val session1 = res1.data
        val session2 = res2.data

        assertTrue("codeVerifier RFC 7636 alt sinir >= 43 olmali", session1.codeVerifier.length >= 43)
        assertNotEquals(session1.codeVerifier, session2.codeVerifier)
        assertNotEquals(session1.state, session2.state)

        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(session1.codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        val expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedDigest)
        assertEquals(expectedChallenge, session1.codeChallenge)
        assertTrue(session1.authorizationUrl.contains("code_challenge_method=S256"))
        assertTrue(session1.authorizationUrl.contains("code_challenge=$expectedChallenge"))
    }

    @Test
    fun `HTTPS olmayan authorization veya token endpointi TLS_ERROR ile reddedilmelidir`() = runTest {
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = testClock,
            requireHttpsEndpoints = true
        )

        val insecureAuthConfig = defaultConfig.copy(
            authorizationEndpoint = "http://accounts.google.com/o/oauth2/v2/auth"
        )
        val sessionRes = protocol.createPkceSession(insecureAuthConfig)
        assertEquals(AppResult.Failure(DataError.Network.TLS_ERROR), sessionRes)

        val insecureTokenConfig = defaultConfig.copy(
            tokenEndpoint = "http://oauth2.googleapis.com/token"
        )
        val exchangeRes = protocol.exchangeAuthorizationCode(
            config = insecureTokenConfig,
            code = "code-123",
            codeVerifier = "verifier-1234567890123456789012345678901234567890",
            redirectUri = "http://127.0.0.1:12345/oauth2redirect"
        )
        assertEquals(AppResult.Failure(DataError.Network.TLS_ERROR), exchangeRes)
    }

    @Test
    fun `CSRF state uyumsuzlugu veya error parametresi callback dogrulamasinda UNAUTHORIZED donmelidir`() {
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = testClock
        )

        val badState = protocol.parseAndValidateCallback(
            callbackUri = "http://127.0.0.1:45555/oauth2redirect?code=abc&state=wrong-state",
            expectedState = "expected-state"
        )
        assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), badState)

        val accessDenied = protocol.parseAndValidateCallback(
            callbackUri = "http://127.0.0.1:45555/oauth2redirect?error=access_denied&state=expected-state",
            expectedState = "expected-state"
        )
        assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), accessDenied)

        val badPath = protocol.parseAndValidateCallback(
            callbackUri = "http://127.0.0.1:45555/evil?code=abc&state=expected-state",
            expectedState = "expected-state"
        )
        assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), badPath)

        val valid = protocol.parseAndValidateCallback(
            callbackUri = "http://127.0.0.1:45555/oauth2redirect?code=auth-code-99&state=expected-state",
            expectedState = "expected-state"
        )
        assertEquals(AppResult.Success("auth-code-99"), valid)
    }

    @Test
    fun `authorization_code degisimi ve refresh yanitinda refresh_token korunmalidir`() = runTest {
        var lastRequestBody = ""
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { request ->
                lastRequestBody = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                if (lastRequestBody.contains("grant_type=authorization_code")) {
                    respond(
                        content = """{"access_token":"acc-1","refresh_token":"ref-1","expires_in":3600,"token_type":"Bearer","scope":"drive.appdata"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                } else {
                    respond(
                        content = """{"access_token":"acc-2","expires_in":1800,"token_type":"Bearer"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            },
            clock = testClock
        )

        val exchange = protocol.exchangeAuthorizationCode(
            config = defaultConfig,
            code = "code-ok",
            codeVerifier = "verifier-xyz",
            redirectUri = "http://127.0.0.1:50000/oauth2redirect"
        ) as AppResult.Success

        assertTrue(lastRequestBody.contains("grant_type=authorization_code"))
        assertTrue(lastRequestBody.contains("code_verifier=verifier-xyz"))
        assertEquals("acc-1", exchange.data.accessToken)
        assertEquals("ref-1", exchange.data.refreshToken)
        assertEquals(clockMillis + 3_600_000L, exchange.data.expiresAtEpochMillis)

        val refreshed = protocol.refreshAccessToken(defaultConfig, "ref-1") as AppResult.Success
        assertEquals("acc-2", refreshed.data.accessToken)
        assertEquals("ref-1", refreshed.data.refreshToken)
        assertEquals(clockMillis + 1_800_000L, refreshed.data.expiresAtEpochMillis)
    }

    @Test
    fun `OAuthSyncTokenProvider gecerli tokeni HTTP cagrisi yapmadan donmeli ve eszamanli yenilemede tek ucus yapmalidir`() =
        runTest {
            val tokenCalls = AtomicInteger(0)
            val storage = InMemorySecureTokenStorage()
            storage.saveTokenBundle(
                OAuthTokenBundle(
                    accessToken = "fresh-access",
                    refreshToken = "keep-refresh",
                    expiresAtEpochMillis = clockMillis + 300_000L
                )
            )
            val protocol = OAuthPkceProtocol(
                httpEngine = MockEngine {
                    tokenCalls.incrementAndGet()
                    respond(
                        content = """{"access_token":"renewed-access","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                },
                clock = testClock
            )
            val provider = OAuthSyncTokenProvider(
                tokenStorage = storage,
                pkceProtocol = protocol,
                configProvider = OAuthClientConfigProvider { defaultConfig },
                clock = testClock
            )

            assertEquals(AppResult.Success("fresh-access"), provider.getAccessToken())
            assertEquals(0, tokenCalls.get())

            // Simulate near expiry (within 60s skew) -> concurrent getAccessToken calls must share single refresh
            clockMillis += 250_000L
            val results = (1..6).map {
                async { provider.getAccessToken() }
            }.awaitAll()

            assertTrue(results.all { it == AppResult.Success("renewed-access") })
            assertEquals("Mutex tek-ucus refresh yalnizca 1 HTTP istegi atmali", 1, tokenCalls.get())
        }

    @Test
    fun `OAuthSyncTokenProvider invalid_grant durumunda stored tokenlari temizlemelidir`() = runTest {
        val storage = InMemorySecureTokenStorage()
        storage.saveTokenBundle(
            OAuthTokenBundle(
                accessToken = "old-access",
                refreshToken = "revoked-refresh",
                expiresAtEpochMillis = clockMillis - 1_000L
            )
        )
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine {
                respond(
                    content = """{"error":"invalid_grant","error_description":"Token has been revoked."}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            },
            clock = testClock
        )
        val provider = OAuthSyncTokenProvider(
            tokenStorage = storage,
            pkceProtocol = protocol,
            configProvider = OAuthClientConfigProvider { defaultConfig },
            clock = testClock
        )

        val res = provider.getAccessToken()
        assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), res)
        assertNull((storage.getTokenBundle() as AppResult.Success).data)
    }

    @Test
    fun `DesktopOAuthPkceManager ephemeral loopback callback ile token alip saklamali ve soketi kapatmalidir`() =
        runBlocking {
            val storage = InMemorySecureTokenStorage()
            val protocol = OAuthPkceProtocol(
                httpEngine = MockEngine {
                    respond(
                        content = """{"access_token":"desktop-access","refresh_token":"desktop-refresh","expires_in":3600}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                },
                clock = testClock
            )

            val callbackHttpResponse = CompletableFuture<String>()
            var capturedPort = -1

            val manager = DesktopOAuthPkceManager(
                pkceProtocol = protocol,
                tokenStorage = storage,
                ioDispatcher = Dispatchers.IO,
                browserLauncher = { authUrl ->
                    val parsed = Url(authUrl)
                    val redirectUri = Url(parsed.parameters["redirect_uri"]!!)
                    val state = parsed.parameters["state"]!!
                    capturedPort = redirectUri.port

                    Thread {
                        try {
                            Socket("127.0.0.1", capturedPort).use { client ->
                                val out = client.getOutputStream()
                                out.write(
                                    "GET /oauth2redirect?code=secret-auth-code-77&state=$state HTTP/1.1\r\nHost: 127.0.0.1:$capturedPort\r\nConnection: close\r\n\r\n"
                                        .toByteArray(StandardCharsets.US_ASCII)
                                )
                                out.flush()
                                val response = BufferedReader(InputStreamReader(client.getInputStream())).readText()
                                callbackHttpResponse.complete(response)
                            }
                        } catch (t: Throwable) {
                            callbackHttpResponse.completeExceptionally(t)
                        }
                    }.start()
                    true
                }
            )

            val result = manager.authorizeInteractive(defaultConfig, timeoutMs = 10_000L)
            assertTrue("Loopback auth basarili olmali: $result", result is AppResult.Success)
            val bundle = (result as AppResult.Success).data
            assertEquals("desktop-access", bundle.accessToken)
            assertEquals("desktop-refresh", bundle.refreshToken)

            val stored = (storage.getTokenBundle() as AppResult.Success).data
            assertEquals(bundle, stored)

            val rawHttp = callbackHttpResponse.get(5, TimeUnit.SECONDS)
            assertTrue(rawHttp.startsWith("HTTP/1.1 200 OK"))
            assertFalse("HTML yanitta auth code/state yansitilmamali (XSS koruma)", rawHttp.contains("secret-auth-code-77"))

            // Socket must be closed immediately after auth completion
            var reconnectFailed = false
            try {
                Socket("127.0.0.1", capturedPort).close()
            } catch (_: ConnectException) {
                reconnectFailed = true
            }
            assertTrue("Loopback socket tamamlaninca hemen kapatilmali", reconnectFailed)
        }

    @Test
    fun `DesktopOAuthPkceManager hatali CSRF state geldiginde 400 donup UNAUTHORIZED uretmelidir`() = runBlocking {
        val storage = InMemorySecureTokenStorage()
        val protocol = OAuthPkceProtocol(
            httpEngine = MockEngine { respond("{}", HttpStatusCode.OK) },
            clock = testClock
        )
        val callbackHttpResponse = CompletableFuture<String>()

        val manager = DesktopOAuthPkceManager(
            pkceProtocol = protocol,
            tokenStorage = storage,
            ioDispatcher = Dispatchers.IO,
            browserLauncher = { authUrl ->
                val redirectUri = Url(Url(authUrl).parameters["redirect_uri"]!!)
                val port = redirectUri.port
                Thread {
                    try {
                        Socket("127.0.0.1", port).use { client ->
                            val out = client.getOutputStream()
                            out.write(
                                "GET /oauth2redirect?code=stolen&state=tampered-state HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n"
                                    .toByteArray(StandardCharsets.US_ASCII)
                            )
                            out.flush()
                            callbackHttpResponse.complete(
                                BufferedReader(InputStreamReader(client.getInputStream())).readText()
                            )
                        }
                    } catch (t: Throwable) {
                        callbackHttpResponse.completeExceptionally(t)
                    }
                }.start()
                true
            }
        )

        val result = manager.authorizeInteractive(defaultConfig, timeoutMs = 10_000L)
        assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), result)
        assertTrue(callbackHttpResponse.get(5, TimeUnit.SECONDS).startsWith("HTTP/1.1 400 Bad Request"))
        assertNull((storage.getTokenBundle() as AppResult.Success).data)
    }

    @Test
    fun `DesktopSecureTokenStorageImpl bundle ve passphrase saklamali bozuk JSONu silip DECRYPTION_FAILED donmelidir`() =
        runTest {
            val fakeKeyring = FakeOsKeyringClient()
            val storage = DesktopSecureTokenStorageImpl(
                ioDispatcher = Dispatchers.IO,
                keyringFactory = { fakeKeyring }
            )

            val bundle = OAuthTokenBundle(
                accessToken = "tok-a",
                refreshToken = "tok-r",
                expiresAtEpochMillis = 999_999L
            )
            assertEquals(AppResult.Success(Unit), storage.saveTokenBundle(bundle))
            assertEquals(AppResult.Success(bundle), storage.getTokenBundle())

            val pass = charArrayOf('S', 'y', 'n', 'c', '#', '1', '2', '3')
            assertEquals(AppResult.Success(Unit), storage.saveSyncPassphrase(pass))
            val loadedPass = (storage.getSyncPassphrase() as AppResult.Success).data!!
            assertArrayEquals(pass, loadedPass)
            loadedPass.fill('\u0000')

            // Corrupt JSON in OS keyring -> must purge key and return DECRYPTION_FAILED (no plaintext fallback)
            fakeKeyring.setPassword(
                DesktopSecureTokenStorageImpl.SERVICE_NAME,
                DesktopSecureTokenStorageImpl.ACCOUNT_OAUTH_BUNDLE,
                "{corrupted-json"
            )
            val corruptRead = storage.getTokenBundle()
            assertEquals(AppResult.Failure(DataError.Security.DECRYPTION_FAILED), corruptRead)
            assertNull(
                fakeKeyring.getPassword(
                    DesktopSecureTokenStorageImpl.SERVICE_NAME,
                    DesktopSecureTokenStorageImpl.ACCOUNT_OAUTH_BUNDLE
                )
            )

            assertEquals(AppResult.Success(Unit), storage.clearAll())
            assertNull((storage.getSyncPassphrase() as AppResult.Success).data)
        }

    private class FakeOsKeyringClient : OsKeyringClient {
        private val store = mutableMapOf<Pair<String, String>, String>()

        override fun getPassword(service: String, account: String): String? = store[service to account]

        override fun setPassword(service: String, account: String, password: String) {
            store[service to account] = password
        }

        override fun deletePassword(service: String, account: String) {
            store.remove(service to account)
        }

        override fun close() = Unit
    }

    private class InMemorySecureTokenStorage : SecureTokenStorage {
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
