package com.notes.purenoteit.desktop

import com.notes.purenoteit.SharedCryptoFactory
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.SharedSyncFactory
import com.notes.purenoteit.desktop.audio.DesktopAudioRecorder
import com.notes.purenoteit.desktop.data.db.DesktopSqliteDatabaseManager
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepositoryImpl
import com.notes.purenoteit.desktop.data.security.DesktopNoteVaultManager
import com.notes.purenoteit.desktop.data.settings.DesktopPreferencesStore
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.data.storage.DesktopImageOptimizer
import com.notes.purenoteit.desktop.data.sync.DesktopLocalSyncStoreImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncDataSourceImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.desktop.ui.components.DesktopDrawingTool
import com.notes.purenoteit.desktop.ui.components.DesktopNoteExporter
import com.notes.purenoteit.desktop.ui.components.DesktopStroke
import com.notes.purenoteit.desktop.ui.components.renderStrokesToPng
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.model.SyncSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color as AwtColor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import com.notes.purenoteit.security.VaultPinSyncProtocol
import org.junit.Assert.assertFalse

class DesktopDataAndSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var nowMs = 1_760_100_000_000L
    private val clock = AppClock { nowMs }

    @Test
    fun `SQLite v6 semasi varsayilan kategoriler Wikilink AltSayfa ve Tombstone silme calismalidir`() = runBlocking {
        val dbFile = tempFolder.newFile("test_repo.db")
        val db = DesktopSqliteDatabaseManager(dbFile, clock, Dispatchers.IO)
        db.use {
            val repo = DesktopNoteRepositoryImpl(db, clock)

            val categories = repo.observeCategories().first()
            assertEquals(listOf("cat-default-1", "cat-default-2", "cat-default-3"), categories.map { it.syncUuid })

            val target = (repo.upsertNote(Note(title = "Mimari", description = "Temel")) as AppResult.Success).data
            val sourceBlocks = listOf(
                NoteBlock(type = BlockType.PARAGRAPH, content = "Bakınız [[Mimari]] notu.")
            )
            val source = (
                repo.upsertNote(
                    Note(
                        title = "Ana Plan",
                        description = BlockParser.serialize(sourceBlocks),
                        categoryId = categories.first().id,
                        categorySyncUuid = categories.first().syncUuid
                    )
                ) as AppResult.Success
                ).data

            val linksAfterCreate = repo.observeNoteLinks().first()
            assertEquals(1, linksAfterCreate.size)
            assertEquals(source.syncUuid, linksAfterCreate[0].sourceSyncUuid)
            assertEquals(target.syncUuid, linksAfterCreate[0].targetSyncUuid)

            // Create sub-page and verify parent link + child note creation
            val childRes = repo.createSubPage(source.syncUuid, "Alt Sayfa Detayı")
            assertTrue(childRes is AppResult.Success)
            assertEquals(2, repo.observeNoteLinks().first().size)

            // Tombstone soft-delete & restore
            repo.softDeleteNote(source.syncUuid)
            assertFalseOrEmpty(repo.observeNotes(includeDeleted = false).first().any { it.syncUuid == source.syncUuid })
            val allIncludingDeleted = repo.observeNotes(includeDeleted = true).first()
            val tombstoned = allIncludingDeleted.first { it.syncUuid == source.syncUuid }
            assertNotNull(tombstoned.deletedAt)

            repo.restoreNote(source.syncUuid)
            val restored = repo.observeNotes(includeDeleted = false).first().first { it.syncUuid == source.syncUuid }
            assertNull(restored.deletedAt)
        }
    }

    @Test
    fun `Iki Desktop cihazi LAN WebSocket Ktor sunucusu uzerinden E2EE ENC_P3 ve PVM3 medya ile senkronize olmalidir`() =
        runBlocking {
            val dirA = tempFolder.newFolder("deviceA")
            val dirB = tempFolder.newFolder("deviceB")

            val dbA = DesktopSqliteDatabaseManager(File(dirA, "a.db"), clock, Dispatchers.IO)
            val dbB = DesktopSqliteDatabaseManager(File(dirB, "b.db"), clock, Dispatchers.IO)

            val mediaA = SharedStorageFactory.createMediaPathResolver(File(dirA, "files"), File(dirA, "cache"))
            val mediaB = SharedStorageFactory.createMediaPathResolver(File(dirB, "files"), File(dirB, "cache"))

            val syncStoreA = DesktopLocalSyncStoreImpl(dbA, mediaA, File(dirA, "cursor.json"))
            val syncStoreB = DesktopLocalSyncStoreImpl(dbB, mediaB, File(dirB, "cursor.json"))

            val repoA = DesktopNoteRepositoryImpl(dbA, clock)
            val repoB = DesktopNoteRepositoryImpl(dbB, clock)

            val crypto = SharedCryptoFactory.createVaultCrypto()
            val server = LanWebSocketSyncServer()

            try {
                val pin = "849205".toCharArray()
                val (_, port) = server.start(pin.copyOf(), requestedPort = 0)
                val baseUrl = "https://127.0.0.1:$port"
                val pin256 = server.certFingerprintHex

                // Verify unauthorized token is rejected (401 UNAUTHORIZED)
                val rogueClient = LanWebSocketSyncDataSourceImpl(baseUrl, "wrong-token", pin256)
                assertEquals(AppResult.Failure(DataError.Network.UNAUTHORIZED), rogueClient.pullChanges(null))

                val validHexToken = String(LanWebSocketSyncServer.hashPasscode(pin), Charsets.UTF_8)
                pin.fill('\u0000')

                val remoteA = LanWebSocketSyncDataSourceImpl(baseUrl, validHexToken, pin256)
                val remoteB = LanWebSocketSyncDataSourceImpl(baseUrl, validHexToken, pin256)

                val engineA = SyncEngine(syncStoreA, remoteA, crypto, ConflictResolver(clock), clock)
                val engineB = SyncEngine(syncStoreB, remoteB, crypto, ConflictResolver(clock), clock)

                // Populate Device A with drawing PNG media + rich blocks + subpage
                val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4, 5)
                val drawingPath = mediaA.storeMediaBytes(pngBytes, "drawings", "png")
                val noteA = (
                    repoA.upsertNote(
                        Note(
                            title = "Ortak Çizimli Not",
                            description = BlockParser.serialize(
                                listOf(
                                    NoteBlock(type = BlockType.HEADING_1, content = "Proje Mimarisi"),
                                    NoteBlock(type = BlockType.DRAWING, mediaPath = drawingPath),
                                    NoteBlock(type = BlockType.CODE, codeLanguage = "python", content = "print(1)")
                                )
                            ),
                            date = nowMs
                        )
                    ) as AppResult.Success
                    ).data
                nowMs += 50L
                repoA.createSubPage(noteA.syncUuid, "Alt Tasarım")
                nowMs += 50L
                val streamRes = repoA.addStreamItem("Mobilde yazılan hızlı fikir")
                assertTrue(streamRes is AppResult.Success)

                val passA = "PureNoteSyncPass!2026".toCharArray()
                val pushRes = engineA.synchronize(passA)
                assertTrue("Cihaz A push basarili olmali: $pushRes", pushRes is AppResult.Success)
                val pushSummary = (pushRes as AppResult.Success<SyncSummary>).data
                assertTrue("En az 2 not gonderilmis olmali: ${pushSummary.pushedEntities}", pushSummary.pushedEntities >= 2)

                // Wrong E2EE passphrase on Device B must fail decryption without saving corrupted data
                val wrongPass = "WrongPass!999".toCharArray()
                val failedPull = engineB.synchronize(wrongPass)
                assertEquals(AppResult.Failure(DataError.Security.DECRYPTION_FAILED), failedPull)

                // Correct passphrase on Device B pulls all notes, subpage, stream_items & verified SHA-256 media bytes
                val passB = "PureNoteSyncPass!2026".toCharArray()
                val pullRes = engineB.synchronize(passB)
                assertTrue("Cihaz B pull basarili olmali: $pullRes", pullRes is AppResult.Success)

                val notesB = repoB.observeNotes(includeDeleted = false).first()
                assertEquals(2, notesB.size)
                val syncedMain = notesB.first { it.syncUuid == noteA.syncUuid }
                assertEquals("Ortak Çizimli Not", syncedMain.title)

                val streamB = repoB.observeStreamItems().first()
                assertEquals(1, streamB.size)
                assertEquals("Mobilde yazılan hızlı fikir", streamB[0].content)

                // Convert synced stream item into a Note on Device B and confirm PDF & Markdown export
                val convRes = repoB.convertStreamItemsToNote(listOf(streamB[0].syncUuid), "Senkronize Fikir")
                assertTrue(convRes is AppResult.Success)
                val convertedStream = repoB.observeStreamItems().first().first()
                assertTrue(convertedStream.isProcessed)
                assertNotNull(convertedStream.mergedNoteSyncUuid)

                val md = DesktopNoteExporter.buildMarkdown(
                    title = "Türkçe PDF Testi ğüşöçı",
                    categoryName = "Projeler",
                    blocks = listOf(
                        NoteBlock(type = BlockType.HEADING_1, content = "Ana Başlık"),
                        NoteBlock(type = BlockType.TODO, content = "Görev 1", isChecked = true)
                    )
                )
                assertTrue(md.contains("- [x] Görev 1"))
                val pdfBytes = DesktopNoteExporter.renderToPdfBytes(
                    title = "Türkçe PDF Testi ğüşöçı",
                    categoryName = "Projeler",
                    blocks = listOf(
                        NoteBlock(type = BlockType.HEADING_1, content = "Ana Başlık"),
                        NoteBlock(type = BlockType.TODO, content = "Görev 1", isChecked = true)
                    )
                )
                val pdfHeader = String(pdfBytes.copyOfRange(0, 8), Charsets.ISO_8859_1)
                val pdfTail = String(pdfBytes.copyOfRange(pdfBytes.size - 10, pdfBytes.size), Charsets.ISO_8859_1)
                assertTrue("PDF %PDF-1.4 ile baslamali: $pdfHeader", pdfHeader.startsWith("%PDF-1.4"))
                assertTrue("PDF %%EOF ile bitmeli: $pdfTail", pdfTail.contains("%%EOF"))

                val syncedMedia = syncStoreB.readLocalMediaBytes(drawingPath)
                assertNotNull(syncedMedia)
                assertArrayEquals(pngBytes, syncedMedia)
            } finally {
                server.close()
                dbA.close()
                dbB.close()
            }
        }

    @Test
    fun `Iki yonlu resim ve cizim medyasi optimizasyonu ve senkronizasyonu eksiksiz calismalidir`() = runBlocking {
        val dirPhone = tempFolder.newFolder("phoneSide")
        val dirPc = tempFolder.newFolder("pcSide")

        val dbPhone = DesktopSqliteDatabaseManager(File(dirPhone, "phone.db"), clock, Dispatchers.IO)
        val dbPc = DesktopSqliteDatabaseManager(File(dirPc, "pc.db"), clock, Dispatchers.IO)

        val mediaPhone = SharedStorageFactory.createMediaPathResolver(File(dirPhone, "files"), File(dirPhone, "cache"))
        val mediaPc = SharedStorageFactory.createMediaPathResolver(File(dirPc, "files"), File(dirPc, "cache"))

        val storePhone = DesktopLocalSyncStoreImpl(dbPhone, mediaPhone, File(dirPhone, "cursor.json"))
        val storePc = DesktopLocalSyncStoreImpl(dbPc, mediaPc, File(dirPc, "cursor.json"))

        val repoPhone = DesktopNoteRepositoryImpl(dbPhone, clock)
        val repoPc = DesktopNoteRepositoryImpl(dbPc, clock)

        val crypto = SharedCryptoFactory.createVaultCrypto()
        val server = LanWebSocketSyncServer()

        try {
            val pin = "551199".toCharArray()
            val (_, port) = server.start(pin.copyOf(), requestedPort = 0)
            val baseUrl = "https://127.0.0.1:$port"
            val lanPin = server.certFingerprintHex
            val tokenHex = String(LanWebSocketSyncServer.hashPasscode(pin), Charsets.UTF_8)
            pin.fill('\u0000')

            val enginePhone = SyncEngine(storePhone, LanWebSocketSyncDataSourceImpl(baseUrl, tokenHex, lanPin), crypto, ConflictResolver(clock), clock)
            val enginePc = SyncEngine(storePc, LanWebSocketSyncDataSourceImpl(baseUrl, tokenHex, lanPin), crypto, ConflictResolver(clock), clock)

            // 1. Optimize an oversized 2400x1600 synthetic photo-like image & verify size drop + valid decode
            val rng = java.util.Random(42L)
            val largeImg = BufferedImage(2400, 1600, BufferedImage.TYPE_INT_RGB).apply {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val r = ((x * 255) / width + rng.nextInt(40)).coerceIn(0, 255)
                        val g = ((y * 255) / height + rng.nextInt(40)).coerceIn(0, 255)
                        val b = (((x + y) * 128) / (width + height) + rng.nextInt(50)).coerceIn(0, 255)
                        setRGB(x, y, (r shl 16) or (g shl 8) or b)
                    }
                }
            }
            val rawPngBaos = ByteArrayOutputStream()
            ImageIO.write(largeImg, "png", rawPngBaos)
            val rawLargeBytes = rawPngBaos.toByteArray()

            val (optBytes, optExt) = DesktopImageOptimizer.optimizeForSync(rawLargeBytes, "png", isDrawing = false)
            assertTrue("Optimize edilmis boyut kuculmeli (${optBytes.size} < ${rawLargeBytes.size})", optBytes.size < rawLargeBytes.size)
            val decodedBitmap = DesktopImageOptimizer.decodeComposeBitmap(optBytes)
            assertNotNull("Optimize gorsel cozumlenebilmeli", decodedBitmap)
            assertTrue("Maksimum kenar <= 1600 olmalı", maxOf(decodedBitmap!!.width, decodedBitmap.height) <= 1600)

            // 2. Phone creates a note with BOTH Photo (IMAGE) and Sketch (DRAWING) -> Sync to PC
            val sketchImg = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                g.color = AwtColor.ORANGE
                g.fillOval(50, 50, 200, 150)
                g.dispose()
            }
            val sketchBaos = ByteArrayOutputStream()
            ImageIO.write(sketchImg, "png", sketchBaos)
            val sketchBytes = sketchBaos.toByteArray()

            val phonePhotoKey = mediaPhone.storeMediaBytes(optBytes, "images", optExt)
            val phoneSketchKey = mediaPhone.storeMediaBytes(sketchBytes, "drawings", "png")

            repoPhone.upsertNote(
                Note(
                    title = "Telefondan Gorsel ve Cizim",
                    description = BlockParser.serialize(
                        listOf(
                            NoteBlock(type = BlockType.IMAGE, mediaPath = phonePhotoKey),
                            NoteBlock(type = BlockType.DRAWING, mediaPath = phoneSketchKey)
                        )
                    ),
                    date = nowMs
                )
            )
            nowMs += 100L

            val pass = "SharedMediaPass!2026".toCharArray()
            assertTrue(enginePhone.synchronize(pass.copyOf()) is AppResult.Success)
            val pcPull = enginePc.synchronize(pass.copyOf()) as AppResult.Success
            assertEquals(1, pcPull.data.pulledNotes)
            assertEquals(2, pcPull.data.mediaDownloaded)

            val pcReadPhoto = storePc.readLocalMediaBytes(phonePhotoKey)!!
            val pcReadSketch = storePc.readLocalMediaBytes(phoneSketchKey)!!
            assertArrayEquals(optBytes, pcReadPhoto)
            assertArrayEquals(sketchBytes, pcReadSketch)
            assertNotNull(DesktopImageOptimizer.decodeComposeBitmap(pcReadPhoto))
            assertNotNull(DesktopImageOptimizer.decodeComposeBitmap(pcReadSketch))

            // 3. PC creates a NEW note with Drawing & Image -> Sync back to Phone!
            val pcDrawingKey = mediaPc.storeMediaBytes(sketchBytes.copyOf() + byteArrayOf(0), "drawings", "png")
            val pcPhotoKey = mediaPc.storeMediaBytes(optBytes.copyOf(), "images", optExt)
            nowMs += 100L
            repoPc.upsertNote(
                Note(
                    title = "PC Den Gorsel ve Cizim",
                    description = BlockParser.serialize(
                        listOf(
                            NoteBlock(type = BlockType.DRAWING, mediaPath = pcDrawingKey),
                            NoteBlock(type = BlockType.IMAGE, mediaPath = pcPhotoKey)
                        )
                    ),
                    date = nowMs
                )
            )
            nowMs += 100L

            assertTrue(enginePc.synchronize(pass.copyOf()) is AppResult.Success)
            val phonePull = enginePhone.synchronize(pass.copyOf()) as AppResult.Success
            assertEquals(1, phonePull.data.pulledNotes)
            assertEquals(1, phonePull.data.mediaDownloaded) // pcPhotoKey is deduped against phonePhotoKey SHA-256!
            assertNotNull(storePhone.readLocalMediaBytes(pcDrawingKey))
            assertNotNull(storePhone.readLocalMediaBytes(pcPhotoKey))

            // Verify DesktopNoteRepository does not mirror DRAWING blocks into note.images & readModifiedSince is strictly '>'
            val savedPcNote = repoPc.observeNotes(includeDeleted = false).first().first { it.title == "PC Den Gorsel ve Cizim" }
            assertEquals(listOf(pcPhotoKey), savedPcNote.images.orEmpty())
            val repeatedPcSync = enginePc.synchronize(pass.copyOf()) as AppResult.Success
            assertEquals(0, repeatedPcSync.data.pushedEntities)
        } finally {
            server.close()
            dbPhone.close()
            dbPc.close()
        }
    }

    @Test
    fun `yabanci subPageNoteId tasiyan eski ebeveyn notu yereldeki ilgisiz notla cakismaz ve sonradan gelen cocuga baglanir`() = runBlocking {
        val dbDir = tempFolder.newFolder("subpage_collision_test")
        var nowMs = 5_000_000L
        val clock = AppClock { nowMs }
        val db = DesktopSqliteDatabaseManager(File(dbDir, "desktop.db"), clock, Dispatchers.IO)
        try {
            db.ensureInitializedWithCurrentKey()
            val media = SharedStorageFactory.createMediaPathResolver(File(dbDir, "files"), File(dbDir, "cache"))
            val store = DesktopLocalSyncStoreImpl(db, media, File(dbDir, "cursor.json"))
            // Unrelated local note occupies id = 1 or 2 depending on insert order
            store.applyResolvedRemoteBatch(
                com.notes.purenoteit.sync.ResolvedRemoteBatch(
                    notes = listOf(
                        Note(
                            title = "Ajanda: 8 Kasım",
                            description = "NOTION_BLOCKS_V1:[]",
                            syncUuid = "ajanda-8-uuid",
                            updatedAt = nowMs
                        )
                    )
                )
            )
            val ajandaId = store.findNotesBySyncUuids(listOf("ajanda-8-uuid")).getValue("ajanda-8-uuid").id

            // Batch 1: Parent with legacy colliding subPageNoteId == ajandaId, no subPageSyncUuid
            nowMs += 100L
            store.applyResolvedRemoteBatch(
                com.notes.purenoteit.sync.ResolvedRemoteBatch(
                    notes = listOf(
                        Note(
                            title = "İş",
                            description = BlockParser.serialize(
                                listOf(
                                    NoteBlock(
                                        type = BlockType.SUB_PAGE,
                                        content = "Site1",
                                        subPageNoteId = ajandaId,
                                        subPageSyncUuid = null
                                    )
                                )
                            ),
                            syncUuid = "work-parent-uuid",
                            updatedAt = nowMs
                        )
                    )
                )
            )
            val parentAfterBatch1 = store.findNotesBySyncUuids(listOf("work-parent-uuid")).getValue("work-parent-uuid")
            assertNull(BlockParser.parse(parentAfterBatch1.description).single().subPageNoteId)

            // Batch 2: Child "Site1" arrives later -> parent is remapped cleanly across batches to Site1's new ID!
            nowMs += 100L
            store.applyResolvedRemoteBatch(
                com.notes.purenoteit.sync.ResolvedRemoteBatch(
                    notes = listOf(
                        Note(
                            title = "Site1",
                            description = "NOTION_BLOCKS_V1:[]",
                            richBlocksJson = BlockParser.buildSubPageMetadataJson(999, "work-parent-uuid"),
                            syncUuid = "site1-child-uuid",
                            updatedAt = nowMs
                        )
                    )
                )
            )
            val childStored = store.findNotesBySyncUuids(listOf("site1-child-uuid")).getValue("site1-child-uuid")
            val parentAfterBatch2 = store.findNotesBySyncUuids(listOf("work-parent-uuid")).getValue("work-parent-uuid")
            val remappedBlock = BlockParser.parse(parentAfterBatch2.description).single()
            assertEquals(childStored.id, remappedBlock.subPageNoteId)
            assertEquals("site1-child-uuid", remappedBlock.subPageSyncUuid)
        } finally {
            db.close()
        }
    }

    @Test
    fun `mevcut cizim PNG uzerine yeni firca darbeleri eklenince eski cizim korunur ve yeni cizgiler islenir`() {
        val stroke1 = DesktopStroke(
            points = listOf(Offset(20f, 20f), Offset(80f, 20f)),
            color = ComposeColor.Red,
            widthPx = 6f
        )
        val initialPng = renderStrokesToPng(
            baseImage = null,
            strokes = listOf(stroke1),
            width = 320,
            height = 240
        )
        val baseImg = ImageIO.read(ByteArrayInputStream(initialPng))
        val stroke2 = DesktopStroke(
            points = listOf(Offset(20f, 120f), Offset(180f, 120f)),
            color = ComposeColor.Blue,
            widthPx = 6f
        )
        val continuedPng = renderStrokesToPng(
            baseImage = baseImg,
            strokes = listOf(stroke2),
            width = 320,
            height = 240
        )
        val resultImg = ImageIO.read(ByteArrayInputStream(continuedPng))

        // First red line pixel around (50, 20) MUST still be non-white (red dominant)
        val p1 = java.awt.Color(resultImg.getRGB(50, 20), true)
        assertTrue("Existing base drawing pixel should be preserved", p1.red > 200 && p1.blue < 50)

        // Second blue line pixel around (100, 120) MUST also be drawn (blue dominant)
        val p2 = java.awt.Color(resultImg.getRGB(100, 120), true)
        assertTrue("New continued stroke should be composited", p2.blue > 200 && p2.red < 50)
    }

    @Test
    fun `yeni cizim araclari Line Rect Circle Triangle Arrow Star PNG uzerine cizilir`() {
        val strokes = listOf(
            DesktopStroke(listOf(Offset(10f, 20f), Offset(90f, 20f)), ComposeColor.Red, 6f, DesktopDrawingTool.LINE),
            DesktopStroke(listOf(Offset(10f, 50f), Offset(90f, 110f)), ComposeColor.Blue, 6f, DesktopDrawingTool.RECTANGLE),
            DesktopStroke(listOf(Offset(110f, 50f), Offset(190f, 110f)), ComposeColor.Green, 6f, DesktopDrawingTool.CIRCLE),
            DesktopStroke(listOf(Offset(210f, 50f), Offset(290f, 110f)), ComposeColor.Black, 6f, DesktopDrawingTool.TRIANGLE),
            DesktopStroke(listOf(Offset(10f, 140f), Offset(110f, 180f)), ComposeColor.Magenta, 6f, DesktopDrawingTool.ARROW),
            DesktopStroke(listOf(Offset(140f, 130f), Offset(220f, 210f)), ComposeColor.Red, 6f, DesktopDrawingTool.STAR)
        )
        val png = renderStrokesToPng(baseImage = null, strokes = strokes, width = 320, height = 240)
        val img = ImageIO.read(ByteArrayInputStream(png))
        assertNotNull(img)
        // Red horizontal line top border at (50, 20)
        val linePx = java.awt.Color(img.getRGB(50, 20), true)
        assertTrue(linePx.red > 180 && linePx.blue < 80)
        // Blue rect top border at (50, 50)
        val rectPx = java.awt.Color(img.getRGB(50, 50), true)
        assertTrue(rectPx.blue > 180 && rectPx.red < 80)
        // Star apex near (180, 130)
        val starPx = java.awt.Color(img.getRGB(180, 131), true)
        assertTrue(starPx.red > 150 && starPx.blue < 120)
    }

    @Test
    fun `DesktopAudioRecorder WAV basligi uretir ve ses kaydi PDM1 sifreli saklanir`() = runBlocking {
        val root = tempFolder.newFolder("audio_enc_test")
        val delegate = SharedStorageFactory.createMediaPathResolver(root, File(root, "cache"))
        val mediaKey = ByteArray(32) { (it + 7).toByte() }
        val encStorage = DesktopEncryptedMediaStorage(delegate, { mediaKey }, root, Dispatchers.IO)

        val pcmSample = ByteArray(320) { i -> (i % 97).toByte() }
        val wavBytes = DesktopAudioRecorder.encodePcmToWav(
            pcmData = pcmSample,
            format = javax.sound.sampled.AudioFormat(16_000f, 16, 1, true, false)
        )
        assertEquals("RIFF", String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals(44 + pcmSample.size, wavBytes.size)

        val relPath = encStorage.storeMediaBytes(wavBytes, "audio", "wav")
        assertTrue(relPath.startsWith("media/audio/") && relPath.endsWith(".wav"))

        val diskFile = File(delegate.resolveToReadablePath(relPath)!!)
        val diskBytes = diskFile.readBytes()
        assertEquals("PDM1", String(diskBytes.copyOfRange(0, 4), Charsets.US_ASCII))

        val decrypted = encStorage.readDecryptedBytes(relPath)
        assertArrayEquals(wavBytes, decrypted)
    }

    @Test
    fun `cop kutusu suresi dolan notlari otomatik temizler ve kalici silme DELETED tombstonena gecirir`() = runBlocking {
        val dbDir = tempFolder.newFolder("trash_purge_test")
        var ms = 10_000_000_000L
        val clk = AppClock { ms }
        val db = DesktopSqliteDatabaseManager(File(dbDir, "trash.db"), clk, Dispatchers.IO)
        try {
            val repo = DesktopNoteRepositoryImpl(db, clk)
            val oldNote = (repo.upsertNote(Note(title = "Eski Cop", description = "10 gun once", syncUuid = "old-trash-uuid")) as AppResult.Success).data
            val recentNote = (repo.upsertNote(Note(title = "Yeni Cop", description = "1 gun once", syncUuid = "new-trash-uuid")) as AppResult.Success).data
            val activeNote = (repo.upsertNote(Note(title = "Aktif", description = "kalir", syncUuid = "active-uuid")) as AppResult.Success).data

            val dayMs = 86_400_000L
            // Trash oldNote 10 days ago
            repo.softDeleteNote(oldNote.syncUuid)
            // Trash recentNote 9 days later (= 1 day before check)
            ms += 9L * dayMs
            repo.softDeleteNote(recentNote.syncUuid)
            ms += 1L * dayMs

            val purgedCount = (repo.purgeExpiredTrashNotes(thresholdMillis = ms - 7L * dayMs) as AppResult.Success).data
            assertEquals(1, purgedCount)

            val allInc = repo.observeNotes(includeDeleted = true).first()
            assertEquals("DELETED", allInc.first { it.syncUuid == oldNote.syncUuid }.status)
            assertEquals("TRASH", allInc.first { it.syncUuid == recentNote.syncUuid }.status)
            assertEquals("TODO", allInc.first { it.syncUuid == activeNote.syncUuid }.status)

            // Permanent delete recentNote explicitly
            assertTrue(repo.permanentDeleteNote(recentNote.syncUuid) is AppResult.Success)
            val afterPerm = repo.observeNotes(includeDeleted = true).first()
            assertEquals("DELETED", afterPerm.first { it.syncUuid == recentNote.syncUuid }.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun `DesktopNoteVaultManager ve DesktopPreferencesStore duz PIN sizdirmaz ve uzak PIN degisiminde oturumu kilitler`() = runBlocking {
        val dir = tempFolder.newFolder("note_vault_mgr_test")
        var ms = 1_760_600_000_000L
        val clk = AppClock { ms }
        val crypto = SharedCryptoFactory.createVaultCrypto()
        val pinFile = File(dir, "vault_pin_sync.json")
        val prefsFile = File(dir, "desktop_prefs.json")

        val prefsStore = DesktopPreferencesStore(prefsFile)
        val savedPrefs = prefsStore.update { it.copy(language = "EN", trashRetentionDays = 14, hideLockedNotes = true) }
        assertEquals("en", savedPrefs.language)
        assertEquals(14, savedPrefs.trashRetentionDays)
        assertTrue(savedPrefs.hideLockedNotes)
        assertEquals("en", prefsStore.load().language)

        val mgr = DesktopNoteVaultManager(pinFile, crypto, clk, Dispatchers.IO, "pc-test")
        mgr.use {
            val pinRes = mgr.setOrChangePin(null, "135790".toCharArray()) as AppResult.Success
            assertTrue(pinRes.data.second.isPinSet)
            assertTrue(pinRes.data.second.isUnlocked)
            assertFalse("Plaintext PIN must never exist in vault_pin_sync.json", pinFile.readText().contains("135790"))

            val encRes = mgr.encryptNoteDescription("Gizli Metin 🔐") as AppResult.Success
            assertTrue(encRes.data.startsWith("ENC_P3_PWD:"))

            mgr.lockSessionSync()
            assertFalse(mgr.isUnlocked())
            assertTrue(mgr.decryptNoteDescription(encRes.data) is AppResult.Failure)

            // Wrong PIN fails & triggers cooldown on 5th attempt
            repeat(5) {
                assertTrue(mgr.verifyPinAndUnlock("000000".toCharArray()) is AppResult.Failure)
            }
            assertTrue(mgr.currentCooldownSeconds() > 0)

            // Advance clock past cooldown & unlock with valid PIN
            ms += 40_000L
            assertTrue(mgr.verifyPinAndUnlock("135790".toCharArray()) is AppResult.Success)
            assertEquals("Gizli Metin 🔐", (mgr.decryptNoteDescription(encRes.data) as AppResult.Success).data)

            // Simulate Phone rotating PIN -> PC MUST lock active session immediately & accept new PIN only
            val remoteProtocol = VaultPinSyncProtocol(crypto)
            val currentDoc = mgr.exportPinDocument()
            val rotated = (remoteProtocol.createPinDocument("246810".toCharArray(), null, currentDoc, ms + 1_000L, "phone-2") as AppResult.Success).data
            rotated.second.fill('\u0000')
            assertTrue(mgr.applySyncedDocument(rotated.first))
            assertFalse("Remote PIN rotation must immediately lock active Desktop session", mgr.isUnlocked())
            assertTrue(mgr.verifyPinAndUnlock("135790".toCharArray()) is AppResult.Failure)
            assertTrue(mgr.verifyPinAndUnlock("246810".toCharArray()) is AppResult.Success)
        }
    }

    private fun assertFalseOrEmpty(condition: Boolean) {
        assertEquals(false, condition)
    }
}
