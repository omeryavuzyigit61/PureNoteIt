package com.notes.purenoteit.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.notes.purenoteit.SharedStorageFactory
import com.notes.purenoteit.desktop.ui.DesktopHomeScreenContent
import com.notes.purenoteit.desktop.ui.PureNoteItDesktopTheme
import com.notes.purenoteit.desktop.ui.contract.CodeBlockRunState
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState
import com.notes.purenoteit.desktop.ui.contract.DesktopViewMode
import com.notes.purenoteit.desktop.ui.contract.SidebarSection
import com.notes.purenoteit.desktop.ui.contract.SplitViewMode
import com.notes.purenoteit.desktop.ui.contract.SubPageTreeNode
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.ConsoleLevel
import com.notes.purenoteit.domain.model.ConsoleLine
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopUiRenderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `Uc sutunlu Desktop arayuzu ve 2D Graph Split-View offscreen Compose sahnesinde hatasiz render edilmelidir`() {
        val root = tempFolder.newFolder("render_media")
        val resolver = SharedStorageFactory.createMediaPathResolver(root, File(root, "cache"))

        val categories = listOf(
            Category(id = 1, name = "Not", syncUuid = "cat-default-1"),
            Category(id = 2, name = "Günlük", syncUuid = "cat-default-2"),
            Category(id = 3, name = "Önemli", syncUuid = "cat-default-3")
        )
        val blocks = listOf(
            NoteBlock(id = "b1", type = BlockType.HEADING_1, content = "PureNoteIt E2EE Mimari"),
            NoteBlock(id = "b2", type = BlockType.CALLOUT, calloutIcon = "🔐", content = "ENC_P3 + OS Keychain aktif"),
            NoteBlock(id = "b3", type = BlockType.TODO, content = "Faz 4 2D Graph Split-View doğrula", isChecked = true),
            NoteBlock(
                id = "b4",
                type = BlockType.CODE,
                codeLanguage = "python",
                content = "print('PureNoteIt Desktop Ready!')"
            ),
            NoteBlock(
                id = "b5",
                type = BlockType.TABLE,
                tableData = listOf(
                    listOf("Katman", "Teknoloji"),
                    listOf("Ortak", ":shared KMP"),
                    listOf("PC", "Compose Desktop + SQLite")
                )
            )
        )
        val mainNote = Note(
            id = 1,
            title = "PC Senkronizasyon Planı",
            icon = "🚀",
            isPinned = true,
            totalTasks = 1,
            completedTasks = 1,
            categorySyncUuid = "cat-default-1",
            syncUuid = "note-1",
            description = BlockParser.serialize(blocks)
        )
        val subNote = Note(
            id = 2,
            title = "Güvenlik Kuralları",
            icon = "🛡️",
            categorySyncUuid = "cat-default-3",
            syncUuid = "note-2",
            description = BlockParser.serialize(listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "PKCE + Zero Shell")))
        )
        val note3 = Note(
            id = 3,
            title = "Günlük Akış",
            icon = "📅",
            categorySyncUuid = "cat-default-2",
            syncUuid = "note-3"
        )
        val links = listOf(
            NoteLink(id = 1, sourceNoteId = 1, targetNoteId = 2, linkText = "Güvenlik", sourceSyncUuid = "note-1", targetSyncUuid = "note-2"),
            NoteLink(id = 2, sourceNoteId = 1, targetNoteId = 3, linkText = "Akış", sourceSyncUuid = "note-1", targetSyncUuid = "note-3")
        )

        val state = DesktopUiState(
            isDarkTheme = true,
            sidebarSection = SidebarSection.ALL,
            categories = categories,
            allActiveNotes = listOf(mainNote, subNote, note3),
            filteredNotes = listOf(mainNote, subNote, note3),
            noteLinks = links,
            subPageTree = listOf(SubPageTreeNode(mainNote, listOf(SubPageTreeNode(subNote)))),
            selectedNoteSyncUuid = mainNote.syncUuid,
            editorTitle = mainNote.title.orEmpty(),
            editorIcon = "🚀",
            editorBlocks = blocks,
            editorCategorySyncUuid = "cat-default-1",
            splitViewMode = SplitViewMode.GRAPH_2D,
            isDriveConnected = true,
            lanServerRunning = true,
            lanServerUrl = "http://192.168.1.40:9443",
            codeRunStates = mapOf(
                "b4" to CodeBlockRunState(
                    blockId = "b4",
                    output = CodeExecutionOutput.Console(
                        lines = listOf(ConsoleLine("PureNoteIt Desktop Ready!", ConsoleLevel.OUTPUT)),
                        durationMs = 18L
                    )
                )
            )
        )

        val scene = ImageComposeScene(
            width = 1360,
            height = 860,
            density = Density(1f)
        ) {
            PureNoteItDesktopTheme(darkTheme = state.isDarkTheme) {
                DesktopHomeScreenContent(
                    state = state,
                    mediaPathResolver = resolver,
                    onEvent = {}
                )
            }
        }

        try {
            val rendered = scene.render()
            assertEquals(1360, rendered.width)
            assertEquals(860, rendered.height)
            val pngData = rendered.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            val pngBytes = pngData!!.bytes
            assertTrue("Render edilmis PNG gecerli boyutta olmali: ${pngBytes.size}", pngBytes.size > 15_000)

            val optionalPath = System.getenv("PURE_DESKTOP_PREVIEW_PNG")
            if (!optionalPath.isNullOrBlank()) {
                File(optionalPath).apply { parentFile?.mkdirs() }.writeBytes(pngBytes)
            }
        } finally {
            scene.close()
        }
    }

    @Test
    fun `Dar pencere genisliginde ve Ana Sayfa kart gorunumunde tasarim bozulmadan render edilmelidir`() {
        val root = tempFolder.newFolder("render_compact_home")
        val resolver = SharedStorageFactory.createMediaPathResolver(root, File(root, "cache"))
        val categories = listOf(
            Category(id = 1, name = "Not", syncUuid = "cat-default-1"),
            Category(id = 2, name = "Günlük", syncUuid = "cat-default-2")
        )
        val note1 = Note(
            id = 1,
            title = "Ana Sayfa Notu",
            icon = "📝",
            categorySyncUuid = "cat-default-1",
            syncUuid = "note-home-1",
            description = BlockParser.serialize(listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "Akıcı kart görünümü")))
        )
        val compactHomeState = DesktopUiState(
            isDarkTheme = true,
            categories = categories,
            allActiveNotes = listOf(note1),
            filteredNotes = listOf(note1),
            selectedNoteSyncUuid = null
        )

        val homeScene = ImageComposeScene(width = 760, height = 600, density = Density(1f)) {
            PureNoteItDesktopTheme(darkTheme = compactHomeState.isDarkTheme) {
                DesktopHomeScreenContent(
                    state = compactHomeState,
                    mediaPathResolver = resolver,
                    onEvent = {}
                )
            }
        }
        try {
            val homeImg = homeScene.render()
            assertEquals(760, homeImg.width)
            assertTrue((homeImg.encodeToData(EncodedImageFormat.PNG)?.bytes?.size ?: 0) > 10_000)
        } finally {
            homeScene.close()
        }

        val compactEditorState = compactHomeState.copy(
            selectedNoteSyncUuid = note1.syncUuid,
            editorTitle = note1.title.orEmpty(),
            editorBlocks = BlockParser.parse(note1.description)
        )
        val editorScene = ImageComposeScene(width = 760, height = 600, density = Density(1f)) {
            PureNoteItDesktopTheme(darkTheme = compactEditorState.isDarkTheme) {
                DesktopHomeScreenContent(
                    state = compactEditorState,
                    mediaPathResolver = resolver,
                    onEvent = {}
                )
            }
        }
        try {
            val editorImg = editorScene.render()
            assertEquals(760, editorImg.width)
            assertTrue((editorImg.encodeToData(EncodedImageFormat.PNG)?.bytes?.size ?: 0) > 10_000)
        } finally {
            editorScene.close()
        }

        assertNotNull(com.notes.purenoteit.desktop.loadDesktopWindowIcon())
        assertEquals(6, com.notes.purenoteit.desktop.loadAwtWindowIcons().size)
    }

    @Test
    fun `Modern Ana Sayfa kartlar pano sablonlar ve yumusak kategori popup sahneleri hatasiz render edilmelidir`() {
        val root = tempFolder.newFolder("render_modern_home")
        val resolver = SharedStorageFactory.createMediaPathResolver(root, File(root, "cache"))
        val previewDir = System.getenv("PURE_DESKTOP_HOME_PREVIEW_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }

        val categories = listOf(
            Category(id = 1, name = "Not", syncUuid = "cat-1"),
            Category(id = 2, name = "Günlük", syncUuid = "cat-2"),
            Category(id = 3, name = "Önemli", syncUuid = "cat-3"),
            Category(id = 4, name = "Projeler", syncUuid = "cat-4")
        )
        val now = 1_762_780_000_000L
        val notes = listOf(
            Note(
                id = 1,
                title = "Sprint Yol Haritası",
                icon = "🚀",
                isPinned = true,
                status = "IN_PROGRESS",
                totalTasks = 4,
                completedTasks = 2,
                categorySyncUuid = "cat-4",
                syncUuid = "n-1",
                updatedAt = now,
                description = BlockParser.serialize(
                    listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "Mobil ve masaüstü sürüm eşzamanlı yayına hazırlanıyor."))
                )
            ),
            Note(
                id = 2,
                title = "Toplantı Kararları",
                icon = "📅",
                status = "TODO",
                totalTasks = 2,
                completedTasks = 0,
                categorySyncUuid = "cat-2",
                syncUuid = "n-2",
                updatedAt = now - 86_400_000L,
                description = BlockParser.serialize(
                    listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "Yeni tasarım dili ve hızlı şablon akışı onaylandı."))
                )
            ),
            Note(
                id = 3,
                title = "Yayın Kontrol Listesi",
                icon = "✅",
                status = "DONE",
                totalTasks = 3,
                completedTasks = 3,
                categorySyncUuid = "cat-3",
                syncUuid = "n-3",
                updatedAt = now - 172_800_000L,
                description = BlockParser.serialize(
                    listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "Play Console paketi ve Google Drive senkronu doğrulandı."))
                )
            )
        )

        fun renderAndCheck(fileName: String, state: DesktopUiState) {
            val scene = ImageComposeScene(width = 1280, height = 800, density = Density(1f)) {
                PureNoteItDesktopTheme(darkTheme = state.isDarkTheme) {
                    DesktopHomeScreenContent(
                        state = state,
                        mediaPathResolver = resolver,
                        onEvent = {}
                    )
                }
            }
            try {
                val img = scene.render()
                assertEquals(1280, img.width)
                val pngBytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
                assertTrue("PNG icerigi anlamli boyutta olmali ($fileName)", pngBytes.size > 15_000)
                if (previewDir != null) {
                    previewDir.mkdirs()
                    File(previewDir, fileName).writeBytes(pngBytes)
                }
            } finally {
                scene.close()
            }
        }

        // 1. Cards Grid + Category Pills + Recent Strip
        renderAndCheck(
            "preview_home_cards.png",
            DesktopUiState(
                isDarkTheme = true,
                categories = categories,
                allActiveNotes = notes,
                filteredNotes = notes,
                homeViewMode = DesktopViewMode.CARDS
            )
        )

        // 2. Empty State with Starter Templates
        renderAndCheck(
            "preview_home_templates.png",
            DesktopUiState(
                isDarkTheme = true,
                categories = categories.take(3),
                allActiveNotes = emptyList(),
                filteredNotes = emptyList()
            )
        )

        // 3. Kanban Board View
        renderAndCheck(
            "preview_home_board.png",
            DesktopUiState(
                isDarkTheme = true,
                categories = categories,
                allActiveNotes = notes,
                filteredNotes = notes,
                homeViewMode = DesktopViewMode.BOARD
            )
        )

        // 4. Create Category Soft Popup Overlay
        renderAndCheck(
            "preview_home_category_popup.png",
            DesktopUiState(
                isDarkTheme = true,
                categories = categories,
                allActiveNotes = notes,
                filteredNotes = notes,
                showCreateCategoryDialog = true
            )
        )
    }
}
