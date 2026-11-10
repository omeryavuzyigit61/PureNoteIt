package com.notes.purenoteit.data.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.security.OAuthPkceConfig
import com.notes.purenoteit.security.OAuthPkceProtocol
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android OAuth 2.0 PKCE orchestrator using Chrome Custom Tabs (`androidx.browser`)
 * and validated custom-scheme deep links (`com.purenoteit.app:/oauth2redirect`).
 * - Strictly avoids `WebView`.
 * - Rejects unsolicited or replayed deep-link Intents without an active matching session & `state`.
 */
@Singleton
class AndroidOAuthPkceManager @Inject constructor(
    private val pkceProtocol: OAuthPkceProtocol,
    private val tokenStorage: SecureTokenStorage
) {
    private data class PendingPkceSession(
        val config: OAuthPkceConfig,
        val state: String,
        val codeVerifier: String,
        val redirectUri: String,
        val deferred: CompletableDeferred<AppResult<OAuthTokenBundle, DataError.Network>>
    )

    private val sessionMutex = Mutex()
    private var activeSession: PendingPkceSession? = null

    suspend fun authorizeWithCustomTab(
        activityContext: Context,
        config: OAuthPkceConfig,
        timeoutMs: Long = DEFAULT_AUTH_TIMEOUT_MS,
        customTabLauncher: ((Context, Uri) -> Unit)? = null
    ): AppResult<OAuthTokenBundle, DataError.Network> {
        val session = when (val s = pkceProtocol.createPkceSession(config, redirectUriOverride = ANDROID_REDIRECT_URI)) {
            is AppResult.Failure -> return s
            is AppResult.Success -> s.data
        }

        val deferred = CompletableDeferred<AppResult<OAuthTokenBundle, DataError.Network>>()
        sessionMutex.withLock {
            activeSession?.deferred?.complete(AppResult.Failure(DataError.Network.UNAUTHORIZED))
            activeSession = PendingPkceSession(
                config = config,
                state = session.state,
                codeVerifier = session.codeVerifier,
                redirectUri = session.redirectUri,
                deferred = deferred
            )
        }

        return try {
            val authUri = Uri.parse(session.authorizationUrl)
            if (customTabLauncher != null) {
                customTabLauncher(activityContext, authUri)
            } else {
                val customTabsIntent = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                customTabsIntent.launchUrl(activityContext, authUri)
            }

            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            clearSession(session.state)
            AppResult.Failure(DataError.Network.TIMEOUT)
        } catch (e: CancellationException) {
            clearSession(session.state)
            throw e
        } catch (_: Exception) {
            clearSession(session.state)
            AppResult.Failure(DataError.Network.UNKNOWN)
        }
    }

    /**
     * Processes an incoming `MainActivity` Intent. Consumes only matching `com.purenoteit.app:/oauth2redirect`
     * URIs tied to an active pending PKCE session and immediately strips `intent.data` to prevent replay.
     */
    suspend fun handleRedirectIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != REDIRECT_SCHEME) return false
        val pathValid = uri.path == REDIRECT_PATH || (uri.host == "oauth2redirect" && uri.path.isNullOrEmpty())
        if (!pathValid) return false

        // Consume intent URI immediately to prevent replay on Activity recreation
        intent.data = null

        val pending = sessionMutex.withLock {
            val current = activeSession
            activeSession = null
            current
        } ?: return false

        val result = try {
            when (
                val parsedCode = pkceProtocol.parseAndValidateCallback(
                    callbackUri = normalizeCallbackUri(uri),
                    expectedState = pending.state,
                    expectedPath = REDIRECT_PATH
                )
            ) {
                is AppResult.Failure -> parsedCode
                is AppResult.Success -> {
                    val tokenRes = pkceProtocol.exchangeAuthorizationCode(
                        config = pending.config,
                        code = parsedCode.data,
                        codeVerifier = pending.codeVerifier,
                        redirectUri = pending.redirectUri
                    )
                    when (tokenRes) {
                        is AppResult.Failure -> tokenRes
                        is AppResult.Success -> {
                            when (tokenStorage.saveTokenBundle(tokenRes.data)) {
                                is AppResult.Failure -> AppResult.Failure(DataError.Network.UNKNOWN)
                                is AppResult.Success -> tokenRes
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Network.UNKNOWN)
        }

        pending.deferred.complete(result)
        return true
    }

    private fun normalizeCallbackUri(uri: Uri): String {
        val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
        return "$REDIRECT_SCHEME:$REDIRECT_PATH$query"
    }

    private suspend fun clearSession(state: String) {
        sessionMutex.withLock {
            if (activeSession?.state == state) {
                activeSession = null
            }
        }
    }

    companion object {
        const val REDIRECT_SCHEME = "com.purenoteit.app"
        const val REDIRECT_PATH = "/oauth2redirect"
        const val ANDROID_REDIRECT_URI = "$REDIRECT_SCHEME:$REDIRECT_PATH"
        private const val DEFAULT_AUTH_TIMEOUT_MS = 180_000L
    }
}
