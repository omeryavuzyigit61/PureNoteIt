package com.notes.purenoteit.desktop.ui.viewmodel

import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepository
import com.notes.purenoteit.desktop.data.runner.DesktopCodeExecutor
import com.notes.purenoteit.desktop.data.security.DesktopVaultSecurityManager
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncDataSourceImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.desktop.ui.contract.CodeBlockRunState
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEffect
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEvent
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState
import com.notes.purenoteit.desktop.ui.contract.SidebarSection
import com.notes.purenoteit.desktop.ui.contract.SplitViewMode
import com.notes.purenoteit.desktop.ui.contract.SubPageTreeNode
import com.notes.purenoteit.desktop.ui.contract.SyncTransportMode
import com.notes.purenoteit.desktop.ui.contract.toTurkishMessage
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.security.DesktopOAuthPkceManager
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.crypto.VaultCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pure MVI controller for the 3-column Desktop workspace.
 * Enforces `CancellationException` propagation, zero raw secrets in `UiState`,
 * and single-source-of-truth persistence across local SQLite, OS Keychain, Drive & LAN Sync.
 */
internal class DesktopHomeStateHolder(
    private val noteRepository: DesktopNoteRepository,
    private val mediaPathResolver: MediaPathResolver,
    private val codeExecutor: DesktopCodeExecutor,
    private val tokenStorage: SecureTokenStorage,
    private val pkceManager: DesktopOAuthPkceManager,
    private val driveSyncEngine: SyncEngine,
    private val localSyncStore: LocalSyncStore,
    private val vaultCrypto: VaultCrypto,
    private val lanSyncServer: LanWebSocketSyncServer,
    private val oauthClientConfigProvider: OAuthClientConfigProvider,
    private val vaultSecurityManager: DesktopVaultSecurityManager,
    private val clock: AppClock,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val autoSyncKeyCoordinator: AutoSyncKeyCoordinator? = null
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var autoSaveJob: Job? = null
    private var observerJob: Job? = null
    private var autoLockJob: Job? = null

    @Volatile
    private var lastActivityMillis: Long = clock.nowMillis()

    @Volatile
    private var pendingCreateSyncUuid: String? = null

    private val _uiState = MutableStateFlow(DesktopUiState())
    val uiState: StateFlow<DesktopUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DesktopUiEffect>(Channel.BUFFERED)
    val effects: Flow<DesktopUiEffect> = _effects.receiveAsFlow()

    init {
        bootVaultSecurity()
    }

    private fun bootVaultSecurity() {
        scope.launch {
            when (val boot = vaultSecurityManager.initializeOnStartup()) {
                is AppResult.Success -> {
                    val snap = boot.data
                    _uiState.update {
                        it.copy(
                            isVaultLocked = snap.isLocked,
                            isMasterLockEnabled = snap.isMasterLockEnabled,
                            autoLockMinutes = snap.autoLockMinutes,
                            vaultCooldownSeconds = snap.cooldownSeconds
                        )
                    }
                    if (!snap.isLocked) {
                        lastActivityMillis = clock.nowMillis()
                        startObservingRepository()
                        refreshCredentialStatus()
                        startAutoLockMonitor()
                    }
                }
                is AppResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isVaultLocked = true,
                            isMasterLockEnabled = true,
                            vaultLockError = boot.error.toTurkishMessage()
                        )
                    }
                }
            }
        }
    }

    private fun startObservingRepository() {
        observerJob?.cancel()
        observerJob = scope.launch {
            combine(
                noteRepository.observeCategories(),
                noteRepository.observeNotes(includeDeleted = true),
                noteRepository.observeNoteLinks()
            ) { categories, allNotesWithDeleted, links ->
                val (activeNotes, trashed) = allNotesWithDeleted.partition { it.deletedAt == null }
                val tree = buildSubPageTree(activeNotes)
                Triple(categories, activeNotes to trashed, links to tree)
            }.collect { (categories, notePair, linkTree) ->
                if (_uiState.value.isVaultLocked) return@collect
                val (activeNotes, trashed) = notePair
                val (links, tree) = linkTree

                _uiState.update { current ->
                    val filtered = applyFilter(
                        activeNotes = activeNotes,
                        trashedNotes = trashed,
                        section = current.sidebarSection,
                        categorySyncUuid = current.selectedCategorySyncUuid,
                        query = current.searchQuery
                    )

                    val createdNowVisible = pendingCreateSyncUuid != null &&
                        activeNotes.any { it.syncUuid == pendingCreateSyncUuid }
                    if (createdNowVisible) {
                        pendingCreateSyncUuid = null
                    }
                    val selectedUuid = current.selectedNoteSyncUuid?.takeIf { uuid ->
                        uuid == pendingCreateSyncUuid || if (current.sidebarSection == SidebarSection.TRASH) {
                            trashed.any { it.syncUuid == uuid }
                        } else {
                            activeNotes.any { it.syncUuid == uuid }
                        }
                    }

                    val selectedObj = activeNotes.firstOrNull { it.syncUuid == selectedUuid }
                        ?: trashed.firstOrNull { it.syncUuid == selectedUuid }

                    val shouldReloadEditor = selectedObj != null &&
                        (selectedObj.syncUuid != current.selectedNoteSyncUuid || !current.isEditorDirty)

                    val updatedSplitNote = current.splitPreviewNote?.syncUuid?.let { sUuid ->
                        activeNotes.firstOrNull { it.syncUuid == sUuid }
                    }

                    current.copy(
                        categories = categories,
                        allActiveNotes = activeNotes,
                        trashedNotes = trashed,
                        filteredNotes = filtered,
                        noteLinks = links,
                        subPageTree = tree,
                        selectedNoteSyncUuid = selectedUuid,
                        editorTitle = if (shouldReloadEditor) selectedObj.title.orEmpty() else current.editorTitle,
                        editorIcon = if (shouldReloadEditor) selectedObj.icon ?: "📝" else current.editorIcon,
                        editorBlocks = if (shouldReloadEditor) BlockParser.parse(selectedObj.description) else current.editorBlocks,
                        editorCategorySyncUuid = if (shouldReloadEditor) selectedObj.categorySyncUuid else current.editorCategorySyncUuid,
                        isEditorDirty = if (selectedObj?.syncUuid != current.selectedNoteSyncUuid) false else current.isEditorDirty,
                        splitPreviewNote = updatedSplitNote,
                        splitPreviewBlocks = updatedSplitNote?.let { BlockParser.parse(it.description) } ?: emptyList()
                    )
                }
            }
        }
    }

    suspend fun readMediaBytes(rawPath: String): ByteArray? {
        if (mediaPathResolver is DesktopEncryptedMediaStorage) {
            return mediaPathResolver.readDecryptedBytes(rawPath)
        }
        val resolved = mediaPathResolver.resolveToReadablePath(rawPath) ?: return null
        return try {
            File(resolved).takeIf { it.isFile }?.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun refreshCredentialStatus() {
        scope.launch {
            val hasToken = (tokenStorage.getTokenBundle() as? AppResult.Success)?.data != null
            val passRes = tokenStorage.getSyncPassphrase()
            val hasPass = when (passRes) {
                is AppResult.Success -> {
                    val chars = passRes.data
                    val exists = chars != null && chars.isNotEmpty()
                    chars?.fill('\u0000')
                    exists
                }
                is AppResult.Failure -> false
            }
            _uiState.update { it.copy(isDriveConnected = hasToken, hasStoredPassphrase = hasPass) }
        }
    }

    fun onEvent(event: DesktopUiEvent) {
        when (event) {
            DesktopUiEvent.ToggleTheme -> _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }

            is DesktopUiEvent.SelectSidebar -> {
                scope.launch {
                    if (_uiState.value.isEditorDirty) {
                        flushCurrentEditorToDb()
                    }
                    _uiState.update { current ->
                        val filtered = applyFilter(
                            current.allActiveNotes,
                            current.trashedNotes,
                            event.section,
                            event.categorySyncUuid,
                            current.searchQuery
                        )
                        current.copy(
                            sidebarSection = event.section,
                            selectedCategorySyncUuid = event.categorySyncUuid,
                            filteredNotes = filtered,
                            selectedNoteSyncUuid = null,
                            editorTitle = "",
                            editorBlocks = emptyList(),
                            isEditorDirty = false,
                            splitPreviewNote = null,
                            splitPreviewBlocks = emptyList()
                        )
                    }
                }
            }

            DesktopUiEvent.NavigateHome -> {
                scope.launch {
                    if (_uiState.value.isEditorDirty) {
                        flushCurrentEditorToDb()
                    }
                    _uiState.update { current ->
                        current.copy(
                            selectedNoteSyncUuid = null,
                            editorTitle = "",
                            editorBlocks = emptyList(),
                            isEditorDirty = false,
                            splitPreviewNote = null,
                            splitPreviewBlocks = emptyList()
                        )
                    }
                }
            }

            is DesktopUiEvent.UpdateSearchQuery -> {
                _uiState.update { current ->
                    val filtered = applyFilter(
                        current.allActiveNotes,
                        current.trashedNotes,
                        current.sidebarSection,
                        current.selectedCategorySyncUuid,
                        event.query
                    )
                    current.copy(searchQuery = event.query, filteredNotes = filtered)
                }
            }

            is DesktopUiEvent.SelectNote -> selectNoteImmediate(event.syncUuid)
            DesktopUiEvent.CreateNewNote -> createNewNote()
            is DesktopUiEvent.CreateNoteFromTemplate -> createNoteInternal(event.title, event.icon, event.blocks)
            is DesktopUiEvent.UpdateNoteStatus -> updateNoteStatus(event.syncUuid, event.status)
            is DesktopUiEvent.CreateCategory -> createCategory(event.name)
            is DesktopUiEvent.DeleteCategory -> deleteCategory(event.syncUuid)
            is DesktopUiEvent.TogglePinNote -> scope.launch { noteRepository.togglePin(event.syncUuid) }
            is DesktopUiEvent.SoftDeleteNote -> softDeleteNote(event.syncUuid)
            is DesktopUiEvent.RestoreNote -> scope.launch {
                noteRepository.restoreNote(event.syncUuid)
                emitMessage("Not çöp kutusundan geri yüklendi.")
            }

            is DesktopUiEvent.UpdateTitle -> {
                _uiState.update { it.copy(editorTitle = event.title, isEditorDirty = true) }
                scheduleAutoSave()
            }

            is DesktopUiEvent.UpdateIcon -> {
                _uiState.update { it.copy(editorIcon = event.icon, isEditorDirty = true, showEmojiPickerDialog = false) }
                scheduleAutoSave()
            }

            is DesktopUiEvent.UpdateCategoryForNote -> {
                _uiState.update { it.copy(editorCategorySyncUuid = event.categorySyncUuid, isEditorDirty = true) }
                scheduleAutoSave()
            }

            is DesktopUiEvent.UpdateBlock -> {
                _uiState.update { current ->
                    val mutable = current.editorBlocks.toMutableList()
                    if (event.index in mutable.indices) {
                        mutable[event.index] = event.block
                    }
                    current.copy(editorBlocks = mutable, isEditorDirty = true)
                }
                scheduleAutoSave()
            }

            is DesktopUiEvent.AddBlock -> {
                _uiState.update { current ->
                    val mutable = current.editorBlocks.toMutableList()
                    val newBlock = createDefaultBlock(event.type)
                    val idx = event.insertAfterIndex
                    if (idx != null && idx in mutable.indices) {
                        mutable.add(idx + 1, newBlock)
                    } else {
                        mutable.add(newBlock)
                    }
                    current.copy(editorBlocks = mutable, isEditorDirty = true, showSlashPalette = false)
                }
                scheduleAutoSave()
            }

            is DesktopUiEvent.DeleteBlock -> {
                _uiState.update { current ->
                    val mutable = current.editorBlocks.toMutableList()
                    if (event.index in mutable.indices) {
                        mutable.removeAt(event.index)
                    }
                    if (mutable.isEmpty()) {
                        mutable += NoteBlock(type = BlockType.PARAGRAPH, content = "")
                    }
                    current.copy(editorBlocks = mutable, isEditorDirty = true)
                }
                scheduleAutoSave()
            }

            is DesktopUiEvent.MoveBlock -> {
                _uiState.update { current ->
                    val mutable = current.editorBlocks.toMutableList()
                    if (event.fromIndex in mutable.indices && event.toIndex in mutable.indices) {
                        val moved = mutable.removeAt(event.fromIndex)
                        mutable.add(event.toIndex, moved)
                    }
                    current.copy(editorBlocks = mutable, isEditorDirty = true)
                }
                scheduleAutoSave()
            }

            is DesktopUiEvent.CreateSubPage -> createSubPageForSelected(event.title)

            is DesktopUiEvent.OpenSubPageInSplit -> {
                val target = _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == event.subPageSyncUuid }
                if (target != null) {
                    _uiState.update {
                        it.copy(
                            splitViewMode = SplitViewMode.SUB_PAGE,
                            splitPreviewNote = target,
                            splitPreviewBlocks = BlockParser.parse(target.description)
                        )
                    }
                } else {
                    emitMessage("Alt sayfa henüz bulunamadı.", isError = true)
                }
            }

            is DesktopUiEvent.OpenNoteByTitleOrUuid -> {
                val target = _uiState.value.allActiveNotes.firstOrNull {
                    it.syncUuid == event.identifier ||
                        (it.title?.equals(event.identifier.trim(), ignoreCase = true) == true)
                }
                if (target != null) {
                    selectNoteImmediate(target.syncUuid)
                }
            }

            DesktopUiEvent.SaveCurrentNote -> scope.launch {
                flushCurrentEditorToDb()
                emitMessage("Not kaydedildi.")
            }

            is DesktopUiEvent.ImportImageToBlock -> importImageBytes(event.blockId, event.fileBytes, event.extension)
            is DesktopUiEvent.OpenDrawingEditor -> _uiState.update { it.copy(activeDrawingBlockId = event.blockId) }
            DesktopUiEvent.CloseDrawingEditor -> _uiState.update { it.copy(activeDrawingBlockId = null) }
            is DesktopUiEvent.SaveDrawingPng -> saveDrawingPngBytes(event.blockId, event.pngBytes)

            is DesktopUiEvent.SetHomeViewMode -> _uiState.update { it.copy(homeViewMode = event.mode) }
            is DesktopUiEvent.SetSplitViewMode -> _uiState.update { it.copy(splitViewMode = event.mode) }
            is DesktopUiEvent.SetQuickSwitcherVisible -> _uiState.update { it.copy(showQuickSwitcher = event.visible) }
            is DesktopUiEvent.SetSlashPaletteVisible -> _uiState.update { it.copy(showSlashPalette = event.visible) }
            is DesktopUiEvent.SetCreateCategoryDialogVisible -> _uiState.update { it.copy(showCreateCategoryDialog = event.visible) }
            is DesktopUiEvent.SetCreateSubPageDialogVisible -> _uiState.update { it.copy(showCreateSubPageDialog = event.visible) }
            is DesktopUiEvent.SetEmojiPickerDialogVisible -> _uiState.update { it.copy(showEmojiPickerDialog = event.visible) }

            is DesktopUiEvent.RunCodeBlock -> runCodeBlock(event.blockId, event.language, event.code, bashApproved = false)
            is DesktopUiEvent.ConfirmBashExecution -> {
                val pending = _uiState.value.codeRunStates[event.blockId] ?: return
                runCodeBlock(event.blockId, pending.pendingLanguage, pending.pendingCode, bashApproved = true)
            }
            is DesktopUiEvent.DismissCodeOutput -> {
                _uiState.update { current ->
                    current.copy(codeRunStates = current.codeRunStates - event.blockId)
                }
            }

            is DesktopUiEvent.SetSyncDialogVisible -> _uiState.update { it.copy(showSyncDialog = event.visible) }
            is DesktopUiEvent.SelectSyncTransport -> _uiState.update { it.copy(syncTransportMode = event.mode) }
            DesktopUiEvent.ConnectGoogleDrive -> connectGoogleDrive()
            DesktopUiEvent.DisconnectGoogleDrive -> disconnectDrive()
            is DesktopUiEvent.StartLanServer -> startLanServer(event.pairPinChars)
            DesktopUiEvent.StopLanServer -> stopLanServer()
            is DesktopUiEvent.ConnectAndSyncLan -> syncOverLanPeer(event.remoteHttpUrl, event.pairPinChars, event.e2eePassChars)
            DesktopUiEvent.TriggerSyncNow -> triggerDriveSync()

            is DesktopUiEvent.SetVaultSecurityDialogVisible -> _uiState.update { it.copy(showVaultSecurityDialog = event.visible) }
            is DesktopUiEvent.UnlockVault -> unlockVaultWithPassword(event.passphraseChars)
            is DesktopUiEvent.EnableMasterPassword -> enableMasterPassword(event.passphraseChars, event.autoLockMinutes)
            is DesktopUiEvent.DisableMasterPassword -> disableMasterPassword(event.currentPassphraseChars)
            is DesktopUiEvent.UpdateAutoLockMinutes -> updateAutoLockMinutes(event.minutes)
            DesktopUiEvent.LockVaultNow -> lockVaultFromUser()
            DesktopUiEvent.RecordUserActivity -> { lastActivityMillis = clock.nowMillis() }
        }
    }

    private fun selectNoteImmediate(syncUuid: String) {
        scope.launch {
            if (_uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val found = _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == syncUuid }
                ?: _uiState.value.trashedNotes.firstOrNull { it.syncUuid == syncUuid }
                ?: return@launch
            _uiState.update {
                it.copy(
                    selectedNoteSyncUuid = found.syncUuid,
                    editorTitle = found.title.orEmpty(),
                    editorIcon = found.icon ?: "📝",
                    editorBlocks = BlockParser.parse(found.description),
                    editorCategorySyncUuid = found.categorySyncUuid,
                    isEditorDirty = false,
                    showQuickSwitcher = false
                )
            }
        }
    }

    private fun createNewNote() {
        createNoteInternal(
            title = "Yeni Not",
            icon = "📝",
            initialBlocks = listOf(NoteBlock(type = BlockType.PARAGRAPH, content = ""))
        )
    }

    private fun createNoteInternal(title: String, icon: String, initialBlocks: List<NoteBlock>) {
        scope.launch {
            if (_uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val state = _uiState.value
            val catUuid = if (state.sidebarSection == SidebarSection.CATEGORY) {
                state.selectedCategorySyncUuid
            } else {
                state.categories.firstOrNull()?.syncUuid
            }
            val newUuid = java.util.UUID.randomUUID().toString()
            pendingCreateSyncUuid = newUuid
            val normalizedBlocks = initialBlocks.ifEmpty {
                listOf(NoteBlock(type = BlockType.PARAGRAPH, content = ""))
            }
            val cleanTitle = title.trim().ifEmpty { "Yeni Not" }
            val cleanIcon = icon.trim().ifEmpty { "📝" }
            _uiState.update {
                it.copy(
                    sidebarSection = if (it.sidebarSection == SidebarSection.TRASH) SidebarSection.ALL else it.sidebarSection,
                    selectedNoteSyncUuid = newUuid,
                    editorTitle = cleanTitle,
                    editorIcon = cleanIcon,
                    editorBlocks = normalizedBlocks,
                    editorCategorySyncUuid = catUuid,
                    isEditorDirty = false
                )
            }
            val res = noteRepository.upsertNote(
                Note(
                    title = cleanTitle,
                    description = BlockParser.serialize(normalizedBlocks),
                    date = clock.nowMillis(),
                    categorySyncUuid = catUuid,
                    icon = cleanIcon,
                    syncUuid = newUuid
                )
            )
            if (res is AppResult.Failure) {
                pendingCreateSyncUuid = null
                _uiState.update { it.copy(selectedNoteSyncUuid = null) }
                emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun updateNoteStatus(syncUuid: String, rawStatus: String) {
        val allowed = setOf("TODO", "IN_PROGRESS", "DONE")
        val normalizedStatus = rawStatus.trim().uppercase().takeIf { it in allowed } ?: "TODO"
        scope.launch {
            if (_uiState.value.selectedNoteSyncUuid == syncUuid && _uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val target = _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == syncUuid } ?: return@launch
            when (val res = noteRepository.upsertNote(target.copy(status = normalizedStatus))) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun createCategory(name: String) {
        scope.launch {
            when (val res = noteRepository.upsertCategory(name)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(showCreateCategoryDialog = false) }
                    emitMessage("'${res.data.name}' kategorisi eklendi.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun deleteCategory(syncUuid: String) {
        scope.launch {
            noteRepository.softDeleteCategory(syncUuid)
            _uiState.update {
                if (it.selectedCategorySyncUuid == syncUuid) {
                    it.copy(sidebarSection = SidebarSection.ALL, selectedCategorySyncUuid = null)
                } else {
                    it
                }
            }
        }
    }

    private fun softDeleteNote(syncUuid: String) {
        scope.launch {
            noteRepository.softDeleteNote(syncUuid)
            _uiState.update { current ->
                if (current.selectedNoteSyncUuid == syncUuid) {
                    current.copy(
                        selectedNoteSyncUuid = null,
                        editorTitle = "",
                        editorBlocks = emptyList(),
                        isEditorDirty = false,
                        splitPreviewNote = null,
                        splitPreviewBlocks = emptyList()
                    )
                } else {
                    current
                }
            }
            emitMessage("Not çöp kutusuna taşındı.")
        }
    }

    private fun createSubPageForSelected(title: String) {
        scope.launch {
            flushCurrentEditorToDb()
            val parentUuid = _uiState.value.selectedNoteSyncUuid ?: return@launch
            when (val res = noteRepository.createSubPage(parentUuid, title)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            splitViewMode = SplitViewMode.SUB_PAGE,
                            splitPreviewNote = res.data,
                            splitPreviewBlocks = BlockParser.parse(res.data.description),
                            showCreateSubPageDialog = false
                        )
                    }
                    emitMessage("'${res.data.title}' alt sayfası oluşturuldu.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun importImageBytes(blockId: String, bytes: ByteArray, extension: String) {
        scope.launch {
            try {
                val mediaPath = mediaPathResolver.storeMediaBytes(bytes, "images", extension.ifBlank { "png" })
                updateMediaPathForBlock(blockId, mediaPath)
                emitMessage("Görsel nota eklendi.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitMessage("Görsel içe aktarılamadı.", isError = true)
            }
        }
    }

    private fun saveDrawingPngBytes(blockId: String, pngBytes: ByteArray) {
        scope.launch {
            try {
                val mediaPath = mediaPathResolver.storeMediaBytes(pngBytes, "drawings", "png")
                updateMediaPathForBlock(blockId, mediaPath)
                _uiState.update { it.copy(activeDrawingBlockId = null) }
                emitMessage("Çizim kaydedildi.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitMessage("Çizim kaydedilemedi.", isError = true)
            }
        }
    }

    private fun updateMediaPathForBlock(blockId: String, mediaPath: String) {
        _uiState.update { current ->
            val updated = current.editorBlocks.map { block ->
                if (block.id == blockId) block.copy(mediaPath = mediaPath) else block
            }
            current.copy(editorBlocks = updated, isEditorDirty = true)
        }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(500L)
            flushCurrentEditorToDb()
        }
    }

    private suspend fun flushCurrentEditorToDb() {
        val state = _uiState.value
        val note = state.selectedNote ?: return
        if (!state.isEditorDirty || note.deletedAt != null) return
        val serialized = BlockParser.serialize(state.editorBlocks)
        val updated = note.copy(
            title = state.editorTitle.ifBlank { "Başlıksız Not" },
            icon = state.editorIcon,
            description = serialized,
            categorySyncUuid = state.editorCategorySyncUuid ?: note.categorySyncUuid
        )
        noteRepository.upsertNote(updated)
        _uiState.update { it.copy(isEditorDirty = false) }
    }

    private fun runCodeBlock(blockId: String, language: String, code: String, bashApproved: Boolean) {
        scope.launch {
            _uiState.update { current ->
                current.copy(
                    codeRunStates = current.codeRunStates + (
                        blockId to CodeBlockRunState(blockId = blockId, isRunning = true)
                        )
                )
            }
            when (val res = codeExecutor.executeWithApproval(language, code, bashExplicitlyApproved = bashApproved)) {
                is AppResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            codeRunStates = current.codeRunStates + (
                                blockId to CodeBlockRunState(blockId = blockId, output = res.data)
                                )
                        )
                    }
                }
                is AppResult.Failure -> {
                    val isBashGate = res.error == CodeExecutionError.SecurityBlocked("BASH_CONFIRMATION_REQUIRED")
                    _uiState.update { current ->
                        current.copy(
                            codeRunStates = current.codeRunStates + (
                                blockId to CodeBlockRunState(
                                    blockId = blockId,
                                    errorMessage = if (isBashGate) null else res.error.toTurkishMessage(),
                                    awaitingBashApproval = isBashGate,
                                    pendingCode = if (isBashGate) code else "",
                                    pendingLanguage = if (isBashGate) language else "bash"
                                )
                                )
                        )
                    }
                }
            }
        }
    }

    private fun connectGoogleDrive() {
        val config = oauthClientConfigProvider.getConfig()
        if (config == null) {
            emitMessage("Bu derlemede Google OAuth istemci kimliği yapılandırılmamış.", isError = true)
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                when (val authRes = pkceManager.authorizeInteractive(config)) {
                    is AppResult.Success -> {
                        _uiState.update { it.copy(isDriveConnected = true, isSyncing = false) }
                        emitMessage("Google Drive hesabı bağlandı. Notlar şifrelenip eşitleniyor...")
                        triggerDriveSync()
                    }
                    is AppResult.Failure -> {
                        _uiState.update { it.copy(isSyncing = false) }
                        emitMessage(authRes.error.toTurkishMessage(), isError = true)
                    }
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun disconnectDrive() {
        scope.launch {
            tokenStorage.clearAll()
            _uiState.update {
                it.copy(isDriveConnected = false, hasStoredPassphrase = false, lastSyncSummary = null)
            }
            emitMessage("Drive oturumu ve anahtar önbelleği temizlendi.")
        }
    }

    private fun unlockVaultWithPassword(passphraseChars: CharArray) {
        val passCopy = passphraseChars.copyOf()
        passphraseChars.fill('\u0000')
        scope.launch {
            try {
                when (val res = vaultSecurityManager.unlockWithMasterPassword(passCopy)) {
                    is AppResult.Success -> {
                        val snap = res.data
                        lastActivityMillis = clock.nowMillis()
                        _uiState.update {
                            it.copy(
                                isVaultLocked = false,
                                isMasterLockEnabled = snap.isMasterLockEnabled,
                                autoLockMinutes = snap.autoLockMinutes,
                                vaultCooldownSeconds = 0,
                                vaultLockError = null
                            )
                        }
                        startObservingRepository()
                        refreshCredentialStatus()
                        startAutoLockMonitor()
                    }
                    is AppResult.Failure -> {
                        val cd = vaultSecurityManager.currentCooldownSeconds()
                        _uiState.update {
                            it.copy(
                                vaultLockError = res.error.toTurkishMessage(),
                                vaultCooldownSeconds = cd
                            )
                        }
                        if (cd > 0) {
                            launchCooldownCountdown()
                        }
                    }
                }
            } finally {
                passCopy.fill('\u0000')
            }
        }
    }

    private fun enableMasterPassword(passphraseChars: CharArray, autoLockMinutes: Int) {
        val passCopy = passphraseChars.copyOf()
        passphraseChars.fill('\u0000')
        scope.launch {
            try {
                flushCurrentEditorToDb()
                when (val res = vaultSecurityManager.enableMasterPassword(passCopy, autoLockMinutes)) {
                    is AppResult.Success -> {
                        val snap = res.data
                        lastActivityMillis = clock.nowMillis()
                        _uiState.update {
                            it.copy(
                                isMasterLockEnabled = snap.isMasterLockEnabled,
                                autoLockMinutes = snap.autoLockMinutes,
                                showVaultSecurityDialog = false
                            )
                        }
                        startAutoLockMonitor()
                        emitMessage("Ana Parola kilidi aktif edildi.")
                    }
                    is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
                }
            } finally {
                passCopy.fill('\u0000')
            }
        }
    }

    private fun disableMasterPassword(passphraseChars: CharArray) {
        val passCopy = passphraseChars.copyOf()
        passphraseChars.fill('\u0000')
        scope.launch {
            try {
                flushCurrentEditorToDb()
                when (val res = vaultSecurityManager.disableMasterPassword(passCopy)) {
                    is AppResult.Success -> {
                        val snap = res.data
                        _uiState.update {
                            it.copy(
                                isMasterLockEnabled = snap.isMasterLockEnabled,
                                autoLockMinutes = snap.autoLockMinutes,
                                showVaultSecurityDialog = false
                            )
                        }
                        emitMessage("Açılış parolası kilidi kaldırıldı.")
                    }
                    is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
                }
            } finally {
                passCopy.fill('\u0000')
            }
        }
    }

    private fun updateAutoLockMinutes(minutes: Int) {
        scope.launch {
            val snap = vaultSecurityManager.setAutoLockMinutes(minutes)
            lastActivityMillis = clock.nowMillis()
            _uiState.update { it.copy(autoLockMinutes = snap.autoLockMinutes) }
        }
    }

    private fun lockVaultFromUser() {
        if (!_uiState.value.isMasterLockEnabled) {
            _uiState.update { it.copy(showVaultSecurityDialog = true) }
            emitMessage("Kasayı kilitlemek için önce Açılış Ana Parolası belirleyin.")
            return
        }
        scope.launch {
            lockVaultInternal(reasonMessage = null)
        }
    }

    private suspend fun lockVaultInternal(reasonMessage: String?) {
        if (_uiState.value.isVaultLocked) return
        autoSaveJob?.cancel()
        if (vaultSecurityManager.isVaultUnlocked()) {
            flushCurrentEditorToDb()
        }
        observerJob?.cancel()
        autoLockJob?.cancel()
        val snap = vaultSecurityManager.lockVault()
        _uiState.update { current ->
            current.copy(
                isVaultLocked = true,
                isMasterLockEnabled = snap.isMasterLockEnabled,
                allActiveNotes = emptyList(),
                trashedNotes = emptyList(),
                filteredNotes = emptyList(),
                noteLinks = emptyList(),
                subPageTree = emptyList(),
                selectedNoteSyncUuid = null,
                editorTitle = "",
                editorBlocks = emptyList(),
                isEditorDirty = false,
                splitPreviewNote = null,
                splitPreviewBlocks = emptyList(),
                codeRunStates = emptyMap(),
                showQuickSwitcher = false,
                showSlashPalette = false,
                showSyncDialog = false,
                showVaultSecurityDialog = false,
                activeDrawingBlockId = null,
                vaultLockError = reasonMessage,
                vaultCooldownSeconds = snap.cooldownSeconds
            )
        }
    }

    private fun startAutoLockMonitor() {
        autoLockJob?.cancel()
        autoLockJob = scope.launch {
            while (true) {
                delay(10_000L)
                val st = _uiState.value
                if (st.isVaultLocked || !st.isMasterLockEnabled || st.autoLockMinutes <= 0) continue
                val idleMs = clock.nowMillis() - lastActivityMillis
                if (idleMs >= st.autoLockMinutes * 60_000L) {
                    lockVaultInternal("Hareketsizlik nedeniyle kasa otomatik kilitlendi.")
                    break
                }
            }
        }
    }

    private fun launchCooldownCountdown() {
        scope.launch {
            while (true) {
                val rem = vaultSecurityManager.currentCooldownSeconds()
                _uiState.update { it.copy(vaultCooldownSeconds = rem) }
                if (rem <= 0) break
                delay(1_000L)
            }
        }
    }

    private fun triggerDriveSync() {
        scope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                flushCurrentEditorToDb()
                val passRes = autoSyncKeyCoordinator?.resolveOrBootstrapPassphrase()
                    ?: tokenStorage.getSyncPassphrase()
                val passChars = when (passRes) {
                    is AppResult.Failure -> {
                        emitMessage(passRes.error.toTurkishMessage(), isError = true)
                        return@launch
                    }
                    is AppResult.Success -> passRes.data ?: run {
                        emitMessage("Otomatik eşitleme anahtarı alınamadı.", isError = true)
                        return@launch
                    }
                }
                _uiState.update { it.copy(hasStoredPassphrase = true) }

                try {
                    when (val syncRes = driveSyncEngine.synchronize(passChars)) {
                        is AppResult.Success -> {
                            val s = syncRes.data
                            val text = "Eşitlendi ↑${s.pushedEntities} kayıt, ↓${s.pulledNotes} not (Çakışma: ${s.conflictCopiesCreated})"
                            _uiState.update { it.copy(lastSyncSummary = text) }
                            emitMessage(text)
                        }
                        is AppResult.Failure -> emitMessage(syncRes.error.toTurkishMessage(), isError = true)
                    }
                } finally {
                    passChars.fill('\u0000')
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun startLanServer(pairPinChars: CharArray) {
        val pinCopy = pairPinChars.copyOf()
        pairPinChars.fill('\u0000')
        if (pinCopy.size < 4) {
            pinCopy.fill('\u0000')
            emitMessage("Wi-Fi P2P eşleşme kodu en az 4 karakter olmalıdır.", isError = true)
            return
        }
        scope.launch {
            try {
                val (ip, port) = lanSyncServer.start(pinCopy)
                val url = "http://$ip:$port"
                _uiState.update { it.copy(lanServerRunning = true, lanServerUrl = url) }
                emitMessage("Yerel Wi-Fi Sync sunucusu hazır: $url")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitMessage("Yerel Wi-Fi sunucusu başlatılamadı.", isError = true)
            } finally {
                pinCopy.fill('\u0000')
            }
        }
    }

    private fun stopLanServer() {
        scope.launch {
            lanSyncServer.stop()
            _uiState.update { it.copy(lanServerRunning = false, lanServerUrl = null) }
            emitMessage("Yerel Wi-Fi Sync sunucusu durduruldu.")
        }
    }

    private fun syncOverLanPeer(remoteUrl: String, pairPinChars: CharArray, e2eePassChars: CharArray) {
        val pinCopy = pairPinChars.copyOf()
        val passCopy = e2eePassChars.copyOf()
        pairPinChars.fill('\u0000')
        e2eePassChars.fill('\u0000')
        val cleanUrl = remoteUrl.trim()
        if (cleanUrl.isEmpty() || pinCopy.isEmpty() || passCopy.isEmpty()) {
            pinCopy.fill('\u0000')
            passCopy.fill('\u0000')
            emitMessage("Sunucu adresi, eşleşme kodu ve güvenlik parolası gereklidir.", isError = true)
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSyncing = true, lanRemoteUrl = cleanUrl) }
            try {
                flushCurrentEditorToDb()
                val tokenHex = String(LanWebSocketSyncServer.hashPasscode(pinCopy), Charsets.UTF_8)
                val remoteDs = LanWebSocketSyncDataSourceImpl(
                    baseHttpUrl = cleanUrl,
                    bearerHexToken = tokenHex
                )
                val lanEngine = SyncEngine(
                    localStore = localSyncStore,
                    remoteDataSource = remoteDs,
                    vaultCrypto = vaultCrypto,
                    conflictResolver = ConflictResolver(clock = clock),
                    clock = clock
                )
                when (val res = lanEngine.synchronize(passCopy)) {
                    is AppResult.Success -> {
                        val s = res.data
                        val text = "Wi-Fi P2P Eşitlendi ↑${s.pushedEntities} ↓${s.pulledNotes}"
                        _uiState.update { it.copy(lastSyncSummary = text) }
                        emitMessage(text)
                    }
                    is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
                }
            } finally {
                pinCopy.fill('\u0000')
                passCopy.fill('\u0000')
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun emitMessage(message: String, isError: Boolean = false) {
        _effects.trySend(DesktopUiEffect.ShowMessage(message, isError))
    }

    private fun applyFilter(
        activeNotes: List<Note>,
        trashedNotes: List<Note>,
        section: SidebarSection,
        categorySyncUuid: String?,
        query: String
    ): List<Note> {
        val base = when (section) {
            SidebarSection.TRASH -> trashedNotes
            SidebarSection.PINNED -> activeNotes.filter { it.isPinned }
            SidebarSection.TASKS -> activeNotes.filter { it.totalTasks > 0 }
            SidebarSection.AGENDA -> activeNotes.filter { it.reminderTime != null }
                .sortedBy { it.reminderTime ?: Long.MAX_VALUE }
            SidebarSection.CATEGORY -> activeNotes.filter { it.categorySyncUuid == categorySyncUuid }
            SidebarSection.ALL -> activeNotes
        }
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return base
        return base.filter { note ->
            note.title.orEmpty().lowercase().contains(cleanQuery) ||
                BlockParser.findSearchSnippet(note.description, cleanQuery) != null
        }
    }

    private fun buildSubPageTree(activeNotes: List<Note>): List<SubPageTreeNode> {
        val byUuid = activeNotes.associateBy { it.syncUuid }
        val childrenMap = mutableMapOf<String, MutableList<Note>>()
        val parentedUuids = HashSet<String>()

        for (note in activeNotes) {
            val meta = BlockParser.parseRichMetadata(note.richBlocksJson)
            val parentUuid = meta?.parentSyncUuid
            if (meta != null && meta.isSubPage && !parentUuid.isNullOrBlank() && byUuid.containsKey(parentUuid)) {
                childrenMap.getOrPut(parentUuid) { mutableListOf() }.add(note)
                parentedUuids += note.syncUuid
            }
        }

        fun toTreeNode(n: Note, depth: Int): SubPageTreeNode {
            if (depth > 4) return SubPageTreeNode(n)
            val kids = childrenMap[n.syncUuid].orEmpty().map { toTreeNode(it, depth + 1) }
            return SubPageTreeNode(note = n, children = kids)
        }

        // Only surface root notes that act as workspace hierarchies (have children) or pinned roots
        return activeNotes
            .filter { it.syncUuid !in parentedUuids && (childrenMap.containsKey(it.syncUuid) || it.isPinned) }
            .take(15)
            .map { toTreeNode(it, 0) }
    }

    private fun createDefaultBlock(type: BlockType): NoteBlock = when (type) {
        BlockType.TABLE -> NoteBlock(
            type = BlockType.TABLE,
            content = "Tablo",
            tableData = listOf(listOf("Başlık 1", "Başlık 2"), listOf("", ""))
        )
        BlockType.CODE -> NoteBlock(
            type = BlockType.CODE,
            codeLanguage = "python",
            content = "print(\"Merhaba PureNoteIt!\")"
        )
        BlockType.CALLOUT -> NoteBlock(type = BlockType.CALLOUT, calloutIcon = "💡", content = "")
        else -> NoteBlock(type = type, content = "")
    }

    override fun close() {
        autoSaveJob?.cancel()
        observerJob?.cancel()
        autoLockJob?.cancel()
        scope.cancel()
    }
}
