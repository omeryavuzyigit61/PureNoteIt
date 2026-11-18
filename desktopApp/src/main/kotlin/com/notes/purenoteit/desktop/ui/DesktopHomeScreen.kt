package com.notes.purenoteit.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.BuildDesktopOAuthConfig
import com.notes.purenoteit.desktop.ui.components.ConfirmActionDialog
import com.notes.purenoteit.desktop.ui.components.CreateCategoryDialog
import com.notes.purenoteit.desktop.ui.components.CreateSubPageDialog
import com.notes.purenoteit.desktop.ui.components.DesktopAnimatedDialog
import com.notes.purenoteit.desktop.ui.components.DesktopBlockRow
import com.notes.purenoteit.desktop.ui.components.DesktopCalendarView
import com.notes.purenoteit.desktop.ui.components.DesktopCategoryManagementDialog
import com.notes.purenoteit.desktop.ui.components.DesktopDrawingCanvasDialog
import com.notes.purenoteit.desktop.ui.components.DesktopExportStudioDialog
import com.notes.purenoteit.desktop.ui.components.DesktopGraphView
import com.notes.purenoteit.desktop.ui.components.DesktopNoteIconBadge
import com.notes.purenoteit.desktop.ui.components.DesktopNotePinSetupDialog
import com.notes.purenoteit.desktop.ui.components.DesktopNotePinUnlockDialog
import com.notes.purenoteit.desktop.ui.components.DesktopReminderDialog
import com.notes.purenoteit.desktop.ui.components.DesktopSettingsDialog
import com.notes.purenoteit.desktop.ui.components.DesktopStreamView
import com.notes.purenoteit.desktop.ui.DesktopStrings
import com.notes.purenoteit.desktop.ui.components.DesktopSyncDialog
import com.notes.purenoteit.desktop.ui.components.DesktopTaskProgressBadge
import com.notes.purenoteit.desktop.ui.components.DesktopTemplateGalleryDialog
import com.notes.purenoteit.desktop.ui.components.DesktopVaultLockScreen
import com.notes.purenoteit.desktop.ui.components.DesktopVaultSettingsDialog
import com.notes.purenoteit.desktop.ui.components.DesktopWhiteboardView
import com.notes.purenoteit.desktop.ui.components.DesktopZenReadingView
import com.notes.purenoteit.desktop.ui.components.NoteEmojiPickerDialog
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEffect
import com.notes.purenoteit.desktop.ui.contract.DesktopUiEvent
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState
import com.notes.purenoteit.desktop.ui.contract.DesktopViewMode
import com.notes.purenoteit.desktop.ui.contract.SidebarSection
import com.notes.purenoteit.desktop.ui.contract.SplitViewMode
import com.notes.purenoteit.desktop.ui.contract.SubPageTreeNode
import com.notes.purenoteit.desktop.ui.viewmodel.DesktopHomeStateHolder
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.storage.MediaPathResolver
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopHomeScreenRoot(
    stateHolder: DesktopHomeStateHolder,
    mediaPathResolver: MediaPathResolver
) {
    val state by stateHolder.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()

    LaunchedEffect(stateHolder) {
        withContext(Dispatchers.Main.immediate) {
            stateHolder.effects.collect { effect ->
                when (effect) {
                    is DesktopUiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                    DesktopUiEffect.FocusSearchField -> stateHolder.onEvent(DesktopUiEvent.SetQuickSwitcherVisible(true))
                }
            }
        }
    }

    PureNoteItDesktopTheme(darkTheme = state.isDarkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(state.isVaultLocked) {
                        if (!state.isVaultLocked) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                    stateHolder.onEvent(DesktopUiEvent.RecordUserActivity)
                                }
                            }
                        }
                    }
            ) {
                if (state.isVaultLocked) {
                    DesktopVaultLockScreen(
                        errorMessage = state.vaultLockError,
                        cooldownSeconds = state.vaultCooldownSeconds,
                        onUnlock = { pass -> stateHolder.onEvent(DesktopUiEvent.UnlockVault(pass)) }
                    )
                } else {
                    DesktopHomeScreenContent(
                        state = state,
                        mediaPathResolver = mediaPathResolver,
                        readMediaBytes = stateHolder::readMediaBytes,
                        onLocalFeedback = { msg -> rootScope.launch { snackbarHostState.showSnackbar(msg) } },
                        onEvent = stateHolder::onEvent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopHomeScreenContent(
    state: DesktopUiState,
    mediaPathResolver: MediaPathResolver,
    onEvent: (DesktopUiEvent) -> Unit,
    readMediaBytes: suspend (String) -> ByteArray? = { null },
    onLocalFeedback: (String) -> Unit = {}
) {
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    var compactDrawerOpen by remember { mutableStateOf(false) }
    var categoryPendingDelete by remember { mutableStateOf<Category?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize()
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactSidebar = maxWidth < 880.dp
            val isWideSplitCapable = maxWidth >= 1140.dp
            val showInlineRightSplit = state.splitViewMode != SplitViewMode.NONE && isWideSplitCapable
            val showSideNoteList = state.selectedNoteSyncUuid != null && maxWidth >= 1180.dp

            LaunchedEffect(isCompactSidebar) {
                if (!isCompactSidebar && compactDrawerOpen) {
                    compactDrawerOpen = false
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (isCompactSidebar) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { compactDrawerOpen = true },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Menüyü Aç")
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "PureNoteIt",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = DesktopColors.VibrantOrange,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.clickable { onEvent(DesktopUiEvent.NavigateHome) }
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { onEvent(DesktopUiEvent.SetSyncDialogVisible(true)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isDriveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                                        contentDescription = "Bulut Eşitleme",
                                        tint = if (state.isDriveConnected) DesktopColors.EmeraldGreen else DesktopColors.VibrantOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onEvent(DesktopUiEvent.ToggleTheme) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Tema Değiştir",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Button(
                                    onClick = { onEvent(DesktopUiEvent.CreateNewNote) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.newNoteShortcut.substringBefore(" ("), fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!isCompactSidebar) {
                        DesktopSidebar(
                            state = state,
                            onEvent = onEvent,
                            onRequestDeleteCategory = { categoryPendingDelete = it },
                            modifier = Modifier
                                .width(236.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    }

                    AnimatedContent(
                        targetState = state.selectedNoteSyncUuid != null,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.985f, animationSpec = tween(180)))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) { isEditorActive ->
                        if (!isEditorActive) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                DesktopHomeDashboard(
                                    state = state,
                                    onEvent = onEvent,
                                    onRequestEmptyTrash = { confirmEmptyTrash = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.background)
                                )
                                AnimatedVisibility(visible = showInlineRightSplit) {
                                    Row(modifier = Modifier.fillMaxHeight()) {
                                        VerticalDivider(color = MaterialTheme.colorScheme.outline)
                                        DesktopRightSplitPane(
                                            state = state,
                                            onEvent = onEvent,
                                            modifier = Modifier.width(340.dp).fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (showSideNoteList) {
                                    DesktopNotesListPane(
                                        state = state,
                                        onEvent = onEvent,
                                        modifier = Modifier
                                            .width(256.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.background)
                                    )
                                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                                }

                                Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    DesktopEditorPane(
                                        state = state,
                                        mediaPathResolver = mediaPathResolver,
                                        readMediaBytes = readMediaBytes,
                                        isWideSplitCapable = isWideSplitCapable,
                                        onEvent = onEvent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(min = 340.dp)
                                            .fillMaxHeight()
                                    )

                                    AnimatedVisibility(visible = showInlineRightSplit) {
                                        Row(modifier = Modifier.fillMaxHeight()) {
                                            VerticalDivider(color = MaterialTheme.colorScheme.outline)
                                            DesktopRightSplitPane(
                                                state = state,
                                                onEvent = onEvent,
                                                modifier = Modifier.width(350.dp).fillMaxHeight()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Compact Overlay Navigation Drawer for Narrow Windows
            if (isCompactSidebar && compactDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { compactDrawerOpen = false }
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        DesktopSidebar(
                            state = state,
                            onEvent = { ev ->
                                onEvent(ev)
                                if (ev is DesktopUiEvent.SelectSidebar ||
                                    ev is DesktopUiEvent.SelectNote ||
                                    ev is DesktopUiEvent.CreateNewNote ||
                                    ev is DesktopUiEvent.NavigateHome
                                ) {
                                    compactDrawerOpen = false
                                }
                            },
                            onRequestDeleteCategory = {
                                compactDrawerOpen = false
                                categoryPendingDelete = it
                            },
                            onCloseDrawer = { compactDrawerOpen = false },
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = false) {}
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Responsive Modal Graph / Preview Overlay when Window is Narrow
            if (state.splitViewMode != SplitViewMode.NONE && !isWideSplitCapable) {
                DesktopAnimatedDialog(
                    onDismiss = { onEvent(DesktopUiEvent.SetSplitViewMode(SplitViewMode.NONE)) },
                    width = 680.dp,
                    title = if (state.splitViewMode == SplitViewMode.GRAPH_2D) "Bilgi Haritası" else "Alt Sayfa Önizleme"
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        DesktopRightSplitPane(
                            state = state,
                            onEvent = { ev ->
                                onEvent(ev)
                                if (ev is DesktopUiEvent.SelectNote) {
                                    onEvent(DesktopUiEvent.SetSplitViewMode(SplitViewMode.NONE))
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Overlays & Soft Animated Popups (rendered at top Z-order inside root Box)
    if (state.showCreateCategoryDialog) {
        CreateCategoryDialog(
            existingNames = state.categories.map { it.name },
            onConfirm = { onEvent(DesktopUiEvent.CreateCategory(it)) },
            onDismiss = { onEvent(DesktopUiEvent.SetCreateCategoryDialogVisible(false)) }
        )
    }

    if (state.showCreateSubPageDialog) {
        CreateSubPageDialog(
            onConfirm = { onEvent(DesktopUiEvent.CreateSubPage(it)) },
            onDismiss = { onEvent(DesktopUiEvent.SetCreateSubPageDialogVisible(false)) }
        )
    }

    if (state.showEmojiPickerDialog) {
        NoteEmojiPickerDialog(
            currentEmoji = state.editorIcon,
            onSelectEmoji = { onEvent(DesktopUiEvent.UpdateIcon(it)) },
            onDismiss = { onEvent(DesktopUiEvent.SetEmojiPickerDialogVisible(false)) }
        )
    }

    categoryPendingDelete?.let { cat ->
        ConfirmActionDialog(
            title = "Kategoriyi Sil",
            message = "'${cat.name}' kategorisini silmek istediğinize emin misiniz? Bu kategorideki notlar 'Tüm Notlar' görünümünde güvenle saklanmaya devam eder.",
            confirmLabel = "Kategoriyi Sil",
            isDestructive = true,
            onConfirm = {
                categoryPendingDelete = null
                onEvent(DesktopUiEvent.DeleteCategory(cat.syncUuid))
            },
            onDismiss = { categoryPendingDelete = null }
        )
    }

    if (confirmEmptyTrash) {
        ConfirmActionDialog(
            title = "Çöp Kutusunu Boşalt",
            message = "Çöp kutusundaki ${state.trashedNotes.size} not kalıcı olarak silinecek ve bu işlem geri alınamayacaktır.",
            confirmLabel = "Tümünü Sil",
            isDestructive = true,
            onConfirm = {
                confirmEmptyTrash = false
                onEvent(DesktopUiEvent.EmptyTrash)
            },
            onDismiss = { confirmEmptyTrash = false }
        )
    }

    if (state.showTemplateGalleryDialog) {
        DesktopTemplateGalleryDialog(
            onSelectTemplate = { tpl ->
                onEvent(
                    DesktopUiEvent.CreateNoteFromTemplate(
                        title = tpl.title,
                        icon = tpl.icon,
                        blocks = tpl.buildBlocks()
                    )
                )
            },
            onDismiss = { onEvent(DesktopUiEvent.SetTemplateGalleryVisible(false)) }
        )
    }

    if (state.showCategoryManagementDialog) {
        DesktopCategoryManagementDialog(
            categories = state.categories,
            onCreateCategory = { name -> onEvent(DesktopUiEvent.CreateCategory(name)) },
            onRenameCategory = { uuid, name -> onEvent(DesktopUiEvent.RenameCategory(uuid, name)) },
            onToggleCategoryLock = { uuid -> onEvent(DesktopUiEvent.ToggleCategoryLock(uuid)) },
            onDeleteCategory = { cat -> categoryPendingDelete = cat },
            onDismiss = { onEvent(DesktopUiEvent.SetCategoryManagementVisible(false)) }
        )
    }

    if (state.showExportDialog && state.selectedNote != null) {
        val catName = state.categories.firstOrNull { it.syncUuid == state.editorCategorySyncUuid }?.name.orEmpty()
        DesktopExportStudioDialog(
            title = state.editorTitle,
            categoryName = catName,
            blocks = state.editorBlocks,
            readMediaBytes = readMediaBytes,
            onExportFeedback = { msg, _ -> onLocalFeedback(msg) },
            onDismiss = { onEvent(DesktopUiEvent.SetExportDialogVisible(false)) }
        )
    }

    if (state.showReminderDialog && state.selectedNote != null) {
        val selectedNote = state.selectedNote!!
        DesktopReminderDialog(
            currentReminderMillis = selectedNote.reminderTime,
            onSaveReminder = { ts -> onEvent(DesktopUiEvent.SetNoteReminder(selectedNote.syncUuid, ts)) },
            onDismiss = { onEvent(DesktopUiEvent.SetReminderDialogVisible(false)) }
        )
    }
    if (state.showQuickSwitcher) {
        QuickSwitcherDialog(
            state = state,
            onSelectNote = { onEvent(DesktopUiEvent.SelectNote(it)) },
            onDismiss = { onEvent(DesktopUiEvent.SetQuickSwitcherVisible(false)) }
        )
    }

    if (state.showSlashPalette) {
        SlashBlockPaletteDialog(
            onSelectBlock = { blockType ->
                onEvent(DesktopUiEvent.AddBlock(blockType))
            },
            onDismiss = { onEvent(DesktopUiEvent.SetSlashPaletteVisible(false)) }
        )
    }

    state.activeDrawingBlockId?.let { drawingBlockId ->
        val drawingBlock = state.editorBlocks.firstOrNull { it.id == drawingBlockId }
        val existingPath = drawingBlock?.mediaPath
        val initialDrawingBytes by produceState<ByteArray?>(initialValue = null, drawingBlockId, existingPath) {
            value = if (existingPath.isNullOrBlank()) null else withContext(Dispatchers.IO) { readMediaBytes(existingPath) }
        }
        DesktopDrawingCanvasDialog(
            initialImageBytes = initialDrawingBytes,
            canvasKey = "$drawingBlockId:${existingPath.orEmpty()}",
            strings = strings,
            onSavePng = { pngBytes -> onEvent(DesktopUiEvent.SaveDrawingPng(drawingBlockId, pngBytes)) },
            onDismiss = { onEvent(DesktopUiEvent.CloseDrawingEditor) }
        )
    }

    if (state.showSettingsDialog) {
        DesktopSettingsDialog(
            state = state,
            strings = strings,
            onSelectLanguage = { onEvent(DesktopUiEvent.SetLanguage(it)) },
            onSelectRetentionDays = { onEvent(DesktopUiEvent.SetTrashRetentionDays(it)) },
            onToggleHideLockedNotes = { onEvent(DesktopUiEvent.SetHideLockedNotes(it)) },
            onOpenPinSetup = { onEvent(DesktopUiEvent.SetNotePinSetupDialogVisible(true)) },
            onOpenMasterLockSettings = { onEvent(DesktopUiEvent.SetVaultSecurityDialogVisible(true)) },
            onOpenSyncSettings = { onEvent(DesktopUiEvent.SetSyncDialogVisible(true)) },
            onDismiss = { onEvent(DesktopUiEvent.SetSettingsDialogVisible(false)) }
        )
    }

    if (state.showNotePinUnlockDialog) {
        DesktopNotePinUnlockDialog(
            language = state.language,
            errorMessage = state.notePinError,
            cooldownSeconds = state.notePinCooldownSeconds,
            onUnlock = { pinChars -> onEvent(DesktopUiEvent.SubmitNotePinUnlock(pinChars)) },
            onDismiss = { onEvent(DesktopUiEvent.DismissNotePinUnlock) }
        )
    }

    if (state.showNotePinSetupDialog) {
        DesktopNotePinSetupDialog(
            language = state.language,
            hasExistingPin = state.isNotePinSet,
            errorMessage = state.notePinError,
            onSavePin = { curr, next -> onEvent(DesktopUiEvent.SaveNotePin(curr, next)) },
            onDismiss = { onEvent(DesktopUiEvent.SetNotePinSetupDialogVisible(false)) }
        )
    }

    if (state.showSyncDialog) {
        DesktopSyncDialog(
            state = state,
            onSelectTransport = { onEvent(DesktopUiEvent.SelectSyncTransport(it)) },
            onConnectDrive = { onEvent(DesktopUiEvent.ConnectGoogleDrive) },
            onDisconnectDrive = { onEvent(DesktopUiEvent.DisconnectGoogleDrive) },
            onSaveDesktopOAuthConfig = { cid, csec ->
                onEvent(DesktopUiEvent.SaveDesktopOAuthConfig(cid, csec))
            },
            onTriggerSync = { onEvent(DesktopUiEvent.TriggerSyncNow) },
            onForcePushAll = { onEvent(DesktopUiEvent.ForcePushAllToDrive) },
            onCleanOrphanMedia = { onEvent(DesktopUiEvent.CleanOrphanDriveMedia) },
            onStartLanServer = { pin -> onEvent(DesktopUiEvent.StartLanServer(pin)) },
            onStopLanServer = { onEvent(DesktopUiEvent.StopLanServer) },
            onConnectAndSyncLan = { url, pin, pass ->
                onEvent(DesktopUiEvent.ConnectAndSyncLan(url, pin, pass))
            },
            onDismiss = { onEvent(DesktopUiEvent.SetSyncDialogVisible(false)) }
        )
    }

    if (state.showVaultSecurityDialog) {
        DesktopVaultSettingsDialog(
            state = state,
            onEnableMasterLock = { pass, mins -> onEvent(DesktopUiEvent.EnableMasterPassword(pass, mins)) },
            onDisableMasterLock = { pass -> onEvent(DesktopUiEvent.DisableMasterPassword(pass)) },
            onUpdateAutoLockMinutes = { mins -> onEvent(DesktopUiEvent.UpdateAutoLockMinutes(mins)) },
            onLockNow = {
                onEvent(DesktopUiEvent.SetVaultSecurityDialogVisible(false))
                onEvent(DesktopUiEvent.LockVaultNow)
            },
            onDismiss = { onEvent(DesktopUiEvent.SetVaultSecurityDialogVisible(false)) }
        )
    }
    }
}

@Composable
private fun DesktopSidebar(
    state: DesktopUiState,
    onEvent: (DesktopUiEvent) -> Unit,
    onRequestDeleteCategory: (Category) -> Unit = {},
    onCloseDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    Column(modifier = modifier.padding(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.clickable { onEvent(DesktopUiEvent.NavigateHome) }) {
                Text(
                    text = "PureNoteIt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DesktopColors.VibrantOrange
                )
                Text(
                    text = "${strings.appSubtitle} · v${BuildDesktopOAuthConfig.APP_VERSION}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(
                    onClick = { onEvent(DesktopUiEvent.SetSettingsDialogVisible(true)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = strings.settings,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onEvent(DesktopUiEvent.SetVaultSecurityDialogVisible(true)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (state.isMasterLockEnabled) Icons.Rounded.Lock else Icons.Rounded.Security,
                        contentDescription = strings.appLock,
                        tint = if (state.isMasterLockEnabled) DesktopColors.EmeraldGreen else DesktopColors.VibrantOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onEvent(DesktopUiEvent.ToggleTheme) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (state.isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Tema",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onCloseDrawer != null) {
                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.closeBtn,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onEvent(DesktopUiEvent.CreateNewNote) },
            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(38.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.newNoteShortcut, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SidebarNavItem(
                    label = "${strings.allNotes} (${state.rootActiveNotes.size})",
                    icon = Icons.Rounded.Description,
                    selected = state.sidebarSection == SidebarSection.ALL,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.ALL)) }
                )
                SidebarNavItem(
                    label = strings.pinned,
                    icon = Icons.Rounded.PushPin,
                    selected = state.sidebarSection == SidebarSection.PINNED,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.PINNED)) }
                )
                SidebarNavItem(
                    label = strings.tasks,
                    icon = Icons.Rounded.CheckBox,
                    selected = state.sidebarSection == SidebarSection.TASKS,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.TASKS)) }
                )
                SidebarNavItem(
                    label = strings.agenda,
                    icon = Icons.Rounded.CalendarMonth,
                    selected = state.sidebarSection == SidebarSection.AGENDA,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.AGENDA)) }
                )
                SidebarNavItem(
                    label = "${strings.trash} (${state.trashedNotes.size})",
                    icon = Icons.Rounded.DeleteOutline,
                    selected = state.sidebarSection == SidebarSection.TRASH && state.selectedNoteSyncUuid == null,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.TRASH)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

                Text(
                    text = strings.workspaceToolsHeader,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                fun openWorkspaceMode(mode: DesktopViewMode) {
                    if (state.sidebarSection == SidebarSection.TRASH) {
                        onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.ALL))
                    }
                    if (state.selectedNoteSyncUuid != null) {
                        onEvent(DesktopUiEvent.NavigateHome)
                    }
                    onEvent(DesktopUiEvent.SetHomeViewMode(mode))
                }

                SidebarNavItem(
                    label = strings.taskBoard,
                    icon = Icons.Rounded.ViewWeek,
                    selected = state.selectedNoteSyncUuid == null && state.homeViewMode == DesktopViewMode.BOARD,
                    onClick = { openWorkspaceMode(DesktopViewMode.BOARD) }
                )
                SidebarNavItem(
                    label = strings.streamTimeline,
                    icon = Icons.Rounded.Bolt,
                    selected = state.selectedNoteSyncUuid == null && state.homeViewMode == DesktopViewMode.STREAM,
                    onClick = { openWorkspaceMode(DesktopViewMode.STREAM) }
                )
                SidebarNavItem(
                    label = strings.whiteboard,
                    icon = Icons.Rounded.Brush,
                    selected = state.selectedNoteSyncUuid == null && state.homeViewMode == DesktopViewMode.WHITEBOARD,
                    onClick = { openWorkspaceMode(DesktopViewMode.WHITEBOARD) }
                )
                SidebarNavItem(
                    label = strings.calendar,
                    icon = Icons.Rounded.CalendarMonth,
                    selected = state.selectedNoteSyncUuid == null && state.homeViewMode == DesktopViewMode.CALENDAR,
                    onClick = { openWorkspaceMode(DesktopViewMode.CALENDAR) }
                )
                SidebarNavItem(
                    label = strings.graph2d,
                    icon = Icons.Rounded.Hub,
                    selected = state.selectedNoteSyncUuid == null && state.homeViewMode == DesktopViewMode.GRAPH,
                    onClick = { openWorkspaceMode(DesktopViewMode.GRAPH) }
                )
                SidebarNavItem(
                    label = strings.templateGallery,
                    icon = Icons.Rounded.DashboardCustomize,
                    selected = state.showTemplateGalleryDialog,
                    onClick = { onEvent(DesktopUiEvent.SetTemplateGalleryVisible(true)) }
                )
                SidebarNavItem(
                    label = strings.dailyAgenda,
                    icon = Icons.Rounded.WbSunny,
                    selected = false,
                    onClick = { onEvent(DesktopUiEvent.CreateDailyAgenda) }
                )
                SidebarNavItem(
                    label = strings.categoryManagement,
                    icon = Icons.Rounded.Folder,
                    selected = state.showCategoryManagementDialog,
                    onClick = { onEvent(DesktopUiEvent.SetCategoryManagementVisible(true)) }
                )
                SidebarNavItem(
                    label = strings.settings,
                    icon = Icons.Rounded.Settings,
                    selected = state.showSettingsDialog,
                    onClick = { onEvent(DesktopUiEvent.SetSettingsDialogVisible(true)) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = strings.categoriesHeader,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DesktopColors.VibrantOrange.copy(alpha = 0.14f),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEvent(DesktopUiEvent.SetCreateCategoryDialogVisible(true)) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Yeni Kategori",
                                tint = DesktopColors.VibrantOrange,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            items(state.categories, key = { it.syncUuid }) { cat ->
                val isProtectedDefault = cat.name in setOf("Not", "Günlük", "Önemli")
                SidebarNavItem(
                    label = cat.name,
                    icon = Icons.Rounded.Folder,
                    selected = state.sidebarSection == SidebarSection.CATEGORY && state.selectedCategorySyncUuid == cat.syncUuid,
                    onClick = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.CATEGORY, cat.syncUuid)) },
                    trailingAction = if (!isProtectedDefault) {
                        {
                            IconButton(
                                onClick = { onRequestDeleteCategory(cat) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Kategoriyi Sil",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            if (state.subPageTree.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ALT SAYFA AĞACI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.subPageTree, key = { "tree_${it.note.syncUuid}" }) { node ->
                    SubPageTreeItem(
                        node = node,
                        depth = 0,
                        selectedNoteSyncUuid = state.selectedNoteSyncUuid,
                        lockedCategoryUuids = state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toSet(),
                        isNoteVaultUnlocked = state.isNoteVaultUnlocked,
                        onSelect = { onEvent(DesktopUiEvent.SelectNote(it)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Sync Status Hub Card (Google Drive + LAN Wi-Fi Badges)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { onEvent(DesktopUiEvent.SetSyncDialogVisible(true)) }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (state.isDriveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = if (state.isDriveConnected) DesktopColors.EmeraldGreen else DesktopColors.VibrantOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isDriveConnected) strings.googleDriveConnected else strings.cloudSyncTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (state.lanServerRunning) DesktopColors.EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.lanServerRunning) strings.wifiLanActive else strings.wifiSameNetwork,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.isNotePinSet) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable {
                        if (state.isNoteVaultUnlocked) {
                            onEvent(DesktopUiEvent.LockNoteVaultSession)
                        } else {
                            onEvent(DesktopUiEvent.RequestOpenNotePinUnlock)
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = if (state.isNoteVaultUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Pin,
                        contentDescription = null,
                        tint = if (state.isNoteVaultUnlocked) DesktopColors.EmeraldGreen else DesktopColors.VibrantOrange,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isNoteVaultUnlocked) strings.noteVaultUnlocked else strings.noteVaultLock,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // App Lock Quick Control Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable {
                    if (state.isMasterLockEnabled) {
                        onEvent(DesktopUiEvent.LockVaultNow)
                    } else {
                        onEvent(DesktopUiEvent.SetVaultSecurityDialogVisible(true))
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = if (state.isMasterLockEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (state.isMasterLockEnabled) DesktopColors.EmeraldGreen else DesktopColors.VibrantOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.isMasterLockEnabled) strings.lockVaultShortcut else strings.appLock,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        when {
            selected -> DesktopColors.VibrantOrange.copy(alpha = 0.16f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            else -> Color.Transparent
        }
    )
    val contentTint by animateColorAsState(
        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface
    )

        Surface(
        shape = RoundedCornerShape(9.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(9.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailingAction != null && (isHovered || selected)) {
                Spacer(Modifier.width(4.dp))
                trailingAction()
            }
        }
    }
}

@Composable
private fun SubPageTreeItem(
    node: SubPageTreeNode,
    depth: Int,
    selectedNoteSyncUuid: String?,
    lockedCategoryUuids: Set<String> = emptySet(),
    isNoteVaultUnlocked: Boolean = true,
    onSelect: (String) -> Unit
) {
    val selected = node.note.syncUuid == selectedNoteSyncUuid
    val isProtected = isProtectedNoteUi(node.note, lockedCategoryUuids)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (6 + depth * 12).dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DesktopColors.VibrantOrange.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onSelect(node.note.syncUuid) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(text = if (depth == 0) "•" else "↳", fontSize = 11.sp, color = DesktopColors.VibrantOrange)
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (isProtected && !isNoteVaultUnlocked) "🔒 ******" else (node.note.title?.takeIf { it.isNotBlank() } ?: "Başlıksız"),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    node.children.forEach { child ->
        SubPageTreeItem(child, depth + 1, selectedNoteSyncUuid, lockedCategoryUuids, isNoteVaultUnlocked, onSelect)
    }
}

private fun isProtectedNoteUi(note: Note, lockedCategoryUuids: Set<String>): Boolean =
    note.isPassword == true ||
        note.isEncrypted ||
        (note.categorySyncUuid != null && note.categorySyncUuid in lockedCategoryUuids)

private fun isTrashedNoteUi(note: Note): Boolean =
    note.deletedAt != null || note.status.equals("TRASH", ignoreCase = true)

private fun maskNotesForLockedViews(
    notes: List<Note>,
    lockedCategoryUuids: Set<String>,
    isNoteVaultUnlocked: Boolean,
    hideLockedNotes: Boolean
): List<Note> {
    if (isNoteVaultUnlocked && !hideLockedNotes) return notes
    return notes.mapNotNull { note ->
        val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
        when {
            !isProtected -> note
            !isNoteVaultUnlocked && hideLockedNotes -> null
            !isNoteVaultUnlocked -> note.copy(
                title = "******",
                description = "",
                icon = "🔒",
                totalTasks = 0,
                completedTasks = 0
            )
            else -> note
        }
    }
}

private data class DesktopStarterTemplate(
    val title: String,
    val icon: String,
    val subtitle: String,
    val tint: Color,
    val previewBadgeIcon: ImageVector,
    val buildBlocks: () -> List<NoteBlock>
)

private val DESKTOP_STARTER_TEMPLATES = listOf(
    DesktopStarterTemplate(
        title = "Hızlı Not",
        icon = "📝",
        subtitle = "Temiz başlık ve serbest çalışma alanı",
        tint = DesktopColors.VibrantOrange,
        previewBadgeIcon = Icons.Rounded.Description,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Ana Fikirler"),
                NoteBlock(type = BlockType.PARAGRAPH, content = "")
            )
        }
    ),
    DesktopStarterTemplate(
        title = "Toplantı Notu",
        icon = "📅",
        subtitle = "Gündem, kararlar ve aksiyon maddeleri",
        tint = DesktopColors.AccentBlue,
        previewBadgeIcon = Icons.AutoMirrored.Rounded.EventNote,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Gündem"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "Katılımcılar & konular"),
                NoteBlock(type = BlockType.HEADING_2, content = "Aksiyon Maddeleri"),
                NoteBlock(type = BlockType.TODO, content = "İlk adımı tamamla", isChecked = false),
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "💡", content = "Öne çıkan kararlar buraya")
            )
        }
    ),
    DesktopStarterTemplate(
        title = "Günlük Plan",
        icon = "✅",
        subtitle = "Bugünün odağı ve yapılacaklar listesi",
        tint = DesktopColors.EmeraldGreen,
        previewBadgeIcon = Icons.Rounded.Checklist,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "🎯", content = "Bugünün en önemli hedefi"),
                NoteBlock(type = BlockType.TODO, content = "Öncelikli görev 1", isChecked = false),
                NoteBlock(type = BlockType.TODO, content = "Öncelikli görev 2", isChecked = false)
            )
        }
    ),
    DesktopStarterTemplate(
        title = "Kod Notu",
        icon = "💻",
        subtitle = "Açıklama, çalıştırılabilir kod ve ipuçları",
        tint = DesktopColors.CyanNeon,
        previewBadgeIcon = Icons.Rounded.Code,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Teknik Özet"),
                NoteBlock(
                    type = BlockType.CODE,
                    codeLanguage = "python",
                    content = "print(\"Merhaba PureNoteIt!\")"
                )
            )
        }
    )
)

private val SHORT_TR_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("tr-TR"))

private val SHORT_EN_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun formatShortDateTr(millis: Long, strings: DesktopStrings = DesktopStrings.TR): String {
    if (millis <= 0L) return ""
    val fmt = if (strings.languageCode == "en") SHORT_EN_DATE_FORMATTER else SHORT_TR_DATE_FORMATTER
    return runCatching {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(fmt)
    }.getOrDefault("")
}

private fun statusDisplayLabel(status: String, strings: DesktopStrings = DesktopStrings.TR): String {
    val isEn = strings.languageCode == "en"
    return when (status.uppercase()) {
        "IN_PROGRESS" -> if (isEn) "In Progress" else "Devam Ediyor"
        "IN_REVIEW" -> if (isEn) "In Review" else "İnceleniyor"
        "DONE" -> if (isEn) "Done" else "Tamamlandı"
        else -> if (isEn) "To Do" else "Yapılacak"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopHomeDashboard(
    state: DesktopUiState,
    onEvent: (DesktopUiEvent) -> Unit,
    onRequestEmptyTrash: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    val collectionTitle = when (state.sidebarSection) {
        SidebarSection.ALL -> strings.allNotes
        SidebarSection.PINNED -> strings.pinnedNotesTitle
        SidebarSection.TASKS -> strings.tasks
        SidebarSection.AGENDA -> strings.agenda
        SidebarSection.TRASH -> strings.trash
        else -> state.categories
            .firstOrNull { it.syncUuid == state.selectedCategorySyncUuid }?.name
            ?: "Kategori"
    }
    val sectionTitle = when (state.homeViewMode) {
        DesktopViewMode.STREAM -> strings.streamTimeline
        DesktopViewMode.WHITEBOARD -> strings.whiteboard
        DesktopViewMode.CALENDAR -> strings.calendar
        DesktopViewMode.GRAPH -> strings.graph2d
        DesktopViewMode.BOARD -> strings.taskBoard
        else -> if (state.sidebarSection == SidebarSection.ALL) strings.homeTitle else collectionTitle
    }

    val visualNotesRaw = remember(state.allActiveNotes, state.rootActiveNotes, state.showSubPagesInVisualModes) {
        if (state.showSubPagesInVisualModes) state.allActiveNotes else state.rootActiveNotes
    }
    val visualNotes = remember(visualNotesRaw, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes) {
        maskNotesForLockedViews(visualNotesRaw, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes)
    }
    val visualLinks = remember(state.noteLinks, visualNotes) {
        val visibleUuids = visualNotes.asSequence().map { it.syncUuid }.toHashSet()
        state.noteLinks.filter { it.sourceSyncUuid in visibleUuids && it.targetSyncUuid in visibleUuids }
    }

    val countBadge = when (state.homeViewMode) {
        DesktopViewMode.STREAM -> "${state.streamItems.size} fikir"
        DesktopViewMode.CALENDAR -> "${state.rootActiveNotes.size} not"
        DesktopViewMode.GRAPH, DesktopViewMode.WHITEBOARD -> "${visualNotes.size} not"
        else -> "${state.filteredNotes.size} not"
    }
    val showCollectionFilters = state.homeViewMode in setOf(
        DesktopViewMode.CARDS,
        DesktopViewMode.LIST,
        DesktopViewMode.BOARD
    )

    Column(modifier = modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false).widthIn(min = 180.dp)
            ) {
                Text(
                    text = sectionTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = DesktopColors.VibrantOrange.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = countBadge,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.VibrantOrange,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.sidebarSection != SidebarSection.TRASH) {
                    ViewModeSwitcherPill(
                        selectedMode = state.homeViewMode,
                        strings = strings,
                        onSelectMode = { onEvent(DesktopUiEvent.SetHomeViewMode(it)) }
                    )
                } else if (state.trashedNotes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onRequestEmptyTrash,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(strings.emptyTrashBtn, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.homeViewMode == DesktopViewMode.GRAPH ||
                    state.homeViewMode == DesktopViewMode.WHITEBOARD ||
                    state.splitViewMode == SplitViewMode.GRAPH_2D
                ) {
                    val showingSub = state.showSubPagesInVisualModes
                    OutlinedButton(
                        onClick = { onEvent(DesktopUiEvent.ToggleShowSubPagesInVisualModes) },
                        border = BorderStroke(
                            1.dp,
                            if (showingSub) DesktopColors.VibrantOrange.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showingSub) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = if (showingSub) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (showingSub) "Alt Sayfalar: Açık" else "Alt Sayfalar: Gizli",
                            fontSize = 12.sp,
                            color = if (showingSub) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (state.allActiveNotes.isNotEmpty() && state.homeViewMode != DesktopViewMode.GRAPH) {
                    val graphActive = state.splitViewMode == SplitViewMode.GRAPH_2D
                    OutlinedButton(
                        onClick = {
                            val next = if (graphActive) SplitViewMode.NONE else SplitViewMode.GRAPH_2D
                            onEvent(DesktopUiEvent.SetSplitViewMode(next))
                        },
                        border = BorderStroke(
                            1.dp,
                            if (graphActive) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = if (graphActive) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Yan Harita",
                            fontSize = 12.sp,
                            color = if (graphActive) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Button(
                    onClick = { onEvent(DesktopUiEvent.CreateNewNote) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DesktopColors.VibrantOrange,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (state.language == "en") "New Note" else "Yeni Not",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (showCollectionFilters) {
            Spacer(Modifier.height(14.dp))

            // Search & Category Filter Pills Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                strings.searchPlaceholder,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = { onEvent(DesktopUiEvent.UpdateSearchQuery(it)) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onEvent(DesktopUiEvent.UpdateSearchQuery("")) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Aramayı Temizle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            CategoryFilterStrip(
                categories = state.categories,
                strings = strings,
                activeSection = state.sidebarSection,
                selectedCategoryUuid = state.selectedCategorySyncUuid,
                onSelectAll = { onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.ALL)) },
                onSelectCategory = { uuid -> onEvent(DesktopUiEvent.SelectSidebar(SidebarSection.CATEGORY, uuid)) },
                onAddCategory = { onEvent(DesktopUiEvent.SetCreateCategoryDialogVisible(true)) }
            )

            val visibleRecentNotes = remember(state.rootActiveNotes, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes) {
                maskNotesForLockedViews(
                    state.rootActiveNotes.sortedByDescending { it.updatedAt }.take(12),
                    lockedCategoryUuids,
                    state.isNoteVaultUnlocked,
                    state.hideLockedNotes
                ).take(6)
            }
            val showRecentStrip = state.homeViewMode in setOf(DesktopViewMode.CARDS, DesktopViewMode.LIST) &&
                state.sidebarSection != SidebarSection.TRASH &&
                state.searchQuery.isBlank() &&
                visibleRecentNotes.isNotEmpty()
            if (showRecentStrip) {
                Spacer(Modifier.height(14.dp))
                RecentNotesStrip(
                    notes = visibleRecentNotes,
                    categories = state.categories,
                    strings = strings,
                    lockedCategoryUuids = lockedCategoryUuids,
                    onSelectNote = { onEvent(DesktopUiEvent.SelectNote(it)) }
                )
            }

            if (state.homeViewMode in setOf(DesktopViewMode.CARDS, DesktopViewMode.LIST)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = collectionTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${state.filteredNotes.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        AnimatedContent(
            targetState = Triple(state.filteredNotes.isEmpty(), state.homeViewMode, state.sidebarSection),
            transitionSpec = {
                fadeIn(tween(170)).togetherWith(fadeOut(tween(110)))
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { (isEmpty, viewMode, section) ->
            when (viewMode) {
                DesktopViewMode.CALENDAR -> DesktopCalendarView(
                    notes = maskNotesForLockedViews(
                        state.rootActiveNotes,
                        lockedCategoryUuids,
                        state.isNoteVaultUnlocked,
                        state.hideLockedNotes
                    ),
                    categories = state.categories,
                    onSelectNote = { onEvent(DesktopUiEvent.SelectNote(it)) },
                    onCreateNoteForDay = { noonMillis ->
                        onEvent(
                            DesktopUiEvent.CreateNoteFromTemplate(
                                title = "Planlanan Not",
                                icon = "📅",
                                blocks = listOf(NoteBlock(type = BlockType.TODO, content = "Yapılacak görev")),
                                reminderTime = noonMillis
                            )
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
                DesktopViewMode.GRAPH -> Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxSize()
                ) {
                    DesktopGraphView(
                        notes = visualNotes,
                        links = visualLinks,
                        categories = state.categories,
                        selectedNoteSyncUuid = state.selectedNoteSyncUuid,
                        onNodeClick = { onEvent(DesktopUiEvent.SelectNote(it)) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DesktopViewMode.WHITEBOARD -> DesktopWhiteboardView(
                    notes = visualNotes,
                    links = visualLinks,
                    categories = state.categories,
                    onSelectNote = { onEvent(DesktopUiEvent.SelectNote(it)) },
                    onCreateNote = { onEvent(DesktopUiEvent.CreateNewNote) },
                    modifier = Modifier.fillMaxSize()
                )
                DesktopViewMode.STREAM -> DesktopStreamView(
                    streamItems = state.streamItems,
                    onAddItem = { onEvent(DesktopUiEvent.AddStreamItem(it)) },
                    onDeleteItem = { onEvent(DesktopUiEvent.DeleteStreamItem(it)) },
                    onConvertToNote = { ids, title -> onEvent(DesktopUiEvent.ConvertStreamItemsToNote(ids, title)) },
                    onOpenNote = { onEvent(DesktopUiEvent.SelectNote(it)) },
                    onPlayAudio = { onEvent(DesktopUiEvent.OpenMediaExternally(it)) },
                    modifier = Modifier.fillMaxSize()
                )
                DesktopViewMode.CARDS, DesktopViewMode.LIST, DesktopViewMode.BOARD -> {
                    if (isEmpty) {
                        if (section == SidebarSection.TRASH || state.searchQuery.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.widthIn(max = 420.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(28.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(DesktopColors.VibrantOrange.copy(alpha = 0.14f))
                                        ) {
                                            Icon(
                                                Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = DesktopColors.VibrantOrange,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = if (state.searchQuery.isNotBlank()) {
                                                strings.searchNoResultTitle
                                            } else {
                                                strings.trashEmptyTitle
                                            },
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = if (state.searchQuery.isNotBlank()) {
                                                strings.searchNoResultDesc
                                            } else {
                                                strings.trashEmptyDesc
                                            },
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            StarterTemplatesEmptyState(
                                strings = strings,
                                onCreateBlank = { onEvent(DesktopUiEvent.CreateNewNote) },
                                onOpenAllTemplates = { onEvent(DesktopUiEvent.SetTemplateGalleryVisible(true)) },
                                onSelectTemplate = { tpl ->
                                    onEvent(
                                        DesktopUiEvent.CreateNoteFromTemplate(
                                            title = tpl.title,
                                            icon = tpl.icon,
                                            blocks = tpl.buildBlocks()
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        when (viewMode) {
                            DesktopViewMode.CARDS -> {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 256.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(bottom = 18.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(state.filteredNotes, key = { it.syncUuid }) { note ->
                                        val categoryName = state.categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                                        DesktopNoteCard(
                                            note = note,
                                            categoryName = categoryName,
                                            strings = strings,
                                            lockedCategoryUuids = lockedCategoryUuids,
                                            isNoteVaultUnlocked = state.isNoteVaultUnlocked,
                                            fixedHeight = 168.dp,
                                            onEvent = onEvent
                                        )
                                    }
                                }
                            }
                            DesktopViewMode.LIST -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(state.filteredNotes, key = { it.syncUuid }) { note ->
                                        val categoryName = state.categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                                        DesktopNoteListRow(
                                            note = note,
                                            categoryName = categoryName,
                                            strings = strings,
                                            lockedCategoryUuids = lockedCategoryUuids,
                                            isNoteVaultUnlocked = state.isNoteVaultUnlocked,
                                            onEvent = onEvent
                                        )
                                    }
                                }
                            }
                            DesktopViewMode.BOARD -> {
                                DesktopKanbanBoard(
                                    notes = state.filteredNotes,
                                    categories = state.categories,
                                    strings = strings,
                                    lockedCategoryUuids = lockedCategoryUuids,
                                    isNoteVaultUnlocked = state.isNoteVaultUnlocked,
                                    onEvent = onEvent,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSwitcherPill(
    selectedMode: DesktopViewMode,
    strings: DesktopStrings,
    onSelectMode: (DesktopViewMode) -> Unit
) {
    val options = listOf(
        Triple(DesktopViewMode.CARDS, strings.viewCards, Icons.Rounded.GridView),
        Triple(DesktopViewMode.LIST, strings.viewList, Icons.AutoMirrored.Rounded.FormatListBulleted),
        Triple(DesktopViewMode.BOARD, strings.viewBoard, Icons.Rounded.ViewWeek),
        Triple(DesktopViewMode.CALENDAR, strings.viewCalendar, Icons.Rounded.CalendarMonth),
        Triple(DesktopViewMode.GRAPH, strings.viewGraph, Icons.Rounded.Hub),
        Triple(DesktopViewMode.WHITEBOARD, strings.viewCanvas, Icons.Rounded.Brush),
        Triple(DesktopViewMode.STREAM, strings.viewStream, Icons.Rounded.Bolt)
    )
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEach { (mode, label, icon) ->
                val active = mode == selectedMode
                val bgColor by animateColorAsState(
                    if (active) DesktopColors.VibrantOrange.copy(alpha = 0.16f) else Color.Transparent
                )
                val fgColor by animateColorAsState(
                    if (active) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable { onSelectMode(mode) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Icon(icon, contentDescription = label, tint = fgColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = fgColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterStrip(
    categories: List<Category>,
    strings: DesktopStrings,
    activeSection: SidebarSection,
    selectedCategoryUuid: String?,
    onSelectAll: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onAddCategory: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        item(key = "cat_all") {
            FilterPillChip(
                label = strings.allCategoriesFilter,
                selected = activeSection == SidebarSection.ALL,
                onClick = onSelectAll
            )
        }
        items(categories, key = { "pill_${it.syncUuid}" }) { cat ->
            FilterPillChip(
                label = cat.name,
                selected = activeSection == SidebarSection.CATEGORY && selectedCategoryUuid == cat.syncUuid,
                onClick = { onSelectCategory(cat.syncUuid) }
            )
        }
        item(key = "cat_add") {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DesktopColors.VibrantOrange.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, DesktopColors.VibrantOrange.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAddCategory)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = DesktopColors.VibrantOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = strings.newCategoryChip,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DesktopColors.VibrantOrange,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surface
    )
    val textColor by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    )
    val borderColor by animateColorAsState(
        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RecentNotesStrip(
    notes: List<Note>,
    categories: List<Category>,
    strings: DesktopStrings,
    lockedCategoryUuids: Set<String>,
    onSelectNote: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = DesktopColors.VibrantOrange,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = strings.recentNotesTitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(notes, key = { "recent_${it.syncUuid}" }) { note ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val hovered by interactionSource.collectIsHoveredAsState()
                    val border by animateColorAsState(
                        if (hovered) DesktopColors.VibrantOrange.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                    val catName = categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                    val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
                    val isMaskedLocked = isProtected && (
                        (note.description.orEmpty().isEmpty() && note.title == strings.protectedNoteTitleMasked) ||
                            note.description.orEmpty().startsWith("ENC_")
                        )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier
                            .width(204.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .hoverable(interactionSource)
                            .clickable { onSelectNote(note.syncUuid) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            DesktopNoteIconBadge(
                                iconKey = note.icon,
                                isLocked = isMaskedLocked,
                                size = 30.dp,
                                iconSize = 16.dp,
                                cornerRadius = 8.dp
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title?.takeIf { it.isNotBlank() } ?: strings.untitledNote,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = if (isMaskedLocked) {
                                    strings.protectedNoteSubtitle
                                } else {
                                    catName ?: formatShortDateTr(note.updatedAt, strings)
                                }
                                if (sub.isNotBlank()) {
                                    Text(
                                        text = sub,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarterTemplatesEmptyState(
    strings: DesktopStrings,
    onCreateBlank: () -> Unit,
    onOpenAllTemplates: () -> Unit,
    onSelectTemplate: (DesktopStarterTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = strings.languageCode == "en"
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(26.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DesktopColors.VibrantOrange.copy(alpha = 0.14f))
                ) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = DesktopColors.VibrantOrange,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isEn) "Your workspace is ready" else "Çalışma alanınız hazır",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isEn) {
                        "Create a blank page right away or start quickly with one of the templates below."
                    } else {
                        "Hemen boş bir sayfa oluşturun ya da aşağıdaki şablonlardan biriyle hızlı başlayın."
                    },
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onCreateBlank,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DesktopColors.VibrantOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isEn) "Create Blank Note" else "Boş Not Oluştur",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAllTemplates,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Rounded.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isEn) "All 14 Templates" else "Tüm 14 Şablon",
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (isEn) "Quick Start Templates" else "Hızlı Başlangıç Şablonları",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DESKTOP_STARTER_TEMPLATES.forEach { tpl ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val hovered by interactionSource.collectIsHoveredAsState()
                        val border by animateColorAsState(
                            if (hovered) tpl.tint.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, border),
                            modifier = Modifier
                                .widthIn(min = 155.dp)
                                .weight(1f, fill = true)
                                .clip(RoundedCornerShape(14.dp))
                                .hoverable(interactionSource)
                                .clickable { onSelectTemplate(tpl) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(tpl.tint.copy(alpha = 0.14f))
                                    ) {
                                        Text(tpl.icon, fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = tpl.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = tpl.subtitle,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopNoteCard(
    note: Note,
    categoryName: String?,
    strings: DesktopStrings,
    lockedCategoryUuids: Set<String>,
    isNoteVaultUnlocked: Boolean,
    onEvent: (DesktopUiEvent) -> Unit,
    showStatusPill: Boolean = true,
    fixedHeight: Dp? = null,
    onMovePrevStatus: (() -> Unit)? = null,
    onMoveNextStatus: (() -> Unit)? = null
) {
    val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
    val isMaskedLocked = isProtected && !isNoteVaultUnlocked
    val isTrashed = isTrashedNoteUi(note)
    val preview = remember(note.description, isMaskedLocked, isProtected, strings.protectedNoteSubtitle) {
        val raw = note.description.orEmpty()
        when {
            isMaskedLocked -> strings.protectedNoteSubtitle
            raw.startsWith("ENC_") -> strings.protectedNoteSubtitle
            else -> BlockParser.toPlainTextSummary(BlockParser.parse(raw), maxLines = 3, includeDecorations = false)
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        when {
            hovered -> DesktopColors.VibrantOrange.copy(alpha = 0.7f)
            note.isPinned -> DesktopColors.VibrantOrange.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline
        }
    )

    val cardModifier = Modifier
        .fillMaxWidth()
        .let { if (fixedHeight != null) it.height(fixedHeight) else it }
        .clip(RoundedCornerShape(16.dp))
        .hoverable(interactionSource)
        .clickable { onEvent(DesktopUiEvent.SelectNote(note.syncUuid)) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = cardModifier
    ) {
        Column(
            modifier = if (fixedHeight != null) {
                Modifier.fillMaxSize().padding(16.dp)
            } else {
                Modifier.padding(16.dp)
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DesktopNoteIconBadge(
                    iconKey = note.icon,
                    isLocked = isMaskedLocked,
                    size = 34.dp,
                    iconSize = 18.dp,
                    cornerRadius = 10.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isMaskedLocked) {
                        strings.protectedNoteTitleMasked
                    } else {
                        note.title?.takeIf { it.isNotBlank() } ?: strings.untitledNote
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isTrashed) {
                    IconButton(
                        onClick = { onEvent(DesktopUiEvent.RestoreNote(note.syncUuid)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.RestoreFromTrash,
                            contentDescription = strings.restoreBtn,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onEvent(DesktopUiEvent.PermanentDeleteNote(note.syncUuid)) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteForever,
                            contentDescription = strings.permanentDeleteBtn,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    if (isProtected) {
                        Icon(
                            imageVector = if (isNoteVaultUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = strings.lockNoteBtn,
                            tint = DesktopColors.VibrantOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (note.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = strings.pinned,
                            tint = DesktopColors.VibrantOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    NoteActionMenu(
                        note = note,
                        isProtected = isProtected,
                        strings = strings,
                        onEvent = onEvent
                    )
                }
            }

            if (preview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = preview,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (fixedHeight != null) {
                Spacer(Modifier.weight(1f, fill = true))
            } else {
                Spacer(Modifier.height(12.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!categoryName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = DesktopColors.VibrantOrange.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = DesktopColors.VibrantOrange,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (!isMaskedLocked && showStatusPill && note.status in setOf("IN_PROGRESS", "IN_REVIEW", "DONE")) {
                    val statusTint = when (note.status.uppercase()) {
                        "DONE" -> DesktopColors.EmeraldGreen
                        "IN_REVIEW" -> Color(0xFF0EA5E9)
                        else -> Color(0xFFF59E0B)
                    }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = statusTint.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = statusDisplayLabel(note.status, strings),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusTint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                if (!isMaskedLocked && note.totalTasks > 0) {
                    DesktopTaskProgressBadge(
                        completed = note.completedTasks,
                        total = note.totalTasks
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onMovePrevStatus != null || onMoveNextStatus != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onMovePrevStatus != null) {
                            IconButton(onClick = onMovePrevStatus, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (onMoveNextStatus != null) {
                            IconButton(onClick = onMoveNextStatus, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = DesktopColors.VibrantOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                    val dateLabel = formatShortDateTr(note.updatedAt, strings)
                    if (dateLabel.isNotBlank()) {
                        Text(
                            text = dateLabel,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopNoteListRow(
    note: Note,
    categoryName: String?,
    strings: DesktopStrings,
    lockedCategoryUuids: Set<String>,
    isNoteVaultUnlocked: Boolean,
    onEvent: (DesktopUiEvent) -> Unit
) {
    val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
    val isMaskedLocked = isProtected && !isNoteVaultUnlocked
    val isTrashed = isTrashedNoteUi(note)
    val preview = remember(note.description, isMaskedLocked, strings.protectedNoteSubtitle) {
        val raw = note.description.orEmpty()
        when {
            isMaskedLocked -> strings.protectedNoteSubtitle
            raw.startsWith("ENC_") -> strings.protectedNoteSubtitle
            else -> BlockParser.toPlainTextSummary(BlockParser.parse(raw), maxLines = 1, includeDecorations = false)
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (hovered) DesktopColors.VibrantOrange.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interactionSource)
            .clickable { onEvent(DesktopUiEvent.SelectNote(note.syncUuid)) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            DesktopNoteIconBadge(
                iconKey = note.icon,
                isLocked = isMaskedLocked,
                size = 34.dp,
                iconSize = 18.dp,
                cornerRadius = 10.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMaskedLocked) {
                        strings.protectedNoteTitleMasked
                    } else {
                        note.title?.takeIf { it.isNotBlank() } ?: strings.untitledNote
                    },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!categoryName.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = DesktopColors.VibrantOrange.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = categoryName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = DesktopColors.VibrantOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (!isMaskedLocked && note.totalTasks > 0) {
                Spacer(Modifier.width(8.dp))
                DesktopTaskProgressBadge(
                    completed = note.completedTasks,
                    total = note.totalTasks
                )
            }
            val dateLabel = formatShortDateTr(note.updatedAt, strings)
            if (dateLabel.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = dateLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            if (isTrashed) {
                IconButton(
                    onClick = { onEvent(DesktopUiEvent.RestoreNote(note.syncUuid)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.RestoreFromTrash,
                        contentDescription = strings.restoreBtn,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { onEvent(DesktopUiEvent.PermanentDeleteNote(note.syncUuid)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteForever,
                        contentDescription = strings.permanentDeleteBtn,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                NoteActionMenu(
                    note = note,
                    isProtected = isProtected,
                    strings = strings,
                    onEvent = onEvent
                )
            }
        }
    }
}

@Composable
private fun DesktopKanbanBoard(
    notes: List<Note>,
    categories: List<Category>,
    strings: DesktopStrings,
    lockedCategoryUuids: Set<String>,
    isNoteVaultUnlocked: Boolean,
    onEvent: (DesktopUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = strings.languageCode == "en"
    val columns = listOf(
        Triple("TODO", if (isEn) "To Do" else "Yapılacaklar", Color(0xFFA855F7)),
        Triple("IN_PROGRESS", if (isEn) "In Progress" else "Devam Ediyor", Color(0xFFF59E0B)),
        Triple("IN_REVIEW", if (isEn) "In Review" else "İnceleniyor", Color(0xFF0EA5E9)),
        Triple("DONE", if (isEn) "Completed" else "Tamamlandı", DesktopColors.EmeraldGreen)
    )
    val columnOrder = listOf("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE")

    BoxWithConstraints(modifier = modifier) {
        val wideColumns = maxWidth >= 920.dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .let { if (wideColumns) it else it.horizontalScroll(rememberScrollState()) }
        ) {
            columns.forEach { (statusKey, colTitle, accent) ->
                val colIdx = columnOrder.indexOf(statusKey)
                val prevKey = if (colIdx > 0) columnOrder[colIdx - 1] else null
                val nextKey = if (colIdx in 0 until columnOrder.lastIndex) columnOrder[colIdx + 1] else null
                val colNotes = remember(notes, statusKey) {
                    notes.filter {
                        when (statusKey) {
                            "IN_PROGRESS" -> it.status.equals("IN_PROGRESS", ignoreCase = true)
                            "IN_REVIEW" -> it.status.equals("IN_REVIEW", ignoreCase = true)
                            "DONE" -> it.status.equals("DONE", ignoreCase = true)
                            else -> !it.status.equals("IN_PROGRESS", ignoreCase = true) &&
                                !it.status.equals("IN_REVIEW", ignoreCase = true) &&
                                !it.status.equals("DONE", ignoreCase = true)
                        }
                    }
                }
                val colModifier = if (wideColumns) {
                    Modifier.weight(1f).fillMaxHeight()
                } else {
                    Modifier.width(268.dp).fillMaxHeight()
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                    modifier = colModifier
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = colTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = accent.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${colNotes.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            IconButton(
                                onClick = { onEvent(DesktopUiEvent.CreateNewNoteWithStatus(statusKey)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = if (isEn) "Add to $colTitle" else "$colTitle sütununa ekle",
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (colNotes.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isEn) "No notes in this column" else "Bu sütunda henüz not yok",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                items(colNotes, key = { "board_${statusKey}_${it.syncUuid}" }) { note ->
                                    val catName = categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                                    DesktopNoteCard(
                                        note = note,
                                        categoryName = catName,
                                        strings = strings,
                                        lockedCategoryUuids = lockedCategoryUuids,
                                        isNoteVaultUnlocked = isNoteVaultUnlocked,
                                        onEvent = onEvent,
                                        showStatusPill = false,
                                        onMovePrevStatus = prevKey?.let { target ->
                                            { onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, target)) }
                                        },
                                        onMoveNextStatus = nextKey?.let { target ->
                                            { onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, target)) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteActionMenu(
    note: Note,
    isProtected: Boolean,
    strings: DesktopStrings,
    onEvent: (DesktopUiEvent) -> Unit
) {
    val isEn = strings.languageCode == "en"
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = if (isEn) "Note Options" else "Not Seçenekleri",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (note.isPinned) {
                            if (isEn) "Unpin" else "Sabitlemeyi Kaldır"
                        } else {
                            if (isEn) "Pin to Top" else "Üste Sabitle"
                        }
                    )
                },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.TogglePinNote(note.syncUuid))
                }
            )
            DropdownMenuItem(
                text = { Text(if (isProtected) strings.unlockNoteBtn else strings.lockNoteBtn) },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.ToggleNoteLock(note.syncUuid))
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            DropdownMenuItem(
                text = { Text(if (isEn) "Status: To Do" else "Durum: Yapılacak") },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, "TODO"))
                }
            )
            DropdownMenuItem(
                text = { Text(if (isEn) "Status: In Progress" else "Durum: Devam Ediyor") },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, "IN_PROGRESS"))
                }
            )
            DropdownMenuItem(
                text = { Text(if (isEn) "Status: In Review" else "Durum: İnceleniyor") },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, "IN_REVIEW"))
                }
            )
            DropdownMenuItem(
                text = { Text(if (isEn) "Status: Done" else "Durum: Tamamlandı") },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.UpdateNoteStatus(note.syncUuid, "DONE"))
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            DropdownMenuItem(
                text = { Text(strings.moveToTrashBtn, color = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onEvent(DesktopUiEvent.SoftDeleteNote(note.syncUuid))
                }
            )
        }
    }
}

@Composable
private fun DesktopNotesListPane(
    state: DesktopUiState,
    onEvent: (DesktopUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    val isEn = strings.languageCode == "en"
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    Column(modifier = modifier.padding(12.dp)) {
        // Search Bar with Ctrl+K Hint
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            strings.searchPlaceholder,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = { onEvent(DesktopUiEvent.UpdateSearchQuery(it)) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (isEn) "${state.filteredNotes.size} notes listed" else "${state.filteredNotes.size} not listeleniyor",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (state.filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isEn) {
                        "No notes in this view yet.\nPress Ctrl+N to create one."
                    } else {
                        "Bu görünümde henüz not yok.\nCtrl+N ile hemen oluşturabilirsiniz."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredNotes, key = { it.syncUuid }) { note ->
                    val isSelected = note.syncUuid == state.selectedNoteSyncUuid
                    val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
                    val isMaskedLocked = isProtected && !state.isNoteVaultUnlocked
                    val isTrashed = isTrashedNoteUi(note)
                    val preview = remember(note.description, isMaskedLocked, strings.protectedNoteSubtitle) {
                        val raw = note.description.orEmpty()
                        when {
                            isMaskedLocked -> strings.protectedNoteSubtitle
                            raw.startsWith("ENC_") -> strings.protectedNoteSubtitle
                            else -> BlockParser.toPlainTextSummary(BlockParser.parse(raw), maxLines = 2, includeDecorations = false)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) DesktopColors.VibrantOrange.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isSelected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onEvent(DesktopUiEvent.SelectNote(note.syncUuid)) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DesktopNoteIconBadge(
                                    iconKey = note.icon,
                                    isLocked = isMaskedLocked,
                                    size = 24.dp,
                                    iconSize = 14.dp,
                                    cornerRadius = 7.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isMaskedLocked) {
                                        strings.protectedNoteTitleMasked
                                    } else {
                                        note.title?.takeIf { it.isNotBlank() } ?: strings.untitledNote
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isTrashed) {
                                    IconButton(
                                        onClick = { onEvent(DesktopUiEvent.RestoreNote(note.syncUuid)) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.RestoreFromTrash,
                                            contentDescription = strings.restoreBtn,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onEvent(DesktopUiEvent.PermanentDeleteNote(note.syncUuid)) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteForever,
                                            contentDescription = strings.permanentDeleteBtn,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else {
                                    if (isProtected) {
                                        Icon(
                                            imageVector = if (state.isNoteVaultUnlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                                            contentDescription = strings.lockNoteBtn,
                                            tint = DesktopColors.VibrantOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    IconButton(
                                        onClick = { onEvent(DesktopUiEvent.TogglePinNote(note.syncUuid)) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PushPin,
                                            contentDescription = strings.pinned,
                                            tint = if (note.isPinned) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            if (preview.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = preview,
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isMaskedLocked && note.totalTasks > 0) {
                                Spacer(Modifier.height(4.dp))
                                DesktopTaskProgressBadge(
                                    completed = note.completedTasks,
                                    total = note.totalTasks,
                                    suffix = if (isEn) "tasks" else "görev"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopEditorPane(
    state: DesktopUiState,
    mediaPathResolver: MediaPathResolver,
    readMediaBytes: suspend (String) -> ByteArray?,
    isWideSplitCapable: Boolean = true,
    onEvent: (DesktopUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedNote = state.selectedNote ?: return
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    val isEn = strings.languageCode == "en"
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    val isNoteProtected = isProtectedNoteUi(selectedNote, lockedCategoryUuids)

    if (state.showZenReadingMode) {
        DesktopZenReadingView(
            title = state.editorTitle,
            icon = state.editorIcon,
            blocks = state.editorBlocks,
            readMediaBytes = readMediaBytes,
            onPlayAudio = { path -> onEvent(DesktopUiEvent.OpenMediaExternally(path)) },
            onExit = { onEvent(DesktopUiEvent.SetZenReadingMode(false)) },
            modifier = modifier
        )
        return
    }

    var categoryDropdownOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        // Editor Top Toolbar (all labels stay single-line so narrow panes never wrap vertically)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.NavigateHome) },
                border = BorderStroke(1.dp, DesktopColors.VibrantOrange.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = DesktopColors.VibrantOrange,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = strings.backHome,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DesktopColors.VibrantOrange,
                    maxLines = 1,
                    softWrap = false
                )
            }

            val activeCat = state.categories.firstOrNull { it.syncUuid == state.editorCategorySyncUuid }
            Box {
                OutlinedButton(
                    onClick = { categoryDropdownOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).wrapContentWidth()
                ) {
                    Text(
                        activeCat?.name ?: if (isEn) "Category" else "Kategori",
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                DropdownMenu(
                    expanded = categoryDropdownOpen,
                    onDismissRequest = { categoryDropdownOpen = false }
                ) {
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                categoryDropdownOpen = false
                                onEvent(DesktopUiEvent.UpdateCategoryForNote(cat.syncUuid))
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.SetCreateSubPageDialogVisible(true)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Text(strings.subPageAddBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = {
                    val next = if (state.splitViewMode == SplitViewMode.GRAPH_2D) SplitViewMode.NONE else SplitViewMode.GRAPH_2D
                    onEvent(DesktopUiEvent.SetSplitViewMode(next))
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(Icons.Rounded.Hub, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.mapBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            if (isWideSplitCapable) {
                OutlinedButton(
                    onClick = {
                        val next = if (state.splitViewMode == SplitViewMode.SUB_PAGE) SplitViewMode.NONE else SplitViewMode.SUB_PAGE
                        onEvent(DesktopUiEvent.SetSplitViewMode(next))
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).wrapContentWidth()
                ) {
                    Icon(Icons.Rounded.VerticalSplit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.splitViewBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }

            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.SetReminderDialogVisible(true)) },
                border = BorderStroke(
                    1.dp,
                    if (selectedNote.reminderTime != null) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = if (selectedNote.reminderTime != null) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (selectedNote.reminderTime != null) strings.plannedBtn else strings.reminderBtn,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.SetZenReadingMode(true)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.readModeBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.SetExportDialogVisible(true)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.exportBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }

            OutlinedButton(
                onClick = { onEvent(DesktopUiEvent.ToggleNoteLock(selectedNote.syncUuid)) },
                border = BorderStroke(
                    1.dp,
                    if (isNoteProtected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(
                    imageVector = if (isNoteProtected) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (isNoteProtected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isNoteProtected) strings.unlockNoteBtn else strings.lockNoteBtn,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Button(
                onClick = { onEvent(DesktopUiEvent.SaveCurrentNote) },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp).wrapContentWidth()
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (state.isEditorDirty) "${strings.saveBtn}*" else strings.saveBtn,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            IconButton(
                onClick = { onEvent(DesktopUiEvent.SoftDeleteNote(selectedNote.syncUuid)) },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = strings.moveToTrashBtn,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Note Title Input with Clickable Modern Material Icon Badge
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, DesktopColors.VibrantOrange.copy(alpha = 0.35f)),
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEvent(DesktopUiEvent.SetEmojiPickerDialogVisible(true)) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    DesktopNoteIconBadge(
                        iconKey = state.editorIcon,
                        isLocked = isNoteProtected,
                        size = 44.dp,
                        iconSize = 22.dp,
                        cornerRadius = 12.dp
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.editorTitle.isEmpty()) {
                    Text(
                        strings.untitledNote,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = state.editorTitle,
                    onValueChange = { onEvent(DesktopUiEvent.UpdateTitle(it)) },
                    textStyle = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(DesktopColors.VibrantOrange),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

        // Rich Notion Blocks Canvas
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(state.editorBlocks, key = { _, b -> b.id }) { idx, block ->
                DesktopBlockRow(
                    block = block,
                    index = idx,
                    totalCount = state.editorBlocks.size,
                    mediaPathResolver = mediaPathResolver,
                    readMediaBytes = readMediaBytes,
                    runState = state.codeRunStates[block.id],
                    onUpdate = { updated -> onEvent(DesktopUiEvent.UpdateBlock(idx, updated)) },
                    onDelete = { onEvent(DesktopUiEvent.DeleteBlock(idx)) },
                    onMoveUp = { onEvent(DesktopUiEvent.MoveBlock(idx, idx - 1)) },
                    onMoveDown = { onEvent(DesktopUiEvent.MoveBlock(idx, idx + 1)) },
                    onRequestSlashMenu = { onEvent(DesktopUiEvent.SetSlashPaletteVisible(true)) },
                    onOpenSubPageSplit = { uuid -> onEvent(DesktopUiEvent.OpenSubPageInSplit(uuid)) },
                    onImportImage = { id, bytes, ext -> onEvent(DesktopUiEvent.ImportImageToBlock(id, bytes, ext)) },
                    onOpenDrawing = { id -> onEvent(DesktopUiEvent.OpenDrawingEditor(id)) },
                    onRunCode = { id, lang, code -> onEvent(DesktopUiEvent.RunCodeBlock(id, lang, code)) },
                    onApproveBash = { id -> onEvent(DesktopUiEvent.ConfirmBashExecution(id)) },
                    onDismissRunOutput = { id -> onEvent(DesktopUiEvent.DismissCodeOutput(id)) },
                    isRecordingAudio = state.isRecordingAudio,
                    playingAudioMediaPath = state.playingAudioMediaPath,
                    onStartAudioRecord = { blockId -> onEvent(DesktopUiEvent.StartAudioRecording(blockId)) },
                    onOpenAudio = { path -> onEvent(DesktopUiEvent.PlayOrToggleAudio(path)) },
                    onOpenAudioExternally = { path -> onEvent(DesktopUiEvent.OpenMediaExternally(path)) }
                )
            }

            item {
                Spacer(Modifier.height(14.dp))
                if (state.isRecordingAudio) {
                    val totalSecs = (state.recordingDurationMs / 1000L).toInt()
                    val timeLabel = "%02d:%02d".format(totalSecs / 60, totalSecs % 60)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DesktopColors.VibrantOrange.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DesktopColors.VibrantOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${strings.recordingInProgress} ($timeLabel)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(state.recordingAmplitude.coerceIn(0.08f, 1f))
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DesktopColors.VibrantOrange)
                                )
                            }
                            OutlinedButton(
                                onClick = { onEvent(DesktopUiEvent.CancelAudioRecording) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(strings.cancelBtn, fontSize = 11.5.sp)
                            }
                            Button(
                                onClick = { onEvent(DesktopUiEvent.StopAudioRecordingAndSave) },
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.saveRecordingBtn, fontSize = 11.5.sp, color = Color.White)
                            }
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(onClick = { onEvent(DesktopUiEvent.AddBlock(BlockType.PARAGRAPH)) }) {
                        Text(if (isEn) "+ Paragraph" else "+ Paragraf", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = { onEvent(DesktopUiEvent.AddBlock(BlockType.TODO)) }) {
                        Text(if (isEn) "+ To-Do" else "+ Görev", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = { onEvent(DesktopUiEvent.AddBlock(BlockType.CODE)) }) {
                        Text(if (isEn) "+ Code Block" else "+ Kod Bloğu", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = { onEvent(DesktopUiEvent.AddBlock(BlockType.DRAWING)) }) {
                        Text(if (isEn) "+ Drawing" else "+ Çizim", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        enabled = !state.isRecordingAudio,
                        onClick = { onEvent(DesktopUiEvent.StartAudioRecording(targetBlockId = null)) }
                    ) {
                        Text(strings.recordAudioBtn, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = { onEvent(DesktopUiEvent.SetSlashPaletteVisible(true)) }) {
                        Text(if (isEn) "/ All Blocks" else "/ Tüm Bloklar", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopRightSplitPane(
    state: DesktopUiState,
    onEvent: (DesktopUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    when (state.splitViewMode) {
        SplitViewMode.GRAPH_2D -> {
            val visualNotesRaw = remember(state.allActiveNotes, state.rootActiveNotes, state.showSubPagesInVisualModes) {
                if (state.showSubPagesInVisualModes) state.allActiveNotes else state.rootActiveNotes
            }
            val visualNotes = remember(visualNotesRaw, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes) {
                maskNotesForLockedViews(visualNotesRaw, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes)
            }
            val visualLinks = remember(state.noteLinks, visualNotes) {
                val visibleUuids = visualNotes.asSequence().map { it.syncUuid }.toHashSet()
                state.noteLinks.filter { it.sourceSyncUuid in visibleUuids && it.targetSyncUuid in visibleUuids }
            }
            DesktopGraphView(
                notes = visualNotes,
                links = visualLinks,
                categories = state.categories,
                selectedNoteSyncUuid = state.selectedNoteSyncUuid,
                onNodeClick = { clickedUuid -> onEvent(DesktopUiEvent.SelectNote(clickedUuid)) },
                modifier = modifier
            )
        }
        SplitViewMode.SUB_PAGE -> {
            val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
            val isEn = strings.languageCode == "en"
            Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                val target = state.splitPreviewNote
                val isTargetMasked = target != null && isProtectedNoteUi(target, lockedCategoryUuids) && !state.isNoteVaultUnlocked
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isTargetMasked -> strings.protectedNoteTitleMasked
                            target != null -> target.title ?: strings.untitledNote
                            else -> if (isEn) "Sub-page Preview" else "Alt Sayfa Önizleme"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (target != null && !isTargetMasked) {
                        OutlinedButton(onClick = { onEvent(DesktopUiEvent.SelectNote(target.syncUuid)) }) {
                            Text(if (isEn) "Open in Editor" else "Ana Editöre Al", fontSize = 11.sp)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline)
                if (target == null) {
                    Text(
                        text = if (isEn) {
                            "Click a Sub-page card to inspect it side-by-side."
                        } else {
                            "Bir Alt Sayfa kartına tıklayarak ana notla yan yana görüntüleyin."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isTargetMasked) {
                    Text(
                        text = strings.protectedNoteSubtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.splitPreviewBlocks, key = { it.id }) { blk ->
                            Text(
                                text = when (blk.type) {
                                    BlockType.TODO -> (if (blk.isChecked) "☑ " else "☐ ") + blk.content
                                    BlockType.HEADING_1 -> "# ${blk.content}"
                                    BlockType.HEADING_2 -> "## ${blk.content}"
                                    BlockType.CODE -> "[${blk.codeLanguage}]\n${blk.content}"
                                    else -> blk.content
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        SplitViewMode.NONE -> Unit
    }
}

@Composable
private fun QuickSwitcherDialog(
    state: DesktopUiState,
    onSelectNote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = remember(state.language) { DesktopStrings.forLanguage(state.language) }
    val isEn = strings.languageCode == "en"
    val lockedCategoryUuids = remember(state.categories) {
        state.categories.filter { it.isPassword == true }.map { it.syncUuid }.toHashSet()
    }
    var q by remember { mutableStateOf("") }
    val matches = remember(q, state.allActiveNotes, lockedCategoryUuids, state.isNoteVaultUnlocked, state.hideLockedNotes) {
        val visible = maskNotesForLockedViews(
            state.allActiveNotes,
            lockedCategoryUuids,
            state.isNoteVaultUnlocked,
            state.hideLockedNotes
        )
        val clean = q.trim().lowercase()
        if (clean.isEmpty()) {
            visible.take(10)
        } else {
            visible.filter { note ->
                val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
                if (isProtected && !state.isNoteVaultUnlocked) {
                    false
                } else {
                    note.title.orEmpty().lowercase().contains(clean) ||
                        BlockParser.findSearchSnippet(note.description, clean) != null
                }
            }.take(12)
        }
    }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 500.dp,
        title = if (isEn) "Quick Switcher" else "Hızlı Not Geçişi",
        subtitle = if (isEn) "Jump to any note instantly with Ctrl+K." else "Ctrl+K ile istediğiniz nota anında atlayın."
    ) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text(if (isEn) "Type title or block content..." else "Başlık veya blok içeriği yazın...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(modifier = Modifier.height(260.dp)) {
            items(matches, key = { it.syncUuid }) { note ->
                val isProtected = isProtectedNoteUi(note, lockedCategoryUuids)
                val isMaskedLocked = isProtected && !state.isNoteVaultUnlocked
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectNote(note.syncUuid) }
                        .padding(10.dp)
                ) {
                    DesktopNoteIconBadge(
                        iconKey = note.icon,
                        isLocked = isMaskedLocked,
                        size = 26.dp,
                        iconSize = 15.dp,
                        cornerRadius = 7.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isMaskedLocked) {
                            strings.protectedNoteTitleMasked
                        } else {
                            note.title?.takeIf { it.isNotBlank() } ?: strings.untitledNote
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SlashBlockPaletteDialog(
    onSelectBlock: (BlockType) -> Unit,
    onDismiss: () -> Unit
) {
    val blockOptions = listOf(
        BlockType.PARAGRAPH to "Paragraf Metni",
        BlockType.HEADING_1 to "Büyük Başlık (H1)",
        BlockType.HEADING_2 to "Alt Başlık (H2)",
        BlockType.TODO to "Yapılacak Görev",
        BlockType.BULLET_LIST to "Madde İşaretli Liste",
        BlockType.CODE to "Çalıştırılabilir Kod Bloğu (9 Dil)",
        BlockType.TABLE to "Dinamik Tablo",
        BlockType.CALLOUT to "Vurgu / Bilgi Kutusu",
        BlockType.TOGGLE to "Açılır/Kapanır Bölüm",
        BlockType.MATH to "Matematik / Formül",
        BlockType.IMAGE to "Görsel Ekle",
        BlockType.DRAWING to "Çizim Tuvali",
        BlockType.AUDIO to "Ses Kaydı (Mikrofon)",
        BlockType.WEB_BOOKMARK to "Web Bağlantı Kartı",
        BlockType.DIVIDER to "Ayırıcı Çizgi"
    )
    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 400.dp,
        title = "Blok Ekle",
        subtitle = "Sayfanıza zengin içerik bloğu yerleştirin."
    ) {
        LazyColumn(modifier = Modifier.height(340.dp)) {
            items(blockOptions) { (type, title) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectBlock(type) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(DesktopColors.VibrantOrange))
                    Spacer(Modifier.width(10.dp))
                    Text(title, fontSize = 13.sp)
                }
            }
        }
    }
}
