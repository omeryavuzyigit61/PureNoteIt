package com.notes.purenoteit.sync

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import com.notes.purenoteit.sync.model.RemoteEncryptedChange
import com.notes.purenoteit.sync.model.RemoteSyncPullPage
import com.notes.purenoteit.sync.model.RemoteSyncPushAck
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.min
import kotlin.random.Random

/**
 * Primary server-free E2EE sync transport backed by Google Drive REST API v3 `appDataFolder`.
 * Implements delta tracking (`changes.list` + `startPageToken`), `429`/`503` Truncated Exponential
 * Backoff + Jitter, `401` single-flight `Mutex` token refresh, and zero sensitive header/payload logging.
 */
internal class GoogleDriveSyncDataSourceImpl(
    httpEngine: HttpClientEngine,
    private val tokenProvider: SyncTokenProvider,
    private val clock: AppClock,
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) : RemoteSyncDataSource {

    private val client = HttpClient(httpEngine)
    private val tokenRefreshMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    override suspend fun pullChanges(sinceCursor: String?): AppResult<RemoteSyncPullPage, DataError.Network> {
        return if (sinceCursor.isNullOrBlank()) {
            pullInitialSnapshot()
        } else {
            pullIncrementalChanges(sinceCursor)
        }
    }

    private suspend fun pullInitialSnapshot(): AppResult<RemoteSyncPullPage, DataError.Network> {
        val tokenResp = executeWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/changes/startPageToken") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url {
                    parameters.append("spaces", "appDataFolder")
                }
            }
        }
        val startToken = when (tokenResp) {
            is AppResult.Failure -> return tokenResp
            is AppResult.Success -> decodeStartPageToken(tokenResp.data) ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
        }

        val allChanges = mutableListOf<RemoteEncryptedChange>()
        var pageToken: String? = null
        do {
            val listResp = executeWithAuthAndBackoff { token ->
                client.get("$DRIVE_API_BASE/files") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url {
                        parameters.append("spaces", "appDataFolder")
                        parameters.append("q", "trashed = false and name contains '$DELTA_FILE_PREFIX'")
                        parameters.append("fields", "files(id,name,modifiedTime),nextPageToken")
                        parameters.append("orderBy", "modifiedTime asc")
                        parameters.append("pageSize", "100")
                        pageToken?.let { parameters.append("pageToken", it) }
                    }
                }
            }
            val parsedList = when (listResp) {
                is AppResult.Failure -> return listResp
                is AppResult.Success -> decodeFileList(listResp.data)
                    ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
            }

            for (fileMeta in parsedList.files) {
                val downloaded = downloadTextFileContent(fileMeta.id)
                when (downloaded) {
                    is AppResult.Failure -> return downloaded
                    is AppResult.Success -> {
                        allChanges += RemoteEncryptedChange(
                            remoteId = fileMeta.id,
                            fileName = fileMeta.name,
                            modifiedAt = parseIso8601ToMillis(fileMeta.modifiedTime),
                            isRemoteDeleted = false,
                            encryptedEnvelope = downloaded.data
                        )
                    }
                }
            }
            pageToken = parsedList.nextPageToken
        } while (!pageToken.isNullOrBlank())

        return AppResult.Success(
            RemoteSyncPullPage(
                changes = allChanges,
                nextCursor = startToken,
                hasMore = false
            )
        )
    }

    private suspend fun pullIncrementalChanges(sinceCursor: String): AppResult<RemoteSyncPullPage, DataError.Network> {
        val changesResp = executeWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/changes") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url {
                    parameters.append("pageToken", sinceCursor)
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("includeRemoved", "true")
                    parameters.append(
                        "fields",
                        "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,modifiedTime,trashed))"
                    )
                }
            }
        }
        val parsed = when (changesResp) {
            is AppResult.Failure -> return changesResp
            is AppResult.Success -> decodeChangesResponse(changesResp.data)
                ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
        }

        val resultChanges = mutableListOf<RemoteEncryptedChange>()
        for (change in parsed.changes) {
            val fileName = change.file?.name
            val isDelta = fileName?.startsWith(DELTA_FILE_PREFIX) == true
            if (!change.removed && !isDelta) continue

            val fileId = change.fileId ?: change.file?.id ?: continue
            val isDeleted = change.removed || change.file?.trashed == true
            val envelopeText = if (!isDeleted && isDelta) {
                when (val dl = downloadTextFileContent(fileId)) {
                    is AppResult.Failure -> return dl
                    is AppResult.Success -> dl.data
                }
            } else null

            resultChanges += RemoteEncryptedChange(
                remoteId = fileId,
                fileName = fileName,
                modifiedAt = parseIso8601ToMillis(change.file?.modifiedTime),
                isRemoteDeleted = isDeleted,
                encryptedEnvelope = envelopeText
            )
        }

        val nextCursor = parsed.nextPageToken ?: parsed.newStartPageToken ?: sinceCursor
        return AppResult.Success(
            RemoteSyncPullPage(
                changes = resultChanges,
                nextCursor = nextCursor,
                hasMore = !parsed.nextPageToken.isNullOrBlank()
            )
        )
    }

    override suspend fun pushDelta(
        encryptedDelta: EncryptedDeltaEnvelope
    ): AppResult<RemoteSyncPushAck, DataError.Network> {
        val safeBatch = sanitizeFileSegment(encryptedDelta.batchId)
        val fileName = "${DELTA_FILE_PREFIX}$safeBatch.enc"
        val metadataJson = json.encodeToString(
            DriveFileCreateMetadata(
                name = fileName,
                parents = listOf("appDataFolder"),
                mimeType = "text/plain"
            )
        )
        val payloadBytes = encryptedDelta.encryptedPayload.encodeToByteArray()

        val uploadResp = uploadMultipartRelated(
            metadataJson = metadataJson,
            mediaContentType = "text/plain; charset=UTF-8",
            mediaBytes = payloadBytes
        )
        val uploadedMeta = when (uploadResp) {
            is AppResult.Failure -> return uploadResp
            is AppResult.Success -> decodeCreatedFile(uploadResp.data)
                ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
        }

        val tokenResp = executeWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/changes/startPageToken") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url { parameters.append("spaces", "appDataFolder") }
            }
        }
        val latestCursor = when (tokenResp) {
            is AppResult.Failure -> encryptedDelta.baseCursor ?: ""
            is AppResult.Success -> decodeStartPageToken(tokenResp.data) ?: (encryptedDelta.baseCursor ?: "")
        }

        return AppResult.Success(
            RemoteSyncPushAck(
                remoteId = uploadedMeta.id,
                newCursor = latestCursor,
                committedAt = clock.nowMillis()
            )
        )
    }

    override suspend fun uploadMediaBlob(
        mediaKey: String,
        encryptedBytes: ByteArray
    ): AppResult<Unit, DataError.Network> {
        val remoteFileName = toSafeMediaBlobName(mediaKey)
            ?: return AppResult.Failure(DataError.Network.UNKNOWN)

        val existingCheck = findFileIdByExactName(remoteFileName)
        when (existingCheck) {
            is AppResult.Failure -> return existingCheck
            is AppResult.Success -> if (existingCheck.data != null) return AppResult.Success(Unit)
        }

        val metadataJson = json.encodeToString(
            DriveFileCreateMetadata(
                name = remoteFileName,
                parents = listOf("appDataFolder"),
                mimeType = "application/octet-stream"
            )
        )

        return when (val up = uploadMultipartRelated(metadataJson, "application/octet-stream", encryptedBytes)) {
            is AppResult.Failure -> up
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }

    override suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network> {
        val remoteFileName = toSafeMediaBlobName(mediaKey)
            ?: return AppResult.Failure(DataError.Network.UNKNOWN)

        val fileId = when (val findRes = findFileIdByExactName(remoteFileName)) {
            is AppResult.Failure -> return findRes
            is AppResult.Success -> findRes.data ?: return AppResult.Failure(DataError.Network.UNKNOWN)
        }

        return executeBinaryWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/files/$fileId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url { parameters.append("alt", "media") }
            }
        }
    }

    override suspend fun readAppDataConfigFile(fileName: String): AppResult<String?, DataError.Network> {
        val safeName = toSafeConfigFileName(fileName) ?: return AppResult.Failure(DataError.Network.UNKNOWN)
        val fileId = when (val findRes = findFileIdByExactName(safeName)) {
            is AppResult.Failure -> return findRes
            is AppResult.Success -> findRes.data ?: return AppResult.Success(null)
        }
        return when (val dl = downloadTextFileContent(fileId)) {
            is AppResult.Failure -> dl
            is AppResult.Success -> AppResult.Success(dl.data)
        }
    }

    override suspend fun getOrCreateAppDataConfigFile(
        fileName: String,
        content: String
    ): AppResult<String, DataError.Network> {
        val safeName = toSafeConfigFileName(fileName) ?: return AppResult.Failure(DataError.Network.UNKNOWN)
        when (val existing = findFileIdByExactName(safeName)) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> {
                val existingId = existing.data
                if (existingId != null) {
                    return downloadTextFileContent(existingId)
                }
            }
        }

        val metadataJson = json.encodeToString(
            DriveFileCreateMetadata(
                name = safeName,
                parents = listOf("appDataFolder"),
                mimeType = "application/json"
            )
        )
        when (val up = uploadMultipartRelated(metadataJson, "application/json; charset=UTF-8", content.encodeToByteArray())) {
            is AppResult.Failure -> return up
            is AppResult.Success -> Unit
        }

        return when (val verify = findFileIdByExactName(safeName)) {
            is AppResult.Failure -> AppResult.Success(content)
            is AppResult.Success -> {
                val earliestId = verify.data ?: return AppResult.Success(content)
                downloadTextFileContent(earliestId)
            }
        }
    }

    private fun toSafeConfigFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty() || trimmed.length > 96 || trimmed.contains("..")) return null
        if (!Regex("^[a-zA-Z0-9._-]+$").matches(trimmed)) return null
        return trimmed
    }

    private suspend fun findFileIdByExactName(exactSafeFileName: String): AppResult<String?, DataError.Network> {
        val resp = executeWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/files") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url {
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("q", "name = '$exactSafeFileName' and trashed = false")
                    parameters.append("orderBy", "createdTime asc")
                    parameters.append("fields", "files(id)")
                    parameters.append("pageSize", "1")
                }
            }
        }
        return when (resp) {
            is AppResult.Failure -> resp
            is AppResult.Success -> {
                val list = decodeFileList(resp.data) ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
                AppResult.Success(list.files.firstOrNull()?.id)
            }
        }
    }

    private suspend fun downloadTextFileContent(fileId: String): AppResult<String, DataError.Network> {
        val safeId = sanitizeFileSegment(fileId)
        return executeWithAuthAndBackoff { token ->
            client.get("$DRIVE_API_BASE/files/$safeId") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url { parameters.append("alt", "media") }
            }
        }
    }

    private suspend fun uploadMultipartRelated(
        metadataJson: String,
        mediaContentType: String,
        mediaBytes: ByteArray
    ): AppResult<String, DataError.Network> {
        val boundary = "purenoteit_${sharedRandomUuid().replace("-", "")}"
        val bodyBytes = buildMultipartRelatedBody(boundary, metadataJson, mediaContentType, mediaBytes)
        return executeWithAuthAndBackoff { token ->
            client.post("$DRIVE_UPLOAD_BASE/files") {
                header(HttpHeaders.Authorization, "Bearer $token")
                url { parameters.append("uploadType", "multipart") }
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(bodyBytes)
            }
        }
    }

    private suspend fun executeWithAuthAndBackoff(
        requestBlock: suspend (accessToken: String) -> HttpResponse
    ): AppResult<String, DataError.Network> {
        return when (val bytesRes = executeBinaryWithAuthAndBackoff(requestBlock)) {
            is AppResult.Failure -> bytesRes
            is AppResult.Success -> AppResult.Success(bytesRes.data.decodeToString())
        }
    }

    private suspend fun executeBinaryWithAuthAndBackoff(
        requestBlock: suspend (accessToken: String) -> HttpResponse
    ): AppResult<ByteArray, DataError.Network> {
        var token = when (val t = tokenProvider.getAccessToken()) {
            is AppResult.Success -> t.data
            is AppResult.Failure -> return t
        }
        var didRefreshAuth = false

        for (attempt in 0..MAX_RETRY_ATTEMPTS) {
            val response = try {
                requestBlock(token)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (attempt < MAX_RETRY_ATTEMPTS && isTransientThrowable(t)) {
                    delayFn(computeBackoffMs(attempt, retryAfterHeader = null))
                    continue
                }
                return AppResult.Failure(classifyNetworkThrowable(t))
            }

            val status = response.status
            when {
                status.value in 200..299 -> {
                    return try {
                        AppResult.Success(response.bodyAsBytes())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        AppResult.Failure(classifyNetworkThrowable(t))
                    }
                }

                status == HttpStatusCode.Unauthorized -> {
                    if (didRefreshAuth) {
                        return AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    }
                    didRefreshAuth = true
                    token = when (val refreshed = singleFlightRefreshToken(token)) {
                        is AppResult.Success -> refreshed.data
                        is AppResult.Failure -> return refreshed
                    }
                }

                status == HttpStatusCode.TooManyRequests || status == HttpStatusCode.ServiceUnavailable -> {
                    if (attempt >= MAX_RETRY_ATTEMPTS) {
                        return AppResult.Failure(
                            if (status == HttpStatusCode.TooManyRequests) DataError.Network.RATE_LIMITED
                            else DataError.Network.SERVER
                        )
                    }
                    val retryAfter = response.headers[HttpHeaders.RetryAfter]
                    delayFn(computeBackoffMs(attempt, retryAfter))
                }

                status == HttpStatusCode.Forbidden -> {
                    val errText = try { response.bodyAsText() } catch (_: Exception) { "" }
                    return if (errText.contains("Quota", ignoreCase = true) ||
                        errText.contains("rateLimitExceeded", ignoreCase = true)
                    ) {
                        AppResult.Failure(DataError.Network.QUOTA_EXCEEDED)
                    } else {
                        AppResult.Failure(DataError.Network.UNAUTHORIZED)
                    }
                }

                status == HttpStatusCode.RequestTimeout -> {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        delayFn(computeBackoffMs(attempt, retryAfterHeader = null))
                        continue
                    }
                    return AppResult.Failure(DataError.Network.TIMEOUT)
                }

                status.value in 500..599 -> {
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        delayFn(computeBackoffMs(attempt, retryAfterHeader = null))
                        continue
                    }
                    return AppResult.Failure(DataError.Network.SERVER)
                }

                else -> return AppResult.Failure(DataError.Network.UNKNOWN)
            }
        }
        return AppResult.Failure(DataError.Network.UNKNOWN)
    }

    private suspend fun singleFlightRefreshToken(expiredToken: String): AppResult<String, DataError.Network> {
        return tokenRefreshMutex.withLock {
            val currentResult = tokenProvider.getAccessToken()
            if (currentResult is AppResult.Success && currentResult.data != expiredToken) {
                return@withLock currentResult
            }
            tokenProvider.refreshAccessToken(expiredToken)
        }
    }

    private fun computeBackoffMs(attempt: Int, retryAfterHeader: String?): Long {
        val headerSeconds = retryAfterHeader?.trim()?.toLongOrNull()
        if (headerSeconds != null && headerSeconds in 1L..MAX_RETRY_AFTER_SEC) {
            return headerSeconds * 1000L
        }
        val expMs = BASE_BACKOFF_MS shl attempt.coerceIn(0, 4)
        val jitter = Random.nextLong(0L, BASE_BACKOFF_MS / 2)
        return min(expMs + jitter, MAX_BACKOFF_MS)
    }

    private fun isTransientThrowable(t: Throwable): Boolean {
        val classified = classifyNetworkThrowable(t)
        return classified == DataError.Network.TIMEOUT
    }

    private fun decodeStartPageToken(raw: String): String? = try {
        json.decodeFromString<DriveStartPageTokenResponse>(raw).startPageToken
    } catch (_: SerializationException) {
        null
    }

    private fun decodeFileList(raw: String): DriveFileListResponse? = try {
        json.decodeFromString<DriveFileListResponse>(raw)
    } catch (_: SerializationException) {
        null
    }

    private fun decodeChangesResponse(raw: String): DriveChangesResponse? = try {
        json.decodeFromString<DriveChangesResponse>(raw)
    } catch (_: SerializationException) {
        null
    }

    private fun decodeCreatedFile(raw: String): DriveFileItem? = try {
        json.decodeFromString<DriveFileItem>(raw)
    } catch (_: SerializationException) {
        null
    }

    private fun buildMultipartRelatedBody(
        boundary: String,
        metadataJson: String,
        mediaContentType: String,
        mediaBytes: ByteArray
    ): ByteArray {
        val headerPart = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: ").append(mediaContentType).append("\r\n\r\n")
        }.encodeToByteArray()
        val trailerPart = "\r\n--$boundary--".encodeToByteArray()

        val result = ByteArray(headerPart.size + mediaBytes.size + trailerPart.size)
        headerPart.copyInto(result, destinationOffset = 0)
        mediaBytes.copyInto(result, destinationOffset = headerPart.size)
        trailerPart.copyInto(result, destinationOffset = headerPart.size + mediaBytes.size)
        return result
    }

    internal fun toSafeMediaBlobName(mediaKey: String): String? {
        if (mediaKey.isBlank() || mediaKey.contains("..") || mediaKey.indexOf('\u0000') >= 0) return null
        val sanitized = mediaKey.trim()
            .replace('\\', '/')
            .trimStart('/')
            .replace('/', '_')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (sanitized.isBlank()) return null
        return "${MEDIA_BLOB_PREFIX}$sanitized.pvm3"
    }

    private fun sanitizeFileSegment(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80).ifBlank { "batch" }

    companion object {
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
        private const val DELTA_FILE_PREFIX = "sync_delta_"
        private const val MEDIA_BLOB_PREFIX = "media_blob_"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 400L
        private const val MAX_BACKOFF_MS = 8_000L
        private const val MAX_RETRY_AFTER_SEC = 15L
    }
}

@Serializable
private data class DriveStartPageTokenResponse(
    @SerialName("startPageToken") val startPageToken: String
)

@Serializable
private data class DriveFileCreateMetadata(
    @SerialName("name") val name: String,
    @SerialName("parents") val parents: List<String>,
    @SerialName("mimeType") val mimeType: String
)

@Serializable
private data class DriveFileItem(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("modifiedTime") val modifiedTime: String? = null,
    @SerialName("trashed") val trashed: Boolean = false
)

@Serializable
private data class DriveFileListResponse(
    @SerialName("files") val files: List<DriveFileItem> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null
)

@Serializable
private data class DriveChangeItem(
    @SerialName("fileId") val fileId: String? = null,
    @SerialName("removed") val removed: Boolean = false,
    @SerialName("file") val file: DriveFileItem? = null
)

@Serializable
private data class DriveChangesResponse(
    @SerialName("changes") val changes: List<DriveChangeItem> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    @SerialName("newStartPageToken") val newStartPageToken: String? = null
)
