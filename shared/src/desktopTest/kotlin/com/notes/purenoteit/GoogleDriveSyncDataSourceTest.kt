package com.notes.purenoteit

import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.security.OAuthTokenBundle
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.SyncTokenProvider
import com.notes.purenoteit.sync.model.EncryptedDeltaEnvelope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveSyncDataSourceTest {

    private val fixedClock = AppClock { 1_760_000_000_000L }

    @Test
    fun `429 Rate Limit yanitinda Retry-After ile exponential backoff yapip basarili olmalidir`() = runTest {
        val attempts = AtomicInteger(0)
        val recordedDelays = mutableListOf<Long>()

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/changes/startPageToken") -> {
                    respond(
                        content = """{"startPageToken":"page-10"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("/files?") -> {
                    val callNo = attempts.incrementAndGet()
                    if (callNo < 3) {
                        respond(
                            content = """{"error":{"code":429}}""",
                            status = HttpStatusCode.TooManyRequests,
                            headers = headersOf(HttpHeaders.RetryAfter, "2")
                        )
                    } else {
                        respond(
                            content = """{"files":[{"id":"file-1","name":"sync_delta_b1.enc","modifiedTime":"2026-11-10T10:00:00Z"}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                }
                url.contains("/files/file-1") -> {
                    respond(
                        content = "ENC_P3_PWD:dummy-ciphertext",
                        status = HttpStatusCode.OK
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenProvider = object : SyncTokenProvider {
            override suspend fun getAccessToken() = AppResult.Success("access-token-1")
            override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("access-token-2")
        }

        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = tokenProvider,
            clock = fixedClock,
            httpEngine = mockEngine,
            delayFn = { ms -> recordedDelays += ms }
        )

        val result = dataSource.pullChanges(sinceCursor = null)
        assertTrue(result is AppResult.Success)
        val page = (result as AppResult.Success).data
        assertEquals("page-10", page.nextCursor)
        assertEquals(1, page.changes.size)
        assertEquals("ENC_P3_PWD:dummy-ciphertext", page.changes[0].encryptedEnvelope)
        assertEquals(listOf(2000L, 2000L), recordedDelays)
    }

    @Test
    fun `401 Unauthorized durumlarinda concurrent isteklerde Mutex ile yalnizca tek token yenileme yapilmalidir`() = runTest {
        val currentToken = java.util.concurrent.atomic.AtomicReference("expired-token")
        val refreshCount = AtomicInteger(0)

        val mockEngine = MockEngine { request ->
            val authHeader = request.headers[HttpHeaders.Authorization]
            if (authHeader != "Bearer fresh-token") {
                respond("", HttpStatusCode.Unauthorized)
            } else {
                respond(
                    content = """{"startPageToken":"cursor-2"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val tokenProvider = object : SyncTokenProvider {
            override suspend fun getAccessToken(): AppResult<String, DataError.Network> =
                AppResult.Success(currentToken.get())

            override suspend fun refreshAccessToken(expiredToken: String?): AppResult<String, DataError.Network> {
                refreshCount.incrementAndGet()
                currentToken.set("fresh-token")
                return AppResult.Success("fresh-token")
            }
        }

        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = tokenProvider,
            clock = fixedClock,
            httpEngine = mockEngine,
            delayFn = {}
        )

        // Fire concurrent pulls hitting 401 simultaneously
        val results = listOf(
            async { dataSource.pullChanges(null) },
            async { dataSource.pullChanges(null) },
            async { dataSource.pullChanges(null) }
        ).awaitAll()

        assertEquals(1, refreshCount.get())
        // Note: second mock endpoint `/files?` wasn't matched after startPageToken above, mock returned 200 {"startPageToken":"cursor-2"}
        // Wait: all 3 calls parsed startPageToken cleanly with fresh token -> verify no UNAUTHORIZED failed
        assertTrue(results.none { it is AppResult.Failure && it.error == DataError.Network.UNAUTHORIZED })
    }

    @Test
    fun `pushDelta ve uploadMediaBlob multipart appDataFolder yuklemesini dogru calistirmalidir`() = runTest {
        val uploadedNames = mutableListOf<String>()
        val blobBytes = byteArrayOf(0x50, 0x56, 0x4D, 0x33, 1, 2, 3)

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/upload/drive/v3/files") -> {
                    val bodyBytes = request.body.toByteArray()
                    val bodyText = bodyBytes.decodeToString()
                    uploadedNames += bodyText
                    respond(
                        content = """{"id":"drv-id-99"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("/files?") -> respond(
                    content = """{"files":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                url.contains("/changes/startPageToken") -> respond(
                    content = """{"startPageToken":"cursor-99"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = object : SyncTokenProvider {
                override suspend fun getAccessToken() = AppResult.Success("ok-token")
                override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok-token")
            },
            clock = fixedClock,
            httpEngine = mockEngine,
            delayFn = {}
        )

        val blobRes = dataSource.uploadMediaBlob("media/drawings/abcd.png", blobBytes)
        assertTrue(blobRes is AppResult.Success)
        assertTrue(uploadedNames.single().contains("appDataFolder"))
        assertTrue(uploadedNames.single().contains("media_blob_media_drawings_abcd.png.pvm3"))

        // Verify path traversal key is rejected cleanly
        val evilKeyRes = dataSource.uploadMediaBlob("../../etc/passwd", blobBytes)
        assertTrue(evilKeyRes is AppResult.Failure)

        val pushRes = dataSource.pushDelta(
            EncryptedDeltaEnvelope(
                batchId = "batch-1",
                sourceDeviceId = "dev-a",
                encryptedPayload = "ENC_P3_PWD:cipher",
                baseCursor = "cursor-1"
            )
        )
        assertTrue(pushRes is AppResult.Success)
        assertEquals("drv-id-99", (pushRes as AppResult.Success).data.remoteId)
        assertEquals("cursor-1", pushRes.data.newCursor)
    }

    @Test
    fun `downloadMediaBlob Drive uzerinden ham PVM3 baytlarini indirmelidir`() = runTest {
        val fakePvm3 = byteArrayOf(0x50, 0x56, 0x4D, 0x33, 9, 8, 7)
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/files?") -> respond(
                    content = """{"files":[{"id":"blob-file-42"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                url.contains("/files/blob-file-42") -> respond(
                    content = fakePvm3,
                    status = HttpStatusCode.OK
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = object : SyncTokenProvider {
                override suspend fun getAccessToken() = AppResult.Success("ok")
                override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
            },
            clock = fixedClock,
            httpEngine = mockEngine,
            delayFn = {}
        )

        val dlRes = dataSource.downloadMediaBlob("media/images/sample.jpg")
        assertTrue(dlRes is AppResult.Success)
        assertArrayEquals(fakePvm3, (dlRes as AppResult.Success).data)
    }

    @Test
    fun `AutoSyncKeyCoordinator ilk cihazda anahtar olusturmali ikinci cihaz Drive appDataFolder uzerinden ayni anahtari almalidir`() = runTest {
        var uploadedConfigJson: String? = null
        val uploadCount = AtomicInteger(0)

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/upload/drive/v3/files") -> {
                    uploadCount.incrementAndGet()
                    val multipart = request.body.toByteArray().decodeToString()
                    val jsonStart = multipart.indexOf("{\"version\"")
                    val jsonEnd = multipart.indexOf("}", jsonStart)
                    uploadedConfigJson = multipart.substring(jsonStart, jsonEnd + 1)
                    respond(
                        content = """{"id":"cfg-key-1","name":"purenoteit_auto_sync_key_v1.json"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                url.contains("/files/cfg-key-1") -> respond(
                    content = uploadedConfigJson.orEmpty(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                url.contains("/files?") -> respond(
                    content = if (uploadedConfigJson == null) {
                        """{"files":[]}"""
                    } else {
                        """{"files":[{"id":"cfg-key-1"}]}"""
                    },
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val tokenProvider = object : SyncTokenProvider {
            override suspend fun getAccessToken() = AppResult.Success("ok")
            override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
        }

        val storageA = InMemoryTokenStorage()
        val dataSourceA = SharedSyncFactory.createGoogleDriveDataSource(tokenProvider, fixedClock, mockEngine, delayFn = {})
        val coordinatorA = SharedSyncFactory.createAutoSyncKeyCoordinator(dataSourceA, storageA, fixedClock)

        val keyA = (coordinatorA.resolveOrBootstrapPassphrase() as AppResult.Success).data
        assertTrue(keyA.isNotEmpty())
        assertEquals(1, uploadCount.get())

        val storageB = InMemoryTokenStorage()
        val dataSourceB = SharedSyncFactory.createGoogleDriveDataSource(tokenProvider, fixedClock, mockEngine, delayFn = {})
        val coordinatorB = SharedSyncFactory.createAutoSyncKeyCoordinator(dataSourceB, storageB, fixedClock)

        val keyB = (coordinatorB.resolveOrBootstrapPassphrase() as AppResult.Success).data
        assertArrayEquals(keyA, keyB)
        assertEquals(1, uploadCount.get())
    }

    @Test
    fun `readAppDataConfigFile gecersiz dosya adlarinda CWE-22 yolu engellemelidir`() = runTest {
        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = object : SyncTokenProvider {
                override suspend fun getAccessToken() = AppResult.Success("ok")
                override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
            },
            clock = fixedClock,
            httpEngine = MockEngine { respond("", HttpStatusCode.OK) },
            delayFn = {}
        )
        val res = dataSource.readAppDataConfigFile("../secret.json")
        assertEquals(AppResult.Failure(DataError.Network.UNKNOWN), res)
    }

    @Test
    fun `getAppDataUsageSummary ve pruneOrphanMediaBlobs kullanilmayan medyalari Drive dan temizlemelidir`() = runTest {
        val activeSha = "a".repeat(64)
        val orphanSha = "b".repeat(64)
        val activeKey = "media/images/$activeSha.jpg"
        val activeBlobName = "media_blob_media_images_${activeSha}.jpg.pvm3"
        val orphanBlobName = "media_blob_media_drawings_${orphanSha}.png.pvm3"
        val deletedIds = mutableListOf<String>()

        val mockEngine = MockEngine { req ->
            val url = req.url.toString()
            when {
                req.method == HttpMethod.Delete && url.contains("/files/orphan-blob-1") -> {
                    deletedIds += "orphan-blob-1"
                    respond("", HttpStatusCode.NoContent)
                }
                req.method == HttpMethod.Get && url.contains("/files?") -> {
                    respond(
                        content = """{"files":[
                            {"id":"delta-1","name":"sync_delta_1.enc","size":"400"},
                            {"id":"active-blob-1","name":"$activeBlobName","size":"120000"},
                            {"id":"orphan-blob-1","name":"$orphanBlobName","size":"85000"}
                        ]}""".trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
            tokenProvider = object : SyncTokenProvider {
                override suspend fun getAccessToken() = AppResult.Success("ok")
                override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
            },
            clock = fixedClock,
            httpEngine = mockEngine,
            delayFn = {}
        )

        val usageRes = dataSource.getAppDataUsageSummary()
        assertTrue(usageRes is AppResult.Success)
        val usage = (usageRes as AppResult.Success).data
        assertEquals(2, usage.mediaFileCount)
        assertEquals(205_000L, usage.totalMediaBytes)
        assertEquals(1, usage.deltaFileCount)
        assertEquals(400L, usage.totalDeltaBytes)

        val pruneRes = dataSource.pruneOrphanMediaBlobs(setOf(activeKey))
        assertEquals(AppResult.Success(1), pruneRes)
        assertEquals(listOf("orphan-blob-1"), deletedIds)
    }

    @Test
    fun `pullIncrementalChanges birden fazla deltayi paralel indirip sirayi korumali ve ilk hatada fail-fast iptal etmelidir`() =
        runTest {
            val mockEngine = MockEngine { req ->
                val url = req.url.toString()
                when {
                    url.contains("/changes?") -> respond(
                        content = """{
                            "newStartPageToken":"cursor-next",
                            "changes":[
                                {"fileId":"d-1","removed":false,"file":{"id":"d-1","name":"sync_delta_1.enc","modifiedTime":"2026-11-19T10:00:01Z"}},
                                {"fileId":"d-del","removed":true},
                                {"fileId":"d-2","removed":false,"file":{"id":"d-2","name":"sync_delta_2.enc","modifiedTime":"2026-11-19T10:00:02Z"}},
                                {"fileId":"d-3","removed":false,"file":{"id":"d-3","name":"sync_delta_3.enc","modifiedTime":"2026-11-19T10:00:03Z"}},
                                {"fileId":"d-4","removed":false,"file":{"id":"d-4","name":"sync_delta_4.enc","modifiedTime":"2026-11-19T10:00:04Z"}}
                            ]
                        }""".trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    url.contains("/files/d-") -> {
                        val id = url.substringAfter("/files/").substringBefore("?")
                        respond(content = "ENV_$id", status = HttpStatusCode.OK)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }

            val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
                tokenProvider = object : SyncTokenProvider {
                    override suspend fun getAccessToken() = AppResult.Success("ok")
                    override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
                },
                clock = fixedClock,
                httpEngine = mockEngine,
                delayFn = {}
            )

            val res = dataSource.pullChanges(sinceCursor = "cursor-1")
            assertTrue(res is AppResult.Success)
            val page = (res as AppResult.Success).data
            assertEquals("cursor-next", page.nextCursor)
            assertEquals(listOf("d-1", "d-del", "d-2", "d-3", "d-4"), page.changes.map { it.remoteId })
            assertEquals(listOf("ENV_d-1", null, "ENV_d-2", "ENV_d-3", "ENV_d-4"), page.changes.map { it.encryptedEnvelope })

            // Fail-fast check: 16 files queued behind Semaphore(6); first file fails with 403 -> remaining queued items should cancel
            val startedDownloads = AtomicInteger(0)
            val failFastEngine = MockEngine { req ->
                val url = req.url.toString()
                when {
                    url.contains("/changes?") -> {
                        val items = (1..16).joinToString(",") { i ->
                            """{"fileId":"f-$i","removed":false,"file":{"id":"f-$i","name":"sync_delta_$i.enc","modifiedTime":"2026-11-19T10:00:00Z"}}"""
                        }
                        respond(
                            content = """{"newStartPageToken":"c2","changes":[$items]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    url.contains("/files/f-1?") -> {
                        startedDownloads.incrementAndGet()
                        respond("""{"error":{"message":"forbidden"}}""", HttpStatusCode.Forbidden)
                    }
                    url.contains("/files/f-") -> {
                        startedDownloads.incrementAndGet()
                        kotlinx.coroutines.delay(5_000L)
                        respond("ENV", HttpStatusCode.OK)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val failFastDs = SharedSyncFactory.createGoogleDriveDataSource(
                tokenProvider = object : SyncTokenProvider {
                    override suspend fun getAccessToken() = AppResult.Success("ok")
                    override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
                },
                clock = fixedClock,
                httpEngine = failFastEngine,
                delayFn = {}
            )
            val failRes = failFastDs.pullChanges(sinceCursor = "c1")
            assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), failRes)
            assertTrue("Fail-fast iptal ile 16 dosyanin hepsi indirilmemeli (baslayan=${startedDownloads.get()})", startedDownloads.get() < 16)
        }

    @Test
    fun `prefetchMediaIndex medya fileId lerini tek sorguda onbellege almali ve stale 404 durumunda yenilemelidir`() =
        runTest {
            val listQueries = AtomicInteger(0)
            val uploads = AtomicInteger(0)
            val k1 = "media/images/${"1".repeat(64)}.jpg"
            val k2 = "media/drawings/${"2".repeat(64)}.png"
            val kNew = "media/images/${"3".repeat(64)}.jpg"
            val name1 = "media_blob_media_images_${"1".repeat(64)}.jpg.pvm3"
            val name2 = "media_blob_media_drawings_${"2".repeat(64)}.png.pvm3"
            val staleReturned = java.util.concurrent.atomic.AtomicBoolean(false)

            val mockEngine = MockEngine { req ->
                val url = req.url.toString()
                when {
                    req.method == HttpMethod.Get && url.contains("/files?") -> {
                        listQueries.incrementAndGet()
                        if (url.contains("name+%3D") || url.contains("name%20%3D") || url.contains("name =")) {
                            respond(
                                content = """{"files":[{"id":"fresh-id-2"}]}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        } else {
                            respond(
                                content = """{"files":[{"id":"id-1","name":"$name1"},{"id":"stale-id-2","name":"$name2"}]}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                    }
                    req.method == HttpMethod.Get && url.contains("/files/id-1") ->
                        respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK)
                    req.method == HttpMethod.Get && url.contains("/files/stale-id-2") -> {
                        staleReturned.set(true)
                        respond("", HttpStatusCode.NotFound)
                    }
                    req.method == HttpMethod.Get && url.contains("/files/fresh-id-2") ->
                        respond(byteArrayOf(4, 5, 6), HttpStatusCode.OK)
                    req.method == HttpMethod.Get && url.contains("/files/uploaded-id-3") ->
                        respond(byteArrayOf(7, 8, 9), HttpStatusCode.OK)
                    req.method == HttpMethod.Post && url.contains("/upload/drive/v3/files") -> {
                        uploads.incrementAndGet()
                        respond(
                            content = """{"id":"uploaded-id-3"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }

            val tokenInvalidations = AtomicInteger(0)
            val dataSource = SharedSyncFactory.createGoogleDriveDataSource(
                tokenProvider = object : SyncTokenProvider {
                    override suspend fun getAccessToken() = AppResult.Success("ok")
                    override suspend fun refreshAccessToken(expiredToken: String?) = AppResult.Success("ok")
                    override fun invalidateCache() { tokenInvalidations.incrementAndGet() }
                },
                clock = fixedClock,
                httpEngine = mockEngine,
                delayFn = {}
            )

            // 1. Prefetch loads both media file IDs in 1 query; second prefetch within TTL is a no-op
            assertTrue(dataSource.prefetchMediaIndex() is AppResult.Success)
            assertTrue(dataSource.prefetchMediaIndex() is AppResult.Success)
            assertEquals(1, listQueries.get())

            // 2. Download k1 uses cached id-1 with 0 extra list queries
            val dl1 = dataSource.downloadMediaBlob(k1) as AppResult.Success
            assertArrayEquals(byteArrayOf(1, 2, 3), dl1.data)
            assertEquals(1, listQueries.get())

            // 3. Upload already-indexed k1 -> 0 HTTP requests
            assertTrue(dataSource.uploadMediaBlob(k1, byteArrayOf(1)) is AppResult.Success)
            assertEquals(0, uploads.get())
            assertEquals(1, listQueries.get())

            // 4. Upload brand-new kNew while index is fresh -> skips lookup and caches returned ID
            assertTrue(dataSource.uploadMediaBlob(kNew, byteArrayOf(7, 8, 9)) is AppResult.Success)
            assertEquals(1, uploads.get())
            assertEquals(1, listQueries.get())
            val dlNew = dataSource.downloadMediaBlob(kNew) as AppResult.Success
            assertArrayEquals(byteArrayOf(7, 8, 9), dlNew.data)
            assertEquals(1, listQueries.get())

            // 5. Stale cached ID returning 404 triggers eviction + single fallback lookup + retry
            val dl2 = dataSource.downloadMediaBlob(k2) as AppResult.Success
            assertTrue(staleReturned.get())
            assertArrayEquals(byteArrayOf(4, 5, 6), dl2.data)
            assertEquals(2, listQueries.get())

            // 6. invalidateCaches clears both media index and token provider cache
            dataSource.invalidateCaches()
            assertEquals(1, tokenInvalidations.get())
        }

    private class InMemoryTokenStorage : SecureTokenStorage {
        private var bundle: OAuthTokenBundle? = null
        private var pass: CharArray? = null
        override suspend fun saveTokenBundle(bundle: OAuthTokenBundle): AppResult<Unit, DataError.Security> {
            this.bundle = bundle
            return AppResult.Success(Unit)
        }
        override suspend fun getTokenBundle() = AppResult.Success(bundle)
        override suspend fun saveSyncPassphrase(passphrase: CharArray): AppResult<Unit, DataError.Security> {
            this.pass?.fill('\u0000')
            this.pass = passphrase.copyOf()
            return AppResult.Success(Unit)
        }
        override suspend fun getSyncPassphrase() = AppResult.Success(pass?.copyOf())
        override suspend fun clearAll(): AppResult<Unit, DataError.Security> {
            bundle = null
            pass?.fill('\u0000')
            pass = null
            return AppResult.Success(Unit)
        }
    }

    private fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray {
        return when (this) {
            is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
            else -> ByteArray(0)
        }
    }
}
