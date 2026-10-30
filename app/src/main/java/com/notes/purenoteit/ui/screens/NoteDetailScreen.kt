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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import com.notes.purenoteit.ui.components.DrawingCanvasSheet
import com.notes.purenoteit.ui.components.EmojiPickerSheet
import com.notes.purenoteit.ui.components.KANBAN_COLUMNS
import com.notes.purenoteit.ui.components.NotionBlockRow
import com.notes.purenoteit.ui.components.NotionSlashDialog
import com.notes.purenoteit.ui.components.NotionToolbar
import com.notes.purenoteit.ui.components.SpotifyAudioVisualizer
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AudioRecorder
import com.notes.purenoteit.util.SecurityUtil
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CategoryViewModel
import com.notes.purenoteit.viewmodel.NoteViewModel
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Int,
    initialDate: Long = -1L,
    noteViewModel: NoteViewModel,
    categoryViewModel: CategoryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorder(context) }

    var title by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<String?>(null) }
    var isPassword by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf(1) }
    var selectedCategoryName by remember { mutableStateOf("Not") }
    var noteStatus by remember { mutableStateOf("TODO") }
    var noteDate by remember { mutableStateOf(if (initialDate > 0) initialDate else System.currentTimeMillis()) }

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
    var editingDrawingPath by remember { mutableStateOf<String?>(null) }
    var editingDrawingBlockIndex by remember { mutableStateOf<Int?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    val categoryList by categoryViewModel.categoryList.observeAsState(emptyList())

    // Load existing note data
    LaunchedEffect(noteId) {
        if (noteId != -1) {
            noteViewModel.getNoteById(noteId).subscribe { note ->
                title = note.title ?: ""
                selectedIcon = note.icon
                isPassword = note.isPassword ?: false
                isPinned = note.isPinned
                selectedCategoryId = note.categoryId ?: 1
                noteStatus = note.status ?: "TODO"
                noteDate = note.date

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
            }
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
            status = noteStatus
        )

        if (noteId != -1) {
            note.id = noteId
        }
        noteViewModel.upsert(note)
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
                    // Notion Status Selection Pill
                    val currentStatusDef = KANBAN_COLUMNS.find { it.statusKey == noteStatus } ?: KANBAN_COLUMNS[0]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(currentStatusDef.badgeBgColor)
                            .clickable { showStatusMenu = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentStatusDef.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentStatusDef.badgeTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Category Selection Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftOrangeLight)
                            .clickable { showCategoryMenu = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedCategoryName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

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
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = if (isPassword) R.drawable.lock else R.drawable.unlock),
                            contentDescription = "Kilit",
                            tint = if (isPassword) VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Save Button
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalGray)
                            .clickable { saveNote() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kaydet",
                            color = PureWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
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

                NotionToolbar(
                    onAddBlock = { type ->
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
                    isRecordingAudio = isRecordingAudio
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
                    onDeleteBlock = {
                        if (blocks.size > 1) {
                            blocks.removeAt(index)
                        } else {
                            blocks[0] = NoteBlock()
                        }
                    },
                    onMoveUp = if (index > 0) {
                        {
                            val item = blocks.removeAt(index)
                            blocks.add(index - 1, item)
                        }
                    } else null,
                    onMoveDown = if (index < blocks.lastIndex) {
                        {
                            val item = blocks.removeAt(index)
                            blocks.add(index + 1, item)
                        }
                    } else null,
                    onEditDrawing = { existingPath ->
                        editingDrawingPath = existingPath
                        editingDrawingBlockIndex = index
                        showDrawingSheet = true
                    },
                    audioRecorder = audioRecorder
                )
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
}
