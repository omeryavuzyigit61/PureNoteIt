package com.notes.purenoteit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkData
import com.notes.purenoteit.repository.CategoryRepository
import com.notes.purenoteit.repository.KnowledgeRepository
import com.notes.purenoteit.repository.NoteRepository
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultBackupPayload(
    val version: Int = 2,
    val backupDate: Long = System.currentTimeMillis(),
    val notes: List<NoteData>,
    val categories: List<CategoryData>,
    val links: List<NoteLinkData>
)

object VaultBackupManager {

    private val gson = Gson()
    private const val MAX_ARCHIVE_ENTRIES = 2000
    private const val MAX_SINGLE_FILE_BYTES = 100L * 1024L * 1024L // 100 MB per media file limit

    /**
     * Creates a compressed, AES-256-GCM encrypted .purevault archive containing all notes,
     * categories, links, and media files via Clean Architecture Repositories.
     */
    fun createBackup(
        context: Context,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Schedulers.io().scheduleDirect {
            try {
                val rawNotes = noteRepository.getAllNotes().blockingGet() ?: emptyList()
                val categories = categoryRepository.getAllCategories().blockingGet() ?: emptyList()
                val links = knowledgeRepository.getAllLinks().blockingGet() ?: emptyList()
                val lockedCategoryIds = categories.filter { it.isPassword == true }.map { it.id }.toSet()

                // Convert any hardware-bound ENC_V2 note descriptions into 60k-iteration portable AES-256-GCM (ENC_P2:)
                // and ensure notes in locked categories are encrypted as well.
                val portableNotes = rawNotes.map { note ->
                    val desc = note.description
                    val isNoteOrCategoryLocked = (note.isPassword == true) ||
                        note.isEncrypted ||
                        (note.categoryId != null && note.categoryId in lockedCategoryIds)
                    val portableDesc = when {
                        desc != null && desc.startsWith("ENC_V2:") -> {
                            val plain = SecurityUtil.decrypt(desc)
                            SecurityUtil.encryptPortable(plain)
                        }
                        isNoteOrCategoryLocked && !desc.isNullOrBlank() && !SecurityUtil.isEncryptedPayload(desc) -> {
                            SecurityUtil.encryptPortable(desc)
                        }
                        else -> desc
                    }
                    note.copy(
                        description = portableDesc,
                        isPassword = isNoteOrCategoryLocked,
                        isEncrypted = isNoteOrCategoryLocked
                    ).also { it.id = note.id }
                }

                val payload = VaultBackupPayload(
                    notes = portableNotes,
                    categories = categories,
                    links = links
                )
                val rawJsonString = gson.toJson(payload)
                // Encrypt the entire manifest with AES-256-GCM so plaintext metadata never leaks in transit
                val encryptedManifest = SecurityUtil.encryptPortable(rawJsonString)

                val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(backupDir, "PureNoteIt_Kasa_$timeStamp.purevault")

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // 1. Add AES-256-GCM encrypted manifest
                    val manifestEntry = ZipEntry("vault_manifest.json")
                    zos.putNextEntry(manifestEntry)
                    zos.write(encryptedManifest.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. Add media files from filesDir
                    val filesDir = context.filesDir
                    filesDir.walkTopDown().filter { it.isFile && it.name != zipFile.name }.forEach { file ->
                        val relativePath = "media/" + file.relativeTo(filesDir).path.replace("\\", "/")
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis -> fis.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSuccess(zipFile)
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(e)
                }
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
     * Restores a .purevault / zip backup archive with strict CWE-22 Zip Slip Path Traversal protection
     * and Zip Bomb resource limits.
     */
    fun restoreBackup(
        context: Context,
        backupUri: Uri,
        noteRepository: NoteRepository,
        categoryRepository: CategoryRepository,
        knowledgeRepository: KnowledgeRepository,
        onSuccess: (restoredNotesCount: Int, restoredCategoriesCount: Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Schedulers.io().scheduleDirect {
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
                                } else if (entry.name.startsWith("media/") && !entry.isDirectory) {
                                    val relPath = entry.name.removePrefix("media/")
                                    val targetFile = File(filesDir, relPath).canonicalFile

                                    // CWE-22 Zip Slip Path Traversal Guard
                                    if (!targetFile.path.startsWith(filesDir.path + File.separator)) {
                                        throw SecurityException("Güvenlik İhlali Engellendi: Zip Slip dizin aşımı tespit edildi (${entry.name})")
                                    }

                                    targetFile.parentFile?.mkdirs()
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
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                }

                if (manifestRawOrEncrypted.isNullOrBlank()) {
                    throw IllegalStateException("Yedek dosyası geçersiz veya manifest bulunamadı")
                }

                // Support both V2 encrypted manifests (ENC: / ENC_V2:) and V1 legacy plaintext JSON manifests
                val jsonContent = if (SecurityUtil.isEncryptedPayload(manifestRawOrEncrypted)) {
                    SecurityUtil.decrypt(manifestRawOrEncrypted!!)
                } else {
                    manifestRawOrEncrypted!!
                }

                val payload = gson.fromJson(jsonContent, VaultBackupPayload::class.java)

                // Restore categories via CategoryRepository
                payload.categories.forEach { cat ->
                    categoryRepository.categoryUpsert(cat).onErrorComplete().blockingAwait()
                }

                // Restore notes via NoteRepository (re-wrapping encrypted notes into hardware AndroidKeyStore ENC_V2)
                payload.notes.forEach { note ->
                    val desc = note.description
                    if (desc != null && (desc.startsWith("ENC:") || desc.startsWith("ENC_P2:"))) {
                        val plain = SecurityUtil.decrypt(desc)
                        note.description = SecurityUtil.encrypt(plain)
                    }
                    noteRepository.upsert(note).onErrorComplete().blockingAwait()
                }

                // Restore note links via KnowledgeRepository
                if (payload.links.isNotEmpty()) {
                    knowledgeRepository.insertAllLinks(payload.links).onErrorComplete().blockingAwait()
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSuccess(payload.notes.size, payload.categories.size)
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onError(e)
                }
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

    private val isAutoSyncRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val autoSyncDebounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
        return runCatching { Uri.parse(uriStr) }.getOrNull()
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

    /**
     * Finds the latest encrypted backup archive inside the connected Google Drive / Cloud folder.
     */
    fun getAutoBackupUri(context: Context): Uri? {
        val treeUri = getConnectedFolderUri(context) ?: return null
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            rootDoc.findFile(AUTO_BACKUP_FILENAME)?.uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves the encrypted .purevault archive directly into the user's connected Google Drive / Cloud folder.
     */
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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Debounces rapid auto-sync triggers (e.g. while typing/saving notes) so disk and SAF
     * streams are only written once after edits settle.
     */
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

    /**
     * Performs a single-flight background E2EE auto-backup to Google Drive / Cloud folder.
     */
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
                Schedulers.io().scheduleDirect {
                    var success = false
                    try {
                        success = saveBackupToTreeUri(context, backupFile, treeUri)
                        if (success) {
                            val sp = context.getSharedPreferences(PREF_VAULT_SYNC, Context.MODE_PRIVATE)
                            sp.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
                        }
                    } finally {
                        runCatching { backupFile.delete() }
                        isAutoSyncRunning.set(false)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onComplete?.invoke(success)
                    }
                }
            },
            onError = {
                isAutoSyncRunning.set(false)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onComplete?.invoke(false)
                }
            }
        )
    }
}
