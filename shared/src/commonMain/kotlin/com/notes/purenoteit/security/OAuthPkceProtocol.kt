package com.notes.purenoteit.security

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.sync.classifyNetworkThrowable
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
data class OAuthPkceConfig(
    @SerialName("client_id") val clientId: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("scopes") val scopes: List<String> = listOf(DRIVE_APPDATA_SCOPE),
    @SerialName("authorization_endpoint") val authorizationEndpoint: String = GOOGLE_AUTH_ENDPOINT,
    @SerialName("token_endpoint") val tokenEndpoint: String = GOOGLE_TOKEN_ENDPOINT,
    @SerialName("client_secret") val clientSecret: String? = null
) {
    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }
}

data class PkceSessionParams(
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val redirectUri: String,
    val authorizationUrl: String
)

fun interface OAuthClientConfigProvider {
    fun getConfig(): OAuthPkceConfig?
}

/**
 * RFC 7636 OAuth 2.0 PKCE (`S256`) protocol engine shared across Android Custom Tabs
 * and Desktop Loopback redirects. Never embeds client secrets on Android and enforces
 * constant-time CSRF `state` verification.
 */
@OptIn(ExperimentalEncodingApi::class)
class OAuthPkceProtocol(
    httpEngine: HttpClientEngine,
    private val clock: AppClock,
    private val requireHttpsEndpoints: Boolean = true
) {
    private val client = HttpClient(httpEngine)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val urlSafeNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun createPkceSession(
        config: OAuthPkceConfig,
        redirectUriOverride: String? = null
    ): AppResult<PkceSessionParams, DataError.Network> {
        if (config.clientId.isBlank()) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }
        if (requireHttpsEndpoints && !config.authorizationEndpoint.startsWith("https://", ignoreCase = true)) {
            return AppResult.Failure(DataError.Network.TLS_ERROR)
        }
        val effectiveRedirect = (redirectUriOverride ?: config.redirectUri).trim()
        if (effectiveRedirect.isBlank()) {
            return AppResult.Failure(DataError.Network.UNKNOWN)
        }

        val verifierBytes = generateSecureRandomBytes(VERIFIER_ENTROPY_BYTES)
        val stateBytes = generateSecureRandomBytes(STATE_ENTROPY_BYTES)
        val verifier = urlSafeNoPadding.encode(verifierBytes)
        val state = urlSafeNoPadding.encode(stateBytes)
        verifierBytes.fill(0)
        stateBytes.fill(0)

        val verifierAscii = verifier.encodeToByteArray()
        val digest = sha256Bytes(verifierAscii)
        val challenge = urlSafeNoPadding.encode(digest)
        verifierAscii.fill(0)
        digest.fill(0)

        val authUrl = try {
            URLBuilder().apply {
                takeFrom(config.authorizationEndpoint)
                parameters.append("client_id", config.clientId)
                parameters.append("redirect_uri", effectiveRedirect)
                parameters.append("response_type", "code")
                parameters.append("scope", config.scopes.joinToString(" "))
                parameters.append("code_challenge", challenge)
                parameters.append("code_challenge_method", "S256")
                parameters.append("state", state)
                parameters.append("access_type", "offline")
                parameters.append("prompt", "consent")
            }.buildString()
        } catch (_: Exception) {
            return AppResult.Failure(DataError.Network.UNKNOWN)
        }

        return AppResult.Success(
            PkceSessionParams(
                state = state,
                codeVerifier = verifier,
                codeChallenge = challenge,
                redirectUri = effectiveRedirect,
                authorizationUrl = authUrl
            )
        )
    }

    fun parseAndValidateCallback(
        callbackUri: String,
        expectedState: String,
        expectedPath: String = "/oauth2redirect"
    ): AppResult<String, DataError.Network> {
        if (callbackUri.isBlank() || expectedState.isBlank()) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }
        val parsed = try {
            Url(callbackUri)
        } catch (_: Exception) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }

        if (expectedPath.isNotBlank() && parsed.encodedPath != expectedPath) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }

        val errorParam = parsed.parameters["error"]
        if (!errorParam.isNullOrBlank()) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }

        val returnedState = parsed.parameters["state"] ?: return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        if (!constantTimeEquals(returnedState, expectedState)) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }

        val authCode = parsed.parameters["code"]?.trim()
        if (authCode.isNullOrBlank()) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }
        return AppResult.Success(authCode)
    }

    suspend fun exchangeAuthorizationCode(
        config: OAuthPkceConfig,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): AppResult<OAuthTokenBundle, DataError.Network> {
        if (requireHttpsEndpoints && !config.tokenEndpoint.startsWith("https://", ignoreCase = true)) {
            return AppResult.Failure(DataError.Network.TLS_ERROR)
        }
        val params = Parameters.build {
            append("grant_type", "authorization_code")
            append("client_id", config.clientId)
            append("code", code)
            append("code_verifier", codeVerifier)
            append("redirect_uri", redirectUri)
            config.clientSecret?.takeIf { it.isNotBlank() }?.let { append("client_secret", it) }
        }
        return postTokenRequest(config.tokenEndpoint, params, fallbackRefreshToken = null)
    }

    suspend fun refreshAccessToken(
        config: OAuthPkceConfig,
        refreshToken: String
    ): AppResult<OAuthTokenBundle, DataError.Network> {
        if (refreshToken.isBlank()) {
            return AppResult.Failure(DataError.Network.UNAUTHORIZED)
        }
        if (requireHttpsEndpoints && !config.tokenEndpoint.startsWith("https://", ignoreCase = true)) {
            return AppResult.Failure(DataError.Network.TLS_ERROR)
        }
        val params = Parameters.build {
            append("grant_type", "refresh_token")
            append("client_id", config.clientId)
            append("refresh_token", refreshToken)
            config.clientSecret?.takeIf { it.isNotBlank() }?.let { append("client_secret", it) }
        }
        return postTokenRequest(config.tokenEndpoint, params, fallbackRefreshToken = refreshToken)
    }

    private suspend fun postTokenRequest(
        tokenEndpoint: String,
        formParams: Parameters,
        fallbackRefreshToken: String?
    ): AppResult<OAuthTokenBundle, DataError.Network> {
        val response = try {
            client.post(tokenEndpoint) {
                setBody(FormDataContent(formParams))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return AppResult.Failure(classifyNetworkThrowable(t))
        }

        val status = response.status
        return when {
            status.value in 200..299 -> {
                val rawBody = try {
                    response.bodyAsText()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    return AppResult.Failure(classifyNetworkThrowable(t))
                }
                val parsed = try {
                    json.decodeFromString<GoogleTokenResponse>(rawBody)
                } catch (_: SerializationException) {
                    return AppResult.Failure(DataError.Network.SERIALIZATION)
                }
                if (parsed.accessToken.isBlank()) {
                    return AppResult.Failure(DataError.Network.UNAUTHORIZED)
                }
                val expiresAt = clock.nowMillis() + (parsed.expiresInSeconds.coerceAtLeast(60L) * 1000L)
                AppResult.Success(
                    OAuthTokenBundle(
                        accessToken = parsed.accessToken,
                        refreshToken = parsed.refreshToken?.takeIf { it.isNotBlank() } ?: fallbackRefreshToken,
                        expiresAtEpochMillis = expiresAt,
                        tokenType = parsed.tokenType,
                        scope = parsed.scope.orEmpty()
                    )
                )
            }
            status == HttpStatusCode.BadRequest || status == HttpStatusCode.Unauthorized ->
                AppResult.Failure(DataError.Network.UNAUTHORIZED)
            status == HttpStatusCode.TooManyRequests ->
                AppResult.Failure(DataError.Network.RATE_LIMITED)
            status == HttpStatusCode.RequestTimeout ->
                AppResult.Failure(DataError.Network.TIMEOUT)
            status.value in 500..599 ->
                AppResult.Failure(DataError.Network.SERVER)
            else -> AppResult.Failure(DataError.Network.UNKNOWN)
        }
    }

    companion object {
        private const val VERIFIER_ENTROPY_BYTES = 64
        private const val STATE_ENTROPY_BYTES = 32
    }
}

@Serializable
private data class GoogleTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String? = null
)
