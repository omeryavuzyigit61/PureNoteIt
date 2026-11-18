package com.notes.purenoteit.desktop.ui.viewmodel

import com.notes.purenoteit.desktop.audio.DesktopAudioRecorder
import com.notes.purenoteit.desktop.data.repository.DesktopNoteRepository
import com.notes.purenoteit.desktop.data.runner.DesktopCodeExecutor
import com.notes.purenoteit.desktop.data.security.DesktopNoteVaultManager
import com.notes.purenoteit.desktop.data.security.DesktopVaultSecurityManager
import com.notes.purenoteit.desktop.data.settings.DesktopPreferencesStore
import com.notes.purenoteit.desktop.data.storage.DesktopEncryptedMediaStorage
import com.notes.purenoteit.desktop.data.storage.DesktopImageOptimizer
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncDataSourceImpl
import com.notes.purenoteit.desktop.data.sync.LanWebSocketSyncServer
import com.notes.purenoteit.security.OAuthClientConfigProvider
import com.notes.purenoteit.security.SecureTokenStorage
import com.notes.purenoteit.security.SyncedVaultSecurityDocument
import com.notes.purenoteit.security.VaultPinSyncProtocol
import com.notes.purenoteit.sync.AutoSyncKeyCoordinator
import com.notes.purenoteit.sync.ConflictResolver
import com.notes.purenoteit.sync.LocalSyncStore
import com.notes.purenoteit.sync.SyncEngine
import com.notes.purenoteit.sync.VaultSettingsSyncCoordinator
import com.notes.purenoteit.desktop.ui.contract.CodeBlockRunState
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEffect
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEvent
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState
import com.notes.purenoteit.desktop.ui.contract.DesktopViewMode
import com.notes.purenoteit.desktop.ui.contract.SidebarSection
import com.notes.purenoteit.desktop.ui.contract.SplitViewMode
import com.notes.purenoteit.desktop.ui.contract.SubPageTreeNode
import com.notes.purenoteit.desktop.ui.contract.SyncTransportMode
import com.notes.purenoteit.desktop.ui.contract.toTurkishMessage
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.model.StreamItem
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.domain.time.AppClock
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.security.DesktopOAuthPkceManager
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    private val autoSyncKeyCoordinator: AutoSyncKeyCoordinator? = null,
    private val preferencesStore: DesktopPreferencesStore? = null,
    private val noteVaultManager: DesktopNoteVaultManager? = null,
    private val vaultSettingsCoordinator: VaultSettingsSyncCoordinator? = null,
    private val cacheDir: File? = null,
    private val saveDesktopOAuthConfig: (suspend (String, String) -> Boolean)? = null
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var autoSaveJob: Job? = null
    private var observerJob: Job? = null
    private var autoLockJob: Job? = null
    private var debouncedSyncJob: Job? = null
    private var autoSyncMonitorJob: Job? = null
    private val editorFlushMutex = Mutex()
    private val driveSyncMutex = Mutex()

    @Volatile
    private var pendingDriveSyncRequested: Boolean = false

    private val pinProtocol = VaultPinSyncProtocol(vaultCrypto)

    private sealed interface PendingProtectedAction {
        data class OpenNote(val syncUuid: String) : PendingProtectedAction
        data class LockNote(val syncUuid: String) : PendingProtectedAction
        data class UnlockNote(val syncUuid: String) : PendingProtectedAction
    }

    private var pendingProtectedAction: PendingProtectedAction? = null

    @Volatile
    private var lastActivityMillis: Long = clock.nowMillis()

    @Volatile
    private var pendingCreateSyncUuid: String? = null

    @Volatile
    private var pendingBlankDraftSyncUuid: String? = null

    private val audioRecorder = DesktopAudioRecorder()

    private val initialPrefs = preferencesStore?.load()
    private val _uiState = MutableStateFlow(
        DesktopUiState(
            isDarkTheme = initialPrefs?.isDarkTheme ?: true,
            language = initialPrefs?.language ?: "tr",
            trashRetentionDays = initialPrefs?.trashRetentionDays ?: 30,
            hideLockedNotes = initialPrefs?.hideLockedNotes ?: false,
            isNotePinSet = noteVaultManager?.isPinSetSync() == true,
            isNoteVaultUnlocked = noteVaultManager?.isUnlocked() == true
        )
    )
    val uiState: StateFlow<DesktopUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DesktopUiEffect>(Channel.BUFFERED)
    val effects: Flow<DesktopUiEffect> = _effects.receiveAsFlow()

    init {
        scrubTempPreviewFiles()
        observeAudioState()
        bootVaultSecurity()
    }

    private fun observeAudioState() {
        scope.launch {
            audioRecorder.state.collect { audio ->
                _uiState.update { current ->
                    current.copy(
                        isRecordingAudio = audio.isRecording,
                        recordingDurationMs = audio.recordingDurationMs,
                        recordingAmplitude = audio.recordingAmplitude,
                        playingAudioMediaPath = audio.playingMediaPath
                    )
                }
            }
        }
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
                        refreshCredentialStatus(syncIfConnected = true)
                        startAutoLockMonitor()
                        startAutoSyncMonitor()
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
                noteRepository.observeNoteLinks(),
                noteRepository.observeStreamItems()
            ) { categories, allNotesWithDeleted, links, streamItems ->
                val activeNotes = allNotesWithDeleted.filter {
                    it.deletedAt == null &&
                        !it.status.equals("TRASH", ignoreCase = true) &&
                        !it.status.equals("DELETED", ignoreCase = true)
                }
                val trashed = allNotesWithDeleted.filter {
                    (it.deletedAt != null || it.status.equals("TRASH", ignoreCase = true)) &&
                        !it.status.equals("DELETED", ignoreCase = true)
                }
                val rootNotes = filterRootActiveNotes(activeNotes)
                val tree = buildSubPageTree(activeNotes)
                RepositorySnapshot(categories, activeNotes, rootNotes, trashed, links, streamItems, tree)
            }.collect { snap ->
                if (_uiState.value.isVaultLocked) return@collect
                val categories = snap.categories
                val activeNotes = snap.activeNotes
                val rootNotes = snap.rootActiveNotes
                val trashed = snap.trashedNotes
                val links = snap.noteLinks
                val streamItems = snap.streamItems
                val tree = snap.subPageTree

                _uiState.update { current ->
                    val filtered = applyFilter(
                        activeNotes = activeNotes,
                        rootActiveNotes = rootNotes,
                        trashedNotes = trashed,
                        categories = categories,
                        section = current.sidebarSection,
                        categorySyncUuid = current.selectedCategorySyncUuid,
                        query = current.searchQuery,
                        isNoteVaultUnlocked = current.isNoteVaultUnlocked,
                        hideLockedNotes = current.hideLockedNotes
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

                    val isPendingCreate = selectedUuid != null && selectedUuid == pendingCreateSyncUuid && selectedObj == null
                    val selectedProtected = selectedObj != null && isNoteProtected(selectedObj, categories)
                    val noteVaultUnlocked = noteVaultManager?.isUnlocked() == true
                    val displayNote = if (selectedObj != null && (!selectedProtected || noteVaultUnlocked)) {
                        selectedObj
                    } else null
                    val effectiveSelectedUuid = when {
                        isPendingCreate -> selectedUuid
                        else -> displayNote?.syncUuid
                    }

                    val shouldReloadEditor = displayNote != null &&
                        (displayNote.syncUuid != current.selectedNoteSyncUuid || !current.isEditorDirty)
                    val reloadedDesc = if (shouldReloadEditor) {
                        decodeNoteDescriptionForEditor(displayNote)
                    } else null

                    val rawSplitNote = current.splitPreviewNote?.syncUuid?.let { sUuid ->
                        activeNotes.firstOrNull { it.syncUuid == sUuid }
                    }
                    val canShowSplit = rawSplitNote != null &&
                        (!isNoteProtected(rawSplitNote, categories) || noteVaultUnlocked)
                    val updatedSplitNote = if (canShowSplit) rawSplitNote else null
                    val updatedSplitDesc = updatedSplitNote?.let { decodeNoteDescriptionForEditor(it) }

                    current.copy(
                        categories = categories,
                        allActiveNotes = activeNotes,
                        rootActiveNotes = rootNotes,
                        trashedNotes = trashed,
                        filteredNotes = filtered,
                        noteLinks = links,
                        streamItems = streamItems,
                        subPageTree = tree,
                        selectedNoteSyncUuid = effectiveSelectedUuid,
                        editorTitle = when {
                            isPendingCreate -> current.editorTitle
                            displayNote == null -> ""
                            shouldReloadEditor -> displayNote.title.orEmpty()
                            else -> current.editorTitle
                        },
                        editorIcon = when {
                            isPendingCreate -> current.editorIcon
                            displayNote == null -> "📝"
                            shouldReloadEditor -> displayNote.icon ?: "📝"
                            else -> current.editorIcon
                        },
                        editorBlocks = when {
                            isPendingCreate -> current.editorBlocks
                            displayNote == null -> emptyList()
                            shouldReloadEditor -> BlockParser.parse(reloadedDesc)
                            else -> current.editorBlocks
                        },
                        editorCategorySyncUuid = when {
                            isPendingCreate -> current.editorCategorySyncUuid
                            displayNote == null -> null
                            shouldReloadEditor -> displayNote.categorySyncUuid
                            else -> current.editorCategorySyncUuid
                        },
                        isEditorDirty = when {
                            isPendingCreate -> current.isEditorDirty
                            displayNote == null || displayNote.syncUuid != current.selectedNoteSyncUuid -> false
                            else -> current.isEditorDirty
                        },
                        splitPreviewNote = updatedSplitNote,
                        splitPreviewBlocks = updatedSplitDesc?.let { BlockParser.parse(it) } ?: emptyList()
                    )
                }
                purgeExpiredTrashIfNeeded(trashed)
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

    private fun refreshCredentialStatus(syncIfConnected: Boolean = false) {
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
            _uiState.update {
                it.copy(
                    isDriveConnected = hasToken,
                    isDesktopOAuthConfigured = oauthClientConfigProvider.getConfig() != null,
                    hasStoredPassphrase = hasPass
                )
            }
            if (syncIfConnected && hasToken && !_uiState.value.isVaultLocked) {
                triggerDriveSync(silent = true, fetchUsage = true)
            }
        }
    }

    fun onEvent(event: DesktopUiEvent) {
        when (event) {
            DesktopUiEvent.ToggleTheme -> {
                val updated = preferencesStore?.update { it.copy(isDarkTheme = !it.isDarkTheme) }
                _uiState.update { it.copy(isDarkTheme = updated?.isDarkTheme ?: !it.isDarkTheme) }
            }

            is DesktopUiEvent.SelectSidebar -> {
                scope.launch {
                    audioRecorder.stopPlayback()
                    if (_uiState.value.isRecordingAudio) {
                        audioRecorder.cancelRecording()
                    }
                    if (_uiState.value.isEditorDirty) {
                        flushCurrentEditorToDb()
                    }
                    pendingBlankDraftSyncUuid = null
                    _uiState.update { current ->
                        val filtered = applyFilter(
                            activeNotes = current.allActiveNotes,
                            rootActiveNotes = current.rootActiveNotes,
                            trashedNotes = current.trashedNotes,
                            categories = current.categories,
                            section = event.section,
                            categorySyncUuid = event.categorySyncUuid,
                            query = current.searchQuery,
                            isNoteVaultUnlocked = current.isNoteVaultUnlocked,
                            hideLockedNotes = current.hideLockedNotes
                        )
                        val keptHomeMode = if (current.homeViewMode in setOf(
                                DesktopViewMode.CARDS,
                                DesktopViewMode.LIST,
                                DesktopViewMode.BOARD
                            )
                        ) {
                            current.homeViewMode
                        } else {
                            DesktopViewMode.CARDS
                        }
                        current.copy(
                            sidebarSection = event.section,
                            selectedCategorySyncUuid = event.categorySyncUuid,
                            filteredNotes = filtered,
                            homeViewMode = keptHomeMode,
                            selectedNoteSyncUuid = null,
                            editorTitle = "",
                            editorBlocks = emptyList(),
                            isEditorDirty = false,
                            showZenReadingMode = false,
                            splitPreviewNote = null,
                            splitPreviewBlocks = emptyList()
                        )
                    }
                }
            }

            DesktopUiEvent.NavigateHome -> {
                scope.launch {
                    audioRecorder.stopPlayback()
                    if (_uiState.value.isRecordingAudio) {
                        audioRecorder.cancelRecording()
                    }
                    if (_uiState.value.isEditorDirty) {
                        flushCurrentEditorToDb(immediateSync = true)
                    }
                    pendingBlankDraftSyncUuid = null
                    _uiState.update { current ->
                        current.copy(
                            selectedNoteSyncUuid = null,
                            editorTitle = "",
                            editorBlocks = emptyList(),
                            isEditorDirty = false,
                            showZenReadingMode = false,
                            splitPreviewNote = null,
                            splitPreviewBlocks = emptyList()
                        )
                    }
                }
            }

            is DesktopUiEvent.UpdateSearchQuery -> {
                _uiState.update { current ->
                    val filtered = applyFilter(
                        activeNotes = current.allActiveNotes,
                        rootActiveNotes = current.rootActiveNotes,
                        trashedNotes = current.trashedNotes,
                        categories = current.categories,
                        section = current.sidebarSection,
                        categorySyncUuid = current.selectedCategorySyncUuid,
                        query = event.query,
                        isNoteVaultUnlocked = current.isNoteVaultUnlocked,
                        hideLockedNotes = current.hideLockedNotes
                    )
                    current.copy(searchQuery = event.query, filteredNotes = filtered)
                }
            }

            is DesktopUiEvent.SelectNote -> selectNoteImmediate(event.syncUuid)
            DesktopUiEvent.CreateNewNote -> createNewNote()
            is DesktopUiEvent.CreateNewNoteWithStatus -> createNewNote(initialStatus = event.status)
            is DesktopUiEvent.CreateNoteFromTemplate -> createNoteInternal(
                title = event.title,
                icon = event.icon,
                initialBlocks = event.blocks,
                reminderTime = event.reminderTime,
                preferredCategoryName = event.preferredCategoryName
            )
            DesktopUiEvent.CreateDailyAgenda -> createDailyAgendaNote()
            is DesktopUiEvent.UpdateNoteStatus -> updateNoteStatus(event.syncUuid, event.status)
            is DesktopUiEvent.SetNoteReminder -> setNoteReminder(event.syncUuid, event.reminderTime)
            is DesktopUiEvent.CreateCategory -> createCategory(event.name)
            is DesktopUiEvent.RenameCategory -> renameCategory(event.syncUuid, event.name)
            is DesktopUiEvent.ToggleCategoryLock -> toggleCategoryLock(event.syncUuid)
            is DesktopUiEvent.DeleteCategory -> deleteCategory(event.syncUuid)
            is DesktopUiEvent.TogglePinNote -> scope.launch {
                noteRepository.togglePin(event.syncUuid)
                triggerSyncAfterCommit()
            }
            is DesktopUiEvent.SoftDeleteNote -> softDeleteNote(event.syncUuid)
            is DesktopUiEvent.RestoreNote -> scope.launch {
                noteRepository.restoreNote(event.syncUuid)
                triggerSyncAfterCommit()
                emitLocalizedMessage("Not çöp kutusundan geri yüklendi.", "Note restored from Trash.")
            }
            is DesktopUiEvent.PermanentDeleteNote -> permanentDeleteNote(event.syncUuid)
            DesktopUiEvent.EmptyTrash -> emptyTrash()
            is DesktopUiEvent.ToggleNoteLock -> handleToggleNoteLock(event.syncUuid)
            is DesktopUiEvent.AddStreamItem -> addStreamItem(event.content)
            is DesktopUiEvent.DeleteStreamItem -> deleteStreamItem(event.syncUuid)
            is DesktopUiEvent.ConvertStreamItemsToNote -> convertStreamItemsToNote(event.syncUuids, event.title)
            is DesktopUiEvent.OpenMediaExternally -> openMediaExternally(event.mediaPath)

            is DesktopUiEvent.UpdateTitle -> {
                _uiState.update { it.copy(editorTitle = event.title, isEditorDirty = true) }
            }

            is DesktopUiEvent.UpdateIcon -> {
                _uiState.update { it.copy(editorIcon = event.icon, isEditorDirty = true, showEmojiPickerDialog = false) }
            }

            is DesktopUiEvent.UpdateCategoryForNote -> {
                _uiState.update { it.copy(editorCategorySyncUuid = event.categorySyncUuid, isEditorDirty = true) }
            }

            is DesktopUiEvent.UpdateBlock -> {
                _uiState.update { current ->
                    val mutable = current.editorBlocks.toMutableList()
                    if (event.index in mutable.indices) {
                        mutable[event.index] = event.block
                    }
                    current.copy(editorBlocks = mutable, isEditorDirty = true)
                }
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
                flushCurrentEditorToDb(immediateSync = true, force = true)
                emitLocalizedMessage("Not kaydedildi ve eşitleniyor.", "Note saved & syncing.")
            }

            is DesktopUiEvent.ImportImageToBlock -> importImageBytes(event.blockId, event.fileBytes, event.extension)
            is DesktopUiEvent.OpenDrawingEditor -> _uiState.update { it.copy(activeDrawingBlockId = event.blockId) }
            DesktopUiEvent.CloseDrawingEditor -> _uiState.update { it.copy(activeDrawingBlockId = null) }
            is DesktopUiEvent.SaveDrawingPng -> saveDrawingPngBytes(event.blockId, event.pngBytes)
            is DesktopUiEvent.StartAudioRecording -> startAudioRecording(event.targetBlockId)
            DesktopUiEvent.StopAudioRecordingAndSave -> stopAudioRecordingAndAttach()
            DesktopUiEvent.CancelAudioRecording -> {
                audioRecorder.cancelRecording()
            }
            is DesktopUiEvent.PlayOrToggleAudio -> playOrToggleAudio(event.mediaPath)

            is DesktopUiEvent.SetHomeViewMode -> _uiState.update { it.copy(homeViewMode = event.mode) }
            DesktopUiEvent.ToggleShowSubPagesInVisualModes -> _uiState.update {
                it.copy(showSubPagesInVisualModes = !it.showSubPagesInVisualModes)
            }
            is DesktopUiEvent.SetSplitViewMode -> _uiState.update { it.copy(splitViewMode = event.mode) }
            is DesktopUiEvent.SetQuickSwitcherVisible -> _uiState.update { it.copy(showQuickSwitcher = event.visible) }
            is DesktopUiEvent.SetSlashPaletteVisible -> _uiState.update { it.copy(showSlashPalette = event.visible) }
            is DesktopUiEvent.SetCreateCategoryDialogVisible -> _uiState.update { it.copy(showCreateCategoryDialog = event.visible) }
            is DesktopUiEvent.SetCreateSubPageDialogVisible -> _uiState.update { it.copy(showCreateSubPageDialog = event.visible) }
            is DesktopUiEvent.SetEmojiPickerDialogVisible -> _uiState.update { it.copy(showEmojiPickerDialog = event.visible) }
            is DesktopUiEvent.SetTemplateGalleryVisible -> _uiState.update { it.copy(showTemplateGalleryDialog = event.visible) }
            is DesktopUiEvent.SetCategoryManagementVisible -> _uiState.update { it.copy(showCategoryManagementDialog = event.visible) }
            is DesktopUiEvent.SetZenReadingMode -> _uiState.update { it.copy(showZenReadingMode = event.enabled) }
            is DesktopUiEvent.SetExportDialogVisible -> _uiState.update { it.copy(showExportDialog = event.visible) }
            is DesktopUiEvent.SetReminderDialogVisible -> _uiState.update { it.copy(showReminderDialog = event.visible) }

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

            is DesktopUiEvent.SetSyncDialogVisible -> {
                _uiState.update { it.copy(showSyncDialog = event.visible) }
                if (event.visible && _uiState.value.isDriveConnected) {
                    refreshDriveUsage()
                }
            }
            is DesktopUiEvent.SelectSyncTransport -> _uiState.update { it.copy(syncTransportMode = event.mode) }
            DesktopUiEvent.ConnectGoogleDrive -> connectGoogleDrive()
            DesktopUiEvent.DisconnectGoogleDrive -> disconnectDrive()
            is DesktopUiEvent.SaveDesktopOAuthConfig -> saveOAuthConfig(event.clientId, event.clientSecret)
            is DesktopUiEvent.StartLanServer -> startLanServer(event.pairPinChars)
            DesktopUiEvent.StopLanServer -> stopLanServer()
            is DesktopUiEvent.ConnectAndSyncLan -> syncOverLanPeer(event.remoteHttpUrl, event.pairPinChars, event.e2eePassChars)
            DesktopUiEvent.TriggerSyncNow -> triggerDriveSync(silent = false, fetchUsage = true)
            DesktopUiEvent.ForcePushAllToDrive -> forcePushAllToDrive()
            DesktopUiEvent.RefreshDriveUsage -> refreshDriveUsage()
            DesktopUiEvent.CleanOrphanDriveMedia -> cleanOrphanDriveMedia()

            is DesktopUiEvent.SetVaultSecurityDialogVisible -> _uiState.update { it.copy(showVaultSecurityDialog = event.visible) }
            is DesktopUiEvent.UnlockVault -> unlockVaultWithPassword(event.passphraseChars)
            is DesktopUiEvent.EnableMasterPassword -> enableMasterPassword(event.passphraseChars, event.autoLockMinutes)
            is DesktopUiEvent.DisableMasterPassword -> disableMasterPassword(event.currentPassphraseChars)
            is DesktopUiEvent.UpdateAutoLockMinutes -> updateAutoLockMinutes(event.minutes)
            DesktopUiEvent.LockVaultNow -> lockVaultFromUser()
            DesktopUiEvent.RecordUserActivity -> { lastActivityMillis = clock.nowMillis() }

            is DesktopUiEvent.SetSettingsDialogVisible -> _uiState.update { it.copy(showSettingsDialog = event.visible) }
            is DesktopUiEvent.SetLanguage -> updateLanguageSetting(event.language)
            is DesktopUiEvent.SetTrashRetentionDays -> updateTrashRetentionDays(event.days)
            is DesktopUiEvent.SetHideLockedNotes -> updateHideLockedNotes(event.hide)
            DesktopUiEvent.RequestOpenNotePinUnlock -> _uiState.update {
                it.copy(showNotePinUnlockDialog = true, notePinError = null)
            }
            DesktopUiEvent.DismissNotePinUnlock -> {
                pendingProtectedAction = null
                _uiState.update { it.copy(showNotePinUnlockDialog = false, notePinError = null) }
            }
            is DesktopUiEvent.SubmitNotePinUnlock -> submitNotePinUnlock(event.pinChars)
            is DesktopUiEvent.SetNotePinSetupDialogVisible -> _uiState.update {
                it.copy(showNotePinSetupDialog = event.visible, notePinError = null)
            }
            is DesktopUiEvent.SaveNotePin -> saveOrUpdateNotePin(event.currentPinChars, event.newPinChars)
            DesktopUiEvent.LockNoteVaultSession -> lockNoteVaultSession()
        }
    }

    private fun selectNoteImmediate(syncUuid: String) {
        scope.launch {
            if (_uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val currentState = _uiState.value
            val found = currentState.allActiveNotes.firstOrNull { it.syncUuid == syncUuid }
                ?: currentState.trashedNotes.firstOrNull { it.syncUuid == syncUuid }
                ?: return@launch

            val protectedNote = isNoteProtected(found, currentState.categories)
            if (protectedNote && noteVaultManager?.isUnlocked() != true) {
                pendingProtectedAction = PendingProtectedAction.OpenNote(syncUuid)
                if (noteVaultManager?.isPinSetSync() == true) {
                    _uiState.update {
                        it.copy(
                            showNotePinUnlockDialog = true,
                            notePinError = null,
                            notePinCooldownSeconds = noteVaultManager.currentCooldownSeconds()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            showNotePinSetupDialog = true,
                            notePinError = null
                        )
                    }
                }
                return@launch
            }

            if (found.description.orEmpty().startsWith("ENC_V2:")) {
                emitLocalizedMessage(
                    "Bu not telefondaki donanım anahtarıyla kilitlenmişti. Telefondan kilidini açıp bir kez kaydedince PC'de de açılır.",
                    "This note was locked with a phone hardware key. Open & save it once on your phone to unlock on PC.",
                    isError = true
                )
                return@launch
            }

            val decodedDesc = decodeNoteDescriptionForEditor(found) ?: return@launch
            _uiState.update {
                it.copy(
                    selectedNoteSyncUuid = found.syncUuid,
                    editorTitle = found.title.orEmpty(),
                    editorIcon = found.icon ?: "📝",
                    editorBlocks = BlockParser.parse(decodedDesc),
                    editorCategorySyncUuid = found.categorySyncUuid,
                    isEditorDirty = false,
                    showQuickSwitcher = false,
                    showZenReadingMode = false
                )
            }
        }
    }

    private fun createNewNote(initialStatus: String = "TODO") {
        val allowed = setOf("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE")
        val normalizedStatus = initialStatus.trim().uppercase().takeIf { it in allowed } ?: "TODO"
        createNoteInternal(
            title = "Yeni Not",
            icon = "📝",
            initialBlocks = listOf(NoteBlock(type = BlockType.PARAGRAPH, content = "")),
            initialStatus = normalizedStatus,
            deferSyncForDraft = true
        )
    }

    private fun createNoteInternal(
        title: String,
        icon: String,
        initialBlocks: List<NoteBlock>,
        reminderTime: Long? = null,
        preferredCategoryName: String? = null,
        initialStatus: String = "TODO",
        deferSyncForDraft: Boolean = false
    ) {
        scope.launch {
            if (_uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val state = _uiState.value
            val catUuid = preferredCategoryName?.let { wanted ->
                state.categories.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.syncUuid
            } ?: if (state.sidebarSection == SidebarSection.CATEGORY) {
                state.selectedCategorySyncUuid
            } else {
                state.categories.firstOrNull()?.syncUuid
            }
            val newUuid = java.util.UUID.randomUUID().toString()
            pendingCreateSyncUuid = newUuid
            if (deferSyncForDraft) {
                pendingBlankDraftSyncUuid = newUuid
            }
            val normalizedBlocks = initialBlocks.ifEmpty {
                listOf(NoteBlock(type = BlockType.PARAGRAPH, content = ""))
            }
            val cleanTitle = title.trim().ifEmpty { "Yeni Not" }
            val cleanIcon = icon.trim().ifEmpty { "📝" }
            val now = clock.nowMillis()
            _uiState.update {
                it.copy(
                    sidebarSection = if (it.sidebarSection == SidebarSection.TRASH) SidebarSection.ALL else it.sidebarSection,
                    selectedNoteSyncUuid = newUuid,
                    editorTitle = cleanTitle,
                    editorIcon = cleanIcon,
                    editorBlocks = normalizedBlocks,
                    editorCategorySyncUuid = catUuid,
                    isEditorDirty = false,
                    showTemplateGalleryDialog = false,
                    showZenReadingMode = false
                )
            }
            val res = noteRepository.upsertNote(
                Note(
                    title = cleanTitle,
                    description = BlockParser.serialize(normalizedBlocks),
                    date = now,
                    reminderTime = reminderTime,
                    status = initialStatus,
                    categorySyncUuid = catUuid,
                    icon = cleanIcon,
                    syncUuid = newUuid
                )
            )
            if (res is AppResult.Failure) {
                pendingCreateSyncUuid = null
                if (pendingBlankDraftSyncUuid == newUuid) pendingBlankDraftSyncUuid = null
                _uiState.update { it.copy(selectedNoteSyncUuid = null) }
                emitMessage(res.error.toTurkishMessage(), isError = true)
            } else if (!deferSyncForDraft) {
                pendingBlankDraftSyncUuid = null
                triggerSyncAfterCommit()
            }
        }
    }

    private fun createDailyAgendaNote() {
        val now = clock.nowMillis()
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale.forLanguageTag("tr-TR"))
        val todayStr = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(formatter)
        val expectedTitle = "Ajanda: $todayStr"
        val existingToday = _uiState.value.allActiveNotes.firstOrNull {
            it.title?.equals(expectedTitle, ignoreCase = true) == true
        }
        if (existingToday != null) {
            selectNoteImmediate(existingToday.syncUuid)
            emitMessage("Bugünün ajandası zaten mevcut, açılıyor.")
            return
        }
        val blocks = listOf(
            NoteBlock(
                type = BlockType.CALLOUT,
                calloutIcon = "☀️",
                content = "Günün Mottosu: Küçük adımlar, büyük zaferler doğurur."
            ),
            NoteBlock(type = BlockType.HEADING_2, content = "Öncelikli Görevler"),
            NoteBlock(type = BlockType.TODO, content = "En kritik 1. işi tamamla", isChecked = false),
            NoteBlock(type = BlockType.TODO, content = "Önemli e-postaları yanıtla", isChecked = false),
            NoteBlock(type = BlockType.TODO, content = "Günün değerlendirmesini yap", isChecked = false),
            NoteBlock(type = BlockType.DIVIDER),
            NoteBlock(type = BlockType.HEADING_2, content = "Günün Notları & Düşünceler"),
            NoteBlock(type = BlockType.PARAGRAPH, content = "Bugün dikkatimi çekenler ve öğrendiklerim...")
        )
        createNoteInternal(
            title = expectedTitle,
            icon = "📅",
            initialBlocks = blocks,
            reminderTime = now,
            preferredCategoryName = "Günlük"
        )
    }

    private fun setNoteReminder(syncUuid: String, reminderTime: Long?) {
        scope.launch {
            if (_uiState.value.selectedNoteSyncUuid == syncUuid && _uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val target = _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == syncUuid } ?: return@launch
            when (val res = noteRepository.upsertNote(target.copy(reminderTime = reminderTime, isReminderDone = false))) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(showReminderDialog = false) }
                    triggerSyncAfterCommit()
                    emitLocalizedMessage(
                        if (reminderTime != null) "Hatırlatıcı takvime işlendi." else "Hatırlatıcı kaldırıldı.",
                        if (reminderTime != null) "Reminder scheduled." else "Reminder removed."
                    )
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun renameCategory(syncUuid: String, newName: String) {
        scope.launch {
            when (val res = noteRepository.renameCategory(syncUuid, newName)) {
                is AppResult.Success -> {
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("'${res.data.name}' olarak güncellendi.", "Renamed to '${res.data.name}'.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun toggleCategoryLock(syncUuid: String) {
        scope.launch {
            val cat = _uiState.value.categories.firstOrNull { it.syncUuid == syncUuid }
            val unlockingCat = cat?.isPassword == true
            if (unlockingCat && noteVaultManager?.isPinSetSync() == true && noteVaultManager.isUnlocked() != true) {
                _uiState.update {
                    it.copy(
                        showNotePinUnlockDialog = true,
                        notePinError = null
                    )
                }
                emitLocalizedMessage("Kilitli kategoriyi açmak için önce PIN girin.", "Enter PIN first to unlock category.", isError = true)
                return@launch
            }
            if (!unlockingCat && noteVaultManager?.isPinSetSync() == false) {
                _uiState.update { it.copy(showNotePinSetupDialog = true, notePinError = null) }
                emitLocalizedMessage("Kategori kilitlemek için önce 6 haneli PIN belirleyin.", "Set a 6-digit PIN before locking a category.")
                return@launch
            }
            when (val res = noteRepository.toggleCategoryLock(syncUuid)) {
                is AppResult.Success -> {
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("Kategori kilit durumu güncellendi.", "Category lock status updated.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun permanentDeleteNote(syncUuid: String) {
        scope.launch {
            when (val res = noteRepository.permanentDeleteNote(syncUuid)) {
                is AppResult.Success -> {
                    _uiState.update { current ->
                        if (current.selectedNoteSyncUuid == syncUuid) {
                            current.copy(
                                selectedNoteSyncUuid = null,
                                editorTitle = "",
                                editorBlocks = emptyList(),
                                isEditorDirty = false
                            )
                        } else current
                    }
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("Not kalıcı olarak silindi.", "Note permanently deleted.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun emptyTrash() {
        scope.launch {
            when (val res = noteRepository.emptyTrash()) {
                is AppResult.Success -> {
                    _uiState.update {
                        if (it.selectedNote != null && isNoteInTrash(it.selectedNote!!)) {
                            it.copy(selectedNoteSyncUuid = null, editorTitle = "", editorBlocks = emptyList(), isEditorDirty = false)
                        } else {
                            it
                        }
                    }
                    triggerSyncAfterCommit()
                    emitLocalizedMessage(
                        "Çöp kutusu kalıcı olarak temizlendi (${res.data} not).",
                        "Trash permanently emptied (${res.data} notes)."
                    )
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun addStreamItem(content: String) {
        scope.launch {
            when (val res = noteRepository.addStreamItem(content)) {
                is AppResult.Success -> triggerSyncAfterCommit()
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun deleteStreamItem(syncUuid: String) {
        scope.launch {
            when (val res = noteRepository.deleteStreamItem(syncUuid)) {
                is AppResult.Success -> {
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("Fikir akıştan kaldırıldı.", "Idea removed from stream.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun convertStreamItemsToNote(syncUuids: List<String>, title: String) {
        scope.launch {
            when (val res = noteRepository.convertStreamItemsToNote(syncUuids, title)) {
                is AppResult.Success -> {
                    val created = res.data
                    _uiState.update {
                        it.copy(
                            homeViewMode = DesktopViewMode.CARDS,
                            selectedNoteSyncUuid = created.syncUuid,
                            editorTitle = created.title.orEmpty(),
                            editorIcon = created.icon ?: "⚡",
                            editorBlocks = BlockParser.parse(created.description),
                            editorCategorySyncUuid = created.categorySyncUuid,
                            isEditorDirty = false
                        )
                    }
                    emitLocalizedMessage(
                        "Seçili fikirler '${created.title}' notuna dönüştürüldü.",
                        "Selected ideas converted into '${created.title}'."
                    )
                    triggerSyncAfterCommit()
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun openMediaExternally(mediaPath: String) {
        scope.launch {
            try {
                val bytes = readMediaBytes(mediaPath)
                if (bytes == null || bytes.isEmpty()) {
                    emitLocalizedMessage("Medya dosyası henüz bu cihaza indirilmemiş.", "Media file has not been downloaded yet.", isError = true)
                    return@launch
                }
                val ext = mediaPath.substringAfterLast('.', "m4a").takeIf { it.length in 1..6 } ?: "m4a"
                val previewDir = resolvePreviewTempDir()
                val opened = withContext(Dispatchers.IO) {
                    previewDir.mkdirs()
                    val tmpFile = Files.createTempFile(previewDir.toPath(), "purenoteit_media_", ".$ext").toFile()
                    tmpFile.deleteOnExit()
                    tmpFile.writeBytes(bytes)
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(tmpFile)
                        true
                    } else {
                        false
                    }
                }
                if (!opened) {
                    emitLocalizedMessage("Sistem varsayılan medya oynatıcısı açılamadı.", "Default media player could not be opened.", isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitLocalizedMessage("Medya dosyası açılamadı.", "Failed to open media file.", isError = true)
            }
        }
    }

    private fun startAudioRecording(targetBlockId: String?) {
        scope.launch {
            if (_uiState.value.selectedNote == null) {
                emitLocalizedMessage(
                    "Ses kaydı eklemek için önce bir not seçin.",
                    "Open or create a note first before recording audio.",
                    isError = true
                )
                return@launch
            }
            when (audioRecorder.startRecording(targetBlockId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> emitLocalizedMessage(
                    "Mikrofon açılamadı. Sistem ses giriş cihazını kontrol edin.",
                    "Microphone unavailable. Please check your audio input device.",
                    isError = true
                )
            }
        }
    }

    private fun stopAudioRecordingAndAttach() {
        val targetBlockId = audioRecorder.state.value.targetBlockId
        scope.launch {
            try {
                val wavBytes = withContext(Dispatchers.IO) {
                    audioRecorder.stopRecordingAndBuildWav()
                }
                when (wavBytes) {
                    is AppResult.Failure -> {
                        emitLocalizedMessage(
                            "Kaydedilecek ses verisi yakalanamadı.",
                            "No audio data was captured.",
                            isError = true
                        )
                    }
                    is AppResult.Success -> {
                        val mediaPath = mediaPathResolver.storeMediaBytes(wavBytes.data, "audio", "wav")
                        val defaultTitle = if (_uiState.value.language == "en") "Voice Note Recording" else "Sesli Not Kaydı"
                        _uiState.update { current ->
                            val existingIdx = targetBlockId?.let { id ->
                                current.editorBlocks.indexOfFirst { it.id == id }
                            } ?: -1
                            val updatedBlocks = if (existingIdx >= 0) {
                                current.editorBlocks.toMutableList().apply {
                                    val blk = this[existingIdx]
                                    this[existingIdx] = blk.copy(
                                        type = BlockType.AUDIO,
                                        mediaPath = mediaPath,
                                        content = blk.content.ifBlank { defaultTitle }
                                    )
                                }
                            } else {
                                current.editorBlocks + NoteBlock(
                                    type = BlockType.AUDIO,
                                    content = defaultTitle,
                                    mediaPath = mediaPath
                                )
                            }
                            current.copy(editorBlocks = updatedBlocks, isEditorDirty = true)
                        }
                        flushCurrentEditorToDb(immediateSync = true, force = true)
                        emitLocalizedMessage(
                            "Ses kaydı şifreli kasaya eklendi ve eşitleniyor.",
                            "Audio recording encrypted, attached & syncing."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitLocalizedMessage("Ses kaydı kaydedilemedi.", "Failed to save audio recording.", isError = true)
            }
        }
    }

    private fun playOrToggleAudio(mediaPath: String) {
        scope.launch {
            try {
                val bytes = readMediaBytes(mediaPath)
                if (bytes == null || bytes.isEmpty()) {
                    emitLocalizedMessage(
                        "Ses dosyası henüz bu cihaza indirilmemiş.",
                        "Audio file has not been downloaded yet.",
                        isError = true
                    )
                    return@launch
                }
                val handledNatively = if (mediaPath.endsWith(".wav", ignoreCase = true)) {
                    audioRecorder.playOrToggleWav(mediaPath, bytes)
                } else {
                    false
                }
                if (!handledNatively) {
                    openMediaExternally(mediaPath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                openMediaExternally(mediaPath)
            }
        }
    }

    private fun updateNoteStatus(syncUuid: String, rawStatus: String) {
        val allowed = setOf("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE")
        val normalizedStatus = rawStatus.trim().uppercase().takeIf { it in allowed } ?: "TODO"
        scope.launch {
            if (_uiState.value.selectedNoteSyncUuid == syncUuid && _uiState.value.isEditorDirty) {
                flushCurrentEditorToDb()
            }
            val target = _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == syncUuid } ?: return@launch
            when (val res = noteRepository.upsertNote(target.copy(status = normalizedStatus))) {
                is AppResult.Success -> triggerSyncAfterCommit()
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun createCategory(name: String) {
        scope.launch {
            when (val res = noteRepository.upsertCategory(name)) {
                is AppResult.Success -> {
                    _uiState.update { it.copy(showCreateCategoryDialog = false) }
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("'${res.data.name}' kategorisi eklendi.", "Category '${res.data.name}' added.")
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
            triggerSyncAfterCommit()
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
            triggerSyncAfterCommit()
            emitLocalizedMessage("Not çöp kutusuna taşındı.", "Note moved to Trash.")
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
                    triggerSyncAfterCommit()
                    emitLocalizedMessage("'${res.data.title}' alt sayfası oluşturuldu.", "Sub-page '${res.data.title}' created.")
                }
                is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
            }
        }
    }

    private fun importImageBytes(blockId: String, bytes: ByteArray, extension: String) {
        scope.launch {
            try {
                val (optBytes, optExt) = withContext(Dispatchers.IO) {
                    DesktopImageOptimizer.optimizeForSync(bytes, extension.ifBlank { "png" }, isDrawing = false)
                }
                val mediaPath = mediaPathResolver.storeMediaBytes(optBytes, "images", optExt)
                updateMediaPathForBlock(blockId, mediaPath)
                emitMessage("Görsel optimize edilip nota eklendi.")
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
                val (optBytes, optExt) = withContext(Dispatchers.IO) {
                    DesktopImageOptimizer.optimizeForSync(pngBytes, "png", isDrawing = true)
                }
                val mediaPath = mediaPathResolver.storeMediaBytes(optBytes, "drawings", optExt)
                // Desktop edit makes the PNG the source of truth; clear stale vector ink so the phone doesn't overwrite it.
                _uiState.update { current ->
                    val updated = current.editorBlocks.map { block ->
                        if (block.id == blockId) block.copy(mediaPath = mediaPath, inkJson = "") else block
                    }
                    current.copy(editorBlocks = updated, isEditorDirty = true, activeDrawingBlockId = null)
                }
                flushCurrentEditorToDb(immediateSync = true)
                emitLocalizedMessage("Çizim kaydedildi ve eşitleniyor.", "Drawing saved & syncing.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emitLocalizedMessage("Çizim kaydedilemedi.", "Failed to save drawing.", isError = true)
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
        scope.launch {
            flushCurrentEditorToDb(immediateSync = true)
        }
    }

    private fun scheduleAutoSave(delayMs: Long = 450L) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(delayMs)
            flushCurrentEditorToDb(immediateSync = true)
        }
    }

    private suspend fun flushCurrentEditorToDb(
        immediateSync: Boolean = true,
        force: Boolean = false
    ) {
        autoSaveJob?.cancel()
        editorFlushMutex.withLock {
            val state = _uiState.value
            val note = state.selectedNote ?: return
            if ((!state.isEditorDirty && !force) || isNoteInTrash(note)) return
            val isProtected = isNoteProtected(note, state.categories)
            if (isProtected && noteVaultManager?.isUnlocked() != true) {
                // Fail-closed: never overwrite an encrypted/protected note while locked
                return
            }
            val serialized = BlockParser.serialize(state.editorBlocks)
            val persistedDescription = if (isProtected && noteVaultManager != null) {
                when (val enc = noteVaultManager.encryptNoteDescription(serialized)) {
                    is AppResult.Success -> enc.data
                    is AppResult.Failure -> return
                }
            } else {
                serialized
            }
            val blocksForCounts = state.editorBlocks
            val (totalTasks, completedTasks) = BlockParser.calculateTasks(blocksForCounts)
            val drawingPaths = blocksForCounts.mapNotNull { b ->
                b.mediaPath?.takeIf { b.type == BlockType.DRAWING }
            }.toSet()
            val extractedImages = blocksForCounts.mapNotNull { b ->
                b.mediaPath?.takeIf { b.type == BlockType.IMAGE }
            }.ifEmpty { note.images.orEmpty().filterNot { it in drawingPaths } }

            val updated = note.copy(
                title = state.editorTitle.ifBlank { "Başlıksız Not" },
                icon = state.editorIcon,
                description = persistedDescription,
                images = extractedImages,
                isPassword = isProtected || note.isPassword == true,
                isEncrypted = isProtected || note.isEncrypted,
                totalTasks = totalTasks,
                completedTasks = completedTasks,
                categorySyncUuid = state.editorCategorySyncUuid ?: note.categorySyncUuid
            )
            val upsertRes = noteRepository.upsertNote(updated)
            if (pendingBlankDraftSyncUuid == note.syncUuid) {
                pendingBlankDraftSyncUuid = null
            }
            _uiState.update { it.copy(isEditorDirty = false) }
            if (upsertRes is AppResult.Success) {
                if (immediateSync) {
                    triggerSyncAfterCommit()
                } else {
                    scheduleDebouncedDriveSync()
                }
            }
        }
    }

    /** Run approvals granted this session, keyed by block + language + code hash (editing the code re-prompts). */
    private val approvedCodeRuns = mutableSetOf<String>()

    private fun requiresRunApproval(language: String): Boolean =
        language.trim().lowercase() in PROCESS_CODE_LANGUAGES

    private fun runCodeBlock(blockId: String, language: String, code: String, bashApproved: Boolean) {
        // Native/host code execution (python, node, java, kotlin, c/c++, bash) always needs explicit approval
        // (notes can arrive via sync, import or templates). Approval is bound to the exact code text.
        val approvalKey = "$blockId:${language.trim().lowercase()}:${code.hashCode()}"
        if (requiresRunApproval(language)) {
            if (bashApproved) {
                approvedCodeRuns.add(approvalKey)
            } else if (approvalKey !in approvedCodeRuns) {
                _uiState.update { current ->
                    current.copy(
                        codeRunStates = current.codeRunStates + (
                            blockId to CodeBlockRunState(
                                blockId = blockId,
                                awaitingBashApproval = true,
                                pendingCode = code,
                                pendingLanguage = language
                            )
                            )
                    )
                }
                return
            }
        }
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
                        triggerDriveSync(silent = false, fetchUsage = true)
                        startAutoSyncMonitor()
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

    private fun saveOAuthConfig(clientId: String, clientSecret: String) {
        val saver = saveDesktopOAuthConfig ?: return
        scope.launch {
            val ok = saver(clientId, clientSecret)
            if (ok) {
                _uiState.update { it.copy(isDesktopOAuthConfigured = oauthClientConfigProvider.getConfig() != null) }
                emitMessage("Google Desktop OAuth bilgileri güvenli kasaya kaydedildi.")
            } else {
                emitMessage("Geçerli bir Google OAuth Client ID girin.", isError = true)
            }
        }
    }

    private fun disconnectDrive() {
        debouncedSyncJob?.cancel()
        autoSyncMonitorJob?.cancel()
        scope.launch {
            tokenStorage.clearAll()
            _uiState.update {
                it.copy(
                    isDriveConnected = false,
                    hasStoredPassphrase = false,
                    lastSyncSummary = null,
                    driveMediaUsage = null
                )
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
                        refreshCredentialStatus(syncIfConnected = true)
                        startAutoLockMonitor()
                        startAutoSyncMonitor()
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
        debouncedSyncJob?.cancel()
        autoSyncMonitorJob?.cancel()
        if (vaultSecurityManager.isVaultUnlocked()) {
            flushCurrentEditorToDb()
        }
        observerJob?.cancel()
        autoLockJob?.cancel()
        noteVaultManager?.lockSessionSync()
        scrubTempPreviewFiles()
        val snap = vaultSecurityManager.lockVault()
        _uiState.update { current ->
            current.copy(
                isVaultLocked = true,
                isMasterLockEnabled = snap.isMasterLockEnabled,
                isNoteVaultUnlocked = false,
                allActiveNotes = emptyList(),
                trashedNotes = emptyList(),
                filteredNotes = emptyList(),
                noteLinks = emptyList(),
                streamItems = emptyList(),
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
                showSettingsDialog = false,
                showNotePinUnlockDialog = false,
                showNotePinSetupDialog = false,
                showTemplateGalleryDialog = false,
                showCategoryManagementDialog = false,
                showZenReadingMode = false,
                showExportDialog = false,
                showReminderDialog = false,
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
                if (st.isVaultLocked || st.autoLockMinutes <= 0) continue
                val idleMs = clock.nowMillis() - lastActivityMillis
                if (idleMs >= st.autoLockMinutes * 60_000L) {
                    if (st.isMasterLockEnabled) {
                        lockVaultInternal("Hareketsizlik nedeniyle kasa otomatik kilitlendi.")
                        break
                    } else if (st.isNoteVaultUnlocked) {
                        lockNoteVaultSession(silent = true)
                    }
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

    private fun triggerSyncAfterCommit() {
        if (_uiState.value.isVaultLocked || !_uiState.value.isDriveConnected) return
        debouncedSyncJob?.cancel()
        triggerDriveSync(silent = true, fetchUsage = false)
    }

    private fun scheduleDebouncedDriveSync() {
        if (_uiState.value.isVaultLocked || !_uiState.value.isDriveConnected) return
        debouncedSyncJob?.cancel()
        debouncedSyncJob = scope.launch {
            delay(350L)
            if (!_uiState.value.isVaultLocked && _uiState.value.isDriveConnected) {
                triggerDriveSync(silent = true, fetchUsage = false)
            }
        }
    }

    private fun startAutoSyncMonitor() {
        autoSyncMonitorJob?.cancel()
        autoSyncMonitorJob = scope.launch {
            while (true) {
                delay(25_000L)
                val st = _uiState.value
                val isEditingUnsavedDraft = pendingBlankDraftSyncUuid != null &&
                    st.selectedNoteSyncUuid == pendingBlankDraftSyncUuid
                if (!st.isVaultLocked && st.isDriveConnected && !st.isSyncing &&
                    !st.isEditorDirty && !st.isRecordingAudio && !isEditingUnsavedDraft
                ) {
                    triggerDriveSync(silent = true, fetchUsage = false)
                }
            }
        }
    }

    private fun triggerDriveSync(silent: Boolean = false, fetchUsage: Boolean = false) {
        if (driveSyncMutex.isLocked) {
            pendingDriveSyncRequested = true
            return
        }
        scope.launch {
            driveSyncMutex.withLock {
                if (!silent) {
                    _uiState.update { it.copy(isSyncing = true) }
                }
                try {
                    do {
                        pendingDriveSyncRequested = false
                        executeDriveSyncLocked(silent = silent, fetchUsage = fetchUsage)
                    } while (pendingDriveSyncRequested && !_uiState.value.isVaultLocked && _uiState.value.isDriveConnected)
                } finally {
                    _uiState.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    private fun forcePushAllToDrive() {
        if (!_uiState.value.isDriveConnected) return
        scope.launch {
            driveSyncMutex.withLock {
                _uiState.update { it.copy(isSyncing = true) }
                try {
                    val cursor = localSyncStore.getSyncCursorState()
                    localSyncStore.saveSyncCursorState(cursor.copy(lastPushedAt = Long.MIN_VALUE))
                    executeDriveSyncLocked(silent = false, fetchUsage = true)
                } finally {
                    _uiState.update { it.copy(isSyncing = false) }
                }
            }
        }
    }

    private suspend fun executeDriveSyncLocked(silent: Boolean, fetchUsage: Boolean) {
        flushCurrentEditorToDb(immediateSync = false)
        val passRes = autoSyncKeyCoordinator?.resolveOrBootstrapPassphrase()
            ?: tokenStorage.getSyncPassphrase()
        val passChars = when (passRes) {
            is AppResult.Failure -> {
                if (!silent) emitMessage(passRes.error.toTurkishMessage(), isError = true)
                return
            }
            is AppResult.Success -> passRes.data ?: run {
                if (!silent) emitLocalizedMessage("Otomatik eşitleme anahtarı alınamadı.", "Auto-sync key could not be retrieved.", isError = true)
                return
            }
        }
        _uiState.update { it.copy(hasStoredPassphrase = true) }

        try {
            syncVaultSettingsBeforeDeltaLocked(passChars)
            when (val syncRes = driveSyncEngine.synchronize(passChars)) {
                is AppResult.Success -> {
                    val s = syncRes.data
                    val text = "Eşitlendi ↑${s.pushedEntities} kayıt, ↓${s.pulledNotes} not (Çakışma: ${s.conflictCopiesCreated})"
                    _uiState.update { it.copy(lastSyncSummary = text) }
                    val hasRemotePull = s.pulledNotes > 0 || s.mediaDownloaded > 0
                    if (!silent || hasRemotePull) {
                        emitMessage(text)
                    }
                    if (fetchUsage || _uiState.value.showSyncDialog) {
                        when (val usage = driveSyncEngine.getRemoteUsageSummary()) {
                            is AppResult.Success -> _uiState.update { it.copy(driveMediaUsage = usage.data) }
                            is AppResult.Failure -> Unit
                        }
                    }
                }
                is AppResult.Failure -> {
                    if (!silent) emitMessage(syncRes.error.toTurkishMessage(), isError = true)
                }
            }
        } finally {
            passChars.fill('\u0000')
        }
    }

    private fun refreshDriveUsage() {
        if (!_uiState.value.isDriveConnected) return
        scope.launch {
            when (val res = driveSyncEngine.getRemoteUsageSummary()) {
                is AppResult.Success -> _uiState.update { it.copy(driveMediaUsage = res.data) }
                is AppResult.Failure -> Unit
            }
        }
    }

    private fun cleanOrphanDriveMedia() {
        if (!_uiState.value.isDriveConnected || _uiState.value.isCleaningCloudMedia) return
        scope.launch {
            _uiState.update { it.copy(isCleaningCloudMedia = true) }
            try {
                val passRes = autoSyncKeyCoordinator?.resolveOrBootstrapPassphrase()
                    ?: tokenStorage.getSyncPassphrase()
                val passChars = (passRes as? AppResult.Success)?.data
                if (passChars != null) {
                    try {
                        driveSyncEngine.synchronize(passChars)
                    } finally {
                        passChars.fill('\u0000')
                    }
                }
                when (val cleanRes = driveSyncEngine.cleanupOrphanRemoteMedia()) {
                    is AppResult.Success -> {
                        val count = cleanRes.data
                        val usage = (driveSyncEngine.getRemoteUsageSummary() as? AppResult.Success)?.data
                        _uiState.update { it.copy(driveMediaUsage = usage ?: it.driveMediaUsage) }
                        emitMessage(
                            if (count > 0) "Drive üzerinden $count kullanılmayan medya dosyası temizlendi."
                            else "Drive üzerinde temizlenecek gereksiz medya bulunmadı."
                        )
                    }
                    is AppResult.Failure -> emitMessage(cleanRes.error.toTurkishMessage(), isError = true)
                }
            } finally {
                _uiState.update { it.copy(isCleaningCloudMedia = false) }
            }
        }
    }

    private fun startLanServer(pairPinChars: CharArray) {
        val pinCopy = pairPinChars.copyOf()
        pairPinChars.fill('\u0000')
        if (pinCopy.size < LAN_PAIR_CODE_MIN_LENGTH) {
            pinCopy.fill('\u0000')
            emitMessage("Wi-Fi P2P eşleşme kodu en az $LAN_PAIR_CODE_MIN_LENGTH karakter olmalıdır.", isError = true)
            return
        }
        scope.launch {
            try {
                val (ip, port) = lanSyncServer.start(pinCopy)
                val url = "https://$ip:$port#${lanSyncServer.certFingerprintHex}"
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
                val (peerBase, peerPin) = com.notes.purenoteit.desktop.data.sync.parseLanPeerUrl(cleanUrl)
                if (!peerBase.startsWith("https://") || peerPin == null) {
                    emitMessage("Güvenli bağlantı için adres https:// ile başlamalı ve #parmakizi içermeli (sunucudaki adresi aynen kopyalayın).", isError = true)
                    return@launch
                }
                val remoteDs = LanWebSocketSyncDataSourceImpl(
                    baseHttpUrl = peerBase,
                    bearerHexToken = tokenHex,
                    pinnedCertSha256Hex = peerPin
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

    private fun emitLocalizedMessage(trMessage: String, enMessage: String, isError: Boolean = false) {
        val msg = if (_uiState.value.language == "en") enMessage else trMessage
        _effects.trySend(DesktopUiEffect.ShowMessage(msg, isError))
    }

    private fun isNoteInTrash(note: Note): Boolean =
        note.deletedAt != null || note.status.equals("TRASH", ignoreCase = true)

    private fun isNoteProtected(note: Note, categories: List<Category>): Boolean {
        val lockedCats = categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
        return note.isPassword == true ||
            note.isEncrypted ||
            (note.categorySyncUuid != null && note.categorySyncUuid in lockedCats) ||
            vaultCrypto.isEncryptedPayload(note.description)
    }

    private fun decodeNoteDescriptionForEditor(note: Note): String? {
        val raw = note.description.orEmpty()
        if (!vaultCrypto.isEncryptedPayload(raw)) return raw
        val manager = noteVaultManager ?: return null
        return when (val dec = manager.decryptNoteDescription(raw)) {
            is AppResult.Success -> dec.data
            is AppResult.Failure -> null
        }
    }

    private fun applyFilter(
        activeNotes: List<Note>,
        rootActiveNotes: List<Note>,
        trashedNotes: List<Note>,
        categories: List<Category>,
        section: SidebarSection,
        categorySyncUuid: String?,
        query: String,
        isNoteVaultUnlocked: Boolean,
        hideLockedNotes: Boolean
    ): List<Note> {
        val lockedCatUuids = categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
        val cleanQuery = query.trim().lowercase()
        val sourcePool = if (cleanQuery.isEmpty()) rootActiveNotes else activeNotes
        val rawPool = when (section) {
            SidebarSection.TRASH -> trashedNotes
            SidebarSection.PINNED -> sourcePool.filter { it.isPinned }
            SidebarSection.TASKS -> sourcePool.filter { it.totalTasks > 0 }
            SidebarSection.AGENDA -> sourcePool.filter { it.reminderTime != null }
                .sortedBy { it.reminderTime ?: Long.MAX_VALUE }
            SidebarSection.CATEGORY -> sourcePool.filter { it.categorySyncUuid == categorySyncUuid }
            SidebarSection.ALL -> sourcePool
        }
        val visibilityFiltered = if (!isNoteVaultUnlocked && hideLockedNotes && section != SidebarSection.TRASH) {
            rawPool.filterNot { note ->
                note.isPassword == true ||
                    note.isEncrypted ||
                    (note.categorySyncUuid != null && note.categorySyncUuid in lockedCatUuids)
            }
        } else {
            rawPool
        }
        if (cleanQuery.isEmpty()) return visibilityFiltered
        return visibilityFiltered.filter { note ->
            val isProtected = note.isPassword == true ||
                note.isEncrypted ||
                (note.categorySyncUuid != null && note.categorySyncUuid in lockedCatUuids) ||
                vaultCrypto.isEncryptedPayload(note.description)
            if (isProtected && !isNoteVaultUnlocked) {
                // Zero Search Oracle leak on locked notes while locked
                false
            } else {
                val plainDesc = decodeNoteDescriptionForEditor(note) ?: note.description
                note.title.orEmpty().lowercase().contains(cleanQuery) ||
                    BlockParser.findSearchSnippet(plainDesc, cleanQuery) != null
            }
        }
    }

    private suspend fun syncVaultSettingsBeforeDeltaLocked(passChars: CharArray) {
        val coordinator = vaultSettingsCoordinator ?: return
        val prefs = preferencesStore?.load()
        val pinDoc = noteVaultManager?.exportPinDocument()
        val localDoc = if (pinDoc != null) {
            pinDoc.copy(
                trashRetentionDays = prefs?.trashRetentionDays ?: pinDoc.trashRetentionDays,
                settingsUpdatedAt = maxOf(pinDoc.settingsUpdatedAt, prefs?.settingsUpdatedAt ?: 0L)
            )
        } else if (prefs != null && (prefs.settingsUpdatedAt > 0L || prefs.trashRetentionDays != 30)) {
            SyncedVaultSecurityDocument(
                trashRetentionDays = prefs.trashRetentionDays,
                settingsUpdatedAt = prefs.settingsUpdatedAt
            )
        } else {
            null
        }
        when (val res = coordinator.synchronize(localDoc, passChars)) {
            is AppResult.Success -> {
                val synced = res.data ?: return
                val pinApplied = noteVaultManager?.applySyncedDocument(synced) == true
                if (prefs == null || synced.settingsUpdatedAt > prefs.settingsUpdatedAt) {
                    val cleanRetention = synced.trashRetentionDays.coerceIn(0, 365)
                    preferencesStore?.update {
                        it.copy(
                            trashRetentionDays = cleanRetention,
                            settingsUpdatedAt = synced.settingsUpdatedAt
                        )
                    }
                    _uiState.update { it.copy(trashRetentionDays = cleanRetention) }
                }
                val snap = noteVaultManager?.getPinStatusSnapshot()
                _uiState.update { current ->
                    val wipeProtectedEditor = pinApplied &&
                        snap?.isUnlocked != true &&
                        current.selectedNote?.let { isNoteProtected(it, current.categories) } == true
                    current.copy(
                        isNotePinSet = snap?.isPinSet ?: current.isNotePinSet,
                        isNoteVaultUnlocked = snap?.isUnlocked ?: current.isNoteVaultUnlocked,
                        trashRetentionDays = synced.trashRetentionDays.coerceIn(1, 365),
                        selectedNoteSyncUuid = if (wipeProtectedEditor) null else current.selectedNoteSyncUuid,
                        editorTitle = if (wipeProtectedEditor) "" else current.editorTitle,
                        editorBlocks = if (wipeProtectedEditor) emptyList() else current.editorBlocks,
                        isEditorDirty = if (wipeProtectedEditor) false else current.isEditorDirty
                    )
                }
            }
            is AppResult.Failure -> Unit
        }
    }

    private fun updateLanguageSetting(rawLang: String) {
        val normalized = DesktopPreferencesStore.normalizeLanguage(rawLang)
        preferencesStore?.update { it.copy(language = normalized) }
        _uiState.update { it.copy(language = normalized) }
        emitLocalizedMessage("Dil Türkçe olarak ayarlandı.", "Language switched to English.")
    }

    private fun updateTrashRetentionDays(days: Int) {
        val clean = days.coerceIn(0, 365)
        val now = clock.nowMillis()
        preferencesStore?.update {
            it.copy(trashRetentionDays = clean, settingsUpdatedAt = now)
        }
        _uiState.update { it.copy(trashRetentionDays = clean) }
        scope.launch {
            purgeExpiredTrashIfNeeded(_uiState.value.trashedNotes, retentionOverride = clean)
            triggerSyncAfterCommit()
        }
        if (clean == 0) {
            emitLocalizedMessage(
                "Çöp kutusu otomatik silme devre dışı (Asla).",
                "Trash auto-empty disabled (Never)."
            )
        } else {
            emitLocalizedMessage(
                "Çöp kutusu saklama süresi $clean gün olarak güncellendi.",
                "Trash retention updated to $clean days."
            )
        }
    }

    private fun updateHideLockedNotes(hide: Boolean) {
        preferencesStore?.update { it.copy(hideLockedNotes = hide) }
        _uiState.update { current ->
            val filtered = applyFilter(
                activeNotes = current.allActiveNotes,
                rootActiveNotes = current.rootActiveNotes,
                trashedNotes = current.trashedNotes,
                categories = current.categories,
                section = current.sidebarSection,
                categorySyncUuid = current.selectedCategorySyncUuid,
                query = current.searchQuery,
                isNoteVaultUnlocked = current.isNoteVaultUnlocked,
                hideLockedNotes = hide
            )
            current.copy(hideLockedNotes = hide, filteredNotes = filtered)
        }
    }

    private suspend fun purgeExpiredTrashIfNeeded(
        trashedNotes: List<Note>,
        retentionOverride: Int? = null
    ) {
        val days = (retentionOverride ?: _uiState.value.trashRetentionDays).coerceIn(0, 365)
        if (days <= 0) return
        val threshold = clock.nowMillis() - (days.toLong() * 86_400_000L)
        val hasExpired = trashedNotes.any { note ->
            val trashedAt = note.deletedAt ?: note.updatedAt.takeIf { it > 0L } ?: note.date
            trashedAt < threshold
        }
        if (hasExpired) {
            val res = noteRepository.purgeExpiredTrashNotes(threshold)
            if (res is AppResult.Success && res.data > 0) {
                triggerSyncAfterCommit()
            }
        }
    }

    private fun handleToggleNoteLock(syncUuid: String) {
        scope.launch {
            if (_uiState.value.selectedNoteSyncUuid == syncUuid && _uiState.value.isEditorDirty) {
                flushCurrentEditorToDb(immediateSync = false)
            }
            val currentState = _uiState.value
            val found = currentState.allActiveNotes.firstOrNull { it.syncUuid == syncUuid } ?: return@launch
            val currentlyProtected = isNoteProtected(found, currentState.categories)
            val manager = noteVaultManager ?: return@launch

            if (!manager.isPinSetSync()) {
                pendingProtectedAction = if (currentlyProtected) {
                    PendingProtectedAction.UnlockNote(syncUuid)
                } else {
                    PendingProtectedAction.LockNote(syncUuid)
                }
                _uiState.update { it.copy(showNotePinSetupDialog = true, notePinError = null) }
                return@launch
            }
            if (!manager.isUnlocked()) {
                pendingProtectedAction = if (currentlyProtected) {
                    PendingProtectedAction.UnlockNote(syncUuid)
                } else {
                    PendingProtectedAction.LockNote(syncUuid)
                }
                _uiState.update {
                    it.copy(
                        showNotePinUnlockDialog = true,
                        notePinError = null,
                        notePinCooldownSeconds = manager.currentCooldownSeconds()
                    )
                }
                return@launch
            }
            executeNoteLockToggleUnlocked(found, shouldLock = !currentlyProtected)
        }
    }

    private suspend fun executeNoteLockToggleUnlocked(found: Note, shouldLock: Boolean) {
        val manager = noteVaultManager ?: return
        val nextDesc = if (shouldLock) {
            val plainDesc = decodeNoteDescriptionForEditor(found) ?: found.description.orEmpty()
            when (val enc = manager.encryptNoteDescription(plainDesc)) {
                is AppResult.Success -> enc.data
                is AppResult.Failure -> {
                    emitMessage(enc.error.toTurkishMessage(), isError = true)
                    return
                }
            }
        } else {
            when (val dec = manager.decryptNoteDescription(found.description.orEmpty())) {
                is AppResult.Success -> dec.data
                is AppResult.Failure -> {
                    emitMessage(dec.error.toTurkishMessage(), isError = true)
                    return
                }
            }
        }
        val updated = found.copy(
            isPassword = shouldLock,
            isEncrypted = shouldLock,
            description = nextDesc
        )
        when (val res = noteRepository.upsertNote(updated)) {
            is AppResult.Success -> {
                triggerSyncAfterCommit()
                emitLocalizedMessage(
                    if (shouldLock) "Not gizlendi ve AES-256-GCM ile şifrelendi." else "Not kilidi kaldırıldı.",
                    if (shouldLock) "Note locked & encrypted with AES-256-GCM." else "Note lock removed."
                )
            }
            is AppResult.Failure -> emitMessage(res.error.toTurkishMessage(), isError = true)
        }
    }

    private fun submitNotePinUnlock(pinChars: CharArray) {
        val pinCopy = pinChars.copyOf()
        pinChars.fill('\u0000')
        val manager = noteVaultManager ?: run {
            pinCopy.fill('\u0000')
            return
        }
        scope.launch {
            try {
                when (val res = manager.verifyPinAndUnlock(pinCopy)) {
                    is AppResult.Success -> {
                        val snap = res.data
                        val action = pendingProtectedAction
                        pendingProtectedAction = null
                        lastActivityMillis = clock.nowMillis()
                        _uiState.update { current ->
                            val filtered = applyFilter(
                                activeNotes = current.allActiveNotes,
                                rootActiveNotes = current.rootActiveNotes,
                                trashedNotes = current.trashedNotes,
                                categories = current.categories,
                                section = current.sidebarSection,
                                categorySyncUuid = current.selectedCategorySyncUuid,
                                query = current.searchQuery,
                                isNoteVaultUnlocked = true,
                                hideLockedNotes = current.hideLockedNotes
                            )
                            current.copy(
                                isNotePinSet = snap.isPinSet,
                                isNoteVaultUnlocked = true,
                                showNotePinUnlockDialog = false,
                                notePinError = null,
                                notePinCooldownSeconds = 0,
                                filteredNotes = filtered
                            )
                        }
                        when (action) {
                            is PendingProtectedAction.OpenNote -> selectNoteImmediate(action.syncUuid)
                            is PendingProtectedAction.LockNote -> {
                                _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == action.syncUuid }
                                    ?.let { executeNoteLockToggleUnlocked(it, shouldLock = true) }
                            }
                            is PendingProtectedAction.UnlockNote -> {
                                _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == action.syncUuid }
                                    ?.let { executeNoteLockToggleUnlocked(it, shouldLock = false) }
                            }
                            null -> Unit
                        }
                    }
                    is AppResult.Failure -> {
                        val cd = manager.currentCooldownSeconds()
                        _uiState.update {
                            it.copy(
                                notePinError = if (_uiState.value.language == "en") "Incorrect PIN." else "Yanlış PIN kodu.",
                                notePinCooldownSeconds = cd
                            )
                        }
                        if (cd > 0) {
                            scope.launch {
                                while (true) {
                                    val rem = manager.currentCooldownSeconds()
                                    _uiState.update { it.copy(notePinCooldownSeconds = rem) }
                                    if (rem <= 0) break
                                    delay(1_000L)
                                }
                            }
                        }
                    }
                }
            } finally {
                pinCopy.fill('\u0000')
            }
        }
    }

    private fun saveOrUpdateNotePin(currentPinChars: CharArray?, newPinChars: CharArray) {
        val currCopy = currentPinChars?.copyOf()
        val newCopy = newPinChars.copyOf()
        currentPinChars?.fill('\u0000')
        newPinChars.fill('\u0000')
        val manager = noteVaultManager ?: run {
            currCopy?.fill('\u0000')
            newCopy.fill('\u0000')
            return
        }
        scope.launch {
            try {
                when (val res = manager.setOrChangePin(currCopy, newCopy)) {
                    is AppResult.Success -> {
                        val (_, snap) = res.data
                        val action = pendingProtectedAction
                        pendingProtectedAction = null
                        _uiState.update {
                            it.copy(
                                isNotePinSet = snap.isPinSet,
                                isNoteVaultUnlocked = snap.isUnlocked,
                                showNotePinSetupDialog = false,
                                notePinError = null,
                                notePinCooldownSeconds = 0
                            )
                        }
                        if (action is PendingProtectedAction.LockNote) {
                            _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == action.syncUuid }
                                ?.let { executeNoteLockToggleUnlocked(it, shouldLock = true) }
                        } else if (action is PendingProtectedAction.UnlockNote) {
                            _uiState.value.allActiveNotes.firstOrNull { it.syncUuid == action.syncUuid }
                                ?.let { executeNoteLockToggleUnlocked(it, shouldLock = false) }
                        } else if (action is PendingProtectedAction.OpenNote) {
                            selectNoteImmediate(action.syncUuid)
                        }
                        triggerSyncAfterCommit()
                        emitLocalizedMessage(
                            "6 haneli Note PIN kaydedildi ve uçtan uca şifreli eşitleniyor.",
                            "6-digit Note PIN saved & syncing end-to-end encrypted."
                        )
                    }
                    is AppResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                notePinError = if (_uiState.value.language == "en") {
                                    "Could not update PIN. Check your current PIN."
                                } else {
                                    "PIN güncellenemedi. Mevcut PIN kodunuzu kontrol edin."
                                }
                            )
                        }
                    }
                }
            } finally {
                currCopy?.fill('\u0000')
                newCopy.fill('\u0000')
            }
        }
    }

    private fun lockNoteVaultSession(silent: Boolean = false) {
        scope.launch {
            if (_uiState.value.isEditorDirty &&
                _uiState.value.selectedNote?.let { isNoteProtected(it, _uiState.value.categories) } == true
            ) {
                flushCurrentEditorToDb(immediateSync = true)
            }
            noteVaultManager?.lockSession()
            pendingProtectedAction = null
            scrubTempPreviewFiles()
            _uiState.update { current ->
                val isSelectedProtected = current.selectedNote?.let { isNoteProtected(it, current.categories) } == true
                val filtered = applyFilter(
                    activeNotes = current.allActiveNotes,
                    rootActiveNotes = current.rootActiveNotes,
                    trashedNotes = current.trashedNotes,
                    categories = current.categories,
                    section = current.sidebarSection,
                    categorySyncUuid = current.selectedCategorySyncUuid,
                    query = current.searchQuery,
                    isNoteVaultUnlocked = false,
                    hideLockedNotes = current.hideLockedNotes
                )
                current.copy(
                    isNoteVaultUnlocked = false,
                    filteredNotes = filtered,
                    selectedNoteSyncUuid = if (isSelectedProtected) null else current.selectedNoteSyncUuid,
                    editorTitle = if (isSelectedProtected) "" else current.editorTitle,
                    editorBlocks = if (isSelectedProtected) emptyList() else current.editorBlocks,
                    isEditorDirty = if (isSelectedProtected) false else current.isEditorDirty,
                    splitPreviewNote = if (current.splitPreviewNote?.let { isNoteProtected(it, current.categories) } == true) null else current.splitPreviewNote,
                    splitPreviewBlocks = if (current.splitPreviewNote?.let { isNoteProtected(it, current.categories) } == true) emptyList() else current.splitPreviewBlocks
                )
            }
            if (!silent) {
                emitLocalizedMessage("Korumalı notlar kilitlendi.", "Protected notes locked.")
            }
        }
    }

    private fun resolvePreviewTempDir(): File =
        File(cacheDir ?: File(System.getProperty("java.io.tmpdir"), "purenoteit_cache"), "preview_tmp")

    private fun scrubTempPreviewFiles() {
        try {
            val dir = resolvePreviewTempDir()
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val len = file.length().coerceAtMost(2 * 1024 * 1024L).toInt()
                    if (len > 0) {
                        file.writeBytes(ByteArray(len))
                    }
                    file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun filterRootActiveNotes(activeNotes: List<Note>): List<Note> {
        val refs = BlockParser.collectSubPageHierarchyRefs(activeNotes.map { it.description })
        return activeNotes.filterNot { note ->
            BlockParser.isSubPageNote(
                noteId = note.id,
                noteSyncUuid = note.syncUuid,
                noteTitle = note.title,
                richBlocksJson = note.richBlocksJson,
                refs = refs
            )
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
        debouncedSyncJob?.cancel()
        autoSyncMonitorJob?.cancel()
        audioRecorder.close()
        if (_uiState.value.isEditorDirty) {
            runCatching {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(1_200L) {
                        flushCurrentEditorToDb(immediateSync = false)
                    }
                }
            }
        }
        observerJob?.cancel()
        autoLockJob?.cancel()
        noteVaultManager?.lockSessionSync()
        scrubTempPreviewFiles()
        scope.cancel()
    }
}

private data class RepositorySnapshot(
    val categories: List<Category>,
    val activeNotes: List<Note>,
    val rootActiveNotes: List<Note>,
    val trashedNotes: List<Note>,
    val noteLinks: List<NoteLink>,
    val streamItems: List<StreamItem>,
    val subPageTree: List<SubPageTreeNode>
)

private const val LAN_PAIR_CODE_MIN_LENGTH = 8

private val PROCESS_CODE_LANGUAGES = setOf(
    "python", "py", "python3",
    "javascript", "js",
    "java",
    "kotlin", "kt",
    "cpp", "c++", "c",
    "bash", "sh"
)
