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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val mediaCacheMutex = Mutex()
    private val mediaIdCache = mutableMapOf<String, String>()
    private var mediaIndexLoadedAtMillis: Long = 0L
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private class FailFastNetworkException(val error: DataError.Network) : Exception()

    private suspend fun <T, R> mapParallelFailFast(
        items: List<T>,
        parallelism: Int,
        block: suspend (T) -> AppResult<R, DataError.Network>
    ): AppResult<List<R>, DataError.Network> {
        if (items.isEmpty()) return AppResult.Success(emptyList())
        if (items.size == 1) {
            return when (val single = block(items[0])) {
                is AppResult.Failure -> single
                is AppResult.Success -> AppResult.Success(listOf(single.data))
            }
        }
        return try {
            val results = coroutineScope {
                val gate = Semaphore(parallelism)
                items.map { item ->
                    async {
                        gate.withPermit {
                            when (val res = block(item)) {
                                is AppResult.Success -> res.data
                                is AppResult.Failure -> throw FailFastNetworkException(res.error)
                            }
                        }
                    }
                }.awaitAll()
            }
            AppResult.Success(results)
        } catch (e: FailFastNetworkException) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            AppResult.Failure(e.error)
        }
    }

    override suspend fun invalidateCaches() {
        mediaCacheMutex.withLock {
            mediaIdCache.clear()
            mediaIndexLoadedAtMillis = 0L
        }
        tokenProvider.invalidateCache()
    }

    override suspend fun prefetchMediaIndex(): AppResult<Unit, DataError.Network> {
        val now = clock.nowMillis()
        val isFresh = mediaCacheMutex.withLock {
            mediaIndexLoadedAtMillis > 0L && (now - mediaIndexLoadedAtMillis) in 0L until MEDIA_INDEX_TTL_MS
        }
        if (isFresh) return AppResult.Success(Unit)

        val fetched = LinkedHashMap<String, String>()
        var pageToken: String? = null
        var loop = 0

        do {
            val resp = executeWithAuthAndBackoff { token ->
                client.get("$DRIVE_API_BASE/files") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url {
                        parameters.append("spaces", "appDataFolder")
                        parameters.append("q", "trashed = false and name contains '$MEDIA_BLOB_PREFIX'")
                        parameters.append("orderBy", "createdTime asc")
                        parameters.append("fields", "files(id,name),nextPageToken")
                        parameters.append("pageSize", "100")
                        pageToken?.let { parameters.append("pageToken", it) }
                    }
                }
            }
            val parsed = when (resp) {
                is AppResult.Failure -> return resp
                is AppResult.Success -> decodeFileList(resp.data)
                    ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
            }
            for (item in parsed.files) {
                val name = item.name.orEmpty()
                if (name.startsWith(MEDIA_BLOB_PREFIX) && !fetched.containsKey(name)) {
                    fetched[name] = item.id
                }
            }
            pageToken = parsed.nextPageToken?.takeIf { it.isNotBlank() }
            loop++
        } while (pageToken != null && loop < MAX_INDEX_PAGES)

        val indexComplete = pageToken == null
        mediaCacheMutex.withLock {
            if (indexComplete) {
                mediaIdCache.clear()
                mediaIdCache.putAll(fetched)
                mediaIndexLoadedAtMillis = clock.nowMillis()
            } else {
                mediaIdCache.putAll(fetched)
                mediaIndexLoadedAtMillis = 0L
            }
        }
        return AppResult.Success(Unit)
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

            // Parallel (bounded + fail-fast) downloads; results keep Drive's modifiedTime order.
            val downloadedChanges = when (
                val dlRes = mapParallelFailFast(parsedList.files, DOWNLOAD_PARALLELISM) { meta ->
                    when (val contentRes = downloadTextFileContent(meta.id)) {
                        is AppResult.Failure -> contentRes
                        is AppResult.Success -> AppResult.Success(
                            RemoteEncryptedChange(
                                remoteId = meta.id,
                                fileName = meta.name,
                                modifiedAt = parseIso8601ToMillis(meta.modifiedTime),
                                isRemoteDeleted = false,
                                encryptedEnvelope = contentRes.data
                            )
                        )
                    }
                }
            ) {
                is AppResult.Failure -> return dlRes
                is AppResult.Success -> dlRes.data
            }
            allChanges += downloadedChanges
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

    private data class PendingIncrementalChange(
        val fileId: String,
        val fileName: String?,
        val modifiedAt: Long,
        val isDeleted: Boolean,
        val needsDownload: Boolean
    )

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

        val pendingChanges = mutableListOf<PendingIncrementalChange>()
        val mediaUpdates = mutableListOf<Pair<String, String?>>()
        for (change in parsed.changes) {
            val fileName = change.file?.name
            val fileId = change.fileId ?: change.file?.id
            val isDeleted = change.removed || change.file?.trashed == true

            if (fileName != null && fileName.startsWith(MEDIA_BLOB_PREFIX)) {
                mediaUpdates += fileName to (if (isDeleted) null else fileId)
            }

            val isDelta = fileName?.startsWith(DELTA_FILE_PREFIX) == true
            if (!change.removed && !isDelta) continue
            if (fileId == null) continue

            pendingChanges += PendingIncrementalChange(
                fileId = fileId,
                fileName = fileName,
                modifiedAt = parseIso8601ToMillis(change.file?.modifiedTime),
                isDeleted = isDeleted,
                needsDownload = !isDeleted && isDelta
            )
        }

        if (mediaUpdates.isNotEmpty()) {
            mediaCacheMutex.withLock {
                for ((name, id) in mediaUpdates) {
                    if (id == null) {
                        mediaIdCache.remove(name)
                    } else {
                        mediaIdCache[name] = id
                    }
                }
            }
        }

        val toDownload = pendingChanges.filter { it.needsDownload }
        val downloadedContents = when (
            val dlRes = mapParallelFailFast(toDownload, DOWNLOAD_PARALLELISM) { entry ->
                downloadTextFileContent(entry.fileId)
            }
        ) {
            is AppResult.Failure -> return dlRes
            is AppResult.Success -> dlRes.data
        }

        var dlIdx = 0
        val resultChanges = pendingChanges.map { entry ->
            val envelopeText = if (entry.needsDownload) downloadedContents[dlIdx++] else null
            RemoteEncryptedChange(
                remoteId = entry.fileId,
                fileName = entry.fileName,
                modifiedAt = entry.modifiedAt,
                isRemoteDeleted = entry.isDeleted,
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

        return AppResult.Success(
            RemoteSyncPushAck(
                remoteId = uploadedMeta.id,
                newCursor = encryptedDelta.baseCursor ?: "",
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

        val now = clock.nowMillis()
        val (cachedId, hasFreshCompleteIndex) = mediaCacheMutex.withLock {
            val existing = mediaIdCache[remoteFileName]
            val fresh = mediaIndexLoadedAtMillis > 0L && (now - mediaIndexLoadedAtMillis) in 0L until MEDIA_INDEX_TTL_MS
            existing to fresh
        }
        if (cachedId != null) {
            return AppResult.Success(Unit)
        }

        if (!hasFreshCompleteIndex) {
            val existingCheck = findFileIdByExactName(remoteFileName)
            when (existingCheck) {
                is AppResult.Failure -> return existingCheck
                is AppResult.Success -> {
                    val existingId = existingCheck.data
                    if (existingId != null) {
                        mediaCacheMutex.withLock { mediaIdCache[remoteFileName] = existingId }
                        return AppResult.Success(Unit)
                    }
                }
            }
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
            is AppResult.Success -> {
                val createdId = decodeCreatedFile(up.data)?.id
                if (!createdId.isNullOrBlank()) {
                    mediaCacheMutex.withLock { mediaIdCache[remoteFileName] = createdId }
                }
                AppResult.Success(Unit)
            }
        }
    }

    override suspend fun downloadMediaBlob(mediaKey: String): AppResult<ByteArray, DataError.Network> {
        val remoteFileName = toSafeMediaBlobName(mediaKey)
            ?: return AppResult.Failure(DataError.Network.UNKNOWN)

        val cachedId = mediaCacheMutex.withLock { mediaIdCache[remoteFileName] }
        if (cachedId != null) {
            var wasNotFound = false
            val cachedDownload = executeBinaryWithAuthAndBackoff(onNotFound = { wasNotFound = true }) { token ->
                client.get("$DRIVE_API_BASE/files/$cachedId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url { parameters.append("alt", "media") }
                }
            }
            if (cachedDownload is AppResult.Success) {
                return cachedDownload
            }
            if (!wasNotFound) {
                return cachedDownload
            }
            // Cached ID became stale (e.g., deleted/re-uploaded by another device); evict and re-lookup once.
            mediaCacheMutex.withLock {
                mediaIdCache.remove(remoteFileName)
                mediaIndexLoadedAtMillis = 0L
            }
        }

        val fileId = when (val findRes = findFileIdByExactName(remoteFileName)) {
            is AppResult.Failure -> return findRes
            is AppResult.Success -> findRes.data ?: return AppResult.Failure(DataError.Network.UNKNOWN)
        }
        mediaCacheMutex.withLock { mediaIdCache[remoteFileName] = fileId }

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

    override suspend fun upsertAppDataConfigFile(
        fileName: String,
        content: String
    ): AppResult<Unit, DataError.Network> {
        val safeName = toSafeConfigFileName(fileName) ?: return AppResult.Failure(DataError.Network.UNKNOWN)
        val existingId = when (val existing = findFileIdByExactName(safeName)) {
            is AppResult.Failure -> return existing
            is AppResult.Success -> existing.data
        }

        val contentBytes = content.encodeToByteArray()
        if (existingId != null) {
            val safeId = sanitizeFileSegment(existingId)
            return when (
                val patchRes = executeWithAuthAndBackoff { token ->
                    client.patch("$DRIVE_UPLOAD_BASE/files/$safeId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        url { parameters.append("uploadType", "media") }
                        contentType(ContentType.Application.Json)
                        setBody(contentBytes)
                    }
                }
            ) {
                is AppResult.Failure -> patchRes
                is AppResult.Success -> AppResult.Success(Unit)
            }
        }

        val metadataJson = json.encodeToString(
            DriveFileCreateMetadata(
                name = safeName,
                parents = listOf("appDataFolder"),
                mimeType = "application/json"
            )
        )
        return when (val up = uploadMultipartRelated(metadataJson, "application/json; charset=UTF-8", contentBytes)) {
            is AppResult.Failure -> up
            is AppResult.Success -> AppResult.Success(Unit)
        }
    }

    override suspend fun getAppDataUsageSummary(): AppResult<RemoteMediaUsageSummary, DataError.Network> {
        var mediaCount = 0
        var mediaBytes = 0L
        var deltaCount = 0
        var deltaBytes = 0L
        var pageToken: String? = null
        var loop = 0

        do {
            val resp = executeWithAuthAndBackoff { token ->
                client.get("$DRIVE_API_BASE/files") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url {
                        parameters.append("spaces", "appDataFolder")
                        parameters.append("q", "trashed = false")
                        parameters.append("fields", "files(id,name,size),nextPageToken")
                        parameters.append("pageSize", "100")
                        pageToken?.let { parameters.append("pageToken", it) }
                    }
                }
            }
            val parsed = when (resp) {
                is AppResult.Failure -> return resp
                is AppResult.Success -> decodeFileList(resp.data) ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
            }
            for (item in parsed.files) {
                val name = item.name.orEmpty()
                val sz = item.size?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                when {
                    name.startsWith(MEDIA_BLOB_PREFIX) -> {
                        mediaCount++
                        mediaBytes += sz
                    }
                    name.startsWith(DELTA_FILE_PREFIX) -> {
                        deltaCount++
                        deltaBytes += sz
                    }
                }
            }
            pageToken = parsed.nextPageToken?.takeIf { it.isNotBlank() }
            loop++
        } while (pageToken != null && loop < 25)

        return AppResult.Success(
            RemoteMediaUsageSummary(
                mediaFileCount = mediaCount,
                totalMediaBytes = mediaBytes,
                deltaFileCount = deltaCount,
                totalDeltaBytes = deltaBytes
            )
        )
    }

    override suspend fun pruneOrphanMediaBlobs(activeCanonicalMediaKeys: Set<String>): AppResult<Int, DataError.Network> {
        val activeBlobNames = activeCanonicalMediaKeys.mapNotNull { toSafeMediaBlobName(it) }.toSet()
        val orphanEntries = mutableListOf<Pair<String, String>>()
        var pageToken: String? = null
        var loop = 0

        do {
            val resp = executeWithAuthAndBackoff { token ->
                client.get("$DRIVE_API_BASE/files") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    url {
                        parameters.append("spaces", "appDataFolder")
                        parameters.append("q", "trashed = false and name contains '$MEDIA_BLOB_PREFIX'")
                        parameters.append("fields", "files(id,name),nextPageToken")
                        parameters.append("pageSize", "100")
                        pageToken?.let { parameters.append("pageToken", it) }
                    }
                }
            }
            val parsed = when (resp) {
                is AppResult.Failure -> return resp
                is AppResult.Success -> decodeFileList(resp.data) ?: return AppResult.Failure(DataError.Network.SERIALIZATION)
            }
            for (item in parsed.files) {
                val name = item.name.orEmpty()
                if (name.startsWith(MEDIA_BLOB_PREFIX) && name !in activeBlobNames) {
                    orphanEntries += item.id to name
                }
            }
            pageToken = parsed.nextPageToken?.takeIf { it.isNotBlank() }
            loop++
        } while (pageToken != null && loop < 25)

        var deletedCount = 0
        for ((fileId, fileName) in orphanEntries) {
            val safeId = sanitizeFileSegment(fileId)
            when (val delRes = executeBinaryWithAuthAndBackoff { token ->
                client.delete("$DRIVE_API_BASE/files/$safeId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }) {
                is AppResult.Success -> {
                    deletedCount++
                    mediaCacheMutex.withLock {
                        mediaIdCache.remove(fileName)
                        mediaIndexLoadedAtMillis = 0L
                    }
                }
                is AppResult.Failure -> return delRes
            }
        }
        return AppResult.Success(deletedCount)
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
        return when (val bytesRes = executeBinaryWithAuthAndBackoff(requestBlock = requestBlock)) {
            is AppResult.Failure -> bytesRes
            is AppResult.Success -> AppResult.Success(bytesRes.data.decodeToString())
        }
    }

    private suspend fun executeBinaryWithAuthAndBackoff(
        onNotFound: (() -> Unit)? = null,
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

                status == HttpStatusCode.NotFound -> {
                    onNotFound?.invoke()
                    return AppResult.Failure(DataError.Network.UNKNOWN)
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
        private const val DOWNLOAD_PARALLELISM = 6
        private const val MEDIA_INDEX_TTL_MS = 30_000L
        private const val MAX_INDEX_PAGES = 25
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
    @SerialName("size") val size: String? = null,
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
