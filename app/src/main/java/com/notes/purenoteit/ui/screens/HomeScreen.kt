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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.ui.components.NotionCalendarView
import com.notes.purenoteit.ui.components.NotionGraphView
import com.notes.purenoteit.ui.components.NotionKanbanBoard
import com.notes.purenoteit.ui.components.NotionListView
import com.notes.purenoteit.ui.components.NotionNoteCard
import com.notes.purenoteit.ui.components.NotionStreamView
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NotionViewMode(val title: String, val iconRes: Int) {
    GRID("Kartlar", R.drawable.category),
    LIST("Liste", R.drawable.ic_checklist),
    BOARD("Pano", R.drawable.category_alt),
    CALENDAR("Takvim", R.drawable.calendar),
    GRAPH("Ağ", R.drawable.ic_network_graph),
    STREAM("Akış", R.drawable.ic_stream_timeline)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateToAddNote: (noteId: Int) -> Unit,
    onNavigateToAddNoteWithDate: (noteId: Int, initialDate: Long) -> Unit = { id, _ -> onNavigateToAddNote(id) },
    onNavigateToPasswordSettings: () -> Unit,
    onNavigateToCategorySettings: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val noteList by noteViewModel.noteList.observeAsState(emptyList())
    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())
    val allLinks by noteViewModel.allLinks.observeAsState(emptyList())
    val streamItems by noteViewModel.streamItems.observeAsState(emptyList())

    var selectedCategory by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var currentViewMode by remember { mutableStateOf(NotionViewMode.GRID) }
    var isSearchFocused by remember { mutableStateOf(false) }

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

    // Dialog state for Daily Agenda Confirmation
    var showAgendaConfirmDialog by remember { mutableStateOf(false) }
    val todayDateStr = remember { SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date()) }
    val existingTodayAgendaNote = remember(noteList, todayDateStr) {
        noteList.firstOrNull { it.title?.contains(todayDateStr) == true && it.icon == "📅" }
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

    // Filter notes
    val filteredNotes = remember(noteList, selectedCategory, searchQuery) {
        noteList.filter { note ->
            val matchesCategory = if (selectedCategory == "Tümü") {
                true
            } else {
                val cat = categoryList.find { it.name == selectedCategory }
                note.categoryId == cat?.id
            }
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                (note.title ?: "").lowercase().contains(q) ||
                        (note.description ?: "").lowercase().contains(q)
            }
            matchesCategory && matchesQuery
        }
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val regularNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    val handleNoteClick: (NoteData) -> Unit = { note ->
        focusManager.clearFocus()
        keyboardController?.hide()
        if (note.isPassword == true && !SecurityUtil.isVaultUnlocked) {
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
                            .setTitle("Korumalı Not")
                            .setSubtitle("Devam etmek için parmak izinizi okutun")
                            .setNegativeButtonText("PIN Girin")
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
                        .padding(20.dp)
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
                                text = "PureNoteIt",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp
                                )
                            )
                            Text(
                                text = "Notion & Kasa Mimarisi",
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
                        iconRes = R.drawable.category,
                        title = "Kategori Yönetimi",
                        subtitle = "Kategorileri yönet ve kilitle",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToCategorySettings()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: PIN & Vault Settings
                    DrawerMenuTile(
                        iconRes = R.drawable.lock,
                        title = "PIN & Kasa Kilidi",
                        subtitle = "Bitwarden güvenlik ayarları",
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToPasswordSettings()
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Daily Agenda Automation (Paket 2)
                    DrawerMenuTile(
                        iconRes = R.drawable.calendar,
                        title = "Günün Ajandası",
                        subtitle = "Otomatik günlük plan şablonu oluştur",
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                showAgendaConfirmDialog = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Menu Item: Dark Mode Switch
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleDarkTheme(!isDarkTheme) },
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
                                    painter = painterResource(id = R.drawable.theme),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Karanlık Mod",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDarkTheme) "Koyu tema aktif" else "Açık tema aktif",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = { onToggleDarkTheme(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureWhite,
                                    checkedTrackColor = VibrantOrange
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Footer
                    Text(
                        text = "PureNoteIt v1.0.0\nModern & Yumuşak Tasarım",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Drawer menu button
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    scope.launch { drawerState.open() }
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

                            // Right: Spacer for visual balance
                            Spacer(modifier = Modifier.size(42.dp))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Notion Multi-View Switcher Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (mode in NotionViewMode.values()) {
                                ViewModeTab(
                                    mode = mode,
                                    isSelected = currentViewMode == mode,
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        currentViewMode = mode
                                    }
                                )
                            }
                        }

                        // Horizontal Category Chips (Visible in Grid, List & Board views)
                        if (currentViewMode != NotionViewMode.CALENDAR && currentViewMode != NotionViewMode.GRAPH && currentViewMode != NotionViewMode.STREAM) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryChip(
                                    label = "Tümü",
                                    isSelected = selectedCategory == "Tümü",
                                    onClick = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        selectedCategory = "Tümü"
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
                                            selectedCategory = category.name
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
                            onNavigateToAddNote(-1)
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
                        onNodeClick = handleNoteClick,
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
                            onDeleteClick = { note -> noteViewModel.deleteNoteById(note.id) },
                            onStatusChange = { note, newStatus -> noteViewModel.updateNoteStatus(note, newStatus) },
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
                                            text = "Sabitlenenler",
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
                                        onDeleteClick = { noteViewModel.deleteNoteById(note.id) }
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
                                    onDeleteClick = { noteViewModel.deleteNoteById(note.id) }
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

            if (currentViewMode != NotionViewMode.STREAM) {
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
                    Text(text = "📅", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (existingTodayAgendaNote != null) "Günün Ajandası Zaten Var" else "Günün Ajandası",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = if (existingTodayAgendaNote != null) {
                        "Bugün için oluşturulmuş bir ajanda notunuz bulunuyor (\"${existingTodayAgendaNote.title}\"). Mevcut ajandayı mı açmak istersiniz, yoksa yeni bir tane mi oluşturulsun?"
                    } else {
                        "Bugün ($todayDateStr) için günün mottosu, öncelikli kontrol listesi görevleri ve not bölümlerini içeren hazır planlama şablonu oluşturulsun mu?"
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
                        text = if (existingTodayAgendaNote != null) "Mevcut Olanı Aç" else "Oluştur ve Aç",
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
}

@Composable
private fun DrawerMenuTile(
    iconRes: Int,
    title: String,
    subtitle: String,
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
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
private fun ViewModeTab(
    mode: NotionViewMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = mode.iconRes),
                contentDescription = null,
                tint = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = mode.title,
                fontSize = 12.sp,
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
                text = if (searchQuery.isNotBlank()) "Aramanızla eşleşen not bulunamadı" else "Henüz bir not yok",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (searchQuery.isNotBlank())
                    "Farklı bir arama terimi deneyebilir veya kategoriyi değiştirebilirsiniz."
                else
                    "Notion blokları, yapılacaklar listesi veya ses kaydı ile hemen yeni bir not oluşturun.",
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
                    Text(text = "Yeni Not Oluştur", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

    Surface(
        modifier = modifier
            .width(135.dp)
            .height(125.dp)
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
            // Top Cover Banner (40% height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(coverColor)
            )

            // Overlapping Emoji Badge + Title + Date
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                // Emoji Icon Badge positioned at the boundary
                Box(
                    modifier = Modifier
                        .offset(y = (-14).dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isDark) Color(0xFF26262B) else PureWhite)
                        .border(
                            1.dp,
                            if (isDark) Color(0xFF36363E) else BorderLight.copy(alpha = 0.5f),
                            RoundedCornerShape(7.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!note.icon.isNullOrBlank()) note.icon!! else "📄",
                        fontSize = 14.sp
                    )
                }

                // Title and Date
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) PureWhite else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = SimpleDateFormat("d MMM", Locale("tr")).format(note.date),
                            fontSize = 9.5.sp,
                            color = if (isDark) PureWhite.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        if (cat != null) {
                            Text(
                                text = cat.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VibrantOrange,
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
                    contentDescription = "Ara",
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
                            text = "Notlarda ara veya sor...",
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
                            contentDescription = "Temizle",
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
                        contentDescription = "Sesle Ara",
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
                    contentDescription = "Yeni Not",
                    tint = VibrantOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
