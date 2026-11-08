package com.notes.purenoteit.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
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
import androidx.fragment.app.FragmentActivity
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.ui.animation.LocalNavAnimatedVisibilityScope
import com.notes.purenoteit.ui.animation.LocalPagerTransitionCoordinator
import com.notes.purenoteit.ui.animation.LocalSharedTransitionScope
import com.notes.purenoteit.ui.mvi.HomeUiEffect
import com.notes.purenoteit.ui.components.GlassyCategoryBar
import com.notes.purenoteit.ui.components.resilientClickable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun HomeScreen(
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateToAddNote: (noteId: Int) -> Unit,
    onNavigateToNoteWithScope: (noteId: Int, originScope: String) -> Unit = { id, _ -> onNavigateToAddNote(id) },
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

    val noteList by noteViewModel.noteListFlow.collectAsStateWithLifecycle()
    val categoryList by categoryViewModel.categoryListFlow.collectAsStateWithLifecycle()
    val allLinks by noteViewModel.allLinksFlow.collectAsStateWithLifecycle()
    val streamItems by noteViewModel.streamItemsFlow.collectAsStateWithLifecycle()

    LaunchedEffect(noteViewModel) {
        withContext(Dispatchers.Main.immediate) {
            noteViewModel.homeEffect.collect { effect ->
                when (effect) {
                    is HomeUiEffect.ShowToast -> context.showToastShort(effect.message)
                    is HomeUiEffect.ShowSnackbar -> context.showToastShort(effect.message)
                    is HomeUiEffect.NavigateToNote -> onNavigateToAddNote(effect.noteId)
                }
            }
        }
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var prevCategoryIndex by remember { mutableIntStateOf(0) }
    var categoryTransitionDirection by remember { mutableIntStateOf(1) } // 1: right/forward, -1: left/backward
    var searchQuery by remember { mutableStateOf("") }
    var currentViewMode by remember { mutableStateOf(NotionViewMode.GRID) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var lastMenuPressTime by remember { mutableLongStateOf(0L) }
    val menuIconScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var viewIconRotation by remember { mutableFloatStateOf(0f) }
    val animatedViewIconRotation by animateFloatAsState(
        targetValue = viewIconRotation,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "view_icon_rotation"
    )
    val viewIconScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var selectedMenuMode by remember { mutableStateOf<NotionViewMode?>(null) }

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
    var pendingUnlockOriginScope by remember { mutableStateOf("none") }
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pendingBatchLock by remember { mutableStateOf(false) }

    DisposableEffect(showPinDialog) {
        val window = (context as? Activity)?.window
        if (showPinDialog) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

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
                SecurityUtil.lockVault()
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
                    if (SecurityUtil.isVaultUnlocked) {
                        (SecurityUtil.decryptSafe(note.description ?: "") as? AppResult.Success)?.data ?: ""
                    } else {
                        ""
                    }
                } else {
                    note.description ?: ""
                }
                val blocks = BlockParser.parse(rawDesc)
                blocks.filter { it.type == BlockType.SUB_PAGE }.mapNotNull { it.subPageNoteId }
            } catch (_: Exception) {
                emptyList()
            }
        }.toSet()
    }
    val subpageTitles = remember(activeNotes) {
        activeNotes.flatMap { note ->
            try {
                val rawDesc = if (SecurityUtil.isEncryptedPayload(note.description)) {
                    if (SecurityUtil.isVaultUnlocked) {
                        (SecurityUtil.decryptSafe(note.description ?: "") as? AppResult.Success)?.data ?: ""
                    } else {
                        ""
                    }
                } else {
                    note.description ?: ""
                }
                val blocks = BlockParser.parse(rawDesc)
                blocks.filter { it.type == BlockType.SUB_PAGE && it.subPageNoteId == null && it.content.isNotBlank() }
                    .map { it.content.trim().lowercase() }
            } catch (_: Exception) {
                emptyList()
            }
        }.toSet()
    }

    // Ekranda gösterilen ana/kök notlar (Notion tarzı alt sayfalar ana ekranda gizlendiği için sadece ekranda listelenenler)
    val activeRootNotes = remember(activeNotes, subpageIds, subpageTitles) {
        activeNotes.filter { note ->
            val isSubpageByMetadata = note.richBlocksJson?.contains("\"isSubPage\":true") == true || note.richBlocksJson?.contains("\"parentNoteId\"") == true
            val isSubpageById = note.id in subpageIds
            val isSubpageByTitle = note.title?.trim()?.lowercase() in subpageTitles
            !(isSubpageByMetadata || isSubpageById || isSubpageByTitle)
        }
    }

    // Sort categories dynamically by root note count (descending) then by name
    val sortedCategories = remember(categoryList, activeRootNotes) {
        categoryList.sortedWith(
            compareByDescending<CategoryData> { cat -> activeRootNotes.count { it.categoryId == cat.id } }
                .thenBy { it.name }
        )
    }

    // HorizontalPager State: Parmak hareketiyle kategoriler arası yatay kaydırma ve duraksama desteği
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { sortedCategories.size + 1 }
    )

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val navVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val pagerTransitionCoordinator = LocalPagerTransitionCoordinator.current
    val isPagerActivelyMoving by remember {
        derivedStateOf {
            pagerState.isScrollInProgress || kotlin.math.abs(pagerState.currentPageOffsetFraction) > 0.001f
        }
    }
    val isAnyNavTransitionActive =
        (sharedTransitionScope?.isTransitionActive == true) ||
        (navVisibilityScope?.transition?.isRunning == true)

    pagerTransitionCoordinator.updatePagerMovement(
        moving = isPagerActivelyMoving,
        isTransitionActive = isAnyNavTransitionActive
    )

    DisposableEffect(pagerTransitionCoordinator) {
        onDispose {
            pagerTransitionCoordinator.reset()
        }
    }

    // Yumuşak, titremesiz ve doğal akıcı sayfa yerleşimi (Soft Fluid Snap Physics)
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    // Parmak hareketiyle sayfa geçildiğinde ve yerleştiğinde kategori durumunu senkronize et
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        val targetCat = if (settled == 0) null else sortedCategories.getOrNull(settled - 1)?.name
        if (selectedCategory != targetCat) {
            categoryTransitionDirection = if (settled >= prevCategoryIndex) 1 else -1
            prevCategoryIndex = settled
            selectedCategory = targetCat
        }
    }

    // Dışarıdan veya menüden kategori değiştiğinde pager'ı animasyonla hedef sayfaya kaydır
    LaunchedEffect(selectedCategory, sortedCategories) {
        val targetPage = if (selectedCategory == null) 0 else {
            val idx = sortedCategories.indexOfFirst { it.name == selectedCategory }
            if (idx >= 0) idx + 1 else 0
        }
        if (pagerState.settledPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // Helper function to compute filtered notes for a given category (used for directional animation)
    fun computeFilteredNotesForCategory(targetCat: String?): List<NoteData> {
        return activeNotes.filter { note ->
            val isNoteProtected = note.isPassword == true || note.isEncrypted || (note.categoryId in lockedCategoryIds)
            val matchesCategory = if (targetCat == null) {
                true
            } else {
                val cat = categoryList.find { it.name == targetCat }
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

    // Filter notes
    val filteredNotes = remember(activeNotes, selectedCategory, searchQuery, selectedHashtag, subpageTitles, subpageIds, selectedSmartFilter, lockedCategoryIds) {
        computeFilteredNotesForCategory(selectedCategory)
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val regularNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    var notePendingTrash by remember { mutableStateOf<NoteData?>(null) }

    val handleSoftDeleteNote: (NoteData) -> Unit = { note ->
        notePendingTrash = note
    }

    var lastNoteClickMark by remember { mutableStateOf<TimeMark?>(null) }
    val openNoteWithScope: (NoteData, String) -> Unit = openNoteLabel@{ note, originScope ->
        val prevMark = lastNoteClickMark
        if (prevMark != null && prevMark.elapsedNow().inWholeMilliseconds < 380L) {
            return@openNoteLabel
        }
        lastNoteClickMark = TimeSource.Monotonic.markNow()
        focusManager.clearFocus()
        keyboardController?.hide()
        val isNoteProtected = note.isPassword == true || note.isEncrypted || (note.categoryId in lockedCategoryIds)
        if (isNoteProtected && !SecurityUtil.isVaultUnlocked) {
            pendingUnlockNoteId = note.id
            pendingUnlockOriginScope = originScope
            val activity = context as? FragmentActivity
            if (activity != null) {
                SecurityUtil.authenticateWithCryptoBiometric(
                    activity = activity,
                    title = context.getString(R.string.biometric_prompt_title),
                    subtitle = context.getString(R.string.biometric_prompt_subtitle),
                    negativeButtonText = context.getString(R.string.enter_pin),
                    onUnlocked = {
                        onNavigateToNoteWithScope(note.id, originScope)
                    },
                    onFallbackToPin = {
                        showPinDialog = true
                    }
                )
            } else {
                showPinDialog = true
            }
        } else {
            onNavigateToNoteWithScope(note.id, originScope)
        }
    }
    val handleNoteClick: (NoteData) -> Unit = { note ->
        openNoteWithScope(note, "none")
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
                        text = "PureNoteIt v${AppUpdateHelper.getCurrentVersionName(context)}\nfrom Pandafy",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))
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
                                // Left: Drawer menu button (Modern Sidebar Dock Pill)
                                IconButton(
                                    onClick = {
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastMenuPressTime > 400L) {
                                            lastMenuPressTime = currentTime
                                            scope.launch {
                                                menuIconScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                                                menuIconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
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
                                        .size(46.dp)
                                        .graphicsLayer {
                                            scaleX = menuIconScale.value
                                            scaleY = menuIconScale.value
                                        }
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_menu_notion_bars),
                                        contentDescription = "Menü",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Center: App Title with Accent Dot & Note Count
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "PureNoteIt",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(VibrantOrange)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.topbar_note_count, filteredNotes.size),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }

                                // Right: View Mode Selector Menu Button (Modern Material 3 Squircle with Spring Flip)
                                Box {
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            viewIconRotation += 360f
                                            scope.launch {
                                                viewIconScale.animateTo(0.85f, spring(stiffness = Spring.StiffnessHigh))
                                                viewIconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
                                            showViewModeMenu = true
                                        },
                                        modifier = Modifier
                                            .size(46.dp)
                                            .graphicsLayer {
                                                scaleX = viewIconScale.value
                                                scaleY = viewIconScale.value
                                                rotationZ = animatedViewIconRotation
                                            }
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            painter = painterResource(id = currentViewMode.iconRes),
                                            contentDescription = stringResource(currentViewMode.titleRes),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showViewModeMenu,
                                        onDismissRequest = { showViewModeMenu = false },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        for (mode in NotionViewMode.values()) {
                                            val isCurrent = currentViewMode == mode
                                            val isSelectedMode = selectedMenuMode == mode
                                            val itemScale by animateFloatAsState(
                                                targetValue = if (isSelectedMode) 1.12f else 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessHigh
                                                ),
                                                label = "mode_item_scale"
                                            )

                                            DropdownMenuItem(
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = itemScale
                                                    scaleY = itemScale
                                                },
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            painter = painterResource(id = mode.iconRes),
                                                            contentDescription = null,
                                                            tint = if (isCurrent) VibrantOrange else MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = stringResource(mode.titleRes),
                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isCurrent) VibrantOrange else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedMenuMode = mode
                                                    scope.launch {
                                                        delay(130L)
                                                        currentViewMode = mode
                                                        showViewModeMenu = false
                                                        selectedMenuMode = null
                                                        viewIconRotation += 360f
                                                    }
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Tek Satır Birleşik Filtreleme Çubuğu (Unified Single Filter Bar)
                        if (currentViewMode != NotionViewMode.CALENDAR && currentViewMode != NotionViewMode.GRAPH && currentViewMode != NotionViewMode.STREAM && currentViewMode != NotionViewMode.WHITEBOARD) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val activeFilterCount = (if (selectedSmartFilter != SmartFilter.ALL) 1 else 0) +
                                        (if (selectedHashtag != null) 1 else 0)

                                // 1. Filtrele Butonu (Bottom Sheet Açıcı) - Full Capsule Pill, borderless
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showFilterSheet = true
                                        },
                                    shape = RoundedCornerShape(50),
                                    color = if (activeFilterCount > 0) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
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
                                            .clip(RoundedCornerShape(50))
                                            .clickable { selectedSmartFilter = SmartFilter.ALL },
                                        shape = RoundedCornerShape(50),
                                        color = SoftOrangeLight
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(selectedSmartFilter.titleRes),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VibrantOrange
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(text = "✕", fontSize = 10.sp, color = VibrantOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (selectedHashtag != null) {
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { selectedHashtag = null },
                                        shape = RoundedCornerShape(50),
                                        color = SoftOrangeLight
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedHashtag!!,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VibrantOrange
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(text = "✕", fontSize = 10.sp, color = VibrantOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // 3. Hızlı Kategori Seçimi (Glassy Navigation Tabs V7 - 3D Door Flip & Cam Efekti)
                                GlassyCategoryBar(
                                    categories = sortedCategories,
                                    selectedCategory = if (selectedSmartFilter == SmartFilter.ALL && selectedHashtag == null) selectedCategory else "__CUSTOM_FILTER__",
                                    totalNotesCount = activeRootNotes.size,
                                    activeNotes = activeRootNotes,
                                    pagerState = pagerState,
                                    isDarkTheme = isDarkTheme,
                                    getCategoryDotColor = { catName -> getCategoryDotColor(catName) },
                                    onAllSelected = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (selectedCategory != null) {
                                            categoryTransitionDirection = -1
                                            prevCategoryIndex = 0
                                        }
                                        selectedCategory = null
                                        selectedSmartFilter = SmartFilter.ALL
                                        selectedHashtag = null
                                        scope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    onCategorySelected = { catName, isSame ->
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        val targetIndex = sortedCategories.indexOfFirst { it.name == catName } + 1
                                        if (isSame) {
                                            categoryTransitionDirection = -1
                                            prevCategoryIndex = 0
                                            selectedCategory = null
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        } else {
                                            categoryTransitionDirection = if (targetIndex >= prevCategoryIndex) 1 else -1
                                            prevCategoryIndex = targetIndex
                                            selectedCategory = catName
                                            scope.launch { pagerState.animateScrollToPage(targetIndex) }
                                        }
                                    }
                                )
                                // Son sekmenin tamamen görünebilmesi için sağ uçta boşluk
                                Spacer(modifier = Modifier.width(12.dp))
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
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = pagerFlingBehavior,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            val targetCat = if (page == 0) null else sortedCategories.getOrNull(page - 1)?.name
                            val currentCategoryNotes = remember(
                                targetCat,
                                activeNotes,
                                searchQuery,
                                selectedHashtag,
                                subpageTitles,
                                subpageIds,
                                selectedSmartFilter,
                                lockedCategoryIds
                            ) {
                                computeFilteredNotesForCategory(targetCat)
                            }

                            if (currentCategoryNotes.isEmpty()) {
                                EmptyStateContent(
                                    searchQuery = searchQuery,
                                    onNavigateToAddNote = onNavigateToAddNote,
                                    paddingValues = paddingValues
                                )
                            } else {
                                NotionListView(
                                    notes = currentCategoryNotes,
                                    categories = categoryList,
                                    onNoteClick = { note -> openNoteWithScope(note, "p${page}_list") },
                                    onPinClick = { note -> noteViewModel.togglePinStatus(note.id, note.isPinned) },
                                    onDeleteClick = { note -> handleSoftDeleteNote(note) },
                                    onStatusChange = { note, newStatus -> noteViewModel.updateNoteStatus(note, newStatus) },
                                    searchQuery = searchQuery,
                                    pageIndex = page,
                                    sharedScopeKey = "p${page}_list",
                                    modifier = Modifier.padding(paddingValues)
                                )
                            }
                        }
                    }
                }

                NotionViewMode.GRID -> {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        flingBehavior = pagerFlingBehavior,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            val targetCat = if (page == 0) null else sortedCategories.getOrNull(page - 1)?.name
                            val currentCategoryNotes = remember(
                                targetCat,
                                activeNotes,
                                searchQuery,
                                selectedHashtag,
                                subpageTitles,
                                subpageIds,
                                selectedSmartFilter,
                                lockedCategoryIds
                            ) {
                                computeFilteredNotesForCategory(targetCat)
                            }
                            val currentPinnedNotes = remember(currentCategoryNotes) { currentCategoryNotes.filter { it.isPinned } }
                            val currentRegularNotes = remember(currentCategoryNotes) { currentCategoryNotes.filter { !it.isPinned } }

                            if (currentCategoryNotes.isEmpty()) {
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
                                    if (searchQuery.isBlank() && currentCategoryNotes.isNotEmpty()) {
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
                                                    val recentList = currentCategoryNotes.take(6)
                                                    items(recentList, key = { "page_${page}_recent_${it.id}" }) { note ->
                                                        NotionRecentCoverCard(
                                                            note = note,
                                                            categoryList = categoryList,
                                                            onClick = { openNoteWithScope(note, "p${page}_recent") },
                                                            isDark = isDarkTheme,
                                                            sharedScopeKey = "p${page}_recent"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (currentPinnedNotes.isNotEmpty()) {
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

                                        items(currentPinnedNotes, key = { "page_${page}_pinned_${it.id}" }) { note ->
                                            NotionNoteCard(
                                                note = note,
                                                onClick = { openNoteWithScope(note, "p${page}_grid") },
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
                                                },
                                                sharedScopeKey = "p${page}_grid"
                                            )
                                        }

                                        if (currentRegularNotes.isNotEmpty()) {
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
                                    } else if (searchQuery.isBlank() && currentCategoryNotes.isNotEmpty()) {
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

                                    items(currentRegularNotes, key = { "page_${page}_regular_${it.id}" }) { note ->
                                        NotionNoteCard(
                                            note = note,
                                            onClick = { openNoteWithScope(note, "p${page}_grid") },
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
                                            },
                                            sharedScopeKey = "p${page}_grid"
                                        )
                                    }
                                }
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val selectedNotes = remember(selectedNoteIds, activeNotes) {
                                activeNotes.filter { it.id in selectedNoteIds }
                            }
                            val shouldLock = selectedNotes.any { it.isPassword != true && !it.isEncrypted }
                            val lockIconRes = if (shouldLock) R.drawable.lock else R.drawable.unlock
                            val lockText = if (shouldLock) stringResource(R.string.lock) else stringResource(R.string.unlock)
                            val lockContentDesc = stringResource(if (shouldLock) R.string.batch_lock_notes else R.string.batch_unlock_notes)
                            val lockTint = if (shouldLock) VibrantOrange else Color(0xFF2E7D32)
                            val lockBg = if (shouldLock) VibrantOrange.copy(alpha = 0.12f) else Color(0xFFE8F5E9)

                            // 1. Şifrele / Kilidi Aç Dynamic Pill Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (!SecurityUtil.isPasswordSet(context)) {
                                            showPinSetupAlert = true
                                        } else {
                                            pendingBatchLock = true
                                            showPinDialog = true
                                        }
                                    }
                                    .background(lockBg)
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = lockIconRes),
                                    contentDescription = lockContentDesc,
                                    tint = lockTint,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = lockText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lockTint
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 2. Sil Pill Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        showBatchDeleteConfirm = true
                                    }
                                    .background(Color(0xFFFFEBEE))
                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.delete),
                                    contentDescription = stringResource(R.string.batch_delete_notes),
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = stringResource(R.string.delete),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // PIN Verification Dialog for Bitwarden Protection
    if (showPinDialog) {
        val selectedNotes = remember(selectedNoteIds, activeNotes) {
            activeNotes.filter { it.id in selectedNoteIds }
        }
        val isBatchLocking = pendingBatchLock && selectedNotes.any { it.isPassword != true && !it.isEncrypted }

        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pendingUnlockNoteId = null
                pendingBatchLock = false
                enteredPin = ""
            },
            title = {
                Text(
                    text = if (pendingBatchLock) {
                        if (isBatchLocking) stringResource(R.string.batch_lock_notes) else stringResource(R.string.batch_unlock_notes)
                    } else {
                        "Not Kilidini Aç"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (pendingBatchLock) {
                            if (isBatchLocking) {
                                "Seçilen notları güvenli şekilde şifrelemek için 6 haneli ana PIN kodunuzu girin:"
                            } else {
                                "Seçilen notların şifresini kaldırmak için 6 haneli ana PIN kodunuzu girin:"
                            }
                        } else {
                            "Bu not Bitwarden düzeyinde şifrelenmiştir. Erişim sağlamak için 6 haneli ana PIN kodunuzu girin:"
                        },
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
                        if (SecurityUtil.verifyPassword(context, enteredPin.toCharArray())) {
                            if (pendingBatchLock) {
                                pendingBatchLock = false
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
                                // Lock the vault session so that newly locked notes require authentication to open
                                SecurityUtil.lockVault()
                            } else {
                                SecurityUtil.unlockVault()
                            }
                            val targetId = pendingUnlockNoteId
                            val targetScope = pendingUnlockOriginScope
                            showPinDialog = false
                            pendingUnlockNoteId = null
                            pendingUnlockOriginScope = "none"
                            enteredPin = ""
                            if (targetId != null) {
                                onNavigateToNoteWithScope(targetId, targetScope)
                            }
                        } else {
                            enteredPin = ""
                            context.showToastShort("Yanlış PIN Kodu")
                        }
                    }
                ) {
                    Text(
                        text = if (pendingBatchLock && isBatchLocking) "Şifrele" else "Kilidi Aç",
                        color = VibrantOrange,
                        fontWeight = FontWeight.Bold
                    )
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
                    sortedCategories.forEach { cat ->
                        val catCount = remember(activeNotes, cat.id) {
                            activeNotes.count { it.categoryId == cat.id }
                        }
                        CategoryChip(
                            label = cat.name,
                            isSelected = selectedCategory == cat.name,
                            isLocked = cat.isPassword == true,
                            count = catCount,
                            dotColor = getCategoryDotColor(cat.name),
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

private fun getCategoryDotColor(categoryName: String): Color {
    val colors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFF06B6D4), // Cyan
        Color(0xFFF97316), // Orange
        Color(0xFF6366F1), // Indigo
        Color(0xFF14B8A6)  // Teal
    )
    val hash = kotlin.math.abs(categoryName.hashCode())
    return colors[hash % colors.size]
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    isLocked: Boolean = false,
    count: Int? = null,
    dotColor: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (isSelected) VibrantOrange
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLocked) {
                Icon(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = null,
                    tint = if (isSelected) PureWhite else VibrantOrange,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (dotColor != null && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface
            )

            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PureWhite.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                } else {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
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
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NotionRecentCoverCard(
    note: NoteData,
    categoryList: List<CategoryData>,
    onClick: () -> Unit,
    isDark: Boolean,
    sharedScopeKey: String? = null,
    modifier: Modifier = Modifier
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val pagerCoordinator = LocalPagerTransitionCoordinator.current

    val sharedBoundsModifier = if (!pagerCoordinator.suppressOverlay && sharedScopeKey != null && sharedTransitionScope != null && animatedVisibilityScope != null && note.id > 0) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "note_card_${sharedScopeKey}_${note.id}"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    spring(
                        dampingRatio = 0.82f,
                        stiffness = 380f
                    )
                },
                renderInOverlayDuringTransition = true,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(14.dp))
            )
        }
    } else {
        Modifier
    }

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
            .then(sharedBoundsModifier)
            .width(135.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(14.dp))
            .resilientClickable { onClick() },
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
