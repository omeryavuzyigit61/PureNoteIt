package com.notes.purenoteit.desktop.data.sync

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.sync.RemoteSyncDataSource
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.RemoteEncryptedChange
import com.notes.purenoteit.sync.model.RemoteSyncPullPage
import com.notes.purenoteit.sync.model.RemoteSyncPushAck
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Encrypted Same-Network Wi-Fi Ktor Server + WebSocket P2P broadcast relay.
 * Stores and serves strictly E2EE `ENC_P3` envelopes and `PVM3` media blobs, authenticating
 * every HTTP/WebSocket peer with constant-time SHA-256 pairing token verification.
 */
internal class LanWebSocketSyncServer : AutoCloseable {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val envelopes = CopyOnWriteArrayList<EncryptedDeltaEnvelope>()
    private val mediaBlobs = ConcurrentHashMap<String, ByteArray>()
    private val mutex = Mutex()

    private val _peerEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val peerEvents: SharedFlow<String> = _peerEvents.asSharedFlow()

    private var server: EmbeddedServer<*, *>? = null
    private var expectedTokenDigest: ByteArray? = null
    var activePort: Int = 0
        private set

    suspend fun start(pairCode: CharArray, requestedPort: Int = 0): Pair<String, Int> = mutex.withLock {
        stopLocked()
        val digest = hashPasscode(pairCode)
        expectedTokenDigest = digest
        val hostAddress = resolveLocalIpv4Address()

        val started = embeddedServer(ServerCIO, port = requestedPort, host = "0.0.0.0") {
            install(WebSockets)

            routing {
                get("/lan/deltas") {
                    if (!isAuthorized(call.request.headers["Authorization"])) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@get
                    }
                    val sinceIdx = call.request.queryParameters["since"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    val slice = if (sinceIdx < envelopes.size) envelopes.subList(sinceIdx, envelopes.size) else emptyList()
                    val now = System.currentTimeMillis()
                    val page = RemoteSyncPullPage(
                        changes = slice.mapIndexed { offset, env ->
                            RemoteEncryptedChange(
                                remoteId = "lan_${sinceIdx + offset}",
                                fileName = "${env.batchId}.enc",
                                modifiedAt = now,
                                isRemoteDeleted = false,
                                encryptedEnvelope = env.encryptedPayload
                            )
                        },
                        nextCursor = envelopes.size.toString(),
                        hasMore = false
                    )
                    call.respondText(json.encodeToString(page), ContentType.Application.Json)
                }

                post("/lan/deltas") {
                    if (!isAuthorized(call.request.headers["Authorization"])) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@post
                    }
                    val raw = call.receiveText()
                    val envelope = try {
                        json.decodeFromString<EncryptedDeltaEnvelope>(raw)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid envelope")
                        return@post
                    }
                    envelopes.add(envelope)
                    val ack = RemoteSyncPushAck(
                        remoteId = "lan_${envelopes.size - 1}",
                        newCursor = envelopes.size.toString(),
                        committedAt = System.currentTimeMillis()
                    )
                    _peerEvents.tryEmit("DELTA_PUSHED:${envelope.batchId}")
                    call.respondText(json.encodeToString(ack), ContentType.Application.Json)
                }

                put("/lan/media") {
                    if (!isAuthorized(call.request.headers["Authorization"])) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@put
                    }
                    val key = call.request.queryParameters["key"].orEmpty()
                    if (!isValidMediaKey(key)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid media key")
                        return@put
                    }
                    val bytes = call.receive<ByteArray>()
                    mediaBlobs[key] = bytes
                    call.respond(HttpStatusCode.Created, "OK")
                }

                get("/lan/media") {
                    if (!isAuthorized(call.request.headers["Authorization"])) {
                        call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                        return@get
                    }
                    val key = call.request.queryParameters["key"].orEmpty()
                    val blob = mediaBlobs[key]
                    if (blob == null) {
                        call.respond(HttpStatusCode.NotFound, "Not found")
                    } else {
                        call.respondBytes(blob, ContentType.Application.OctetStream)
                    }
                }

                webSocket("/lan/ws") {
                    val tokenHeader = call.request.headers["Authorization"]
                    if (!isAuthorized(tokenHeader)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                        return@webSocket
                    }
                    send(Frame.Text("PURE_SYNC_READY:${envelopes.size}"))
                    peerEvents.collect { msg ->
                        send(Frame.Text(msg))
                    }
                }
            }
        }.start(wait = false)

        server = started
        activePort = started.engine.resolvedConnectors().firstOrNull()?.port ?: requestedPort
        return hostAddress to activePort
    }

    suspend fun stop() = mutex.withLock {
        stopLocked()
    }

    private fun stopLocked() {
        server?.stop(200L, 1_000L)
        server = null
        activePort = 0
        expectedTokenDigest?.fill(0)
        expectedTokenDigest = null
    }

    private fun isAuthorized(authHeader: String?): Boolean {
        val expected = expectedTokenDigest ?: return false
        val provided = authHeader?.removePrefix("Bearer ")?.trim() ?: return false
        val providedBytes = provided.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, providedBytes)
    }

    private fun isValidMediaKey(key: String): Boolean =
        key.startsWith("media/") && !key.contains("..") && !key.contains('\\') && key.length in 10..180

    override fun close() {
        stopLocked()
    }

    companion object {
        fun hashPasscode(passcode: CharArray): ByteArray {
            val bytes = String(passcode).toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            bytes.fill(0)
            return digest.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
        }

        fun resolveLocalIpv4Address(): String {
            return try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.hostAddress
                    ?: "127.0.0.1"
            } catch (_: Exception) {
                "127.0.0.1"
            }
        }
    }
}

/**
 * [RemoteSyncDataSource] backed by a Same-Network Wi-Fi [LanWebSocketSyncServer] peer,
 * allowing `:shared`'s [com.notes.purenoteit.sync.SyncEngine] to sync over local Wi-Fi without cloud storage.
 */
internal class LanWebSocketSyncDataSourceImpl(
    private val baseHttpUrl: String,
    private val bearerHexToken: String,
    httpEngine: HttpClientEngine = CIO.create()
) : RemoteSyncDataSource {

    private val client = HttpClient(httpEngine)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val normalizedBase = baseHttpUrl.trimEnd('/')

    override suspend fun pullChanges(sinceCursor: String?): AppResult<RemoteSyncPullPage, DataError.Network> {
        return try {
            val since = sinceCursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val response = client.get("$normalizedBase/lan/deltas?since=$since") {
                header("Authorization", "Bearer $bearerHexToken")
            }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val page = json.decodeFromString<RemoteSyncPullPage>(response.bodyAsText())
                    AppResult.Success(page)
                }
                HttpStatusCode.Unauthorized -> AppResult.Failure(DataError.Network.UNAUTHORIZED)
                else -> AppResult.Failure(DataError.Network.SERVER)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Network.NO_CONNECTION)
        }
    }

    override suspend fun pushDelta(encryptedDelta: EncryptedDeltaEnvelope): AppResult<RemoteSyncPushAck, DataError.Network> {
        return try {
            val response = client.post("$normalizedBase/lan/deltas") {
                header("Authorization", "Bearer $bearerHexToken")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(encryptedDelta))
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> {
                    AppResult.Success(json.decodeFromString<RemoteSyncPushAck>(response.bodyAsText()))
                }
                HttpStatusCode.Unauthorized -> AppResult.Failure(DataError.Network.UNAUTHORIZED)
                else -> AppResult.Failure(DataError.Network.SERVER)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Network.NO_CONNECTION)
        }
    }

    override suspend fun uploadMediaBlob(mediaKey: String, encryptedBytes: ByteArray): AppResult<Unit, DataError.Network> {
        return try {
            val encodedKey = java.net.URLEncoder.encode(mediaKey, "UTF-8")
            val response = client.put("$normalizedBase/lan/media?key=$encodedKey") {
                header("Authorization", "Bearer $bearerHexToken")
                contentType(ContentType.Application.OctetStream)
                setBody(encryptedBytes)
            }
            if (response.status.value in 200..299) AppResult.Success(Unit) else AppResult.Failure(DataError.Network.UNAUTHORIZED)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Network.NO_CONNECTION)
        }
    }

    override suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network> {
        return try {
            val encodedKey = java.net.URLEncoder.encode(mediaKey, "UTF-8")
            val response = client.get("$normalizedBase/lan/media?key=$encodedKey") {
                header("Authorization", "Bearer $bearerHexToken")
            }
            when (response.status) {
                HttpStatusCode.OK -> AppResult.Success(response.bodyAsBytes())
                HttpStatusCode.Unauthorized -> AppResult.Failure(DataError.Network.UNAUTHORIZED)
                else -> AppResult.Failure(DataError.Network.SERVER)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppResult.Failure(DataError.Network.NO_CONNECTION)
        }
    }
}
