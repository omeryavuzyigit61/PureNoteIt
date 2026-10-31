package com.notes.purenoteit.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import android.os.Build
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.reminder.ReminderManager
import com.notes.purenoteit.ui.components.DrawingCanvasSheet
import com.notes.purenoteit.ui.components.EmojiPickerSheet
import com.notes.purenoteit.ui.components.KANBAN_COLUMNS
import com.notes.purenoteit.ui.components.NotionBlockRow
import com.notes.purenoteit.ui.components.NotionSlashDialog
import com.notes.purenoteit.ui.components.NotionToolbar
import com.notes.purenoteit.ui.components.ReminderBottomSheet
import com.notes.purenoteit.ui.components.SpotifyAudioVisualizer
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Int,
    initialDate: Long = -1L,
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }
    val voiceHelper = remember { VoiceTranscriptionHelper(context) }
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

    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var isPassword by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(1) }
    var selectedCategoryName by remember { mutableStateOf("Not") }
    var noteStatus by remember { mutableStateOf("TODO") }
    var noteDate by remember { mutableStateOf(if (initialDate > 0) initialDate else System.currentTimeMillis()) }

    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var reminderType by remember { mutableStateOf("ALARM") }
    var reminderSound by remember { mutableStateOf("default") }
    var isReminderDone by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            context.showToastShort("Hatırlatıcılar için bildirim izni gereklidir")
        }
    }

    val blocks = remember { mutableStateListOf<NoteBlock>() }
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
    var showDrawingSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showOcrSourceSheet by remember { mutableStateOf(false) }
    var isScanningOcr by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editingDrawingPath by remember { mutableStateOf<String?>(null) }
    var editingDrawingBlockIndex by remember { mutableStateOf<Int?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showLinkNotePicker by remember { mutableStateOf(false) }
    var linkSearchQuery by remember { mutableStateOf("") }
    val allNotesForLinking by noteViewModel.noteList.observeAsState(emptyList())

    // Smart Link Suggestion (Feature 3)
    val suggestedLinkTitle = remember(blocks.map { it.content }, allNotesForLinking, noteId) {
        val allText = blocks.joinToString(" ") { it.content + " " + it.toggleBody }
        val existingLinks = Regex("""\[\[(.*?)\]\]""").findAll(allText).map { it.groupValues[1].trim().lowercase() }.toSet()
        val subpageTitles = blocks.filter { it.type == BlockType.SUB_PAGE }.map { it.content.trim().lowercase() }.toSet()

        allNotesForLinking.firstOrNull { noteCandidate ->
            val candidateTitle = noteCandidate.title?.trim() ?: ""
            candidateTitle.length >= 3 &&
            noteCandidate.id != noteId &&
            !existingLinks.contains(candidateTitle.lowercase()) &&
            !subpageTitles.contains(candidateTitle.lowercase()) &&
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
            context.showToastShort("Geri alındı")
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(blocks.map { it.copy() })
            blocks.clear()
            blocks.addAll(nextState)
            context.showToastShort("İleri alındı")
        }
    }

    fun navigateToNoteByTitle(targetTitle: String) {
        val trimmed = if (targetTitle.isBlank()) "Yeni Alt Sayfa" else targetTitle.trim()
        val found = allNotesForLinking.find { it.title?.trim().equals(trimmed, ignoreCase = true) }
        if (found != null) {
            onNavigateToNote?.invoke(found.id)
        } else {
            val newNote = NoteData(
                title = trimmed,
                description = BlockParser.serialize(listOf(NoteBlock(content = ""))),
                date = System.currentTimeMillis(),
                categoryId = selectedCategoryId
            )
            noteViewModel.saveNote(newNote, contentForLinks = null) { newId ->
                context.showToastShort("\"$trimmed\" oluşturuldu ve açıldı")
                onNavigateToNote?.invoke(newId)
            }
        }
    }

    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())

    // Load existing note data
    LaunchedEffect(noteId) {
        if (noteId != -1) {
            noteViewModel.getNoteById(noteId).subscribe({ note ->
                title = note.title ?: ""
                selectedIcon = note.icon
                isPassword = note.isPassword ?: false
                isPinned = note.isPinned
                selectedCategoryId = note.categoryId ?: 1
                noteStatus = note.status ?: "TODO"
                noteDate = note.date
                reminderTime = note.reminderTime
                reminderType = note.reminderType ?: "ALARM"
                reminderSound = note.reminderSound ?: "default"
                isReminderDone = note.isReminderDone

                val rawDesc = if (note.isEncrypted) {
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
            }, { error ->
                android.util.Log.e("NoteDetailScreen", "Error loading note $noteId: ${error.message}")
            })
        } else {
            blocks.clear()
            blocks.add(NoteBlock())
        }
    }

    LaunchedEffect(Unit) {
        categoryViewModel.getAllCategories()
    }

    // Category name resolver with safe fallback
    LaunchedEffect(categoryList, selectedCategoryId) {
        if (categoryList.isNotEmpty()) {
            val cat = categoryList.find { it.id == selectedCategoryId }
            if (cat != null) {
                selectedCategoryName = cat.name
            } else {
                selectedCategoryId = categoryList.first().id
                selectedCategoryName = categoryList.first().name
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
                context.showToastShort("Görsel yüklenemedi")
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
                        context.showToastShort("Yapay zeka metni çıkardı (${validLines.size} satır)!")
                    } else {
                        context.showToastShort("Görselde okunabilir metin bulunamadı")
                    }
                },
                onError = { e ->
                    isScanningOcr = false
                    context.showToastShort("Metin tarama hatası: ${e.message}")
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
                        context.showToastShort("Yapay zeka metni çıkardı (${validLines.size} satır)!")
                    } else {
                        context.showToastShort("Görselde okunabilir metin bulunamadı")
                    }
                },
                onError = { e ->
                    isScanningOcr = false
                    context.showToastShort("Metin tarama hatası: ${e.message}")
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
            context.showToastShort("Kamera başlatılamadı: ${e.message}")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraOcr()
        } else {
            context.showToastShort("Kamera ile metin taramak için kamera izni gereklidir")
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
            context.showToastShort("Ses kaydı için mikrofon izni gereklidir")
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
                context.showToastShort("Ses kaydı başladı")
            }
        } else {
            val path = audioRecorder.stopRecording()
            isRecordingAudio = false
            if (path != null) {
                blocks.add(NoteBlock(type = BlockType.AUDIO, mediaPath = path))
                context.showToastShort("Ses kaydı eklendi")
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
                    context.showToastShort("Sesli dikte eklendi")
                }
                voiceHelper.stopListening()
                isVoiceDictating = false
                liveVoiceText = ""
            }
        }
    }

    fun saveNote() {
        val jsonContent = BlockParser.serialize(blocks)

        // Calculate tasks count
        val total = blocks.count { it.type == BlockType.TODO }
        val completed = blocks.count { it.type == BlockType.TODO && it.isChecked }

        // Bitwarden AES-256 GCM encryption
        val finalContent = if (isPassword) SecurityUtil.encrypt(jsonContent) else jsonContent
        val isEncrypted = isPassword

        val images = blocks.filter { it.type == BlockType.IMAGE && it.mediaPath != null }.map { it.mediaPath!! }
        val sounds = blocks.filter { it.type == BlockType.AUDIO && it.mediaPath != null }.map { it.mediaPath!! }

        val validCategoryId = if (categoryList.any { it.id == selectedCategoryId }) {
            selectedCategoryId
        } else if (categoryList.isNotEmpty()) {
            categoryList.first().id
        } else {
            1
        }

        val note = NoteData(
            title = if (title.isBlank()) "Başlıksız Not" else title.trim(),
            description = finalContent,
            date = if (noteDate > 0) noteDate else System.currentTimeMillis(),
            images = images,
            isPassword = isPassword,
            categoryId = validCategoryId,
            sounds = sounds,
            isPinned = isPinned,
            icon = selectedIcon,
            isEncrypted = isEncrypted,
            totalTasks = total,
            completedTasks = completed,
            status = noteStatus,
            reminderTime = reminderTime,
            reminderType = reminderType,
            reminderSound = reminderSound,
            isReminderDone = isReminderDone
        )

        if (noteId != -1) {
            note.id = noteId
        }

        // Save note and safely sync bi-directional [[Note]] & Sub-Page links with real SQLite ID (Paket 1)
        val allContentText = title + " " + blocks.joinToString(" ") { block ->
            if (block.type == BlockType.SUB_PAGE && block.content.isNotBlank()) {
                "[[${block.content.trim()}]]"
            } else {
                block.content + " " + block.toggleBody
            }
        }
        noteViewModel.saveNote(note, contentForLinks = allContentText) { actualId ->
            if (reminderTime != null && reminderTime!! > System.currentTimeMillis() && !isReminderDone) {
                val noteDescPreview = blocks.firstOrNull { it.content.isNotBlank() }?.content ?: ""
                ReminderManager.scheduleReminder(
                    context = context,
                    noteId = actualId,
                    noteTitle = note.title ?: "Başlıksız Not",
                    noteDesc = noteDescPreview,
                    reminderTimeMs = reminderTime!!,
                    reminderType = reminderType,
                    soundType = reminderSound
                )
            } else if (reminderTime == null && actualId != 0) {
                ReminderManager.cancelReminder(context, actualId)
            }
        }

        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Geri",
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
                            contentDescription = "Geri Al",
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
                            contentDescription = "İleri Al",
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Export / Share Action Button (Feature 1)
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share_square),
                            contentDescription = "Dışa Aktar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Bitwarden Lock Action Button
                    IconButton(
                        onClick = {
                            if (!isPassword) {
                                if (SecurityUtil.isPasswordSet(context)) {
                                    isPassword = true
                                    context.showToastShort("Not AES-256 ile şifrelendi")
                                } else {
                                    showPasswordDialog = true
                                }
                            } else {
                                isPassword = false
                                context.showToastShort("Kilit kaldırıldı")
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = if (isPassword) R.drawable.lock else R.drawable.unlock),
                            contentDescription = "Kilit",
                            tint = if (isPassword) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Save Button (Clean, single-line, never wraps or squishes)
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { saveNote() },
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalGray
                    ) {
                        Text(
                            text = "Kaydet",
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
                                        text = "Ses Kaydediliyor...",
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
                                        context.showToastShort("Ses kaydı iptal edildi")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.cancel),
                                        contentDescription = "İptal",
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
                                        text = "Bitir & Ekle",
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
                                Text(text = "💡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "\"$suggestedLinkTitle\" notuna referans tespit edildi",
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
                                            context.showToastShort("\"$titleToLink\" bağlandı!")
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = VibrantOrange
                            ) {
                                Text(
                                    text = "+ Bağla",
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
                                        text = "Canlı Sesli Dikte (Türkçe)...",
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
                                        Text("İptal", color = Color(0xFF757575), fontSize = 12.sp)
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (liveVoiceText.isNotBlank()) {
                                                    recordUndoState()
                                                    blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = liveVoiceText.trim()))
                                                    context.showToastShort("Metin eklendi")
                                                }
                                                voiceHelper.stopListening()
                                                isVoiceDictating = false
                                                liveVoiceText = ""
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF8E24AA)
                                    ) {
                                        Text(
                                            text = "Not'a Ekle",
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
                                text = if (liveVoiceText.isBlank()) "Konuşun, söyledikleriniz anında yazıya dönüşsün..." else liveVoiceText,
                                fontSize = 13.sp,
                                color = if (liveVoiceText.isBlank()) Color(0xFF9E9E9E) else Color(0xFF212121),
                                lineHeight = 18.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // VIP Quick Access Action Dock (Sol Alt ve Sağ Alt Hızlı Erişim)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sol Alt: Hızlı Geri Al / İleri Al
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = undoStack.isNotEmpty()) { handleUndo() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (undoStack.isNotEmpty()) 0.75f else 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_undo),
                                    contentDescription = "Geri Al",
                                    tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = redoStack.isNotEmpty()) { handleRedo() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (redoStack.isNotEmpty()) 0.75f else 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_redo),
                                    contentDescription = "İleri Al",
                                    tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    // Sağ Alt: VIP Butonlar [📷 Tara] [📤 Paylaş] [🎙️ Sesli Dikte]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hızlı Metin Tara (OCR)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showOcrSourceSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = SoftOrangeLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_ocr_scan),
                                    contentDescription = "Metin Tara",
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tara",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }
                        }

                        // Hızlı Paylaş / Dışa Aktar
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showExportSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.share_square),
                                    contentDescription = "Paylaş",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Paylaş",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // VIP Hızlı Sesli Dikte (Pulsing Purple Glow)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { handleToggleVoiceDictation() },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isVoiceDictating) Color(0xFF8E24AA) else Color(0xFFF3E5F5),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isVoiceDictating) Color(0xFFBA68C8) else Color(0xFFCE93D8)
                            ),
                            shadowElevation = if (isVoiceDictating) 6.dp else 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.microphone_lines),
                                    contentDescription = "Sesli Dikte",
                                    tint = if (isVoiceDictating) PureWhite else Color(0xFF6A1B9A),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isVoiceDictating) "Dinleniyor..." else "Sesli Dikte",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isVoiceDictating) PureWhite else Color(0xFF6A1B9A)
                                )
                            }
                        }
                    }
                }

                NotionToolbar(
                    onAddBlock = { type ->
                        recordUndoState()
                        blocks.add(NoteBlock(type = type, content = ""))
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
                    isRecordingAudio = isRecordingAudio,
                    isVoiceDictating = isVoiceDictating
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
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
                    // Page Emoji Icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SoftOrangeLight)
                            .clickable { showEmojiSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedIcon != null) {
                            Text(text = selectedIcon!!, fontSize = 24.sp)
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.apps_add),
                                contentDescription = "İkon Seç",
                                tint = VibrantOrange,
                                modifier = Modifier.size(20.dp)
                            )
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
                                    text = "Not Başlığı...",
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

                // Notion Mobile Page Properties Row (Durum, Kategori, Hatırlatıcı / Alarm)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status Chip
                    val currentStatusDef = KANBAN_COLUMNS.find { it.statusKey == noteStatus } ?: KANBAN_COLUMNS[0]
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showStatusMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = currentStatusDef.badgeBgColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(currentStatusDef.dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentStatusDef.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentStatusDef.badgeTextColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "▼",
                                fontSize = 8.sp,
                                color = currentStatusDef.badgeTextColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Category Chip
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showCategoryMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = SoftOrangeLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🏷️", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = selectedCategoryName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "▼",
                                fontSize = 8.sp,
                                color = VibrantOrange.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Reminder / Alarm Chip
                    val hasActiveReminder = reminderTime != null && reminderTime!! > System.currentTimeMillis() && !isReminderDone
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
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
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasActiveReminder) SoftOrangeLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (hasActiveReminder) androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_alarm_clock),
                                contentDescription = "Hatırlatıcı",
                                tint = if (hasActiveReminder) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hasActiveReminder) SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(reminderTime) else "Hatırlatıcı Ekle",
                                fontSize = 11.5.sp,
                                fontWeight = if (hasActiveReminder) FontWeight.Bold else FontWeight.Medium,
                                color = if (hasActiveReminder) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Dynamic Notion Blocks
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                NotionBlockRow(
                    block = block,
                    onContentChange = { newText ->
                        blocks[index] = block.copy(content = newText)
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
                    onDeleteBlock = {
                        recordUndoState()
                        if (blocks.size > 1) {
                            blocks.removeAt(index)
                        } else {
                            blocks[0] = NoteBlock()
                        }
                    },
                    onMoveUp = if (index > 0) {
                        {
                            recordUndoState()
                            val item = blocks.removeAt(index)
                            blocks.add(index - 1, item)
                        }
                    } else null,
                    onMoveDown = if (index < blocks.lastIndex) {
                        {
                            recordUndoState()
                            val item = blocks.removeAt(index)
                            blocks.add(index + 1, item)
                        }
                    } else null,
                    onEditDrawing = { existingPath ->
                        editingDrawingPath = existingPath
                        editingDrawingBlockIndex = index
                        showDrawingSheet = true
                    },
                    onOpenLinkedNote = { targetTitle ->
                        navigateToNoteByTitle(targetTitle)
                    },
                    onOpenSubPage = { pageTitle ->
                        navigateToNoteByTitle(pageTitle)
                    },
                    audioRecorder = audioRecorder
                )
            }

            // Backlinks Section (Bu Nota Referans Veren Diğer Notlar - Paket 1)
            if (noteId != -1 && backlinks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔗", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bu Nota Bağlantılar (${backlinks.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (bNote in backlinks) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onNavigateToNote?.invoke(bNote.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, VibrantOrange.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = bNote.icon ?: "📝", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = bNote.title ?: "Başlıksız Not",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
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
                recordUndoState()
                if (type == BlockType.DRAWING) {
                    editingDrawingPath = null
                    editingDrawingBlockIndex = null
                    showDrawingSheet = true
                } else {
                    blocks.add(NoteBlock(type = type, content = ""))
                }
            }
        )
    }

    // Link Note Picker Sheet (Paket 1: Çift Yönlü Bağlantı Seçici)
    if (showLinkNotePicker) {
        val filteredLinkNotes = remember(allNotesForLinking, linkSearchQuery, noteId) {
            allNotesForLinking
                .filter { it.id != noteId }
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
                    Text(text = "🔗", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Not Bağla (Bi-directional Link)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Seçilen not, metne [[Başlık]] formatında eklenecek ve çift yönlü bağlanacaktır.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    placeholder = { Text("Bağlanacak notu arayın...", fontSize = 13.sp) },
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
                            text = if (linkSearchQuery.isBlank()) "Bağlanabilecek başka not bulunamadı." else "Eşleşen not bulunamadı.",
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
                                        val linkText = "[[${targetNote.title ?: "Başlıksız Not"}]] "
                                        recordUndoState()
                                        if (blocks.isNotEmpty() && blocks.last().content.isBlank() && blocks.last().type == BlockType.PARAGRAPH) {
                                            blocks[blocks.lastIndex] = blocks.last().copy(content = linkText)
                                        } else {
                                            blocks.add(NoteBlock(type = BlockType.PARAGRAPH, content = linkText))
                                        }
                                        showLinkNotePicker = false
                                        linkSearchQuery = ""
                                        context.showToastShort("\"${targetNote.title}\" nota bağlandı")
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
                                    Text(text = targetNote.icon ?: "📝", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = targetNote.title ?: "Başlıksız Not",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (targetNote.totalTasks > 0) "${targetNote.completedTasks}/${targetNote.totalTasks} Görev" else "Not",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "+ Bağla",
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
                    context.showToastShort("Çizim güncellendi")
                } else {
                    blocks.add(NoteBlock(type = BlockType.DRAWING, mediaPath = savedPath))
                    context.showToastShort("Çizim nota eklendi")
                }
                showDrawingSheet = false
                editingDrawingPath = null
                editingDrawingBlockIndex = null
            }
        )
    }

    // Modern Notion Status Bottom Sheet
    if (showStatusMenu) {
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
                        Text(text = "🎯", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Not Durumu Seçin",
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
                            contentDescription = "Kapat",
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
                                if (isSelected) col.badgeBgColor
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) col.dotColor else Color.Transparent,
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
                                text = col.title,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) col.badgeTextColor else MaterialTheme.colorScheme.onSurface
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
                        Text(text = "🏷️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kategori Seçin veya Ekleyin",
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
                            contentDescription = "Kapat",
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
                                    text = "+ Yeni Kategori Adı...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                                context.showToastShort("'$trimmed' kategorisi eklendi ve seçildi")
                                showCategoryMenu = false
                            } else {
                                context.showToastShort("Lütfen bir kategori adı yazın")
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
                            text = "Ekle",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Mevcut Kategoriler",
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
                                    if (isSelected) SoftOrangeLight
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) VibrantOrange else Color.Transparent,
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
                                    color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.onSurface
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
                Text(text = "Yeni PIN Belirleyin", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = "Korumalı notlarınızı şifrelemek için 6 haneli bir PIN giriniz:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text("6 Haneli PIN") },
                        singleLine = true,
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
                            context.showToastShort("PIN kaydedildi ve not kilitlendi")
                        } else {
                            context.showToastShort("Lütfen 6 haneli bir PIN giriniz")
                        }
                    }
                ) {
                    Text("Kaydet", color = VibrantOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; pinInput = "" }) {
                    Text("İptal")
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
                context.showToastShort("Hatırlatıcı ayarlandı: " + SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(timeMs))
            },
            onRemoveReminder = {
                reminderTime = null
                isReminderDone = false
                showReminderSheet = false
                if (noteId != -1) {
                    ReminderManager.cancelReminder(context, noteId)
                }
                context.showToastShort("Hatırlatıcı kaldırıldı")
            },
            onDismiss = {
                showReminderSheet = false
            }
        )
    }

    // Export & Share Sheet (Feature 1: PDF & Markdown)
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
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
                        Text(text = "📤", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Notu Dışa Aktar & Paylaş",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Notunuzu standart formatlarda dışarı aktarın",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = { showExportSheet = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cancel),
                            contentDescription = "Kapat",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Option 1: PDF Export
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showExportSheet = false
                            val currentTitle = if (title.isBlank()) "Başlıksız Not" else title
                            NoteExporter.shareAsPdf(context, currentTitle, blocks)
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
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📄", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDF Olarak Paylaş / Yazdır",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Temiz A4 formatında, tüm bloklar ve başlıklar ile birlikte PDF oluşturur.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option 2: Markdown Export
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            showExportSheet = false
                            val currentTitle = if (title.isBlank()) "Başlıksız Not" else title
                            NoteExporter.shareAsMarkdown(context, currentTitle, blocks)
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
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📝", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Markdown (.md) Olarak Paylaş",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Obsidian, Notion ve GitHub uyumlu standart Markdown belgesi oluşturur.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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
                        Text(text = "📷", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Metin Tara (Yapay Zeka OCR)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Görseldeki yazıları cihaz içi ML Kit ile ayrıştırın",
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
                            contentDescription = "Kapat",
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
                                text = "Kamerayı Aç & Fotoğraf Çek",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kitap, evrak veya tahtayı anında çekip yazıya dönüştürür.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "Çek ↗", color = VibrantOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                text = "Galeriden Görsel Seç",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Daha önce kaydettiğiniz bir fotoğraf veya ekran görüntüsünü tarar.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "Seç ↗", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                Text(text = "Metin Taranıyor...", fontWeight = FontWeight.Bold)
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📷", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Google ML Kit cihazınızda görseldeki metinleri ayrıştırıyor...")
                }
            },
            confirmButton = {}
        )
    }
}
