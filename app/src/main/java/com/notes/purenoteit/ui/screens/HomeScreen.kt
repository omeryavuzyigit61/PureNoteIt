package com.notes.purenoteit.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.ui.components.LanguageSelectionSheet
import com.notes.purenoteit.ui.components.ModernNoteIconBadge
import com.notes.purenoteit.ui.components.NotionCalendarView
import com.notes.purenoteit.ui.components.NotionGraphView
import com.notes.purenoteit.ui.components.NotionKanbanBoard
import com.notes.purenoteit.ui.components.NotionListView
import com.notes.purenoteit.ui.components.NotionNoteCard
import com.notes.purenoteit.ui.components.NotionStreamView
import com.notes.purenoteit.ui.components.NotionWhiteboardView
import com.notes.purenoteit.ui.components.TemplateHubSheet
import com.notes.purenoteit.ui.components.TrashBinSheet
import com.notes.purenoteit.ui.components.UpdateAvailableDialog
import com.notes.purenoteit.ui.components.VaultBackupRestoreDialog
import com.notes.purenoteit.util.Constants
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.util.AppUpdateData
import com.notes.purenoteit.util.AppUpdateHelper
import com.notes.purenoteit.util.ThemeMode
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.LanguageManager
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SmartFilter(val titleRes: Int, val iconRes: Int) {
    ALL(R.string.filter_all, R.drawable.category),
    PINNED(R.string.filter_pinned, R.drawable.ic_pin),
    LOCKED(R.string.filter_locked, R.drawable.lock),
    REMINDERS(R.string.filter_reminders, R.drawable.ic_alarm_clock),
    PHOTOS(R.string.filter_photos, R.drawable.picture),
    AUDIO(R.string.filter_audio, R.drawable.microphone_lines),
    TASKS(R.string.filter_tasks, R.drawable.ic_checklist),
    TABLES(R.string.filter_tables, R.drawable.ic_table)
}

enum class NotionViewMode(val titleRes: Int, val iconRes: Int) {
    GRID(R.string.tab_cards, R.drawable.category),
    LIST(R.string.tab_list, R.drawable.ic_checklist),
    BOARD(R.string.tab_board, R.drawable.category_alt),
    CALENDAR(R.string.tab_calendar, R.drawable.calendar),
    GRAPH(R.string.tab_graph, R.drawable.ic_network_graph),
    WHITEBOARD(R.string.tab_whiteboard, R.drawable.ic_brush),
    STREAM(R.string.tab_stream, R.drawable.ic_stream_timeline)
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateToAddNote: (noteId: Int) -> Unit,
    onNavigateToAddNoteWithDate: (noteId: Int, initialDate: Long) -> Unit = { id, _ -> onNavigateToAddNote(id) },
    onNavigateToAddNoteWithStatus: (status: String) -> Unit = { _ -> onNavigateToAddNote(-1) },
    onNavigateToPasswordSettings: () -> Unit,
    onNavigateToCategorySettings: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val noteList by noteViewModel.noteList.observeAsState(emptyList())
    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())
    val allLinks by noteViewModel.allLinks.observeAsState(emptyList())
    val streamItems by noteViewModel.streamItems.observeAsState(emptyList())

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var currentViewMode by remember { mutableStateOf(NotionViewMode.GRID) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var lastMenuPressTime by remember { mutableLongStateOf(0L) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                searchQuery = matches[0]
            }
        }
    }

    // Dialog state for PIN unlock
    var pendingUnlockNoteId by remember { mutableStateOf<Int?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pendingBatchLock by remember { mutableStateOf(false) }

    // Multi-Selection / Batch Mode State
    var selectedNoteIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val isSelectionMode = selectedNoteIds.isNotEmpty()
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showPinSetupAlert by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        selectedNoteIds = emptySet()
    }

    // Dedicated Vault Backup & Restore Center Modal (Feature 5)
    var showVaultRestoreDialog by remember { mutableStateOf(false) }

    // Trash Bin Sheet state (Feature 2)
    var showTrashBinSheet by remember { mutableStateOf(false) }

    // Dedicated Settings Screen state
    var showSettingsScreen by remember { mutableStateOf(false) }

    // Language Selection Sheet state (Feature 3: Çoklu Dil)
    var showLanguageSheet by remember { mutableStateOf(false) }

    // Template Hub Sheet state (Zirve Özellik 2: Şablon Galerisi)
    var showTemplateSheet by remember { mutableStateOf(false) }

    // Smart Filter state (Zirve Özellik 3: Hızlı Filtreleme Çipleri)
    var selectedSmartFilter by remember { mutableStateOf(SmartFilter.ALL) }

    // Smart Hashtag filter state (Feature 3)
    var selectedHashtag by remember { mutableStateOf<String?>(null) }

    // Unified Filter Bottom Sheet & View Mode Menu states
    var showFilterSheet by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    // Dialog state for Daily Agenda Confirmation
    var showAgendaConfirmDialog by remember { mutableStateOf(false) }
    val todayDateStr = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date()) }
    val existingTodayAgendaNote = remember(noteList, todayDateStr) {
        noteList.firstOrNull { it.title?.contains(todayDateStr) == true && it.icon == "📅" }
    }

    // In-App Update Dialog state
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateDialogData by remember { mutableStateOf<AppUpdateData?>(null) }
    var isManualReleaseNotesView by remember { mutableStateOf(false) }
    val currentActivity = context as? Activity

    // Check for App Updates in background on startup
    LaunchedEffect(Unit) {
        if (currentActivity != null) {
            AppUpdateHelper.checkForAppUpdate(
                activity = currentActivity,
                isManual = false,
                onUpdateFound = { data ->
                    updateDialogData = data
                    isManualReleaseNotesView = false
                    showUpdateDialog = true
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        noteViewModel.getAllNotes()
        categoryViewModel.getAllCategories()
        noteViewModel.getAllLinks()
        noteViewModel.loadStreamItems()
    }

    // Refresh notes whenever returning from detail or other screens
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                noteViewModel.getAllNotes()
                categoryViewModel.getAllCategories()
                noteViewModel.getAllLinks()
                noteViewModel.loadStreamItems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Segregate active notes and trashed notes (Feature 2)
    val activeNotes = remember(noteList) {
        noteList.filter { it.status != "TRASH" }
    }
    val trashedNotes = remember(noteList) {
        noteList.filter { it.status == "TRASH" }
    }

    // Dynamic Trash Retention Auto-Purge (Configurable in Settings)
    LaunchedEffect(trashedNotes.size) {
        val prefs = SharedPrefencesUtil(context)
        val retentionDays = prefs.get(Constants.KEY_TRASH_RETENTION_DAYS, Constants.DEFAULT_TRASH_RETENTION_DAYS)
        if (retentionDays > 0) {
            val threshold = System.currentTimeMillis() - (retentionDays.toLong() * 24L * 60L * 60L * 1000L)
            val expiredNotes = trashedNotes.filter { it.date < threshold }
            for (expired in expiredNotes) {
                noteViewModel.deleteNoteById(expired.id)
            }
        }
    }

    // Set of locked category IDs for unified Vault security
    val lockedCategoryIds = remember(categoryList) {
        categoryList.filter { it.isPassword == true }.map { it.id }.toSet()
    }

    // Extract all unique hashtags from public (non-locked) active notes (Feature 3)
    val allHashtags = remember(activeNotes, lockedCategoryIds) {
        val tagRegex = Regex("""#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)""")
        activeNotes
            .filter { note ->
                note.isPassword != true && !note.isEncrypted && (note.categoryId !in lockedCategoryIds)
            }
            .flatMap { note ->
                tagRegex.findAll((note.title ?: "") + " " + (note.description ?: ""))
                    .map { "#" + it.groupValues[1] }
                    .toList()
            }.distinct()
    }

    // Extract all sub-page titles & IDs across active notes (Notion Tarzı Hiyerarşik Gizleme)
    val subpageIds = remember(activeNotes) {
        activeNotes.flatMap { note ->
            try {
                val rawDesc = if (SecurityUtil.isEncryptedPayload(note.description)) {
                    SecurityUtil.decrypt(note.description ?: "")
                } else {
                    note.description ?: ""
                }
                val blocks = BlockParser.parse(rawDesc)
                blocks.filter { it.type == BlockType.SUB_PAGE }.mapNotNull { it.subPageNoteId }
            } catch (e: Exception) {
                emptyList()
            }
        }.toSet()
    }
    val subpageTitles = remember(activeNotes) {
        activeNotes.flatMap { note ->
            try {
                val rawDesc = if (SecurityUtil.isEncryptedPayload(note.description)) {
                    SecurityUtil.decrypt(note.description ?: "")
                } else {
                    note.description ?: ""
                }
                val blocks = BlockParser.parse(rawDesc)
                blocks.filter { it.type == BlockType.SUB_PAGE && it.subPageNoteId == null && it.content.isNotBlank() }
                    .map { it.content.trim().lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
        }.toSet()
    }

    // Filter notes
    val filteredNotes = remember(activeNotes, selectedCategory, searchQuery, selectedHashtag, subpageTitles, subpageIds, selectedSmartFilter, lockedCategoryIds) {
        activeNotes.filter { note ->
            val isNoteProtected = note.isPassword == true || note.isEncrypted || (note.categoryId in lockedCategoryIds)
            val matchesCategory = if (selectedCategory == null) {
                true
            } else {
                val cat = categoryList.find { it.name == selectedCategory }
                note.categoryId == cat?.id
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                if (isNoteProtected && !SecurityUtil.isVaultUnlocked) {
                    // Prevent Boolean Search Oracle attacks on locked notes when Vault is locked
                    false
                } else {
                    (note.title ?: "").lowercase().contains(q) ||
                            (note.description ?: "").lowercase().contains(q)
                }
            }
            val matchesHashtag = if (selectedHashtag == null) {
                true
            } else if (isNoteProtected && !SecurityUtil.isVaultUnlocked) {
                false
            } else {
                val text = (note.title ?: "") + " " + (note.description ?: "")
                text.contains(selectedHashtag!!, ignoreCase = true)
            }
            // Notion Hiyerarşik Gizleme: Alt sayfalar ana ekranda gizlenir; yalnızca ana not içerisinden (veya arama yapıldığında) erişilir!
            val isSubpageByMetadata = note.richBlocksJson?.contains("\"isSubPage\":true") == true || note.richBlocksJson?.contains("\"parentNoteId\"") == true
            val isSubpageById = note.id in subpageIds
            val isSubpageByTitle = note.title?.trim()?.lowercase() in subpageTitles
            val isSubpage = isSubpageByMetadata || isSubpageById || isSubpageByTitle
            val matchesSubpageHierarchy = if (searchQuery.isBlank()) !isSubpage else true

            val matchesSmartFilter = when (selectedSmartFilter) {
                SmartFilter.ALL -> true
                SmartFilter.PINNED -> note.isPinned
                SmartFilter.LOCKED -> isNoteProtected
                SmartFilter.REMINDERS -> (note.reminderTime != null && note.reminderTime!! > 0L)
                SmartFilter.PHOTOS -> (!note.images.isNullOrEmpty()) ||
                        (note.description ?: "").contains("\"type\":\"IMAGE\"") ||
                        (note.richBlocksJson ?: "").contains("\"type\":\"IMAGE\"") ||
                        (note.description ?: "").contains("\"type\":\"DRAWING\"")
                SmartFilter.AUDIO -> (!note.sounds.isNullOrEmpty()) ||
                        (note.description ?: "").contains("\"type\":\"AUDIO\"") ||
                        (note.richBlocksJson ?: "").contains("\"type\":\"AUDIO\"")
                SmartFilter.TASKS -> note.totalTasks > 0 ||
                        (note.description ?: "").contains("\"type\":\"TODO\"") ||
                        (note.richBlocksJson ?: "").contains("\"type\":\"TODO\"")
                SmartFilter.TABLES -> (note.description ?: "").contains("\"type\":\"TABLE\"") ||
                        (note.richBlocksJson ?: "").contains("\"type\":\"TABLE\"")
            }

            matchesCategory && matchesQuery && matchesHashtag && matchesSubpageHierarchy && matchesSmartFilter
        }
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val regularNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    var notePendingTrash by remember { mutableStateOf<NoteData?>(null) }

    val handleSoftDeleteNote: (NoteData) -> Unit = { note ->
        notePendingTrash = note
    }

    val handleNoteClick: (NoteData) -> Unit = { note ->
        focusManager.clearFocus()
        keyboardController?.hide()
        val isNoteProtected = note.isPassword == true || note.isEncrypted || (note.categoryId in lockedCategoryIds)
        if (isNoteProtected && !SecurityUtil.isVaultUnlocked) {
            pendingUnlockNoteId = note.id
            // Try biometrics first, fallback to PIN dialog
            val activity = context as? FragmentActivity
            if (activity != null) {
                val biometricManager = BiometricManager.from(context)
                val canAuth = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    val prompt = BiometricPrompt(
                        activity,
                        ContextCompat.getMainExecutor(context),
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                SecurityUtil.unlockVault()
                                onNavigateToAddNote(note.id)
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                showPinDialog = true
                            }
                        }
                    )
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle(context.getString(R.string.biometric_prompt_title))
                            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                            .setNegativeButtonText(context.getString(R.string.enter_pin))
                            .build()
                    )
                } else {
                    showPinDialog = true
                }
            } else {
                showPinDialog = true
            }
        } else {
            onNavigateToAddNote(note.id)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Modern App Logo & Brand Header
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.note),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = stringResource(R.string.drawer_subtitle),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = BorderLight.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Menu Item: Category Management
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_drawer_categories,
                        title = stringResource(R.string.drawer_category_mgmt),
                        subtitle = stringResource(R.string.drawer_category_mgmt_sub),
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToCategorySettings()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Daily Agenda Automation (Paket 2)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_drawer_agenda,
                        title = stringResource(R.string.drawer_daily_agenda),
                        subtitle = stringResource(R.string.drawer_daily_agenda_sub),
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showAgendaConfirmDialog = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Templates Hub (Şablon Galerisi)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_drawer_templates,
                        title = stringResource(R.string.drawer_template_hub),
                        subtitle = stringResource(R.string.drawer_template_hub_sub),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showTemplateSheet = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Notion Kanban Board
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_drawer_board,
                        title = stringResource(R.string.drawer_kanban_board),
                        subtitle = stringResource(R.string.drawer_kanban_board_sub),
                        onClick = {
                            currentViewMode = NotionViewMode.BOARD
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: 2D Knowledge Graph (Paket 1)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_network_graph,
                        title = stringResource(R.string.drawer_graph_view),
                        subtitle = stringResource(R.string.drawer_graph_view_sub),
                        onClick = {
                            currentViewMode = NotionViewMode.GRAPH
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Thought Stream (Paket 4)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_stream_timeline,
                        title = stringResource(R.string.drawer_stream_view),
                        subtitle = stringResource(R.string.drawer_stream_view_sub),
                        onClick = {
                            currentViewMode = NotionViewMode.STREAM
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Infinite Whiteboard (Feature 4)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_brush,
                        title = stringResource(R.string.drawer_whiteboard),
                        subtitle = stringResource(R.string.drawer_whiteboard_sub),
                        onClick = {
                            currentViewMode = NotionViewMode.WHITEBOARD
                            scope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Trash Bin (Feature 2)
                    DrawerMenuTile(
                        iconRes = R.drawable.delete,
                        title = stringResource(R.string.drawer_trash_bin),
                        subtitle = if (trashedNotes.isNotEmpty()) "${trashedNotes.size} " + stringResource(R.string.deleted_notes_count) else stringResource(R.string.empty),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showTrashBinSheet = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Settings (Ayarlar - Tema, Dil, Çöp Saklama Süresi ve Güncellemeler)
                    DrawerMenuTile(
                        iconRes = R.drawable.ic_drawer_settings,
                        title = stringResource(R.string.drawer_settings),
                        subtitle = stringResource(R.string.drawer_settings_sub),
                        onClick = {
                            scope.launch { drawerState.close() }
                            showSettingsScreen = true
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Footer
                    Text(
                        text = "PureNoteIt v${AppUpdateHelper.getCurrentVersionName(context)}\nModern & Yumuşak Tasarım",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Scaffold(
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isSelectionMode) {
                            // Clean Multi-Select Header Bar (Count & Quick Select All)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { selectedNoteIds = emptySet() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.cancel),
                                            contentDescription = stringResource(R.string.close),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.batch_selected_count, selectedNoteIds.size),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val currentVisibleIds = remember(filteredNotes) { filteredNotes.map { it.id }.toSet() }
                                val isAllSelected = currentVisibleIds.isNotEmpty() && selectedNoteIds.containsAll(currentVisibleIds)

                                // Quick Select All Chip with Icon and Label
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedNoteIds = if (isAllSelected) emptySet() else currentVisibleIds
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isAllSelected) VibrantOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, if (isAllSelected) VibrantOrange else Color.Transparent)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_checklist),
                                            contentDescription = null,
                                            tint = if (isAllSelected) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isAllSelected) stringResource(R.string.batch_deselect_all) else stringResource(R.string.batch_select_all),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAllSelected) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Drawer menu button
                                IconButton(
                                    onClick = {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastMenuPressTime > 400L) {
                                            lastMenuPressTime = currentTime
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            scope.launch {
                                                if (drawerState.isClosed) {
                                                    drawerState.open()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.menu),
                                        contentDescription = "Menü",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Center: App Title
                                Text(
                                    text = "PureNoteIt",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                )

                                // Right: View Mode Selector Menu Button
                                Box {
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showViewModeMenu = true
                                        },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            painter = painterResource(id = currentViewMode.iconRes),
                                            contentDescription = stringResource(currentViewMode.titleRes),
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showViewModeMenu,
                                        onDismissRequest = { showViewModeMenu = false },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        for (mode in NotionViewMode.values()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            painter = painterResource(id = mode.iconRes),
                                                            contentDescription = null,
                                                            tint = if (currentViewMode == mode) VibrantOrange else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = stringResource(mode.titleRes),
                                                            fontWeight = if (currentViewMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (currentViewMode == mode) VibrantOrange else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    showViewModeMenu = false
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    currentViewMode = mode
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tek Satır Birleşik Filtreleme Çubuğu (Unified Single Filter Bar)
                        if (currentViewMode != NotionViewMode.CALENDAR && currentViewMode != NotionViewMode.GRAPH && currentViewMode != NotionViewMode.STREAM && currentViewMode != NotionViewMode.WHITEBOARD) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activeFilterCount = (if (selectedCategory != null) 1 else 0) +
                                        (if (selectedSmartFilter != SmartFilter.ALL) 1 else 0) +
                                        (if (selectedHashtag != null) 1 else 0)

                                // 1. Filtrele Butonu (Bottom Sheet Açıcı)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showFilterSheet = true
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (activeFilterCount > 0) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = if (activeFilterCount == 0) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.customize),
                                            contentDescription = stringResource(R.string.filter_button_title),
                                            tint = if (activeFilterCount > 0) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.filter_button_title) + if (activeFilterCount > 0) " ($activeFilterCount)" else "",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (activeFilterCount > 0) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // 2. Aktif Filtre Rozetleri (Hızlı İptal [✕] Butonlu)
                                if (selectedSmartFilter != SmartFilter.ALL) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedSmartFilter = SmartFilter.ALL },
                                        shape = RoundedCornerShape(12.dp),
                                        color = SoftOrangeLight,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(selectedSmartFilter.titleRes),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VibrantOrange
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "✕", fontSize = 10.sp, color = VibrantOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (selectedHashtag != null) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedHashtag = null },
                                        shape = RoundedCornerShape(12.dp),
                                        color = SoftOrangeLight,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedHashtag!!,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VibrantOrange
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = "✕", fontSize = 10.sp, color = VibrantOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 3. Hızlı Kategori Seçimi (Tüm Kategoriler + Kategori Çipleri)
                                CategoryChip(
                                    label = stringResource(R.string.all_categories),
                                    isSelected = selectedCategory == null && selectedSmartFilter == SmartFilter.ALL && selectedHashtag == null,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        selectedCategory = null
                                        selectedSmartFilter = SmartFilter.ALL
                                        selectedHashtag = null
                                    }
                                )

                                categoryList.forEach { category ->
                                    CategoryChip(
                                        label = category.name,
                                        isSelected = selectedCategory == category.name,
                                        isLocked = category.isPassword == true,
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            selectedCategory = if (selectedCategory == category.name) null else category.name
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
            when (currentViewMode) {
                NotionViewMode.BOARD -> {
                    NotionKanbanBoard(
                        notes = filteredNotes,
                        categories = categoryList,
                        onNoteClick = handleNoteClick,
                        onStatusChange = { note, newStatus ->
                            noteViewModel.updateNoteStatus(note, newStatus)
                        },
                        onAddNewNoteWithStatus = { status ->
                            onNavigateToAddNoteWithStatus(status)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                NotionViewMode.CALENDAR -> {
                    NotionCalendarView(
                        notes = filteredNotes,
                        categories = categoryList,
                        onNoteClick = handleNoteClick,
                        onAddNoteForDate = { targetDate ->
                            onNavigateToAddNoteWithDate(-1, targetDate)
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                NotionViewMode.GRAPH -> {
                    NotionGraphView(
                        notes = filteredNotes,
                        links = allLinks,
                        categories = categoryList,
                        onNodeClick = handleNoteClick,
                        isDark = isDarkTheme,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                NotionViewMode.WHITEBOARD -> {
                    NotionWhiteboardView(
                        notes = filteredNotes,
                        links = allLinks,
                        onNoteClick = handleNoteClick,
                        onAddNote = { onNavigateToAddNote(-1) },
                        isDark = isDarkTheme,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                NotionViewMode.STREAM -> {
                    NotionStreamView(
                        streamItems = streamItems,
                        onAddItem = { content -> noteViewModel.addStreamItem(content) },
                        onDeleteItem = { id -> noteViewModel.deleteStreamItem(id) },
                        onConvertToNote = { ids, title ->
                            noteViewModel.convertStreamItemsToNote(ids, title) { newNote ->
                                onNavigateToAddNote(newNote.id)
                            }
                        },
                        isDark = isDarkTheme,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                NotionViewMode.LIST -> {
                    if (filteredNotes.isEmpty()) {
                        EmptyStateContent(
                            searchQuery = searchQuery,
                            onNavigateToAddNote = onNavigateToAddNote,
                            paddingValues = paddingValues
                        )
                    } else {
                        NotionListView(
                            notes = filteredNotes,
                            categories = categoryList,
                            onNoteClick = handleNoteClick,
                            onPinClick = { note -> noteViewModel.togglePinStatus(note.id, note.isPinned) },
                            onDeleteClick = { note -> handleSoftDeleteNote(note) },
                            onStatusChange = { note, newStatus -> noteViewModel.updateNoteStatus(note, newStatus) },
                            searchQuery = searchQuery,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }

                NotionViewMode.GRID -> {
                    if (filteredNotes.isEmpty()) {
                        EmptyStateContent(
                            searchQuery = searchQuery,
                            onNavigateToAddNote = onNavigateToAddNote,
                            paddingValues = paddingValues
                        )
                    } else {
                        val staggeredGridState = rememberLazyStaggeredGridState()
                        LaunchedEffect(staggeredGridState.isScrollInProgress) {
                            if (staggeredGridState.isScrollInProgress) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }

                        LazyVerticalStaggeredGrid(
                            state = staggeredGridState,
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 12.dp),
                            verticalItemSpacing = 10.dp,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                        ) {
                            // Notion Mobile: "Son Kullanılanlar" (Recents) Cover Carousel (Görsel 5)
                            if (searchQuery.isBlank() && filteredNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Son Kullanılanlar",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "›",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                            }
                                        }

                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                        ) {
                                            val recentList = filteredNotes.take(6)
                                            items(recentList, key = { "recent_${it.id}" }) { note ->
                                                NotionRecentCoverCard(
                                                    note = note,
                                                    categoryList = categoryList,
                                                    onClick = { handleNoteClick(note) },
                                                    isDark = isDarkTheme
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (pinnedNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_pin),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.pinned_notes),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                items(pinnedNotes, key = { it.id }) { note ->
                                    NotionNoteCard(
                                        note = note,
                                        onClick = { handleNoteClick(note) },
                                        onPinClick = { noteViewModel.togglePinStatus(note.id, note.isPinned) },
                                        onDeleteClick = { handleSoftDeleteNote(note) },
                                        isCategoryLocked = note.categoryId in lockedCategoryIds,
                                        searchQuery = searchQuery,
                                        isSelectionMode = isSelectionMode,
                                        isSelected = note.id in selectedNoteIds,
                                        onToggleSelect = {
                                            selectedNoteIds = if (note.id in selectedNoteIds) {
                                                selectedNoteIds - note.id
                                            } else {
                                                selectedNoteIds + note.id
                                            }
                                        },
                                        onLongClick = {
                                            selectedNoteIds = selectedNoteIds + note.id
                                        }
                                    )
                                }

                                if (regularNotes.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            text = "Tüm Notlar",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                                        )
                                    }
                                }
                            } else if (searchQuery.isBlank() && filteredNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text(
                                        text = "Tüm Notlar",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp, start = 4.dp)
                                    )
                                }
                            }

                            items(regularNotes, key = { it.id }) { note ->
                                NotionNoteCard(
                                    note = note,
                                    onClick = { handleNoteClick(note) },
                                    onPinClick = { noteViewModel.togglePinStatus(note.id, note.isPinned) },
                                    onDeleteClick = { handleSoftDeleteNote(note) },
                                    isCategoryLocked = note.categoryId in lockedCategoryIds,
                                    searchQuery = searchQuery,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = note.id in selectedNoteIds,
                                    onToggleSelect = {
                                        selectedNoteIds = if (note.id in selectedNoteIds) {
                                            selectedNoteIds - note.id
                                        } else {
                                            selectedNoteIds + note.id
                                        }
                                    },
                                    onLongClick = {
                                        selectedNoteIds = selectedNoteIds + note.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full-screen tap catcher when search is focused:
            // Tapping ANYWHERE outside the bottom dock immediately drops focus and stops the blinking cursor!
            if (isSearchFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                )
            }

            // Pinned Notion Mobile Bottom Dock:
            // Sits at the bottom above navigation bar when keyboard is closed.
            // When keyboard is open, lifts by EXACTLY the keyboard height (via maxOf), sitting 6dp above the keyboard!
            val density = LocalDensity.current
            val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            val bottomDockInset = maxOf(imeBottom, navBottom)

            if (currentViewMode != NotionViewMode.STREAM && currentViewMode != NotionViewMode.WHITEBOARD && !isSelectionMode) {
                NotionMobileBottomDock(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onFocusChanged = { isSearchFocused = it },
                    onVoiceClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Notlarda ara...")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            context.showToastShort("Sesli arama cihazınızda desteklenmiyor")
                        }
                    },
                    onAddNoteClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onNavigateToAddNote(-1)
                    },
                    isDark = isDarkTheme,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomDockInset + 6.dp)
                        .padding(horizontal = 16.dp)
                )
            }

            // Multi-Select Dedicated Bottom Action Dock (Thumb-Friendly, Clear & Labeled)
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomDockInset + 8.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Toplu Şifrele Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (!SecurityUtil.isPasswordSet(context)) {
                                        showPinSetupAlert = true
                                    } else {
                                        val selectedNotes = activeNotes.filter { it.id in selectedNoteIds }
                                        val shouldLock = selectedNotes.any { it.isPassword != true && !it.isEncrypted }
                                        if (!SecurityUtil.isVaultUnlocked) {
                                            pendingBatchLock = true
                                            showPinDialog = true
                                        } else {
                                            noteViewModel.batchToggleLock(context, selectedNotes, shouldLock) {
                                                val msg = if (shouldLock) {
                                                    context.getString(R.string.batch_lock_success, selectedNotes.size)
                                                } else {
                                                    context.getString(R.string.batch_unlock_success, selectedNotes.size)
                                                }
                                                context.showToastShort(msg)
                                                selectedNoteIds = emptySet()
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VibrantOrange.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.lock),
                                    contentDescription = stringResource(R.string.batch_lock_notes),
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.batch_lock_notes),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Elegant Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        )

                        // 2. Toplu Sil Button (Red)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showBatchDeleteConfirm = true
                                }
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = stringResource(R.string.batch_delete_notes),
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.batch_delete_notes),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }
    }

    // PIN Verification Dialog for Bitwarden Protection
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pendingUnlockNoteId = null
                enteredPin = ""
            },
            title = {
                Text(text = "Not Kilidini Aç", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Bu not Bitwarden düzeyinde şifrelenmiştir. Erişim sağlamak için 6 haneli ana PIN kodunuzu girin:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) enteredPin = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("••••••") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (SecurityUtil.verifyPassword(context, enteredPin)) {
                            SecurityUtil.unlockVault()
                            if (pendingBatchLock) {
                                pendingBatchLock = false
                                val selectedNotes = activeNotes.filter { it.id in selectedNoteIds }
                                val shouldLock = selectedNotes.any { it.isPassword != true && !it.isEncrypted }
                                noteViewModel.batchToggleLock(context, selectedNotes, shouldLock) {
                                    val msg = if (shouldLock) {
                                        context.getString(R.string.batch_lock_success, selectedNotes.size)
                                    } else {
                                        context.getString(R.string.batch_unlock_success, selectedNotes.size)
                                    }
                                    context.showToastShort(msg)
                                    selectedNoteIds = emptySet()
                                }
                            }
                            val targetId = pendingUnlockNoteId
                            showPinDialog = false
                            pendingUnlockNoteId = null
                            enteredPin = ""
                            if (targetId != null) {
                                onNavigateToAddNote(targetId)
                            }
                        } else {
                            context.showToastShort("Yanlış PIN Kodu")
                        }
                    }
                ) {
                    Text("Kilidi Aç", color = VibrantOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPinDialog = false
                        pendingUnlockNoteId = null
                        pendingBatchLock = false
                        enteredPin = ""
                    }
                ) {
                    Text("İptal")
                }
            },
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Daily Agenda Confirmation / Selector Dialog (Paket 2)
    if (showAgendaConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAgendaConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.calendar),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (existingTodayAgendaNote != null) stringResource(R.string.agenda_already_exists) else stringResource(R.string.agenda_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = if (existingTodayAgendaNote != null) {
                        stringResource(R.string.agenda_exists_desc, existingTodayAgendaNote.title ?: "")
                    } else {
                        stringResource(R.string.agenda_create_desc, todayDateStr)
                    },
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAgendaConfirmDialog = false
                        if (existingTodayAgendaNote != null) {
                            onNavigateToAddNote(existingTodayAgendaNote.id)
                        } else {
                            noteViewModel.createDailyAgenda { newNote ->
                                if (newNote.id > 0) {
                                    onNavigateToAddNote(newNote.id)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (existingTodayAgendaNote != null) stringResource(R.string.agenda_open_existing) else stringResource(R.string.agenda_create_and_open),
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (existingTodayAgendaNote != null) {
                        TextButton(
                            onClick = {
                                showAgendaConfirmDialog = false
                                noteViewModel.createDailyAgenda { newNote ->
                                    if (newNote.id > 0) {
                                        onNavigateToAddNote(newNote.id)
                                    }
                                }
                            }
                        ) {
                            Text("Yeni Oluştur", color = VibrantOrange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = { showAgendaConfirmDialog = false }) {
                        Text("Vazgeç", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Home Screen Note Delete Confirmation Dialog
    notePendingTrash?.let { targetNote ->
        val isNoteProtected = targetNote.isPassword == true || targetNote.isEncrypted || (targetNote.categoryId in lockedCategoryIds)
        val safeTitle = when {
            isNoteProtected && !SecurityUtil.isVaultUnlocked -> "******"
            targetNote.title.isNullOrBlank() -> stringResource(R.string.untitled_note)
            else -> targetNote.title!!
        }
        AlertDialog(
            onDismissRequest = { notePendingTrash = null },
            title = {
                Text(
                    text = "Notu Çöp Kutusu'na Taşı?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "\"$safeTitle\" adlı notu Çöp Kutusu'na taşımak istediğinize emin misiniz? (Çöp Kutusu'ndan istediğiniz zaman geri yükleyebilirsiniz.)",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trashed = targetNote.copy(status = "TRASH")
                        trashed.id = targetNote.id
                        noteViewModel.saveNote(trashed, contentForLinks = null)
                        context.showToastShort("\"$safeTitle\" Çöp Kutusu'na taşındı (30 gün saklanır)")
                        notePendingTrash = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Evet, Sil",
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { notePendingTrash = null }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Multi-Select Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.batch_delete_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.batch_delete_confirm_desc, selectedNoteIds.size),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val notesToTrash = activeNotes.filter { it.id in selectedNoteIds }
                        noteViewModel.batchMoveToTrash(notesToTrash) {
                            context.showToastShort(context.getString(R.string.batch_delete_success, notesToTrash.size))
                            selectedNoteIds = emptySet()
                            showBatchDeleteConfirm = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.yes_delete),
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Vault PIN Required Alert Dialog
    if (showPinSetupAlert) {
        AlertDialog(
            onDismissRequest = { showPinSetupAlert = false },
            title = {
                Text(
                    text = stringResource(R.string.batch_pin_required_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.batch_pin_required_desc),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPinSetupAlert = false
                        onNavigateToPasswordSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.drawer_settings),
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupAlert = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Vault Backup & Restore Modal Management Center (Feature 5)
    if (showVaultRestoreDialog) {
        VaultBackupRestoreDialog(
            noteViewModel = noteViewModel,
            totalNotesCount = activeNotes.size,
            totalCategoriesCount = categoryList.size,
            onDismiss = { showVaultRestoreDialog = false }
        )
    }

    // 30-Day Trash Bin & Safe Note Recovery Sheet (Feature 2)
    if (showTrashBinSheet) {
        TrashBinSheet(
            trashedNotes = trashedNotes,
            onRestoreNote = { note ->
                val restored = note.copy(status = "TODO")
                restored.id = note.id
                noteViewModel.saveNote(restored, null)
                context.showToastShort("\"${note.title ?: "Not"}\" geri yüklendi!")
            },
            onPermanentDelete = { id ->
                noteViewModel.deleteNoteById(id)
                context.showToastShort("Not kalıcı olarak silindi")
            },
            onEmptyTrash = {
                trashedNotes.forEach { noteViewModel.deleteNoteById(it.id) }
                context.showToastShort("Çöp kutusu boşaltıldı")
            },
            onDismiss = { showTrashBinSheet = false }
        )
    }

    // Settings Screen Modal (Central Settings Hub)
    if (showSettingsScreen) {
        SettingsScreen(
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange,
            isDarkTheme = isDarkTheme,
            onShowUpdateDialog = { data, isReleaseOnly ->
                updateDialogData = data
                isManualReleaseNotesView = isReleaseOnly
                showUpdateDialog = true
            },
            onNavigateToPasswordSettings = onNavigateToPasswordSettings,
            onOpenVaultBackupRestore = { showVaultRestoreDialog = true },
            onDismiss = { showSettingsScreen = false }
        )
    }

    // Language Selection Sheet (Çoklu Dil)
    if (showLanguageSheet) {
        LanguageSelectionSheet(
            onDismiss = { showLanguageSheet = false }
        )
    }

    // In-App Update Dialog ("Yeni Sürüm Bulundu!" veya "Sürüm Notları")
    if (showUpdateDialog && updateDialogData != null) {
        UpdateAvailableDialog(
            updateData = updateDialogData!!,
            isReleaseNotesOnly = isManualReleaseNotesView,
            onDismiss = { showUpdateDialog = false },
            onUpdateClick = {
                showUpdateDialog = false
                if (currentActivity != null) {
                    AppUpdateHelper.launchUpdate(currentActivity, updateDialogData!!)
                }
            }
        )
    }

    // Template Hub Sheet (Zirve Özellik 2: Şablon Galerisi)
    if (showTemplateSheet) {
        TemplateHubSheet(
            onDismiss = { showTemplateSheet = false },
            onSelectTemplate = { selectedTemplate ->
                showTemplateSheet = false
                val newNote = NoteData(
                    title = selectedTemplate.getDefaultTitle(context),
                    description = BlockParser.serialize(selectedTemplate.buildBlocks(context)),
                    date = System.currentTimeMillis(),
                    icon = selectedTemplate.icon
                )
                noteViewModel.saveNote(newNote, contentForLinks = null) { generatedId ->
                    onNavigateToAddNote(generatedId)
                }
            }
        )
    }

    // Unified Filter Bottom Sheet (Tek Filtreleme Alanı)
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Title & Clear All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.filter_sheet_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val hasFilters = selectedCategory != null || selectedSmartFilter != SmartFilter.ALL || selectedHashtag != null
                    if (hasFilters) {
                        TextButton(
                            onClick = {
                                selectedCategory = null
                                selectedSmartFilter = SmartFilter.ALL
                                selectedHashtag = null
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.filter_clear_all),
                                color = VibrantOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Kategoriler
                Text(
                    text = stringResource(R.string.filter_section_categories),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CategoryChip(
                        label = stringResource(R.string.all_categories),
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    categoryList.forEach { cat ->
                        CategoryChip(
                            label = cat.name,
                            isSelected = selectedCategory == cat.name,
                            isLocked = cat.isPassword == true,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat.name) null else cat.name
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 2: Akıllı Filtreler
                Text(
                    text = stringResource(R.string.filter_section_smart),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmartFilter.values().forEach { filter ->
                        val isSelected = selectedSmartFilter == filter
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedSmartFilter = filter },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (!isSelected) androidx.compose.foundation.BorderStroke(0.6.dp, BorderLight.copy(alpha = 0.6f)) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = filter.iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(filter.titleRes),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Section 3: Etiketler (Hashtags)
                if (allHashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.filter_section_tags),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        allHashtags.forEach { tag ->
                            val isSelected = selectedHashtag == tag
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedHashtag = if (isSelected) null else tag
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Apply button
                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange)
                ) {
                    Text(
                        text = stringResource(R.string.filter_apply_btn, filteredNotes.size),
                        fontWeight = FontWeight.Bold,
                        color = PureWhite,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DrawerMenuTile(
    iconRes: Int,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = VibrantOrange,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftOrangeLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = VibrantOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badge,
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                1.dp,
                if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLocked) {
                Icon(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = null,
                    tint = if (isSelected) PureWhite else VibrantOrange,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}



@Composable
private fun EmptyStateContent(
    searchQuery: String,
    onNavigateToAddNote: (Int) -> Unit,
    paddingValues: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(SoftOrangeLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.note),
                    contentDescription = null,
                    tint = VibrantOrange,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.no_notes_found),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.no_notes_found_sub),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            )

            if (searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { onNavigateToAddNote(-1) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantOrange,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus_small),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.create_new_note), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * Notion Mobile "Son Kullanılanlar" (Recents) Cover Card.
 * Inspired directly by Notion Mobile Official App (Image 5):
 * - Aesthetic colored cover header (peach, mint, rose, sky, amber, lavender)
 * - Overlapping Notion page icon / emoji badge
 * - Bold title, clean date & category tag
 */
@Composable
private fun NotionRecentCoverCard(
    note: NoteData,
    categoryList: List<CategoryData>,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cat = categoryList.find { it.id == note.categoryId }
    val coverColorsLight = listOf(
        Color(0xFFFFEDD5), // Peach
        Color(0xFFD1FAE5), // Mint
        Color(0xFFFFE4E6), // Rose
        Color(0xFFDBEAFE), // Sky
        Color(0xFFFEF3C7), // Amber
        Color(0xFFEDE9FE)  // Lavender
    )
    val coverColorsDark = listOf(
        Color(0xFF382318),
        Color(0xFF163124),
        Color(0xFF361822),
        Color(0xFF17283E),
        Color(0xFF362C18),
        Color(0xFF271A38)
    )
    val colorIndex = kotlin.math.abs((note.id * 31 + (note.categoryId ?: 0)).hashCode()) % coverColorsLight.size
    val coverColor = if (isDark) coverColorsDark[colorIndex] else coverColorsLight[colorIndex]
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.35f)
    val cardHeight = (132f * fontScale).dp

    Surface(
        modifier = modifier
            .width(135.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF1E1E22) else PureWhite,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2A2A30) else BorderLight.copy(alpha = 0.6f)
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Cover Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(coverColor)
            )

            // Overlapping Modern Material Vector Icon Badge + Title + Date
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                // Modern Material Vector Icon Badge positioned at the boundary
                val isProtectedRecent = note.isPassword == true || note.isEncrypted || cat?.isPassword == true
                ModernNoteIconBadge(
                    iconKey = note.icon,
                    isProtected = isProtectedRecent,
                    modifier = Modifier.offset(y = (-13).dp),
                    size = 26.dp,
                    iconSize = 14.dp
                )

                // Title and Date (Bottom row is unweighted so Compose measures it first and never clips it)
                val isProtectedTitle = note.isPassword == true || note.isEncrypted || cat?.isPassword == true
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 15.dp, bottom = 7.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = when {
                                isProtectedTitle -> "******"
                                note.title.isNullOrBlank() -> stringResource(R.string.untitled_note)
                                else -> note.title!!
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) PureWhite else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.5.sp,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SimpleDateFormat("d MMM", Locale.getDefault()).format(note.date),
                            fontSize = 9.5.sp,
                            color = if (isDark) PureWhite.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        )
                        if (cat != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VibrantOrange,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Notion Mobile Bottom Floating Dock (Ask AI / Search Pill + Circle Pen FAB).
 * Faithful reproduction of Notion Mobile (Image 5):
 * - Pill on the left: Search & Ask AI with Voice search microphone
 * - Circle on the right: Floating Edit Pen for instant new note creation
 */
@Composable
private fun NotionMobileBottomDock(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onVoiceClick: () -> Unit,
    onAddNoteClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Notion Search / Ask AI Pill with inline interactive BasicTextField
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                },
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) Color(0xFF1E1E22) else PureWhite,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) Color(0xFF2E2E34) else BorderLight.copy(alpha = 0.8f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = stringResource(R.string.search),
                    tint = if (isDark) PureWhite.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            fontSize = 13.sp,
                            color = if (isDark) PureWhite.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                onFocusChanged(focusState.isFocused)
                            },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = if (isDark) PureWhite else MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(VibrantOrange),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )
                    )
                }

                // Clear button [ ✕ ] when query has text
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = stringResource(R.string.clear),
                            tint = if (isDark) PureWhite.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onVoiceClick()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.microphone_lines),
                        contentDescription = stringResource(R.string.voice_search),
                        tint = VibrantOrange,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Notion Floating New Note Circle Button [ ✏️ ]
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onAddNoteClick()
                },
            shape = CircleShape,
            color = if (isDark) Color(0xFF222228) else PureWhite,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) Color(0xFF2E2E34) else BorderLight.copy(alpha = 0.8f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.file_edit),
                    contentDescription = stringResource(R.string.new_note),
                    tint = VibrantOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
