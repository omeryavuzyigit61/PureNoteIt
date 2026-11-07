package com.notes.purenoteit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.data.db.StreamItemData
import com.notes.purenoteit.data.mapper.toDomain
import com.notes.purenoteit.data.mapper.toEntity
import com.notes.purenoteit.domain.model.AppResult
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.domain.repository.CategoryRepository
import com.notes.purenoteit.domain.repository.KnowledgeRepository
import com.notes.purenoteit.domain.repository.NoteRepository
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.model.BlockType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultBackupPayload(
    val version: Int = 3,
    val backupDate: Long = System.currentTimeMillis(),
    val notes: List<NoteData>,
    val categories: List<CategoryData>,
    val links: List<NoteLinkData>,
    val streamItems: List<StreamItemData>? = emptyList()
)

object VaultBackupManager {

    private val gson = Gson()
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val MAX_ARCHIVE_ENTRIES = 2000
    private const val MAX_SINGLE_FILE_BYTES = 100L * 1024L * 1024L // 100 MB per media file limit

    private inline fun <T> suspendSafeOrNull(block: () -> T): T? {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Remaps absolute media paths (drawings, images, audio recordings) from a previous device
     * or user profile into the current device's [currentFilesDir] so media never breaks after restore.
     */
    private fun normalizeMediaPath(rawPath: String?, currentFilesDir: File): String? {
        if (rawPath.isNullOrBlank()) return null
        val normalized = rawPath.replace("\\", "/")

        val filesMarker = "/files/"
        val markerIdx = normalized.indexOf(filesMarker)
        if (markerIdx != -1) {
            val relSuffix = normalized.substring(markerIdx + filesMarker.length).trimStart('/')
            if (relSuffix.isNotBlank()) {
                val remapped = File(currentFilesDir, relSuffix)
                if (remapped.exists()) return remapped.absolutePath
            }
        }

        if (normalized.startsWith("media/")) {
            val relSuffix = normalized.removePrefix("media/").trimStart('/')
            val remapped = File(currentFilesDir, relSuffix)
            if (remapped.exists()) return remapped.absolutePath
        }

        val directFile = File(rawPath)
        if (directFile.exists()) return directFile.absolutePath

        val fileName = directFile.name
        if (fileName.isNotBlank()) {
            val subDirs = listOf("drawings", "shared_images", "recordings", "")
            for (sub in subDirs) {
                val candidate = if (sub.isEmpty()) File(currentFilesDir, fileName) else File(File(currentFilesDir, sub), fileName)
                if (candidate.exists()) return candidate.absolutePath
            }
        }

        return if (markerIdx != -1) {
            val relSuffix = normalized.substring(markerIdx + filesMarker.length).trimStart('/')
            File(currentFilesDir, relSuffix).absolutePath
        } else {
            rawPath
        }
    }

    /**
     * Creates a compressed, AES-256-GCM (120k PBKDF2) encrypted .purevault archive containing all notes,
     * categories, links, stream items, and AES-256-GCM encrypted media files via Domain Repositories.
     */
    fun createBackup(
        context: Context,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit,
        passphrase: CharArray? = null
    ) {
        val passCopy = passphrase?.copyOf()
        passphrase?.fill('\u0000')

        backupScope.launch {
            try {
                val rawNotes = noteRepository.getAllNotes()
                val categories = categoryRepository.getAllCategories()
                val links = suspendSafeOrNull { knowledgeRepository.getAllLinks() }.orEmpty()
                val streamItems = suspendSafeOrNull { knowledgeRepository.getAllStreamItems() }.orEmpty()
                val lockedCategoryIds = categories.filter { it.isPassword == true }.map { it.id }.toSet()

                val portableNotes = rawNotes.map { note ->
                    val desc = note.description
                    val isNoteOrCategoryLocked = (note.isPassword == true) ||
                        note.isEncrypted ||
                        (note.categoryId != null && note.categoryId in lockedCategoryIds)
                    val portableDesc = when {
                        desc != null && desc.startsWith("ENC_V2:") -> {
                            when (val dec = SecurityUtil.decryptSafe(desc)) {
                                is AppResult.Success -> SecurityUtil.encryptPortable(dec.data, passCopy)
                                is AppResult.Failure -> desc
                            }
                        }
                        isNoteOrCategoryLocked && !desc.isNullOrBlank() && !SecurityUtil.isEncryptedPayload(desc) -> {
                            SecurityUtil.encryptPortable(desc, passCopy)
                        }
                        else -> desc
                    }
                    note.copy(
                        description = portableDesc,
                        isPassword = isNoteOrCategoryLocked,
                        isEncrypted = isNoteOrCategoryLocked
                    ).toEntity()
                }

                val payload = VaultBackupPayload(
                    notes = portableNotes,
                    categories = categories.map { it.toEntity() },
                    links = links.map { it.toEntity() },
                    streamItems = streamItems.map { it.toEntity() }
                )
                val rawJsonString = gson.toJson(payload)
                val encryptedManifest = SecurityUtil.encryptPortable(rawJsonString, passCopy)

                val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(backupDir, "PureNoteIt_Kasa_$timeStamp.purevault")

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // 1. Add AES-256-GCM encrypted manifest
                    val manifestEntry = ZipEntry("vault_manifest.json")
                    zos.putNextEntry(manifestEntry)
                    zos.write(encryptedManifest.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. Add AES-256-GCM encrypted media files ("media_enc/...") so media never leaks in plaintext
                    val filesDir = context.filesDir
                    filesDir.walkTopDown().filter { it.isFile && it.name != zipFile.name }.forEach { file ->
                        if (file.length() <= MAX_SINGLE_FILE_BYTES) {
                            val relativePath = "media_enc/" + file.relativeTo(filesDir).path.replace("\\", "/")
                            val entry = ZipEntry(relativePath)
                            zos.putNextEntry(entry)
                            val rawBytes = file.readBytes()
                            val encBytes = try {
                                SecurityUtil.encryptPortableBytes(rawBytes, passCopy)
                            } finally {
                                rawBytes.fill(0)
                            }
                            zos.write(encBytes)
                            zos.closeEntry()
                        }
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    onSuccess(zipFile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError(e)
                }
            } finally {
                passCopy?.fill('\u0000')
            }
        }
    }

    /**
     * Triggers Android share sheet to export/save the backup file to Google Drive, Telegram, WhatsApp or Downloads.
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            backupFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PureNoteIt Kasa Yedegi")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Yedek Dosyasını Paylaş / Drive'a Kaydet"))
    }

    /**
     * Restores a .purevault / zip backup archive with strict CWE-22 Zip Slip Path Traversal protection,
     * Zip Bomb resource limits, and full backward compatibility for V1/V2/V3 manifests and media.
     */
    fun restoreBackup(
        context: Context,
        backupUri: Uri,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository,
        onSuccess: (restoredNotesCount: Int, restoredCategoriesCount: Int) -> Unit,
        onError: (Exception) -> Unit,
        passphrase: CharArray? = null
    ) {
        val passCopy = passphrase?.copyOf()
        passphrase?.fill('\u0000')

        backupScope.launch {
            try {
                var manifestRawOrEncrypted: String? = null
                val filesDir = context.filesDir.canonicalFile
                var entriesCount = 0

                context.contentResolver.openInputStream(backupUri)?.let { rawStream ->
                    java.io.BufferedInputStream(rawStream).use { bufStream ->
                        ZipInputStream(bufStream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                entriesCount++
                                if (entriesCount > MAX_ARCHIVE_ENTRIES) {
                                    throw SecurityException("Yedek arşivinde aşırı fazla dosya tespit edildi (Zip Bomb koruması)")
                                }

                                if (entry.name == "vault_manifest.json") {
                                    manifestRawOrEncrypted = zis.bufferedReader(Charsets.UTF_8).readText()
                                } else if ((entry.name.startsWith("media_enc/") || entry.name.startsWith("media/")) && !entry.isDirectory) {
                                    val isEncryptedMedia = entry.name.startsWith("media_enc/")
                                    val relPath = if (isEncryptedMedia) {
                                        entry.name.removePrefix("media_enc/")
                                    } else {
                                        entry.name.removePrefix("media/")
                                    }
                                    val targetFile = File(filesDir, relPath).canonicalFile

                                    // CWE-22 Zip Slip Path Traversal Guard
                                    if (!targetFile.path.startsWith(filesDir.path + File.separator)) {
                                        throw SecurityException("Güvenlik İhlali Engellendi: Zip Slip dizin aşımı tespit edildi (${entry.name})")
                                    }

                                    targetFile.parentFile?.mkdirs()
                                    if (isEncryptedMedia) {
                                        val baos = ByteArrayOutputStream()
                                        val buffer = ByteArray(8192)
                                        var totalRead = 0L
                                        var read = zis.read(buffer)
                                        while (read != -1) {
                                            totalRead += read
                                            if (totalRead > MAX_SINGLE_FILE_BYTES + 1024L) {
                                                throw SecurityException("Dosya boyutu güvenlik sınırını aştı")
                                            }
                                            baos.write(buffer, 0, read)
                                            read = zis.read(buffer)
                                        }
                                        val decryptedBytes = SecurityUtil.decryptPortableBytes(baos.toByteArray(), passCopy)
                                        FileOutputStream(targetFile).use { fos ->
                                            fos.write(decryptedBytes)
                                        }
                                        decryptedBytes.fill(0)
                                    } else {
                                        FileOutputStream(targetFile).use { fos ->
                                            val buffer = ByteArray(8192)
                                            var totalWritten = 0L
                                            var read = zis.read(buffer)
                                            while (read != -1) {
                                                totalWritten += read
                                                if (totalWritten > MAX_SINGLE_FILE_BYTES) {
                                                    throw SecurityException("Dosya boyutu güvenlik sınırını aştı")
                                                }
                                                fos.write(buffer, 0, read)
                                                read = zis.read(buffer)
                                            }
                                        }
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                }

                if (manifestRawOrEncrypted.isNullOrBlank()) {
                    throw IllegalStateException("Yedek dosyası geçersiz veya manifest bulunamadı")
                }

                val jsonContent = if (SecurityUtil.isEncryptedPayload(manifestRawOrEncrypted)) {
                    when (val dec = SecurityUtil.decryptSafe(manifestRawOrEncrypted, passCopy)) {
                        is AppResult.Success -> dec.data
                        is AppResult.Failure -> throw SecurityException("Yedek şifresi çözülemedi veya parola hatalı")
                    }
                } else {
                    manifestRawOrEncrypted
                }

                val payload = gson.fromJson(jsonContent, VaultBackupPayload::class.java)

                // 1. Restore categories via Domain CategoryRepository
                payload.categories.forEach { cat ->
                    suspendSafeOrNull { categoryRepository.upsertCategory(cat.toDomain()) }
                }
                var currentCategories = suspendSafeOrNull { categoryRepository.getAllCategories() }.orEmpty()
                if (currentCategories.isEmpty()) {
                    val defaultCat = Category(id = 1, name = "Genel", isPassword = false)
                    suspendSafeOrNull { categoryRepository.upsertCategory(defaultCat) }
                    currentCategories = suspendSafeOrNull { categoryRepository.getAllCategories() } ?: listOf(defaultCat)
                }
                val validCategoryIds = currentCategories.map { it.id }.toSet()
                val fallbackCategoryId = currentCategories.firstOrNull()?.id ?: 1
                val lockedCategoryIds = currentCategories.filter { it.isPassword == true }.map { it.id }.toSet()

                val noteIdsInBackup = payload.notes.map { it.id }.toSet()
                val activeTitleToId = payload.notes
                    .filter { it.status != "TRASH" && !it.title.isNullOrBlank() }
                    .associate { it.title!!.trim().lowercase() to it.id }

                val reconstructedLinks = mutableListOf<NoteLink>()
                val wikiRegex = Regex("""\[\[(.*?)\]\]""")

                // 2. Restore notes (normalizing drawing, image & audio paths, sub-page references, and re-wrapping encryption)
                payload.notes.forEach { note ->
                    if (note.categoryId == null || note.categoryId !in validCategoryIds) {
                        note.categoryId = fallbackCategoryId
                    }

                    val rawDesc = note.description
                    val wasEncrypted = (note.isPassword == true) ||
                        note.isEncrypted ||
                        (note.categoryId in lockedCategoryIds) ||
                        SecurityUtil.isEncryptedPayload(rawDesc)

                    val plainDescResult = if (!rawDesc.isNullOrBlank() && SecurityUtil.isEncryptedPayload(rawDesc)) {
                        SecurityUtil.decryptSafe(rawDesc, passCopy)
                    } else {
                        AppResult.Success(rawDesc ?: "")
                    }

                    if (plainDescResult is AppResult.Success && plainDescResult.data.isNotBlank()) {
                        val plainDesc = plainDescResult.data
                        val blocks = BlockParser.parse(plainDesc).toMutableList()
                        var blocksUpdated = false
                        for (idx in blocks.indices) {
                            var blk = blocks[idx]
                            var blkChanged = false

                            if (!blk.mediaPath.isNullOrBlank()) {
                                val remapped = normalizeMediaPath(blk.mediaPath, filesDir)
                                if (remapped != blk.mediaPath) {
                                    blk = blk.copy(mediaPath = remapped)
                                    blkChanged = true
                                }
                            }

                            if (blk.type == BlockType.SUB_PAGE) {
                                val currentSubId = blk.subPageNoteId
                                val resolvedSubId = if (currentSubId != null && currentSubId in noteIdsInBackup) {
                                    currentSubId
                                } else {
                                    activeTitleToId[blk.content.trim().lowercase()]
                                }
                                if (resolvedSubId != null && resolvedSubId != currentSubId) {
                                    blk = blk.copy(subPageNoteId = resolvedSubId)
                                    blkChanged = true
                                }
                                if (resolvedSubId != null && note.status != "TRASH") {
                                    reconstructedLinks.add(
                                        NoteLink(
                                            sourceNoteId = note.id,
                                            targetNoteId = resolvedSubId,
                                            linkText = blk.content.trim().ifBlank { "Alt Sayfa" }
                                        )
                                    )
                                }
                            }

                            if (note.status != "TRASH") {
                                val textToScan = blk.content + " " + blk.toggleBody
                                if (textToScan.contains("[[")) {
                                    wikiRegex.findAll(textToScan).forEach { match ->
                                        val linkTitle = match.groupValues[1].trim()
                                        val targetId = activeTitleToId[linkTitle.lowercase()]
                                        if (targetId != null && targetId != note.id) {
                                            reconstructedLinks.add(
                                                NoteLink(
                                                    sourceNoteId = note.id,
                                                    targetNoteId = targetId,
                                                    linkText = linkTitle
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            if (blkChanged) {
                                blocks[idx] = blk
                                blocksUpdated = true
                            }
                        }

                        val finalPlain = if (blocksUpdated) BlockParser.serialize(blocks) else plainDesc
                        note.description = if (wasEncrypted) SecurityUtil.encrypt(finalPlain) else finalPlain
                    }

                    note.isPassword = wasEncrypted
                    note.isEncrypted = wasEncrypted
                    note.images = note.images?.mapNotNull { normalizeMediaPath(it, filesDir) }
                    note.sounds = note.sounds?.mapNotNull { normalizeMediaPath(it, filesDir) }

                    suspendSafeOrNull { noteRepository.upsertNote(note.toDomain()) }
                }

                // 3. Restore and verify note links via Domain KnowledgeRepository
                val allExistingNoteIds = suspendSafeOrNull {
                    noteRepository.getAllNotes().map { it.id }.toSet()
                } ?: noteIdsInBackup

                val mergedLinks = (payload.links.map { it.toDomain() } + reconstructedLinks)
                    .filter { it.sourceNoteId in allExistingNoteIds && it.targetNoteId in allExistingNoteIds }
                    .distinctBy { Triple(it.sourceNoteId, it.targetNoteId, it.linkText.trim().lowercase()) }

                if (mergedLinks.isNotEmpty()) {
                    suspendSafeOrNull { knowledgeRepository.insertAllLinks(mergedLinks) }
                }

                // 4. Restore Quick Thought Stream items with normalized media paths
                val streamList = payload.streamItems
                if (!streamList.isNullOrEmpty()) {
                    val normalizedStream = streamList.map { item ->
                        item.copy(
                            audioPath = normalizeMediaPath(item.audioPath, filesDir),
                            imagePath = normalizeMediaPath(item.imagePath, filesDir)
                        ).also { it.id = item.id }.toDomain()
                    }
                    suspendSafeOrNull { knowledgeRepository.insertAllStreamItems(normalizedStream) }
                }

                Handler(Looper.getMainLooper()).post {
                    onSuccess(payload.notes.size, payload.categories.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError(e)
                }
            } finally {
                passCopy?.fill('\u0000')
            }
        }
    }

    // --- Google Drive & Cloud SAF E2EE Auto-Sync Engine ---
    private const val PREF_VAULT_SYNC = "vault_auto_sync_prefs"
    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_TREE_URI = "auto_sync_tree_uri"
    private const val KEY_FOLDER_NAME = "auto_sync_folder_name"
    private const val KEY_LAST_SYNC_TIME = "auto_sync_last_time"
    const val AUTO_BACKUP_FILENAME = "PureNoteIt_AutoVault.purevault"

    private val isAutoSyncRunning = AtomicBoolean(false)
    private val autoSyncDebounceHandler = Handler(Looper.getMainLooper())
    private var pendingAutoSyncRunnable: Runnable? = null
    private const val AUTO_SYNC_DEBOUNCE_MS = 3500L

    fun isAutoSyncEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
    }

    fun getConnectedFolderUri(context: Context): Uri? {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        val uriStr = sp.getString(KEY_TREE_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (_: Exception) {
            null
        }
    }

    fun getConnectedFolderName(context: Context): String? {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        return sp.getString(KEY_FOLDER_NAME, null)
    }

    fun getLastSyncTime(context: Context): Long {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        return sp.getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun saveConnectedFolder(context: Context, uri: Uri, folderName: String) {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .putString(KEY_FOLDER_NAME, folderName)
            .putBoolean(KEY_AUTO_SYNC_ENABLED, true)
            .apply()
    }

    fun disconnectFolder(context: Context) {
        val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
        sp.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_FOLDER_NAME)
            .putBoolean(KEY_AUTO_SYNC_ENABLED, false)
            .apply()
    }

    fun getAutoBackupUri(context: Context): Uri? {
        val treeUri = getConnectedFolderUri(context) ?: return null
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            rootDoc.findFile(AUTO_BACKUP_FILENAME)?.uri
        } catch (_: Exception) {
            null
        }
    }

    fun saveBackupToTreeUri(context: Context, backupFile: File, treeUri: Uri): Boolean {
        return try {
            val folderDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!folderDoc.canWrite()) return false

            var targetFile = folderDoc.findFile(AUTO_BACKUP_FILENAME)
            if (targetFile == null) {
                targetFile = folderDoc.createFile("application/octet-stream", AUTO_BACKUP_FILENAME)
            }
            val targetUri = targetFile?.uri ?: return false

            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                FileInputStream(backupFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return false

            true
        } catch (_: Exception) {
            false
        }
    }

    fun scheduleDebouncedAutoSync(
        context: Context,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository
    ) {
        if (!isAutoSyncEnabled(context) || getConnectedFolderUri(context) == null) return
        pendingAutoSyncRunnable?.let { autoSyncDebounceHandler.removeCallbacks(it) }
        val runnable = Runnable {
            performAutoSync(
                context = context,
                noteRepository = noteRepository,
                categoryRepository = categoryRepository,
                knowledgeRepository = knowledgeRepository
            )
        }
        pendingAutoSyncRunnable = runnable
        autoSyncDebounceHandler.postDelayed(runnable, AUTO_SYNC_DEBOUNCE_MS)
    }

    fun performAutoSync(
        context: Context,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val treeUri = getConnectedFolderUri(context)
        if (treeUri == null || !isAutoSyncEnabled(context)) {
            onComplete?.invoke(false)
            return
        }

        if (!isAutoSyncRunning.compareAndSet(false, true)) {
            onComplete?.invoke(false)
            return
        }

        createBackup(
            context = context,
            noteRepository = noteRepository,
            categoryRepository = categoryRepository,
            knowledgeRepository = knowledgeRepository,
            onSuccess = { backupFile ->
                backupScope.launch {
                    var success = false
                    try {
                        success = saveBackupToTreeUri(context, backupFile, treeUri)
                        if (success) {
                            val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
                            sp.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
                        }
                    } finally {
                        try { backupFile.delete() } catch (_: Exception) {}
                        isAutoSyncRunning.set(false)
                    }
                    Handler(Looper.getMainLooper()).post {
                        onComplete?.invoke(success)
                    }
                }
            },
            onError = {
                isAutoSyncRunning.set(false)
                Handler(Looper.getMainLooper()).post {
                    onComplete?.invoke(false)
                }
            }
        )
    }
}
