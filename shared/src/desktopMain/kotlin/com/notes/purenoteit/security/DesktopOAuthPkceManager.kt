package com.notes.purenoteit.security

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI

/**
 * Desktop OAuth 2.0 PKCE Flow Manager implementing RFC 8252 Ephemeral Loopback Redirect:
 * - Binds strictly to IPv4 loopback `127.0.0.1` (`port = 0` ephemeral assignment, NEVER `0.0.0.0`).
 * - Opens authorization URL cleanly in the user's default OS browser (`Desktop.browse`).
 * - Caps incoming HTTP request line length (8 KB DoS guard) and never reflects untrusted query params in HTML.
 * - Verifies `state` CSRF nonce in constant time before exchanging `code` + `code_verifier`.
 */
class DesktopOAuthPkceManager(
    private val pkceProtocol: OAuthPkceProtocol,
    private val tokenStorage: SecureTokenStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val browserLauncher: (String) -> Boolean = { url -> openSystemBrowser(url) }
) {
    suspend fun authorizeInteractive(
        config: OAuthPkceConfig,
        timeoutMs: Long = DEFAULT_AUTH_TIMEOUT_MS
    ): AppResult<OAuthTokenBundle, DataError.Network> {
        val loopbackAddr = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val serverSocket = try {
            withContext(ioDispatcher) {
                ServerSocket(0, 1, loopbackAddr)
            }
        } catch (_: Exception) {
            return AppResult.Failure(DataError.Network.UNKNOWN)
        }

        val port = serverSocket.localPort
        val loopbackRedirectUri = "http://127.0.0.1:$port$CALLBACK_PATH"

        val session = when (val s = pkceProtocol.createPkceSession(config, redirectUriOverride = loopbackRedirectUri)) {
            is AppResult.Failure -> {
                closeQuietly(serverSocket)
                return s
            }
            is AppResult.Success -> s.data
        }

        return try {
            if (!browserLauncher(session.authorizationUrl)) {
                return AppResult.Failure(DataError.Network.UNKNOWN)
            }

            val authCodeResult = withTimeout(timeoutMs) {
                withContext(ioDispatcher) {
                    acceptAndValidateLoopbackCallback(serverSocket, port, session.state)
                }
            }

            val authCode = when (authCodeResult) {
                is AppResult.Failure -> return authCodeResult
                is AppResult.Success -> authCodeResult.data
            }

            val tokenResult = pkceProtocol.exchangeAuthorizationCode(
                config = config,
                code = authCode,
                codeVerifier = session.codeVerifier,
                redirectUri = session.redirectUri
            )

            when (tokenResult) {
                is AppResult.Failure -> tokenResult
                is AppResult.Success -> {
                    when (tokenStorage.saveTokenBundle(tokenResult.data)) {
                        is AppResult.Failure -> AppResult.Failure(DataError.Network.UNKNOWN)
                        is AppResult.Success -> tokenResult
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            AppResult.Failure(DataError.Network.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } finally {
            closeQuietly(serverSocket)
        }
    }

    private fun acceptAndValidateLoopbackCallback(
        serverSocket: ServerSocket,
        port: Int,
        expectedState: String
    ): AppResult<String, DataError.Network> {
        serverSocket.soTimeout = SOCKET_ACCEPT_POLL_TIMEOUT_MS
        while (true) {
            val socket = try {
                serverSocket.accept()
            } catch (_: java.net.SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                return AppResult.Failure(DataError.Network.UNKNOWN)
            }

            socket.use { client ->
                client.soTimeout = CLIENT_READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val requestLine = readBoundedLine(reader, MAX_REQUEST_LINE_BYTES)
                val targetUriSuffix = extractGetTarget(requestLine)
                if (targetUriSuffix == null) {
                    writeStaticHtmlResponse(client, statusLine = "400 Bad Request", html = HTML_FAILURE)
                    return AppResult.Failure(DataError.Network.UNAUTHORIZED)
                }

                val fullUri = "http://127.0.0.1:$port$targetUriSuffix"
                val validation = pkceProtocol.parseAndValidateCallback(
                    callbackUri = fullUri,
                    expectedState = expectedState,
                    expectedPath = CALLBACK_PATH
                )
                return when (validation) {
                    is AppResult.Success -> {
                        writeStaticHtmlResponse(client, statusLine = "200 OK", html = HTML_SUCCESS)
                        validation
                    }
                    is AppResult.Failure -> {
                        writeStaticHtmlResponse(client, statusLine = "400 Bad Request", html = HTML_FAILURE)
                        validation
                    }
                }
            }
        }
    }

    private fun readBoundedLine(reader: BufferedReader, maxChars: Int): String? {
        val sb = StringBuilder()
        while (sb.length < maxChars) {
            val ch = reader.read()
            if (ch == -1 || ch == '\n'.code) break
            if (ch != '\r'.code) sb.append(ch.toChar())
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun extractGetTarget(requestLine: String?): String? {
        if (requestLine.isNullOrBlank()) return null
        val parts = requestLine.split(' ')
        if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) return null
        val path = parts[1].trim()
        return if (path.startsWith("/")) path else null
    }

    private fun writeStaticHtmlResponse(client: java.net.Socket, statusLine: String, html: String) {
        try {
            val body = html.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("HTTP/1.1 ").append(statusLine).append("\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("Content-Length: ").append(body.size).append("\r\n")
                append("Cache-Control: no-store\r\n")
                append("X-Content-Type-Options: nosniff\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(Charsets.US_ASCII)
            client.getOutputStream().apply {
                write(headers)
                write(body)
                flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly(socket: ServerSocket) {
        try {
            if (!socket.isClosed) socket.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CALLBACK_PATH = "/oauth2redirect"
        private const val DEFAULT_AUTH_TIMEOUT_MS = 120_000L
        private const val SOCKET_ACCEPT_POLL_TIMEOUT_MS = 2_000
        private const val CLIENT_READ_TIMEOUT_MS = 5_000
        private const val MAX_REQUEST_LINE_BYTES = 8_192

        private const val HTML_SUCCESS =
            "<!doctype html><html><head><meta charset='utf-8'><title>PureNoteIt</title></head>" +
                "<body style='font-family:sans-serif;text-align:center;margin-top:48px;'>" +
                "<h2>Oturum Başarıyla Bağlandı</h2>" +
                "<p>Bu tarayıcı sekmesini kapatıp PureNoteIt masaüstü uygulamasına dönebilirsiniz.</p>" +
                "</body></html>"

        private const val HTML_FAILURE =
            "<!doctype html><html><head><meta charset='utf-8'><title>PureNoteIt</title></head>" +
                "<body style='font-family:sans-serif;text-align:center;margin-top:48px;'>" +
                "<h2>Yetkilendirme Doğrulanamadı</h2>" +
                "<p>Lütfen PureNoteIt uygulamasından bağlantıyı yeniden başlatın.</p>" +
                "</body></html>"

        private fun openSystemBrowser(url: String): Boolean {
            return try {
                if (!Desktop.isDesktopSupported()) return false
                val desktop = Desktop.getDesktop()
                if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
                desktop.browse(URI(url))
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
