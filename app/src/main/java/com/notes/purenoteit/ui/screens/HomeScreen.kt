package com.notes.purenoteit.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.ui.components.NotionCalendarView
import com.notes.purenoteit.ui.components.NotionKanbanBoard
import com.notes.purenoteit.ui.components.NotionListView
import com.notes.purenoteit.ui.components.NotionNoteCard
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

enum class NotionViewMode(val title: String, val iconRes: Int) {
    GRID("Kartlar", R.drawable.category),
    LIST("Liste", R.drawable.ic_checklist),
    BOARD("Pano", R.drawable.category_alt),
    CALENDAR("Takvim", R.drawable.calendar)
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

    var selectedCategory by remember { mutableStateOf("Tümü") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var currentViewMode by remember { mutableStateOf(NotionViewMode.GRID) }

    // Dialog state for PIN unlock
    var pendingUnlockNoteId by remember { mutableStateOf<Int?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        noteViewModel.getAllNotes()
        categoryViewModel.getAllCategories()
    }

    // Refresh notes whenever returning from detail or other screens
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                noteViewModel.getAllNotes()
                categoryViewModel.getAllCategories()
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
                            onClick = { scope.launch { drawerState.open() } },
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

                        // Center: Search bar or App Title
                        if (isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Notlarda ara...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = VibrantOrange,
                                    unfocusedBorderColor = BorderLight,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        } else {
                            Text(
                                text = "PureNoteIt",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }

                        // Right: Search Toggle
                        IconButton(
                            onClick = { isSearchExpanded = !isSearchExpanded },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isSearchExpanded) R.drawable.cancel else R.drawable.search
                                ),
                                contentDescription = "Ara",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                                onClick = { currentViewMode = mode }
                            )
                        }
                    }

                    // Horizontal Category Chips (Visible in Grid, List & Board views)
                    if (currentViewMode != NotionViewMode.CALENDAR) {
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
                                onClick = { selectedCategory = "Tümü" }
                            )

                            categoryList.forEach { category ->
                                CategoryChip(
                                    label = category.name,
                                    isSelected = selectedCategory == category.name,
                                    isLocked = category.isPassword == true,
                                    onClick = { selectedCategory = category.name }
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onNavigateToAddNote(-1) },
                    containerColor = CharcoalGray,
                    contentColor = PureWhite,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp, end = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus_small),
                        contentDescription = "Yeni Not",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
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
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 12.dp),
                            verticalItemSpacing = 10.dp,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                        ) {
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
