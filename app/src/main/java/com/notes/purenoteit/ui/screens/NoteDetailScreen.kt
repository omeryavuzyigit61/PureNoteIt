package com.notes.purenoteit.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import android.os.Build
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.reminder.ReminderManager
import com.notes.purenoteit.ui.animation.LocalNavAnimatedVisibilityScope
import com.notes.purenoteit.ui.animation.LocalPagerTransitionCoordinator
import com.notes.purenoteit.ui.animation.LocalSharedTransitionScope
import com.notes.purenoteit.ui.components.DrawingCanvasSheet
import com.notes.purenoteit.ui.components.EmojiPickerSheet
import com.notes.purenoteit.ui.components.KANBAN_COLUMNS
import com.notes.purenoteit.ui.components.MediaLightboxDialog
import com.notes.purenoteit.ui.components.ModernNoteIconBadge
import com.notes.purenoteit.ui.components.NotionBlockRow
import com.notes.purenoteit.ui.components.NotionSlashDialog
import com.notes.purenoteit.ui.components.NotionToolbar
import com.notes.purenoteit.ui.components.PageRelationsHub
import com.notes.purenoteit.ui.components.PdfExportStudioSheet
import com.notes.purenoteit.ui.components.ReminderBottomSheet
import com.notes.purenoteit.ui.components.SpotifyAudioVisualizer
import com.notes.purenoteit.ui.components.TemplateHubSheet
import com.notes.purenoteit.ui.components.ZenReadingModeView
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import com.notes.purenoteit.util.NoteExporter
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.TextRecognitionHelper
import com.notes.purenoteit.util.VoiceTranscriptionHelper
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NoteDetailScreen(
    noteId: Int,
    initialDate: Long = -1L,
    initialStatus: String = "TODO",
    autoVoice: Boolean = false,
    autoOcr: Boolean = false,
    originScope: String = "none",
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    val voiceHelper = remember { VoiceTranscriptionHelper(context) }
    var lastBackPressMark by remember { mutableStateOf<TimeMark?>(null) }
    val safeNavigateBack = {
        val prevMark = lastBackPressMark
        if (prevMark == null || prevMark.elapsedNow().inWholeMilliseconds > 600L) {
            lastBackPressMark = TimeSource.Monotonic.markNow()
            onNavigateBack()
        }
    }
    var isVoiceDictating by remember { mutableStateOf(false) }
    var liveVoiceText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.stopListening()
        }
    }

    val backlinks by noteViewModel.backlinks.observeAsState(emptyList())

    LaunchedEffect(noteId) {
        if (noteId != -1) {
            noteViewModel.loadBacklinksForNote(noteId)
        }
    }

    var currentNoteId by rememberSaveable(noteId) { mutableIntStateOf(noteId) }
    var noteRichBlocksJson by rememberSaveable(noteId) { mutableStateOf<String?>(null) }
    var isInitialLoadCompleted by remember(noteId) { mutableStateOf(noteId == -1 && currentNoteId == -1) }

    var title by remember(noteId) { mutableStateOf("") }
    var selectedIcon by remember(noteId) { mutableStateOf<String?>(null) }
    var isPassword by remember(noteId) { mutableStateOf(false) }
    var isPinned by remember(noteId) { mutableStateOf(false) }
    var selectedCategoryId by remember(noteId) { mutableIntStateOf(1) }
    var selectedCategoryName by remember(noteId) { mutableStateOf("Not") }
    var noteStatus by remember(noteId) { mutableStateOf(initialStatus) }
    var noteDate by remember(noteId) { mutableLongStateOf(if (initialDate > 0) initialDate else System.currentTimeMillis()) }

    var reminderTime by remember(noteId) { mutableStateOf<Long?>(null) }
    var reminderType by remember(noteId) { mutableStateOf("ALARM") }
    var reminderSound by remember(noteId) { mutableStateOf("default") }
    var isReminderDone by remember(noteId) { mutableStateOf(false) }
    var showReminderSheet by remember(noteId) { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            context.showToastShort(context.getString(R.string.reminder_perm_required))
        }
    }

    val blocks = remember { mutableStateListOf<NoteBlock>() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val showTopBarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 160)
        }
    }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var currentAmplitude by remember { mutableIntStateOf(0) }

    // Pulsing animation for audio recording
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Recording timer & live amplitude tracker
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            recordingSeconds = 0
            while (true) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            while (true) {
                currentAmplitude = audioRecorder.getMaxAmplitude()
                delay(70L)
            }
        } else {
            currentAmplitude = 0
        }
    }

    // Dialog sheets
    var showEmojiSheet by remember { mutableStateOf(false) }
    var showSlashSheet by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var isTemplateBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var autoVoiceHandled by rememberSaveable { mutableStateOf(false) }
    var autoOcrHandled by rememberSaveable { mutableStateOf(false) }
    var showDrawingSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showOcrSourceSheet by remember { mutableStateOf(false) }
    var isScanningOcr by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editingDrawingPath by remember { mutableStateOf<String?>(null) }
    var editingDrawingBlockIndex by remember { mutableStateOf<Int?>(null) }
    var lightboxMediaPath by remember { mutableStateOf<String?>(null) }
    var lightboxIsDrawing by remember { mutableStateOf(false) }
    var lightboxEditAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    // Gatekeeper & Vault Security states
    var loadedNoteRef by remember { mutableStateOf<NoteData?>(null) }
    var isAccessGranted by remember(noteId) { mutableStateOf(noteId == -1 || SecurityUtil.isVaultUnlocked) }
    var showGatekeeperPinDialog by remember { mutableStateOf(false) }
    var gatekeeperPinInput by remember { mutableStateOf("") }
    var gatekeeperSuccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var resumeTriggerEpoch by remember { mutableLongStateOf(0L) }

    val categoriesForSecurity by categoryViewModel.categoryListFlow.collectAsStateWithLifecycle()
    val isNoteProtected = isPassword ||
        (loadedNoteRef?.isEncrypted == true) ||
        categoriesForSecurity.any { it.id == selectedCategoryId && it.isPassword == true }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isNoteProtected) {
        val window = (context as? Activity)?.window
        if (isNoteProtected) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                noteViewModel.getAllNotes()
                noteViewModel.getAllLinks()
                resumeTriggerEpoch = System.currentTimeMillis()
                if (isNoteProtected && !SecurityUtil.isVaultUnlocked) {
                    isAccessGranted = false
                    title = "🔒 Korumalı Not"
                    blocks.clear()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showLinkNotePicker by remember { mutableStateOf(false) }
    var linkSearchQuery by remember { mutableStateOf("") }
    val allNotesForLinking by noteViewModel.noteList.observeAsState(emptyList())
    val allLinks by noteViewModel.allLinks.observeAsState(emptyList())
    val knownLinkTargets = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }

    LaunchedEffect(allLinks, currentNoteId) {
        if (currentNoteId != -1) {
            allLinks.filter { it.sourceNoteId == currentNoteId }.forEach { link ->
                knownLinkTargets[link.linkText.trim().lowercase()] = link.targetNoteId
            }
        }
    }

    // Zen Mode & Stats states (Feature 4 & 2)
    var isZenMode by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showTrashConfirmDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Live Word Count, Character Count & Reading Time Metrics (Feature 4)
    val allNoteText = remember(title, blocks.map { it.content + " " + it.toggleBody }) {
        title + " " + blocks.joinToString(" ") { it.content + " " + it.toggleBody }
    }
    val wordCount = remember(allNoteText) {
        val t = allNoteText.trim()
        if (t.isEmpty()) 0 else t.split("\\s+".toRegex()).count { it.isNotBlank() }
    }
    val charCount = remember(allNoteText) { allNoteText.length }
    val readingTimeMinutes = remember(wordCount) { maxOf(1, (wordCount + 199) / 200) }

    // Page Relations & Index Hub State (Centralized Alt Sayfalar & Not Bağlantıları)
    var isRelationsExpanded by rememberSaveable { mutableStateOf(true) }
    var showRelationsHubManually by rememberSaveable { mutableStateOf(false) }
    var renamingSubPageBlock by remember { mutableStateOf<NoteBlock?>(null) }
    var renameSubPageInput by remember { mutableStateOf("") }

    val subPageBlocks by remember {
        derivedStateOf {
            val seenIds = mutableSetOf<Int>()
            val result = mutableListOf<NoteBlock>()
            for (b in blocks) {
                if (b.type == BlockType.SUB_PAGE) {
                    val sid = b.subPageNoteId
                    if (sid == null || seenIds.add(sid)) {
                        result.add(b)
                    }
                }
            }
            result
        }
    }
    val outgoingWikiLinks by remember {
        derivedStateOf {
            val wikiRegex = Regex("""\[\[(.*?)\]\]""")
            blocks.flatMap { blk ->
                wikiRegex.findAll(blk.content + " " + blk.toggleBody).map { it.groupValues[1].trim() }
            }.filter { it.isNotBlank() }.distinct()
        }
    }

    // Parent Note resolution (Hierarchical Subpage Parent)
    val parentNoteId: Int? = remember(noteRichBlocksJson, loadedNoteRef, allNotesForLinking, currentNoteId) {
        val json = noteRichBlocksJson
            ?: loadedNoteRef?.takeIf { it.id == currentNoteId }?.richBlocksJson
            ?: allNotesForLinking.find { it.id == currentNoteId }?.richBlocksJson
        val idFromJson = if (json != null) {
            Regex(""""parentNoteId"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
        } else null
        if (idFromJson != null) return@remember idFromJson

        // Fallback: Check if any note in allNotesForLinking has a SUB_PAGE block referencing currentNoteId
        if (currentNoteId != -1) {
            val parent = allNotesForLinking.find { candidate ->
                candidate.description?.contains("\"subPageNoteId\":$currentNoteId") == true
            }
            if (parent != null) return@remember parent.id
        }
        null
    }

    val parentNote: NoteData? = remember(parentNoteId, allNotesForLinking) {
        if (parentNoteId != null) {
            allNotesForLinking.find { it.id == parentNoteId }
        } else null
    }

    // Auto-mount PAGE_RELATIONS block in blocks when relations exist (parent, subpages, outgoing links, or backlinks) and none is mounted
    LaunchedEffect(subPageBlocks.size, outgoingWikiLinks.size, backlinks.size, parentNote, showRelationsHubManually) {
        val shouldHaveHub = subPageBlocks.isNotEmpty() || outgoingWikiLinks.isNotEmpty() || backlinks.isNotEmpty() || (parentNote != null) || showRelationsHubManually
        val hasHubBlock = blocks.any { it.type == BlockType.PAGE_RELATIONS }
        if (shouldHaveHub && !hasHubBlock) {
            blocks.add(0, NoteBlock(type = BlockType.PAGE_RELATIONS))
        }
    }

    // Smart Link Suggestion (Feature 3)
    val suggestedLinkTitle = remember(blocks.map { it.content }, allNotesForLinking, noteId, currentNoteId, title) {
        val allText = blocks.joinToString(" ") { it.content + " " + it.toggleBody }
        val existingLinks = Regex("""\[\[(.*?)\]\]""").findAll(allText).map { it.groupValues[1].trim().lowercase() }.toSet()
        val subpageTitles = blocks.filter { it.type == BlockType.SUB_PAGE }.map { it.content.trim().lowercase() }.toSet()
        val subpageIds = blocks.filter { it.type == BlockType.SUB_PAGE }.mapNotNull { it.subPageNoteId }.toSet()
        val currentTitleClean = title.trim()

        allNotesForLinking.firstOrNull { noteCandidate ->
            val candidateTitle = noteCandidate.title?.trim() ?: ""
            val isCandidateSubpage = noteCandidate.id in subpageIds ||
                    (noteCandidate.richBlocksJson?.contains("\"isSubPage\":true") == true) ||
                    (noteCandidate.richBlocksJson?.contains("\"parentNoteId\"") == true) ||
                    subpageTitles.contains(candidateTitle.lowercase())

            candidateTitle.length >= 3 &&
            noteCandidate.id != noteId &&
            (currentNoteId == -1 || noteCandidate.id != currentNoteId) &&
            (currentTitleClean.isBlank() || !candidateTitle.equals(currentTitleClean, ignoreCase = true)) &&
            !isCandidateSubpage &&
            !existingLinks.contains(candidateTitle.lowercase()) &&
            allText.contains(candidateTitle, ignoreCase = true)
        }?.title?.trim()
    }

    // Undo / Redo history stacks (Feature 2)
    val undoStack = remember { mutableStateListOf<List<NoteBlock>>() }
    val redoStack = remember { mutableStateListOf<List<NoteBlock>>() }

    fun recordUndoState() {
        if (undoStack.size >= 25) {
            undoStack.removeAt(0)
        }
        undoStack.add(blocks.map { it.copy() })
        redoStack.clear()
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val lastState = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(blocks.map { it.copy() })
            blocks.clear()
            blocks.addAll(lastState)
            context.showToastShort(context.getString(R.string.note_undo_toast))
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(blocks.map { it.copy() })
            blocks.clear()
            blocks.addAll(nextState)
            context.showToastShort(context.getString(R.string.note_redo_toast))
        }
    }

    fun populateNoteBlocks(note: NoteData) {
        title = note.title ?: ""
        val rawDesc = if (note.isEncrypted || SecurityUtil.isEncryptedPayload(note.description)) {
            SecurityUtil.decrypt(note.description ?: "")
        } else {
            note.description ?: ""
        }
        blocks.clear()
        val parsed = BlockParser.parse(rawDesc)
        blocks.addAll(parsed)

        // Add existing images and sounds as blocks if not already included
        note.images?.forEach { imgPath ->
            if (blocks.none { it.mediaPath == imgPath }) {
                blocks.add(NoteBlock(type = BlockType.IMAGE, mediaPath = imgPath))
            }
        }
        note.sounds?.forEach { soundPath ->
            if (blocks.none { it.mediaPath == soundPath }) {
                blocks.add(NoteBlock(type = BlockType.AUDIO, mediaPath = soundPath))
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(NoteBlock())
        }
    }

    fun requestGatekeeperUnlock(onSuccess: (() -> Unit)? = null) {
        if (SecurityUtil.isVaultUnlocked) {
            isAccessGranted = true
            onSuccess?.invoke()
            return
        }
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
                            isAccessGranted = true
                            onSuccess?.invoke() ?: run {
                                loadedNoteRef?.let { populateNoteBlocks(it) }
                            }
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            gatekeeperSuccessAction = onSuccess
                            showGatekeeperPinDialog = true
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
                gatekeeperSuccessAction = onSuccess
                showGatekeeperPinDialog = true
            }
        } else {
            gatekeeperSuccessAction = onSuccess
            showGatekeeperPinDialog = true
        }
    }

    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())

    // Load existing note data with Kotlin Coroutines LaunchedEffect
    LaunchedEffect(noteId) {
        val targetIdToLoad = if (noteId != -1) noteId else currentNoteId
        if (targetIdToLoad != -1) {
            val note = noteViewModel.getNoteByIdSuspend(targetIdToLoad)
            if (note != null) {
                loadedNoteRef = note
                currentNoteId = note.id
                noteRichBlocksJson = note.richBlocksJson
                selectedIcon = note.icon
                val isCatLocked = categoryList.find { it.id == (note.categoryId ?: 1) }?.isPassword == true
                val requiresLock = (note.isPassword == true) ||
                    note.isEncrypted ||
                    SecurityUtil.isEncryptedPayload(note.description) ||
                    isCatLocked
                isPassword = requiresLock
                isPinned = note.isPinned
                selectedCategoryId = note.categoryId ?: 1
                noteStatus = note.status ?: "TODO"
                noteDate = note.date
                reminderTime = note.reminderTime
                reminderType = note.reminderType ?: "ALARM"
                reminderSound = note.reminderSound ?: "default"
                isReminderDone = note.isReminderDone

                if (requiresLock && !SecurityUtil.isVaultUnlocked) {
                    title = "🔒 Korumalı Not"
                    isAccessGranted = false
                    blocks.clear()
                    requestGatekeeperUnlock {
                        populateNoteBlocks(note)
                        isInitialLoadCompleted = true
                    }
                } else {
                    isAccessGranted = true
                    populateNoteBlocks(note)
                    isInitialLoadCompleted = true
                }
            }
        } else {
            currentNoteId = -1
            isAccessGranted = true
            blocks.clear()
            blocks.add(NoteBlock())
            isInitialLoadCompleted = true
        }
    }

    LaunchedEffect(Unit) {
        categoryViewModel.getAllCategories()
        noteViewModel.getAllLinks()
    }

    // Category name & lock inheritance resolver with safe fallback
    LaunchedEffect(categoryList, selectedCategoryId) {
        if (categoryList.isNotEmpty()) {
            val cat = categoryList.find { it.id == selectedCategoryId }
            val resolvedCat = if (cat != null) {
                selectedCategoryName = cat.name
                cat
            } else {
                val firstCat = categoryList.first()
                selectedCategoryId = firstCat.id
                selectedCategoryName = firstCat.name
                firstCat
            }
            if (resolvedCat.isPassword == true) {
                isPassword = true
                if (noteId != -1 && !SecurityUtil.isVaultUnlocked && isAccessGranted) {
                    title = "🔒 Korumalı Not"
                    isAccessGranted = false
                    blocks.clear()
                    requestGatekeeperUnlock {
                        loadedNoteRef?.let { populateNoteBlocks(it) }
                        isInitialLoadCompleted = true
                    }
                }
            }
        }
    }

    // Photo picker
    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                recordUndoState()
                val dir = File(context.filesDir, "shared_images").apply { mkdirs() }
                val file = File(dir, "note_image_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                blocks.add(NoteBlock(type = BlockType.IMAGE, mediaPath = file.absolutePath))
            } catch (e: Exception) {
                context.showToastShort(context.getString(R.string.image_load_failed))
            }
        }
    }

    // OCR On-Device Text Scanner (Feature 4 - Google ML Kit)
    val ocrLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            isScanningOcr = true
            recordUndoState()
            TextRecognitionHelper.recognizeTextFromImage(
                context = context,
                imageUri = it,
                onSuccess = { recognizedLines ->
                    isScanningOcr = false
                    val validLines = recognizedLines.filter { it.isNotBlank() }
                    if (validLines.isNotEmpty()) {
                        for (line in validLines) {
                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = line))
                        }
                        context.showToastShort(context.getString(R.string.ocr_success_toast, validLines.size))
                    } else {
                        context.showToastShort(context.getString(R.string.ocr_empty_toast))
                    }
                },
                onError = { e ->
                    isScanningOcr = false
                    context.showToastShort(context.getString(R.string.ocr_error_toast, e.message ?: ""))
                }
            )
        }
    }

    // Camera OCR Capture
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            isScanningOcr = true
            recordUndoState()
            TextRecognitionHelper.recognizeTextFromImage(
                context = context,
                imageUri = tempCameraUri!!,
                onSuccess = { recognizedLines ->
                    isScanningOcr = false
                    val validLines = recognizedLines.filter { it.isNotBlank() }
                    if (validLines.isNotEmpty()) {
                        for (line in validLines) {
                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = line))
                        }
                        context.showToastShort(context.getString(R.string.ocr_success_toast, validLines.size))
                    } else {
                        context.showToastShort(context.getString(R.string.ocr_empty_toast))
                    }
                },
                onError = { e ->
                    isScanningOcr = false
                    context.showToastShort(context.getString(R.string.ocr_error_toast, e.message ?: ""))
                }
            )
        }
    }

    fun launchCameraOcr() {
        try {
            val dir = File(context.cacheDir, "camera_ocr").apply { mkdirs() }
            val file = File(dir, "ocr_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            context.showToastShort(context.getString(R.string.camera_init_error, e.message ?: ""))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraOcr()
        } else {
            context.showToastShort(context.getString(R.string.ocr_perm_camera_required))
        }
    }

    fun checkCameraAndLaunch() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraOcr()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Audio recording permission
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        } else {
            context.showToastShort(context.getString(R.string.mic_perm_required))
        }
    }

    fun handleRecordAudio() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!isRecordingAudio) {
            val path = audioRecorder.startRecording()
            if (path != null) {
                isRecordingAudio = true
                context.showToastShort(context.getString(R.string.voice_rec_started))
            }
        } else {
            val path = audioRecorder.stopRecording()
            isRecordingAudio = false
            if (path != null) {
                blocks.add(NoteBlock(type = BlockType.AUDIO, mediaPath = path))
                context.showToastShort(context.getString(R.string.voice_rec_added))
            }
        }
    }

    fun handleToggleVoiceDictation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            if (!isVoiceDictating) {
                isVoiceDictating = true
                liveVoiceText = ""
                voiceHelper.startListening(
                    onPartial = { partial -> liveVoiceText = partial },
                    onResult = { finalResult ->
                        if (finalResult.isNotBlank()) {
                            liveVoiceText = finalResult
                        }
                    },
                    onError = { err ->
                        isVoiceDictating = false
                        context.showToastShort(err)
                    }
                )
            } else {
                if (liveVoiceText.isNotBlank()) {
                    recordUndoState()
                    blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = liveVoiceText.trim()))
                    context.showToastShort(context.getString(R.string.voice_dict_added))
                }
                voiceHelper.stopListening()
                isVoiceDictating = false
                liveVoiceText = ""
            }
        }
    }

    LaunchedEffect(autoVoice) {
        if (autoVoice && !autoVoiceHandled) {
            autoVoiceHandled = true
            delay(400)
            handleToggleVoiceDictation()
        }
    }

    LaunchedEffect(autoOcr) {
        if (autoOcr && !autoOcrHandled) {
            autoOcrHandled = true
            delay(400)
            checkCameraAndLaunch()
        }
    }

    val hasContent = remember(title, blocks.toList()) {
        title.isNotBlank() || blocks.any {
            it.content.isNotBlank() ||
            it.mediaPath != null ||
            it.type == BlockType.SUB_PAGE ||
            it.type == BlockType.TABLE ||
            it.type == BlockType.DRAWING ||
            it.toggleBody.isNotBlank()
        }
    }

    fun buildNoteDataObject(targetStatus: String = noteStatus): NoteData {
        val jsonContent = BlockParser.serialize(blocks)
        val total = blocks.count { it.type == BlockType.TODO }
        val completed = blocks.count { it.type == BlockType.TODO && it.isChecked }
        val images = blocks.filter { it.type == BlockType.IMAGE && it.mediaPath != null }.map { it.mediaPath!! }
        val sounds = blocks.filter { it.type == BlockType.AUDIO && it.mediaPath != null }.map { it.mediaPath!! }

        val validCategoryId = if (categoryList.any { it.id == selectedCategoryId }) {
            selectedCategoryId
        } else if (categoryList.isNotEmpty()) {
            categoryList.first().id
        } else {
            1
        }

        val isSelectedCategoryLocked = categoryList.find { it.id == validCategoryId }?.isPassword == true
        val effectiveLocked = isPassword || isSelectedCategoryLocked
        val finalContent = if (effectiveLocked) SecurityUtil.encrypt(jsonContent) else jsonContent

        val preservedRichJson = noteRichBlocksJson
            ?: loadedNoteRef?.takeIf { it.id == currentNoteId }?.richBlocksJson
            ?: allNotesForLinking.find { it.id == currentNoteId }?.richBlocksJson

        val note = NoteData(
            title = if (title.isBlank()) "Başlıksız Not" else title.trim(),
            description = finalContent,
            date = if (noteDate > 0) noteDate else System.currentTimeMillis(),
            images = images,
            isPassword = effectiveLocked,
            categoryId = validCategoryId,
            sounds = sounds,
            isPinned = isPinned,
            icon = selectedIcon,
            isEncrypted = effectiveLocked,
            totalTasks = total,
            completedTasks = completed,
            status = targetStatus,
            reminderTime = reminderTime,
            reminderType = reminderType,
            reminderSound = reminderSound,
            isReminderDone = isReminderDone,
            richBlocksJson = preservedRichJson
        )
        if (currentNoteId != -1) {
            note.id = currentNoteId
        }
        return note
    }

    val pendingSubPageBlockIds = remember { mutableStateListOf<String>() }
    val permanentlyDeletedSubPageIds = remember { mutableStateListOf<Int>() }
    var openWhenReadyBlockId by remember { mutableStateOf<String?>(null) }
    var lastParentSubPageEditTime by remember { mutableLongStateOf(0L) }

    fun syncSubPageTitlesToChildren() {
        for (blk in blocks) {
            if (blk.type == BlockType.SUB_PAGE) {
                val childId = blk.subPageNoteId ?: continue
                val childNote = allNotesForLinking.find { it.id == childId && it.status != "TRASH" } ?: continue
                val desiredTitle = if (blk.content.isBlank()) "Yeni Alt Sayfa" else blk.content.trim()
                if (childNote.title != desiredTitle) {
                    val ensuredRichJson = childNote.richBlocksJson
                        ?: if (currentNoteId != -1) "{\"isSubPage\":true,\"parentNoteId\":$currentNoteId}" else null
                    val updatedChild = childNote.copy(
                        title = desiredTitle,
                        richBlocksJson = ensuredRichJson
                    ).also { it.id = childNote.id }
                    noteViewModel.saveNote(updatedChild, contentForLinks = null)
                }
            }
        }
    }

    fun performSilentAutoSave(onDone: ((Int) -> Unit)? = null) {
        if (isPassword && !isAccessGranted) return
        val hasAnyBlockContent = title.isNotBlank() || blocks.any {
            it.content.isNotBlank() ||
            it.mediaPath != null ||
            it.type == BlockType.SUB_PAGE ||
            it.type == BlockType.TABLE ||
            it.type == BlockType.DRAWING ||
            it.toggleBody.isNotBlank()
        }
        if (!hasAnyBlockContent && currentNoteId == -1) return

        syncSubPageTitlesToChildren()
        val note = buildNoteDataObject()
        val allContentText = title + " " + blocks.joinToString(" ") { block ->
            if (block.type == BlockType.SUB_PAGE && block.content.isNotBlank()) {
                "[[${block.content.trim()}]]"
            } else {
                block.content + " " + block.toggleBody
            }
        }
        noteViewModel.saveNote(note, contentForLinks = allContentText) { actualId ->
            currentNoteId = actualId
            onDone?.invoke(actualId)
        }
    }

    fun saveNote(andClose: Boolean = true) {
        if (isPassword && !isAccessGranted) return
        syncSubPageTitlesToChildren()
        val note = buildNoteDataObject()
        val allContentText = title + " " + blocks.joinToString(" ") { block ->
            if (block.type == BlockType.SUB_PAGE && block.content.isNotBlank()) {
                "[[${block.content.trim()}]]"
            } else {
                block.content + " " + block.toggleBody
            }
        }
        noteViewModel.saveNote(note, contentForLinks = allContentText) { actualId ->
            currentNoteId = actualId
            if (reminderTime != null && reminderTime!! > System.currentTimeMillis() && !isReminderDone) {
                val safeTitle = if (isPassword) "🔒 Korumalı Not" else (note.title ?: "Başlıksız Not")
                val noteDescPreview = if (isPassword) "ENC:" else (blocks.firstOrNull { it.content.isNotBlank() }?.content ?: "")
                ReminderManager.scheduleReminder(
                    context = context,
                    noteId = actualId,
                    noteTitle = safeTitle,
                    noteDesc = noteDescPreview,
                    reminderTimeMs = reminderTime!!,
                    reminderType = reminderType,
                    soundType = reminderSound
                )
            } else if (reminderTime == null && actualId != 0) {
                ReminderManager.cancelReminder(context, actualId)
            }
            if (andClose) {
                safeNavigateBack()
            }
        }
    }

    fun handleExitWithAutoSave() {
        if (isPassword && !isAccessGranted) {
            safeNavigateBack()
            return
        }
        if (hasContent || currentNoteId != -1) {
            saveNote(andClose = true)
        } else {
            safeNavigateBack()
        }
    }

    fun addSubPageBlock() {
        recordUndoState()
        if (blocks.none { it.type == BlockType.PAGE_RELATIONS }) {
            blocks.add(0, NoteBlock(type = BlockType.PAGE_RELATIONS))
        }
        isRelationsExpanded = true
        val newBlock = NoteBlock(type = BlockType.SUB_PAGE, content = "")
        val blockId = newBlock.id
        pendingSubPageBlockIds.add(blockId)
        blocks.add(newBlock)

        val createChildForParent: (Int) -> Unit = { savedParentId ->
            val currentBlock = blocks.find { it.id == blockId }
            if (currentBlock == null) {
                pendingSubPageBlockIds.remove(blockId)
            } else {
                val initialTitle = if (currentBlock.content.isNotBlank()) {
                    currentBlock.content.trim()
                } else {
                    "Yeni Alt Sayfa"
                }
                val newChild = NoteData(
                    title = initialTitle,
                    description = BlockParser.serialize(listOf(
                        NoteBlock(type = BlockType.PAGE_RELATIONS),
                        NoteBlock(content = "")
                    )),
                    date = System.currentTimeMillis(),
                    categoryId = selectedCategoryId,
                    icon = "📄",
                    richBlocksJson = "{\"isSubPage\":true,\"parentNoteId\":$savedParentId}"
                )
                noteViewModel.saveNote(newChild, contentForLinks = null) { newChildId ->
                    pendingSubPageBlockIds.remove(blockId)
                    val idx = blocks.indexOfFirst { it.id == blockId }
                    if (idx != -1) {
                        val latest = blocks[idx]
                        val shouldOpenImmediately = openWhenReadyBlockId == blockId
                        blocks[idx] = latest.copy(
                            subPageNoteId = newChildId,
                            content = if (shouldOpenImmediately && latest.content.isBlank()) initialTitle else latest.content
                        )
                        performSilentAutoSave {
                            if (shouldOpenImmediately) {
                                openWhenReadyBlockId = null
                                context.showToastShort(context.getString(R.string.subpage_created_toast, initialTitle))
                                onNavigateToNote?.invoke(newChildId)
                            }
                        }
                    } else {
                        if (openWhenReadyBlockId == blockId) {
                            openWhenReadyBlockId = null
                        }
                        noteViewModel.deleteNoteById(newChildId)
                    }
                }
            }
        }

        if (currentNoteId != -1) {
            createChildForParent(currentNoteId)
        } else {
            performSilentAutoSave { savedParentId ->
                createChildForParent(savedParentId)
            }
        }
    }

    fun navigateToSubPage(block: NoteBlock) {
        if (isPassword && !isAccessGranted) return

        val blockIndex = blocks.indexOfFirst { it.id == block.id }
        val latestBlock = if (blockIndex != -1) blocks[blockIndex] else block

        if (latestBlock.subPageNoteId == null && latestBlock.id in pendingSubPageBlockIds) {
            openWhenReadyBlockId = latestBlock.id
            return
        }

        performSilentAutoSave { savedParentId ->
            val subId = latestBlock.subPageNoteId
            val existingTarget = if (subId != null) {
                allNotesForLinking.find { it.id == subId && it.status != "TRASH" }
            } else null

            if (existingTarget != null) {
                val desiredTitle = if (latestBlock.content.isBlank()) {
                    existingTarget.title?.takeIf { it.isNotBlank() } ?: "Yeni Alt Sayfa"
                } else {
                    latestBlock.content.trim()
                }
                if (latestBlock.content.isBlank() && blockIndex != -1) {
                    blocks[blockIndex] = blocks[blockIndex].copy(content = desiredTitle)
                }
                val navigateAction = {
                    if (existingTarget.isPassword == true && !SecurityUtil.isVaultUnlocked) {
                        requestGatekeeperUnlock {
                            onNavigateToNote?.invoke(existingTarget.id)
                        }
                    } else {
                        onNavigateToNote?.invoke(existingTarget.id)
                    }
                }
                if (existingTarget.title != desiredTitle) {
                    val updatedChild = existingTarget.copy(title = desiredTitle).also { it.id = existingTarget.id }
                    noteViewModel.saveNote(updatedChild, contentForLinks = null) {
                        performSilentAutoSave { navigateAction() }
                    }
                } else {
                    performSilentAutoSave { navigateAction() }
                }
            } else if (subId != null) {
                // Target sub-page was deleted; remove the dead block instead of creating a duplicate note
                blocks.removeAll { it.id == latestBlock.id }
                if (blocks.isEmpty()) {
                    blocks.add(NoteBlock())
                }
                performSilentAutoSave()
                context.showToastShort("Alt sayfa silinmiş, bağlantı kaldırıldı")
            } else {
                val childTitle = if (latestBlock.content.isBlank()) "Yeni Alt Sayfa" else latestBlock.content.trim()
                val newChild = NoteData(
                    title = childTitle,
                    description = BlockParser.serialize(listOf(
                        NoteBlock(type = BlockType.PAGE_RELATIONS),
                        NoteBlock(content = "")
                    )),
                    date = System.currentTimeMillis(),
                    categoryId = selectedCategoryId,
                    icon = "📄",
                    richBlocksJson = "{\"isSubPage\":true,\"parentNoteId\":$savedParentId}"
                )
                noteViewModel.saveNote(newChild, contentForLinks = null) { newChildId ->
                    val currentIdx = blocks.indexOfFirst { it.id == latestBlock.id }
                    if (currentIdx != -1) {
                        val cur = blocks[currentIdx]
                        blocks[currentIdx] = cur.copy(
                            subPageNoteId = newChildId,
                            content = if (cur.content.isBlank()) childTitle else cur.content
                        )
                    }
                    performSilentAutoSave {
                        context.showToastShort(context.getString(R.string.subpage_created_toast, childTitle))
                        onNavigateToNote?.invoke(newChildId)
                    }
                }
            }
        }
    }

    fun navigateToNoteByTitle(targetTitle: String) {
        val trimmed = if (targetTitle.isBlank()) "Yeni Alt Sayfa" else targetTitle.trim()
        val trimmedLower = trimmed.lowercase()

        // 1. Resolve by Note ID first so renamed target notes never spawn duplicate notes
        val linkedTargetId = knownLinkTargets[trimmedLower]
            ?: allLinks.find {
                it.sourceNoteId == currentNoteId && it.linkText.trim().lowercase() == trimmedLower
            }?.targetNoteId
            ?: allLinks.find {
                it.linkText.trim().lowercase() == trimmedLower
            }?.targetNoteId

        val foundById = linkedTargetId?.let { id ->
            allNotesForLinking.find { it.id == id && it.status != "TRASH" }
        }

        // 2. Fallback to exact title match
        val found = foundById ?: allNotesForLinking.find {
            it.status != "TRASH" && it.title?.trim().equals(trimmed, ignoreCase = true)
        }

        if (found != null) {
            val latestTitle = found.title?.trim()
            if (!latestTitle.isNullOrBlank()) {
                knownLinkTargets[latestTitle.lowercase()] = found.id
                if (!latestTitle.equals(trimmed, ignoreCase = true)) {
                    val pattern = Regex("""\[\[\s*${Regex.escape(trimmed)}\s*\]\]""", RegexOption.IGNORE_CASE)
                    for (idx in blocks.indices) {
                        val blk = blocks[idx]
                        var updated = blk
                        var changed = false
                        if (pattern.containsMatchIn(blk.content)) {
                            updated = updated.copy(content = pattern.replace(blk.content, "[[$latestTitle]]"))
                            changed = true
                        }
                        if (pattern.containsMatchIn(blk.toggleBody)) {
                            updated = updated.copy(toggleBody = pattern.replace(blk.toggleBody, "[[$latestTitle]]"))
                            changed = true
                        }
                        if (changed) blocks[idx] = updated
                    }
                }
            }
            performSilentAutoSave {
                val isTargetCatLocked = categoryList.find { it.id == (found.categoryId ?: 1) }?.isPassword == true
                if ((found.isPassword == true || found.isEncrypted || isTargetCatLocked) && !SecurityUtil.isVaultUnlocked) {
                    requestGatekeeperUnlock {
                        onNavigateToNote?.invoke(found.id)
                    }
                } else {
                    onNavigateToNote?.invoke(found.id)
                }
            }
        } else {
            // Never create a new note when clicking a dead or non-existent [[WikiLink]].
            // Clean up the dead link from the current note and notify the user.
            knownLinkTargets.remove(trimmedLower)
            val deadPattern = Regex("""\[\[\s*${Regex.escape(trimmed)}\s*\]\]\s*""", RegexOption.IGNORE_CASE)
            val emptyBlocksToRemove = mutableListOf<NoteBlock>()
            for (idx in blocks.indices) {
                val blk = blocks[idx]
                var updated = blk
                var changed = false
                if (deadPattern.containsMatchIn(blk.content)) {
                    updated = updated.copy(content = deadPattern.replace(blk.content, "").trimEnd())
                    changed = true
                }
                if (deadPattern.containsMatchIn(blk.toggleBody)) {
                    updated = updated.copy(toggleBody = deadPattern.replace(blk.toggleBody, "").trimEnd())
                    changed = true
                }
                if (changed) {
                    if (updated.type == BlockType.PARAGRAPH &&
                        updated.content.isBlank() &&
                        updated.toggleBody.isBlank() &&
                        updated.mediaPath == null
                    ) {
                        emptyBlocksToRemove.add(blk)
                    } else {
                        blocks[idx] = updated
                    }
                }
            }
            if (emptyBlocksToRemove.isNotEmpty()) {
                blocks.removeAll(emptyBlocksToRemove)
                if (blocks.isEmpty()) {
                    blocks.add(NoteBlock())
                }
            }
            performSilentAutoSave()
            context.showToastShort("Bağlanan not silinmiş veya mevcut değil, bağlantı kaldırıldı")
        }
    }

    // Debounced real-time auto-save (800ms) - preserves Undo/Redo history in memory
    LaunchedEffect(title, blocks.toList(), isPinned, selectedCategoryId, selectedIcon) {
        if (!isInitialLoadCompleted) return@LaunchedEffect
        delay(800L)
        if (hasContent || currentNoteId != -1) {
            performSilentAutoSave()
        }
    }

    // Sub-page & [[WikiLink]] synchronization: auto-clean deleted child notes and sync renamed titles
    LaunchedEffect(allNotesForLinking, allLinks, resumeTriggerEpoch) {
        if (allNotesForLinking.isNotEmpty() && isInitialLoadCompleted) {
            var blocksModified = false
            val toRemove = mutableListOf<NoteBlock>()
            val wikiRegex = Regex("""\[\[(.*?)\]\]""")

            // Deduplicate accidental multiple SUB_PAGE blocks with identical subPageNoteId
            val seenSubIds = mutableSetOf<Int>()
            val duplicatesToRemove = mutableListOf<NoteBlock>()
            for (b in blocks) {
                if (b.type == BlockType.SUB_PAGE && b.subPageNoteId != null) {
                    if (!seenSubIds.add(b.subPageNoteId!!)) {
                        duplicatesToRemove.add(b)
                    }
                }
            }
            if (duplicatesToRemove.isNotEmpty()) {
                blocks.removeAll(duplicatesToRemove)
                blocksModified = true
            }

            for (i in blocks.indices) {
                var b = blocks[i]
                if (b.type == BlockType.SUB_PAGE) {
                    val subId = b.subPageNoteId
                    if (subId != null) {
                        val targetNote = allNotesForLinking.find { it.id == subId }
                        if (targetNote == null || targetNote.status == "TRASH") {
                            toRemove.add(b)
                            blocksModified = true
                        } else {
                            val newTitle = targetNote.title
                            val isRecentlyEditedInParent = (System.currentTimeMillis() - lastParentSubPageEditTime) < 1500L
                            val needTitleUpdate = !isRecentlyEditedInParent &&
                                !newTitle.isNullOrBlank() &&
                                b.content != newTitle &&
                                !(b.content.isEmpty() && newTitle == "Yeni Alt Sayfa")
                            if (needTitleUpdate) {
                                b = b.copy(content = newTitle!!)
                                blocks[i] = b
                                blocksModified = true
                            }
                        }
                    }
                }

                // Sync [[WikiLink]] text in block content and toggleBody: update if renamed, remove if deleted/trashed
                val combinedText = b.content + " " + b.toggleBody
                if (combinedText.contains("[[")) {
                    val matches = wikiRegex.findAll(combinedText).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toSet()
                    var updatedBlock = b
                    var textChanged = false
                    for (rawLink in matches) {
                        val lower = rawLink.lowercase()
                        val targetId = knownLinkTargets[lower]
                            ?: allLinks.find { it.sourceNoteId == currentNoteId && it.linkText.trim().lowercase() == lower }?.targetNoteId
                            ?: allLinks.find { it.linkText.trim().lowercase() == lower }?.targetNoteId
                        if (targetId != null) {
                            val targetNote = allNotesForLinking.find { it.id == targetId && it.status != "TRASH" }
                            if (targetNote == null) {
                                // Target note was deleted or moved to Trash -> remove [[rawLink]]
                                val removeRegex = Regex("""\[\[\s*${Regex.escape(rawLink)}\s*\]\]\s*""", RegexOption.IGNORE_CASE)
                                if (removeRegex.containsMatchIn(updatedBlock.content)) {
                                    updatedBlock = updatedBlock.copy(content = removeRegex.replace(updatedBlock.content, "").trimEnd())
                                    textChanged = true
                                }
                                if (removeRegex.containsMatchIn(updatedBlock.toggleBody)) {
                                    updatedBlock = updatedBlock.copy(toggleBody = removeRegex.replace(updatedBlock.toggleBody, "").trimEnd())
                                    textChanged = true
                                }
                                knownLinkTargets.remove(lower)
                            } else {
                                val latestTitle = targetNote.title?.trim()
                                if (!latestTitle.isNullOrBlank() && latestTitle != rawLink) {
                                    val replaceRegex = Regex("""\[\[\s*${Regex.escape(rawLink)}\s*\]\]""", RegexOption.IGNORE_CASE)
                                    if (replaceRegex.containsMatchIn(updatedBlock.content)) {
                                        updatedBlock = updatedBlock.copy(content = replaceRegex.replace(updatedBlock.content, "[[$latestTitle]]"))
                                        textChanged = true
                                    }
                                    if (replaceRegex.containsMatchIn(updatedBlock.toggleBody)) {
                                        updatedBlock = updatedBlock.copy(toggleBody = replaceRegex.replace(updatedBlock.toggleBody, "[[$latestTitle]]"))
                                        textChanged = true
                                    }
                                    knownLinkTargets[latestTitle.lowercase()] = targetId
                                }
                            }
                        } else {
                            val activeMatch = allNotesForLinking.find {
                                it.status != "TRASH" && it.title?.trim().equals(rawLink, ignoreCase = true)
                            }
                            if (activeMatch != null) {
                                knownLinkTargets[lower] = activeMatch.id
                            } else {
                                // Note does not exist in active notes (was deleted or trashed) -> remove dead [[rawLink]]
                                val removeRegex = Regex("""\[\[\s*${Regex.escape(rawLink)}\s*\]\]\s*""", RegexOption.IGNORE_CASE)
                                if (removeRegex.containsMatchIn(updatedBlock.content)) {
                                    updatedBlock = updatedBlock.copy(content = removeRegex.replace(updatedBlock.content, "").trimEnd())
                                    textChanged = true
                                }
                                if (removeRegex.containsMatchIn(updatedBlock.toggleBody)) {
                                    updatedBlock = updatedBlock.copy(toggleBody = removeRegex.replace(updatedBlock.toggleBody, "").trimEnd())
                                    textChanged = true
                                }
                            }
                        }
                    }
                    if (textChanged) {
                        if (updatedBlock.type == BlockType.PARAGRAPH &&
                            updatedBlock.content.isBlank() &&
                            updatedBlock.toggleBody.isBlank() &&
                            updatedBlock.mediaPath == null
                        ) {
                            toRemove.add(b)
                        } else {
                            blocks[i] = updatedBlock
                        }
                        blocksModified = true
                    }
                }
            }
            if (toRemove.isNotEmpty()) {
                blocks.removeAll(toRemove)
            }
            if (currentNoteId != -1 && pendingSubPageBlockIds.isEmpty()) {
                val existingSubPageIds = blocks.filter { it.type == BlockType.SUB_PAGE }.mapNotNull { it.subPageNoteId }.toSet()
                val restoredChildren = allNotesForLinking.filter { candidate ->
                    candidate.status != "TRASH" &&
                        candidate.id !in existingSubPageIds &&
                        candidate.id !in permanentlyDeletedSubPageIds &&
                        candidate.richBlocksJson?.let { json ->
                            Regex(""""parentNoteId"\s*:\s*$currentNoteId\b""").containsMatchIn(json)
                        } == true
                }
                for (restoredChild in restoredChildren) {
                    blocks.add(
                        NoteBlock(
                            type = BlockType.SUB_PAGE,
                            content = restoredChild.title ?: "Yeni Alt Sayfa",
                            subPageNoteId = restoredChild.id
                        )
                    )
                    blocksModified = true
                }
            }
            if (blocks.isEmpty()) {
                blocks.add(NoteBlock())
            }
            if (blocksModified) {
                performSilentAutoSave()
            }
        }
    }

    // Android Hardware / Gesture Back button protection
    BackHandler {
        handleExitWithAutoSave()
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val pagerCoordinator = LocalPagerTransitionCoordinator.current
    val isExitingScreen = animatedVisibilityScope?.transition?.targetState == EnterExitState.PostExit
    val hideExitingGhost = isExitingScreen && pagerCoordinator.suppressOverlay

    @OptIn(ExperimentalSharedTransitionApi::class)
    val targetBoundsModifier = if (originScope != "none" && sharedTransitionScope != null && animatedVisibilityScope != null && noteId > 0) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "note_card_${originScope}_$noteId"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    if (pagerCoordinator.suppressOverlay) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = 0.82f,
                            stiffness = 380f
                        )
                    }
                },
                renderInOverlayDuringTransition = !pagerCoordinator.suppressOverlay,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp))
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (hideExitingGhost) 0f else 1f
            }
            .then(targetBoundsModifier)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!isAccessGranted && isPassword) {
            Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = safeNavigateBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_left),
                                contentDescription = "Geri",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VibrantOrange.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.lock),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.locked_note_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.locked_note_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                requestGatekeeperUnlock {
                                    loadedNoteRef?.let { populateNoteBlocks(it) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VibrantOrange)
                        ) {
                            Text(
                                text = stringResource(R.string.unlock_button),
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = safeNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.go_back),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else if (isZenMode) {
        ZenReadingModeView(
            title = title,
            icon = selectedIcon,
            blocks = blocks,
            wordCount = wordCount,
            readingTimeMinutes = readingTimeMinutes,
            audioRecorder = audioRecorder,
            onExit = { isZenMode = false }
        )
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    AnimatedVisibility(
                        visible = showTopBarTitle,
                        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(220)
                        ),
                        exit = fadeOut(animationSpec = tween(180)) + slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(180)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            if (selectedIcon != null) {
                                ModernNoteIconBadge(
                                    iconKey = selectedIcon,
                                    isProtected = isPassword,
                                    size = 24.dp,
                                    iconSize = 13.dp,
                                    shape = RoundedCornerShape(7.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = title.ifBlank { stringResource(R.string.untitled_note) },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleExitWithAutoSave() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = stringResource(R.string.go_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Undo Action Button (Feature 2)
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_undo),
                            contentDescription = stringResource(R.string.undo),
                            tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Redo Action Button (Feature 2)
                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_redo),
                            contentDescription = stringResource(R.string.redo),
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Export / Share Action Button
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share_square),
                            contentDescription = stringResource(R.string.export),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // More Options Dropdown (⋮) for secondary actions (Responsive & never crowds screen)
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(
                                text = "⋮",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        ) {
                            // Bitwarden Lock Action
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = if (isPassword) R.drawable.lock else R.drawable.unlock),
                                        contentDescription = null,
                                        tint = if (isPassword) VibrantOrange else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = if (isPassword) stringResource(R.string.note_lock_removed_toast) else stringResource(R.string.lock),
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPassword) VibrantOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    if (!isPassword) {
                                        if (SecurityUtil.isPasswordSet(context)) {
                                            isPassword = true
                                            context.showToastShort(context.getString(R.string.note_aes_encrypted_toast))
                                        } else {
                                            showPasswordDialog = true
                                        }
                                    } else {
                                        isPassword = false
                                        context.showToastShort(context.getString(R.string.note_lock_removed_toast))
                                    }
                                }
                            )

                            // Zen Mode Action
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.diary),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = stringResource(R.string.zen_mode_badge),
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    isZenMode = true
                                }
                            )

                            // Move to Trash (if existing note)
                            if (noteId != -1) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.delete),
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.drawer_trash_bin),
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFE53935)
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        showTrashConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Save Button (Clean, single-line, never wraps or squishes)
                    Surface(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { saveNote() },
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalGray
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val density = LocalDensity.current
            val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            val bottomBarInset = maxOf(imeBottom, navBottom)

            Column(
                modifier = Modifier
                    .padding(bottom = bottomBarInset)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Live Audio Recording Banner with Timer & Soundwave
                AnimatedVisibility(
                    visible = isRecordingAudio,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val timerText = String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFF0F0),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2)),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Pulsing red dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935).copy(alpha = pulseAlpha))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.saving_sound),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        text = timerText,
                                        fontSize = 11.sp,
                                        color = Color(0xFFC62828).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                // Spotify Dynamic Fluid Waveform
                                SpotifyAudioVisualizer(
                                    isPlayingOrRecording = isRecordingAudio,
                                    currentAmplitude = currentAmplitude,
                                    maxHeight = 24.dp,
                                    activeColor = Color(0xFFE53935),
                                    modifier = Modifier.width(75.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Cancel Button
                                IconButton(
                                    onClick = {
                                        audioRecorder.cancelRecording()
                                        isRecordingAudio = false
                                        context.showToastShort(context.getString(R.string.voice_rec_cancelled))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cancel),
                                        contentDescription = stringResource(R.string.cancel),
                                        tint = Color(0xFFE53935).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Finish & Add audio button
                                Button(
                                    onClick = { handleRecordAudio() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE53935),
                                        contentColor = PureWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.voice_dict_finish_btn),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Smart Link Suggestion Banner (Feature 3)
                AnimatedVisibility(
                    visible = suggestedLinkTitle != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.7f)),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_link_hub),
                                    contentDescription = null,
                                    tint = Color(0xFF1976D2),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.link_detected_title, suggestedLinkTitle ?: ""),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        suggestedLinkTitle?.let { titleToLink ->
                                            recordUndoState()
                                            var linked = false
                                            for (i in blocks.indices) {
                                                val block = blocks[i]
                                                if (block.content.contains(titleToLink, ignoreCase = true) && !block.content.contains("[[$titleToLink]]", ignoreCase = true)) {
                                                    val regex = Regex("(?i)\\b" + Regex.escape(titleToLink) + "\\b")
                                                    val newContent = if (regex.containsMatchIn(block.content)) {
                                                        regex.replaceFirst(block.content, "[[$titleToLink]]")
                                                    } else {
                                                        block.content.replaceFirst(titleToLink, "[[$titleToLink]]", ignoreCase = true)
                                                    }
                                                    blocks[i] = block.copy(content = newContent)
                                                    linked = true
                                                    break
                                                }
                                            }
                                            if (!linked) {
                                                blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = "[[$titleToLink]]"))
                                            }
                                            context.showToastShort(context.getString(R.string.link_connected_toast, titleToLink))
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = VibrantOrange
                            ) {
                                Text(
                                    text = stringResource(R.string.link_connect_btn),
                                    color = PureWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Live Voice Transcription Banner (Feature 1)
                AnimatedVisibility(
                    visible = isVoiceDictating,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3E5F5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCE93D8)),
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF8E24AA).copy(alpha = pulseAlpha))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.live_voice_listening),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF6A1B9A)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            voiceHelper.stopListening()
                                            isVoiceDictating = false
                                            liveVoiceText = ""
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), color = Color(0xFF757575), fontSize = 12.sp)
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (liveVoiceText.isNotBlank()) {
                                                    recordUndoState()
                                                    blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = liveVoiceText.trim()))
                                                    context.showToastShort(context.getString(R.string.voice_dict_added))
                                                }
                                                voiceHelper.stopListening()
                                                isVoiceDictating = false
                                                liveVoiceText = ""
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF8E24AA)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.add_to_note),
                                            color = PureWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (liveVoiceText.isBlank()) stringResource(R.string.live_voice_placeholder) else liveVoiceText,
                                fontSize = 13.sp,
                                color = if (liveVoiceText.isBlank()) Color(0xFF9E9E9E) else Color(0xFF212121),
                                lineHeight = 18.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                NotionToolbar(
                    onAddBlock = { type ->
                        if (type == BlockType.SUB_PAGE) {
                            addSubPageBlock()
                        } else {
                            recordUndoState()
                            blocks.add(NoteBlock(type = type, content = ""))
                        }
                    },
                    onPickImage = {
                        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRecordAudio = { handleRecordAudio() },
                    onOpenDrawing = {
                        editingDrawingPath = null
                        editingDrawingBlockIndex = null
                        showDrawingSheet = true
                    },
                    onOpenSlashMenu = { showSlashSheet = true },
                    onLinkNote = { showLinkNotePicker = true },
                    onScanOcr = {
                        showOcrSourceSheet = true
                    },
                    onVoiceDictation = { handleToggleVoiceDictation() },
                    onInsertHighlight = {
                        recordUndoState()
                        if (blocks.isNotEmpty() && blocks.last().type == BlockType.PARAGRAPH && blocks.last().content.isNotBlank()) {
                            val last = blocks.last()
                            blocks[blocks.lastIndex] = last.copy(content = last.content + " ==vurgulanan metin==")
                        } else {
                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = "==vurgulanan metin=="))
                        }
                        context.showToastShort(context.getString(R.string.highlighter_added_toast))
                    },
                    onInsertTag = {
                        recordUndoState()
                        if (blocks.isNotEmpty() && blocks.last().type == BlockType.PARAGRAPH) {
                            val last = blocks.last()
                            blocks[blocks.lastIndex] = last.copy(content = (last.content + " #etiket").trim())
                        } else {
                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = "#etiket"))
                        }
                        context.showToastShort(context.getString(R.string.tag_added_toast))
                    },
                    isRecordingAudio = isRecordingAudio,
                    isVoiceDictating = isVoiceDictating
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Notion Page Header (Emoji Icon + Title)
            item {
                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Modern Note Icon Badge
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showEmojiSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedIcon != null) {
                            ModernNoteIconBadge(
                                iconKey = selectedIcon,
                                isProtected = isPassword,
                                size = 46.dp,
                                iconSize = 24.dp,
                                shape = RoundedCornerShape(14.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SoftOrangeLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.apps_add),
                                    contentDescription = stringResource(R.string.select_icon),
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title with crisp contrast and clear placeholder
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(VibrantOrange),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.note_title_placeholder),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notion Mobile Page Properties Row (Tasarım B: Glassy Capsule Bar - M3 Standartları)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isDark = isSystemInDarkTheme()

                    // Frosted Glass Dış Taşıyıcı Kapsül (Smoky/Milky Acrylic Glass Background - GlassyCategoryBar İkizi)
                    val glassBgBrush = if (isDark) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x35FFFFFF),
                                Color(0x15FFFFFF)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xE6FFFFFF),
                                Color(0xB3E5E5ED)
                            )
                        )
                    }

                    // Cam Işık Kırılması / Parlama Kenarlığı (Specular Highlight Rim)
                    val glassBorderBrush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                Color.White.copy(alpha = 0.32f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        } else {
                            listOf(
                                Color.White,
                                Color.White.copy(alpha = 0.75f),
                                Color(0x22000000)
                            )
                        }
                    )

                    // Glassy Pill Base Brushes (Her özelliğe eşit, bağımsız mikro cam hapı)
                    val pillBgBrush = if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0x38FFFFFF),
                                Color(0x18FFFFFF)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF2F2F7)
                            )
                        )
                    }

                    val pillBorderBrush = if (isDark) {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.40f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White,
                                Color(0xFFE2E2E8)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.12f),
                                ambientColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.06f)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .background(brush = glassBgBrush)
                            .border(
                                width = 1.dp,
                                brush = glassBorderBrush,
                                shape = RoundedCornerShape(26.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Status Pill (Bağımsız Eşit Glassy Pill)
                            val currentStatusDef = KANBAN_COLUMNS.find { it.statusKey == noteStatus } ?: KANBAN_COLUMNS[0]
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBgBrush)
                                    .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                    .clickable { showStatusMenu = true }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(currentStatusDef.dotColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(currentStatusDef.titleRes),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_down),
                                        contentDescription = null,
                                        tint = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // 2. Kategori Pill (Bağımsız Eşit Glassy Pill)
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBgBrush)
                                    .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                    .clickable { showCategoryMenu = true }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_m3_folder),
                                        contentDescription = null,
                                        tint = VibrantOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = selectedCategoryName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_chevron_down),
                                        contentDescription = null,
                                        tint = if (isDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // 3. Hatırlatıcı / Alarm Pill (Bağımsız Eşit Glassy Pill)
                            val hasActiveReminder = reminderTime != null && reminderTime!! > System.currentTimeMillis() && !isReminderDone
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBgBrush)
                                    .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                    .clickable {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val hasNotifPermission = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!hasNotifPermission) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                        showReminderSheet = true
                                    }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_m3_schedule),
                                        contentDescription = stringResource(R.string.reminder_title),
                                        tint = if (hasActiveReminder) VibrantOrange else if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (hasActiveReminder) SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(reminderTime) else stringResource(R.string.reminder_title),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (hasActiveReminder) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (hasActiveReminder) VibrantOrange else if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // 4. İstatistik / Sayaç Pill (Bağımsız Eşit Glassy Pill)
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBgBrush)
                                    .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                    .clickable { showStatsDialog = true }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_m3_insights),
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(R.string.zen_words, wordCount) + " • " + stringResource(R.string.zen_reading_time, readingTimeMinutes),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // 5. İçindekiler (TOC - Bağımsız Eşit Glassy Pill)
                            val headingsCount = remember(blocks.size, blocks.map { it.content }) {
                                blocks.count { (it.type == BlockType.HEADING_1 || it.type == BlockType.HEADING_2) && it.content.isNotBlank() }
                            }
                            if (headingsCount > 0) {
                                var showTocMenu by remember { mutableStateOf(false) }
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .shadow(
                                                elevation = 1.5.dp,
                                                shape = RoundedCornerShape(20.dp),
                                                spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                            )
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(pillBgBrush)
                                            .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                            .clickable { showTocMenu = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_m3_toc),
                                                contentDescription = null,
                                                tint = VibrantOrange,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = stringResource(R.string.toc_title) + " ($headingsCount)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showTocMenu,
                                        onDismissRequest = { showTocMenu = false }
                                    ) {
                                        blocks.forEachIndexed { headIdx, headBlock ->
                                            if ((headBlock.type == BlockType.HEADING_1 || headBlock.type == BlockType.HEADING_2) && headBlock.content.isNotBlank()) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = (if (headBlock.type == BlockType.HEADING_1) "• " else "   ◦ ") + headBlock.content,
                                                            fontWeight = if (headBlock.type == BlockType.HEADING_1) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = if (headBlock.type == BlockType.HEADING_1) 14.sp else 13.sp
                                                        )
                                                    },
                                                    onClick = {
                                                        showTocMenu = false
                                                        coroutineScope.launch {
                                                            listState.animateScrollToItem((headIdx + 1).coerceAtLeast(0))
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 6. Bağlantılar (Relations Hub - Bağımsız Eşit Glassy Pill)
                            val totalRelations = subPageBlocks.size + outgoingWikiLinks.size
                            Box(
                                modifier = Modifier
                                    .shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(20.dp),
                                        spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.1f)
                                    )
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(pillBgBrush)
                                    .border(1.dp, pillBorderBrush, RoundedCornerShape(20.dp))
                                    .clickable {
                                        showRelationsHubManually = true
                                        isRelationsExpanded = true
                                        val hubIdx = blocks.indexOfFirst { it.type == BlockType.PAGE_RELATIONS }
                                        if (hubIdx == -1) {
                                            blocks.add(0, NoteBlock(type = BlockType.PAGE_RELATIONS))
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(1)
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((hubIdx + 1).coerceAtLeast(0))
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(VibrantOrange)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_m3_link),
                                        contentDescription = null,
                                        tint = VibrantOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.relations_hub_title) + if (totalRelations > 0) " ($totalRelations)" else "",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Notion Ready-to-Use Templates Hub Banner (Visible on fresh/empty notes, dismissible)
                val isNoteEmpty = noteId == -1 && title.isBlank() && !isTemplateBannerDismissed && blocks.all { it.content.isBlank() && it.mediaPath == null }
                AnimatedVisibility(
                    visible = isNoteEmpty,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showTemplateSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            color = SoftOrangeLight.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(PureWhite),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_drawer_templates),
                                        contentDescription = null,
                                        tint = VibrantOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.template_banner_title),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.template_banner_desc),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = VibrantOrange
                                ) {
                                    Text(
                                        text = stringResource(R.string.template_use_btn),
                                        color = PureWhite,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { isTemplateBannerDismissed = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cancel),
                                        contentDescription = "Kapat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Dynamic Notion Blocks
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                val isPureLinkBlock = block.type == BlockType.PARAGRAPH && block.content.trim().matches(Regex("""^\[\[.+?\]\]$"""))
                // Do not render standalone subpage cards or standalone link cards in the main feed (all grouped inside PageRelationsHub)
                if (block.type == BlockType.SUB_PAGE || isPureLinkBlock) {
                    return@itemsIndexed
                }

                fun isBlockHiddenInFeed(b: NoteBlock): Boolean =
                    b.type == BlockType.SUB_PAGE || (b.type == BlockType.PARAGRAPH && b.content.trim().matches(Regex("""^\[\[.+?\]\]$""")))

                val canMoveUp = remember(blocks.size, index) {
                    var p = index - 1
                    while (p >= 0 && isBlockHiddenInFeed(blocks[p])) p--
                    p >= 0
                }
                val canMoveDown = remember(blocks.size, index) {
                    var n = index + 1
                    while (n < blocks.size && isBlockHiddenInFeed(blocks[n])) n++
                    n < blocks.size
                }

                val moveBlockUpVisible: () -> Unit = {
                    var prevIdx = index - 1
                    while (prevIdx >= 0 && isBlockHiddenInFeed(blocks[prevIdx])) {
                        prevIdx--
                    }
                    if (prevIdx >= 0) {
                        recordUndoState()
                        val item = blocks.removeAt(index)
                        blocks.add(prevIdx, item)
                        performSilentAutoSave()
                    }
                }

                val moveBlockDownVisible: () -> Unit = {
                    var nextIdx = index + 1
                    while (nextIdx < blocks.size && isBlockHiddenInFeed(blocks[nextIdx])) {
                        nextIdx++
                    }
                    if (nextIdx < blocks.size) {
                        recordUndoState()
                        val item = blocks.removeAt(index)
                        blocks.add(nextIdx, item)
                        performSilentAutoSave()
                    }
                }

                if (block.type == BlockType.PAGE_RELATIONS) {
                    PageRelationsHub(
                        parentNote = parentNote,
                        subPageBlocks = subPageBlocks,
                        linkedNotes = outgoingWikiLinks,
                        backlinks = backlinks.filter { it.id != parentNote?.id },
                        isExpanded = isRelationsExpanded,
                        onToggleExpand = { isRelationsExpanded = !isRelationsExpanded },
                        onOpenParentNote = { pNote ->
                            if (pNote.isPassword == true && !SecurityUtil.isVaultUnlocked) {
                                requestGatekeeperUnlock {
                                    onNavigateToNote?.invoke(pNote.id)
                                }
                            } else {
                                onNavigateToNote?.invoke(pNote.id)
                            }
                        },
                        onOpenSubPage = { subBlock -> navigateToSubPage(subBlock) },
                        onOpenLinkedNote = { targetTitle -> navigateToNoteByTitle(targetTitle) },
                        onOpenBacklink = { bNote ->
                            if (bNote.isPassword == true && !SecurityUtil.isVaultUnlocked) {
                                requestGatekeeperUnlock {
                                    onNavigateToNote?.invoke(bNote.id)
                                }
                            } else {
                                onNavigateToNote?.invoke(bNote.id)
                            }
                        },
                        onAddSubPage = { addSubPageBlock() },
                        onAddLink = { showLinkNotePicker = true },
                        onRequestRename = { subBlock ->
                            renamingSubPageBlock = subBlock
                            renameSubPageInput = subBlock.content
                        },
                        onDeleteSubPage = { subBlock ->
                            recordUndoState()
                            pendingSubPageBlockIds.remove(subBlock.id)
                            if (openWhenReadyBlockId == subBlock.id) {
                                openWhenReadyBlockId = null
                            }
                            val childId = subBlock.subPageNoteId
                            if (childId != null) {
                                permanentlyDeletedSubPageIds.add(childId)
                                noteViewModel.deleteNoteById(childId)
                            }
                            blocks.removeAll { it.id == subBlock.id }
                            performSilentAutoSave()
                            context.showToastShort(context.getString(R.string.delete) + ": " + (subBlock.content.ifBlank { context.getString(R.string.new_subpage_default) }))
                        },
                        onRemoveLinkedNote = { targetTitle ->
                            recordUndoState()
                            val removeRegex = Regex("""\[\[\s*${Regex.escape(targetTitle)}\s*\]\]\s*""", RegexOption.IGNORE_CASE)
                            var changed = false
                            for (i in blocks.indices) {
                                val blk = blocks[i]
                                if (removeRegex.containsMatchIn(blk.content) || removeRegex.containsMatchIn(blk.toggleBody)) {
                                    val newContent = removeRegex.replace(blk.content, "").trimEnd()
                                    val newToggle = removeRegex.replace(blk.toggleBody, "").trimEnd()
                                    blocks[i] = blk.copy(content = newContent, toggleBody = newToggle)
                                    changed = true
                                }
                            }
                            if (changed) {
                                knownLinkTargets.remove(targetTitle.trim().lowercase())
                                performSilentAutoSave()
                                context.showToastShort(targetTitle + " " + context.getString(R.string.delete))
                            }
                        },
                        onMoveUp = if (canMoveUp) moveBlockUpVisible else null,
                        onMoveDown = if (canMoveDown) moveBlockDownVisible else null,
                        onDeleteHub = {
                            recordUndoState()
                            blocks.removeAt(index)
                            showRelationsHubManually = false
                            performSilentAutoSave()
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    return@itemsIndexed
                }

                NotionBlockRow(
                    block = block,
                    onContentChange = { newText ->
                        blocks[index] = block.copy(content = newText)
                        if (block.type == BlockType.SUB_PAGE) {
                            lastParentSubPageEditTime = System.currentTimeMillis()
                        }
                    },
                    onCheckedChange = { isChecked ->
                        blocks[index] = block.copy(isChecked = isChecked)
                    },
                    onToggleExpand = {
                        blocks[index] = block.copy(isExpanded = !block.isExpanded)
                    },
                    onToggleBodyChange = { newBody ->
                        blocks[index] = block.copy(toggleBody = newBody)
                    },
                    onTableChange = { newTable ->
                        blocks[index] = block.copy(tableData = newTable)
                    },
                    onCodeLanguageChange = { newLang ->
                        blocks[index] = blocks[index].copy(codeLanguage = newLang)
                    },
                    allBlocks = blocks,
                    onScrollToBlock = { targetIndex ->
                        coroutineScope.launch {
                            listState.animateScrollToItem((targetIndex + 1).coerceAtLeast(0))
                        }
                    },
                    onDeleteBlock = {
                        recordUndoState()
                        val blockToDelete = blocks.getOrNull(index)
                        if (blockToDelete?.type == BlockType.SUB_PAGE) {
                            pendingSubPageBlockIds.remove(blockToDelete.id)
                            if (openWhenReadyBlockId == blockToDelete.id) {
                                openWhenReadyBlockId = null
                            }
                            val childId = blockToDelete.subPageNoteId
                            if (childId != null) {
                                permanentlyDeletedSubPageIds.add(childId)
                                noteViewModel.deleteNoteById(childId)
                            }
                        }
                        if (blocks.size > 1) {
                            blocks.removeAt(index)
                        } else {
                            blocks[0] = NoteBlock()
                        }
                        performSilentAutoSave()
                    },
                    onMoveUp = if (canMoveUp) moveBlockUpVisible else null,
                    onMoveDown = if (canMoveDown) moveBlockDownVisible else null,
                    onEditDrawing = { existingPath ->
                        editingDrawingPath = existingPath
                        editingDrawingBlockIndex = index
                        showDrawingSheet = true
                    },
                    onPreviewMedia = { path, isDrawingBlock, editAction ->
                        lightboxMediaPath = path
                        lightboxIsDrawing = isDrawingBlock
                        lightboxEditAction = editAction
                    },
                    onOpenLinkedNote = { targetTitle ->
                        navigateToNoteByTitle(targetTitle)
                    },
                    onOpenSubPage = { subPageBlock ->
                        navigateToSubPage(subPageBlock)
                    },
                    audioRecorder = audioRecorder
                )
            }
        }
    }

    // Sub-Page Rename Dialog (Atomic Popup Edit)
    if (renamingSubPageBlock != null) {
        val targetBlock = renamingSubPageBlock!!
        AlertDialog(
            onDismissRequest = { renamingSubPageBlock = null },
            title = {
                Text(
                    text = stringResource(R.string.rename_subpage_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = renameSubPageInput,
                    onValueChange = { renameSubPageInput = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.rename_subpage_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTitle = renameSubPageInput.trim().ifBlank { context.getString(R.string.new_subpage_default) }
                        recordUndoState()
                        val idx = blocks.indexOfFirst { it.id == targetBlock.id }
                        if (idx != -1) {
                            blocks[idx] = targetBlock.copy(content = newTitle)
                            lastParentSubPageEditTime = System.currentTimeMillis()
                            val childId = targetBlock.subPageNoteId
                            if (childId != null) {
                                val childNote = allNotesForLinking.find { it.id == childId }
                                if (childNote != null && childNote.title != newTitle) {
                                    val updatedChild = childNote.copy(title = newTitle).also { it.id = childNote.id }
                                    noteViewModel.saveNote(updatedChild, contentForLinks = null)
                                }
                            }
                            performSilentAutoSave()
                            context.showToastShort(context.getString(R.string.rename_subpage_updated_toast))
                        }
                        renamingSubPageBlock = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VibrantOrange,
                        contentColor = PureWhite
                    )
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingSubPageBlock = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Emoji Picker Sheet
    if (showEmojiSheet) {
        EmojiPickerSheet(
            onDismiss = { showEmojiSheet = false },
            onSelectEmoji = { emoji -> selectedIcon = emoji }
        )
    }

    // Slash Command Sheet
    if (showSlashSheet) {
        NotionSlashDialog(
            onDismiss = { showSlashSheet = false },
            onSelectBlock = { type ->
                if (type == BlockType.DRAWING) {
                    recordUndoState()
                    editingDrawingPath = null
                    editingDrawingBlockIndex = null
                    showDrawingSheet = true
                } else if (type == BlockType.SUB_PAGE) {
                    addSubPageBlock()
                } else {
                    recordUndoState()
                    blocks.add(NoteBlock(type = type, content = ""))
                }
            },
            onOpenTemplateHub = {
                showTemplateSheet = true
            }
        )
    }

    // Template Hub BottomSheet
    if (showTemplateSheet) {
        TemplateHubSheet(
            onDismiss = { showTemplateSheet = false },
            onSelectTemplate = { selectedTemplate ->
                showTemplateSheet = false
                recordUndoState()
                val templateBlocks = selectedTemplate.buildBlocks(context)
                blocks.clear()
                blocks.addAll(templateBlocks)
                if (title.isBlank()) {
                    title = selectedTemplate.getDefaultTitle(context)
                }
                if (selectedIcon == null) {
                    selectedIcon = selectedTemplate.icon
                }
                context.showToastShort(context.getString(R.string.template_applied_toast, context.getString(selectedTemplate.titleRes)))
            }
        )
    }

    // Link Note Picker Sheet (Paket 1: Çift Yönlü Bağlantı Seçici)
    if (showLinkNotePicker) {
        val currentSubpageIds = remember(blocks) {
            blocks.filter { it.type == BlockType.SUB_PAGE }.mapNotNull { it.subPageNoteId }.toSet()
        }
        val currentSubpageTitles = remember(blocks) {
            blocks.filter { it.type == BlockType.SUB_PAGE }.map { it.content.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        }
        val currentTitleLower = title.trim().lowercase()
        val outgoingTitles = remember(outgoingWikiLinks) {
            outgoingWikiLinks.map { it.trim().lowercase() }.toSet()
        }
        val filteredLinkNotes = remember(allNotesForLinking, linkSearchQuery, noteId, currentNoteId, currentTitleLower, currentSubpageIds, currentSubpageTitles, outgoingTitles) {
            allNotesForLinking
                .filter { it.id != noteId }
                .filter { currentNoteId == -1 || it.id != currentNoteId }
                .filter { currentTitleLower.isBlank() || (it.title?.trim()?.lowercase() ?: "") != currentTitleLower }
                .filter { (it.title?.trim()?.lowercase() ?: "") !in outgoingTitles }
                .filter { it.status != "TRASH" }
                .filter { it.id !in currentSubpageIds }
                .filter { it.richBlocksJson?.contains("\"isSubPage\":true") != true }
                .filter { it.richBlocksJson?.contains("\"parentNoteId\"") != true }
                .filter { (it.title?.trim()?.lowercase() ?: "") !in currentSubpageTitles }
                .filter { linkSearchQuery.isBlank() || it.title?.contains(linkSearchQuery, ignoreCase = true) == true }
        }

        ModalBottomSheet(
            onDismissRequest = {
                showLinkNotePicker = false
                linkSearchQuery = ""
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_link_hub),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.link_note_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.link_note_subtitle),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    placeholder = { Text(stringResource(R.string.link_search_placeholder), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VibrantOrange,
                        unfocusedBorderColor = BorderLight
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredLinkNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (linkSearchQuery.isBlank()) stringResource(R.string.link_no_notes) else stringResource(R.string.link_no_matching),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredLinkNotes, key = { it.id }) { targetNote ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        val resolvedTargetTitle = (targetNote.title ?: context.getString(R.string.untitled_note)).trim()
                                        val linkText = "[[$resolvedTargetTitle]] "
                                        knownLinkTargets[resolvedTargetTitle.lowercase()] = targetNote.id
                                        recordUndoState()
                                        if (blocks.none { it.type == BlockType.PAGE_RELATIONS }) {
                                            blocks.add(0, NoteBlock(type = BlockType.PAGE_RELATIONS))
                                        }
                                        isRelationsExpanded = true
                                        if (blocks.isNotEmpty() && blocks.last().content.isBlank() && blocks.last().type == BlockType.PARAGRAPH) {
                                            blocks[blocks.lastIndex] = blocks.last().copy(content = linkText)
                                        } else {
                                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = linkText))
                                        }
                                        showLinkNotePicker = false
                                        linkSearchQuery = ""
                                        performSilentAutoSave()
                                        context.showToastShort(context.getString(R.string.link_connected_toast, resolvedTargetTitle))
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ModernNoteIconBadge(
                                        iconKey = targetNote.icon,
                                        isProtected = targetNote.isPassword == true,
                                        size = 28.dp,
                                        iconSize = 15.dp,
                                        shape = RoundedCornerShape(7.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = targetNote.title ?: stringResource(R.string.untitled_note),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (targetNote.totalTasks > 0) stringResource(R.string.tasks_progress, targetNote.completedTasks, targetNote.totalTasks) else stringResource(R.string.note_fallback),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.link_connect_btn),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Full-Screen Read-Only Pinch-to-Zoom Lightbox for Images & Drawings
    lightboxMediaPath?.let { path ->
        MediaLightboxDialog(
            imagePath = path,
            isDrawing = lightboxIsDrawing,
            onDismiss = {
                lightboxMediaPath = null
                lightboxEditAction = null
            },
            onSwitchToEdit = lightboxEditAction
        )
    }

    // Drawing Canvas Sheet (Supports both creating new and editing existing drawings)
    if (showDrawingSheet) {
        DrawingCanvasSheet(
            existingImagePath = editingDrawingPath,
            onDismiss = {
                showDrawingSheet = false
                editingDrawingPath = null
                editingDrawingBlockIndex = null
            },
            onSaveDrawing = { savedPath ->
                val targetIdx = editingDrawingBlockIndex
                if (targetIdx != null && targetIdx in blocks.indices) {
                    blocks[targetIdx] = blocks[targetIdx].copy(mediaPath = savedPath)
                    context.showToastShort(context.getString(R.string.drawing_updated_toast))
                } else {
                    blocks.add(NoteBlock(type = BlockType.DRAWING, mediaPath = savedPath))
                    context.showToastShort(context.getString(R.string.drawing_added_toast))
                }
                showDrawingSheet = false
                editingDrawingPath = null
                editingDrawingBlockIndex = null
            }
        )
    }

    // Modern Notion Status Bottom Sheet
    if (showStatusMenu) {
        val isDarkStatusSheet = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        ModalBottomSheet(
            onDismissRequest = { showStatusMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tpl_target),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.select_status),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { showStatusMenu = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                for (col in KANBAN_COLUMNS) {
                    val isSelected = noteStatus == col.statusKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    isSelected && isDarkStatusSheet -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                    isSelected -> col.badgeBgColor
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkStatusSheet) 0.45f else 0.35f)
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) col.dotColor else if (isDarkStatusSheet) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                noteStatus = col.statusKey
                                showStatusMenu = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(col.dotColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(col.titleRes),
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected && isDarkStatusSheet -> PureWhite
                                    isSelected -> col.badgeTextColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = col.dotColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modern Notion Category Bottom Sheet with Inline "+ Yeni Kategori Ekle"
    if (showCategoryMenu) {
        var newCategoryText by remember { mutableStateOf("") }
        val isDarkCatSheet = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        ModalBottomSheet(
            onDismissRequest = { showCategoryMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.category),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.category_sheet_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { showCategoryMenu = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Inline New Category Input Row (Add without leaving page)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkCatSheet) 0.6f else 0.5f))
                        .border(
                            1.dp,
                            if (isDarkCatSheet) Color.White.copy(alpha = 0.12f) else BorderLight.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = newCategoryText,
                        onValueChange = { newCategoryText = it },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(VibrantOrange),
                        decorationBox = { innerTextField ->
                            if (newCategoryText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.category_sheet_new_hint),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            val trimmed = newCategoryText.trim()
                            if (trimmed.isNotEmpty()) {
                                val newCat = CategoryData(name = trimmed, isPassword = false)
                                categoryViewModel.upsert(newCat)
                                selectedCategoryName = trimmed
                                val existing = categoryList.find { it.name.equals(trimmed, ignoreCase = true) }
                                if (existing != null) {
                                    selectedCategoryId = existing.id
                                }
                                newCategoryText = ""
                                context.showToastShort(context.getString(R.string.category_sheet_added_toast, trimmed))
                                showCategoryMenu = false
                            } else {
                                context.showToastShort(context.getString(R.string.please_enter_category))
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibrantOrange,
                            contentColor = PureWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        enabled = newCategoryText.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(R.string.cat_add_btn_action),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.category_sheet_existing),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Existing Categories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    items(categoryList) { category ->
                        val isSelected = selectedCategoryId == category.id || selectedCategoryName == category.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isSelected && isDarkCatSheet -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                        isSelected -> Color(0xFFFFF8F2)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDarkCatSheet) 0.45f else 0.35f)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) VibrantOrange else if (isDarkCatSheet) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedCategoryId = category.id
                                    selectedCategoryName = category.name
                                    showCategoryMenu = false
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outline)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = category.name,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected && isDarkCatSheet -> PureWhite
                                        isSelected -> CharcoalGray
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = VibrantOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Set PIN dialog if not set yet
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                pinInput = ""
            },
            title = {
                Text(text = stringResource(R.string.cat_set_lock_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = stringResource(R.string.cat_lock_prompt_no_pwd))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text(stringResource(R.string.pwd_placeholder_pin)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length == 6) {
                            SecurityUtil.savePassword(context, pinInput)
                            isPassword = true
                            showPasswordDialog = false
                            pinInput = ""
                            context.showToastShort(context.getString(R.string.cat_pwd_saved_and_locked_toast))
                        } else {
                            context.showToastShort(context.getString(R.string.pin_must_be_6_digits))
                        }
                    }
                ) {
                    Text(stringResource(R.string.save), color = VibrantOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; pinInput = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Reminder & Alarm BottomSheet
    if (showReminderSheet) {
        ReminderBottomSheet(
            initialTimeMs = reminderTime,
            initialType = reminderType,
            initialSound = reminderSound,
            onSaveReminder = { timeMs, type, sound ->
                reminderTime = timeMs
                reminderType = type
                reminderSound = sound
                isReminderDone = false
                showReminderSheet = false
                context.showToastShort(context.getString(R.string.reminder_set_toast, SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(timeMs)))
            },
            onRemoveReminder = {
                reminderTime = null
                isReminderDone = false
                showReminderSheet = false
                if (noteId != -1) {
                    ReminderManager.cancelReminder(context, noteId)
                }
                context.showToastShort(context.getString(R.string.reminder_removed_toast))
            },
            onDismiss = {
                showReminderSheet = false
            }
        )
    }

    // Export & Share Sheet (Gelişmiş Filtreli & Canlı Önizlemeli PDF Stüdyosu)
    if (showExportSheet) {
        val currentTitle = if (title.isBlank()) stringResource(R.string.untitled_note) else title
        PdfExportStudioSheet(
            title = currentTitle,
            categoryName = selectedCategoryName,
            blocks = blocks,
            onDismiss = { showExportSheet = false },
            onExportPdf = { config ->
                showExportSheet = false
                NoteExporter.shareAsPdf(
                    context = context,
                    title = currentTitle,
                    blocks = blocks,
                    categoryName = selectedCategoryName,
                    config = config
                )
            },
            onExportMarkdown = {
                showExportSheet = false
                NoteExporter.shareAsMarkdown(context, currentTitle, blocks)
            }
        )
    }

    // OCR Source Selector Sheet (Kamera veya Galeri)
    if (showOcrSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOcrSourceSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_ocr_scan),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.ocr_scan_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ocr_scan_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { showOcrSourceSheet = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: Kamerayı Aç & Çek
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showOcrSourceSheet = false
                            checkCameraAndLaunch()
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = SoftOrangeLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VibrantOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ocr_scan),
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ocr_camera_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.ocr_camera_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = stringResource(R.string.ocr_camera_snap), color = VibrantOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Galeriden Seç
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showOcrSourceSheet = false
                            ocrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.picture),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ocr_gallery_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.ocr_gallery_desc),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = stringResource(R.string.ocr_gallery_pick), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // OCR Scanning Dialog (Feature 4)
    if (isScanningOcr) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(text = stringResource(R.string.ocr_scanning_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ocr_scan),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = stringResource(R.string.ocr_scanning_desc))
                }
            },
            confirmButton = {}
        )
    }

    // Note Statistics Detailed Dialog (Feature 4)
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_stream_timeline),
                    contentDescription = null,
                    tint = VibrantOrange,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(text = stringResource(R.string.note_stats_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "• " + stringResource(R.string.note_stat_words, wordCount), fontSize = 13.sp)
                    Text(text = "• " + stringResource(R.string.note_stat_chars, charCount), fontSize = 13.sp)
                    Text(text = "• " + stringResource(R.string.note_stat_reading, readingTimeMinutes), fontSize = 13.sp)
                    Text(text = "• " + stringResource(R.string.note_stat_blocks, blocks.size), fontSize = 13.sp)
                    val totalTasks = blocks.count { it.type == BlockType.TODO }
                    if (totalTasks > 0) {
                        val completedTasks = blocks.count { it.type == BlockType.TODO && it.isChecked }
                        Text(text = "• " + stringResource(R.string.note_stat_tasks, completedTasks, totalTasks), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatsDialog = false }) {
                    Text(stringResource(R.string.close), fontWeight = FontWeight.Bold, color = VibrantOrange)
                }
            }
        )
    }

    // Move to Trash Confirmation Dialog (Feature 2)
    if (showTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showTrashConfirmDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.delete),
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(text = stringResource(R.string.note_trash_confirm_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = stringResource(R.string.note_trash_confirm_desc),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrashConfirmDialog = false
                        noteStatus = "TRASH"
                        saveNote()
                        context.showToastShort(context.getString(R.string.note_moved_to_trash_toast))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = PureWhite
                    )
                ) {
                    Text(stringResource(R.string.drawer_trash_bin), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    }

    // Gatekeeper PIN fallback dialog (Bitwarden Security)
    if (showGatekeeperPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showGatekeeperPinDialog = false
                gatekeeperPinInput = ""
                gatekeeperSuccessAction = null
            },
            title = {
                Text(text = stringResource(R.string.enter_pin), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.enter_pin_desc),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = gatekeeperPinInput,
                        onValueChange = { if (it.length <= 10) gatekeeperPinInput = it },
                        label = { Text(stringResource(R.string.enter_pin)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (SecurityUtil.verifyPassword(context, gatekeeperPinInput)) {
                            SecurityUtil.unlockVault()
                            isAccessGranted = true
                            showGatekeeperPinDialog = false
                            val pendingAction = gatekeeperSuccessAction
                            gatekeeperSuccessAction = null
                            gatekeeperPinInput = ""
                            pendingAction?.invoke() ?: run {
                                loadedNoteRef?.let { populateNoteBlocks(it) }
                            }
                        } else {
                            context.showToastShort(context.getString(R.string.wrong_pin))
                            gatekeeperPinInput = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold, color = VibrantOrange)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGatekeeperPinDialog = false
                        gatekeeperPinInput = ""
                        gatekeeperSuccessAction = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    }
}
