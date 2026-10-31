package com.notes.purenoteit.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.notes.purenoteit.data.db.CategoryDao
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteDao
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.data.db.NoteLinkDao
import com.notes.purenoteit.data.db.NoteLinkData
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultBackupPayload(
    val version: Int = 1,
    val backupDate: Long = System.currentTimeMillis(),
    val notes: List<NoteData>,
    val categories: List<CategoryData>,
    val links: List<NoteLinkData>
)

object VaultBackupManager {

    private val gson = Gson()

    /**
     * Creates a compressed .purevault archive containing all notes, categories, links and media files.
     */
    fun createBackup(
        context: Context,
        noteDao: NoteDao,
        categoryDao: CategoryDao,
        noteLinkDao: NoteLinkDao,
        onSuccess: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Schedulers.io().scheduleDirect {
            try {
                val notes = noteDao.getAllNotes().blockingGet() ?: emptyList()
                val categories = categoryDao.getAllCategories().blockingGet() ?: emptyList()
                val links = noteLinkDao.getAllLinks().blockingGet() ?: emptyList()

                val payload = VaultBackupPayload(
                    notes = notes,
                    categories = categories,
                    links = links
                )
                val jsonString = gson.toJson(payload)

                val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(backupDir, "PureNoteIt_Kasa_$timeStamp.purevault")

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    // 1. Add manifest JSON
                    val manifestEntry = ZipEntry("vault_manifest.json")
                    zos.putNextEntry(manifestEntry)
                    zos.write(jsonString.toByteArray(Charsets.UTF_8))
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
     * Restores a .purevault / zip backup archive from a chosen URI.
     */
    fun restoreBackup(
        context: Context,
        backupUri: Uri,
        noteDao: NoteDao,
        categoryDao: CategoryDao,
        noteLinkDao: NoteLinkDao,
        onSuccess: (restoredNotesCount: Int, restoredCategoriesCount: Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Schedulers.io().scheduleDirect {
            try {
                var jsonContent: String? = null
                val filesDir = context.filesDir

                context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "vault_manifest.json") {
                                jsonContent = zis.bufferedReader(Charsets.UTF_8).readText()
                            } else if (entry.name.startsWith("media/")) {
                                val relPath = entry.name.removePrefix("media/")
                                val targetFile = File(filesDir, relPath)
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                if (jsonContent.isNullOrBlank()) {
                    throw IllegalStateException("Yedek dosyası geçersiz veya manifest bulunamadı")
                }

                val payload = gson.fromJson(jsonContent, VaultBackupPayload::class.java)

                // Restore categories
                payload.categories.forEach { cat ->
                    categoryDao.upsertCategory(cat).onErrorComplete().blockingAwait()
                }

                // Restore notes
                payload.notes.forEach { note ->
                    noteDao.upsert(note).blockingAwait()
                }

                // Restore note links
                if (payload.links.isNotEmpty()) {
                    noteLinkDao.insertAll(payload.links).onErrorComplete().blockingAwait()
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
}
