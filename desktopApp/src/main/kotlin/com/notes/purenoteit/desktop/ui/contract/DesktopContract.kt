package com.notes.purenoteit.desktop.ui.contract

import com.notes.purenoteit.domain.model.AppError
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.CodeExecutionError
import com.notes.purenoteit.domain.model.CodeExecutionOutput
import com.notes.purenoteit.domain.model.DataError
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.domain.model.NoteLink
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock

enum class SidebarSection {
    ALL,
    PINNED,
    TASKS,
    AGENDA,
    TRASH,
    CATEGORY
}

enum class SplitViewMode {
    NONE,
    GRAPH_2D,
    SUB_PAGE
}

enum class DesktopViewMode {
    CARDS,
    LIST,
    BOARD
}

enum class SyncTransportMode {
    GOOGLE_DRIVE,
    LAN_WIFI
}

data class SubPageTreeNode(
    val note: Note,
    val children: List<SubPageTreeNode> = emptyList()
)

data class CodeBlockRunState(
    val blockId: String,
    val isRunning: Boolean = false,
    val output: CodeExecutionOutput? = null,
    val errorMessage: String? = null,
    val awaitingBashApproval: Boolean = false,
    val pendingCode: String = "",
    val pendingLanguage: String = "bash"
)

data class DesktopUiState(
    val isDarkTheme: Boolean = true,
    val sidebarSection: SidebarSection = SidebarSection.ALL,
    val selectedCategorySyncUuid: String? = null,
    val searchQuery: String = "",
    val categories: List<Category> = emptyList(),
    val allActiveNotes: List<Note> = emptyList(),
    val trashedNotes: List<Note> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val noteLinks: List<NoteLink> = emptyList(),
    val subPageTree: List<SubPageTreeNode> = emptyList(),
    val selectedNoteSyncUuid: String? = null,
    val editorTitle: String = "",
    val editorIcon: String = "📝",
    val editorBlocks: List<NoteBlock> = emptyList(),
    val editorCategorySyncUuid: String? = null,
    val isEditorDirty: Boolean = false,
    val homeViewMode: DesktopViewMode = DesktopViewMode.CARDS,
    val splitViewMode: SplitViewMode = SplitViewMode.NONE,
    val splitPreviewNote: Note? = null,
    val splitPreviewBlocks: List<NoteBlock> = emptyList(),
    val showQuickSwitcher: Boolean = false,
    val showSlashPalette: Boolean = false,
    val showCreateCategoryDialog: Boolean = false,
    val showCreateSubPageDialog: Boolean = false,
    val showEmojiPickerDialog: Boolean = false,
    val activeDrawingBlockId: String? = null,
    val codeRunStates: Map<String, CodeBlockRunState> = emptyMap(),
    val showSyncDialog: Boolean = false,
    val syncTransportMode: SyncTransportMode = SyncTransportMode.GOOGLE_DRIVE,
    val isDriveConnected: Boolean = false,
    val hasStoredPassphrase: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncSummary: String? = null,
    val lanServerRunning: Boolean = false,
    val lanServerUrl: String? = null,
    val lanRemoteUrl: String = "",
    val isVaultLocked: Boolean = false,
    val isMasterLockEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val vaultCooldownSeconds: Int = 0,
    val vaultLockError: String? = null,
    val showVaultSecurityDialog: Boolean = false
) {
    val selectedNote: Note?
        get() = allActiveNotes.firstOrNull { it.syncUuid == selectedNoteSyncUuid }
            ?: trashedNotes.firstOrNull { it.syncUuid == selectedNoteSyncUuid }
}

sealed interface DesktopUiEvent {
    data object ToggleTheme : DesktopUiEvent
    data class SelectSidebar(val section: SidebarSection, val categorySyncUuid: String? = null) : DesktopUiEvent
    data class UpdateSearchQuery(val query: String) : DesktopUiEvent
    data class SelectNote(val syncUuid: String) : DesktopUiEvent
    data object CreateNewNote : DesktopUiEvent
    data class CreateNoteFromTemplate(val title: String, val icon: String, val blocks: List<NoteBlock>) : DesktopUiEvent
    data class UpdateNoteStatus(val syncUuid: String, val status: String) : DesktopUiEvent
    data class CreateCategory(val name: String) : DesktopUiEvent
    data class DeleteCategory(val syncUuid: String) : DesktopUiEvent
    data class TogglePinNote(val syncUuid: String) : DesktopUiEvent
    data class SoftDeleteNote(val syncUuid: String) : DesktopUiEvent
    data class RestoreNote(val syncUuid: String) : DesktopUiEvent

    data class UpdateTitle(val title: String) : DesktopUiEvent
    data class UpdateIcon(val icon: String) : DesktopUiEvent
    data class UpdateCategoryForNote(val categorySyncUuid: String) : DesktopUiEvent
    data class UpdateBlock(val index: Int, val block: NoteBlock) : DesktopUiEvent
    data class AddBlock(val type: BlockType, val insertAfterIndex: Int? = null) : DesktopUiEvent
    data class DeleteBlock(val index: Int) : DesktopUiEvent
    data class MoveBlock(val fromIndex: Int, val toIndex: Int) : DesktopUiEvent
    data class CreateSubPage(val title: String) : DesktopUiEvent
    data class OpenSubPageInSplit(val subPageSyncUuid: String) : DesktopUiEvent
    data class OpenNoteByTitleOrUuid(val identifier: String) : DesktopUiEvent
    data object SaveCurrentNote : DesktopUiEvent

    data class ImportImageToBlock(val blockId: String, val fileBytes: ByteArray, val extension: String) : DesktopUiEvent
    data class OpenDrawingEditor(val blockId: String) : DesktopUiEvent
    data object CloseDrawingEditor : DesktopUiEvent
    data class SaveDrawingPng(val blockId: String, val pngBytes: ByteArray) : DesktopUiEvent

    data class SetHomeViewMode(val mode: DesktopViewMode) : DesktopUiEvent
    data class SetSplitViewMode(val mode: SplitViewMode) : DesktopUiEvent
    data class SetQuickSwitcherVisible(val visible: Boolean) : DesktopUiEvent
    data class SetSlashPaletteVisible(val visible: Boolean) : DesktopUiEvent
    data class SetCreateCategoryDialogVisible(val visible: Boolean) : DesktopUiEvent
    data class SetCreateSubPageDialogVisible(val visible: Boolean) : DesktopUiEvent
    data class SetEmojiPickerDialogVisible(val visible: Boolean) : DesktopUiEvent

    data class RunCodeBlock(val blockId: String, val language: String, val code: String) : DesktopUiEvent
    data class ConfirmBashExecution(val blockId: String) : DesktopUiEvent
    data class DismissCodeOutput(val blockId: String) : DesktopUiEvent

    data class SetSyncDialogVisible(val visible: Boolean) : DesktopUiEvent
    data class SelectSyncTransport(val mode: SyncTransportMode) : DesktopUiEvent
    data object ConnectGoogleDrive : DesktopUiEvent
    data object DisconnectGoogleDrive : DesktopUiEvent
    data object NavigateHome : DesktopUiEvent
    data class StartLanServer(val pairPinChars: CharArray) : DesktopUiEvent
    data object StopLanServer : DesktopUiEvent
    data class ConnectAndSyncLan(val remoteHttpUrl: String, val pairPinChars: CharArray, val e2eePassChars: CharArray) : DesktopUiEvent
    data object TriggerSyncNow : DesktopUiEvent

    data class SetVaultSecurityDialogVisible(val visible: Boolean) : DesktopUiEvent
    data class UnlockVault(val passphraseChars: CharArray) : DesktopUiEvent
    data class EnableMasterPassword(val passphraseChars: CharArray, val autoLockMinutes: Int) : DesktopUiEvent
    data class DisableMasterPassword(val currentPassphraseChars: CharArray) : DesktopUiEvent
    data class UpdateAutoLockMinutes(val minutes: Int) : DesktopUiEvent
    data object LockVaultNow : DesktopUiEvent
    data object RecordUserActivity : DesktopUiEvent
}

sealed interface DesktopUiEffect {
    data class ShowMessage(val message: String, val isError: Boolean = false) : DesktopUiEffect
    data object FocusSearchField : DesktopUiEffect
}

fun AppError.toTurkishMessage(): String = when (this) {
    DataError.Local.DISK_FULL -> "Disk alanı yetersiz olduğu için kayıt yapılamadı."
    DataError.Local.NOT_FOUND -> "İstenen not veya kayıt bulunamadı."
    DataError.Local.VALIDATION_FAILED -> "Lütfen geçerli bir başlık veya ad girin."
    DataError.Local.UNKNOWN -> "Yerel veritabanı işlemi sırasında beklenmeyen bir hata oluştu."
    DataError.Security.UNAUTHORIZED -> "Yetkilendirme doğrulanamadı."
    DataError.Security.DECRYPTION_FAILED -> "Şifre çözme başarısız: ana parola hatalı veya veri bozulmuş."
    DataError.Security.KEYSTORE_UNAVAILABLE -> "Sistem güvenlik kasasına erişilemedi."
    DataError.Security.KEY_REQUIRED -> "Devam etmek için güvenlik parolası gereklidir."
    DataError.Security.INTEGRITY_MISMATCH -> "İndirilen medya dosyasının bütünlük doğrulaması başarısız oldu."
    DataError.Network.TIMEOUT -> "Bağlantı veya yetkilendirme zaman aşımına uğradı."
    DataError.Network.NO_CONNECTION -> "Ağ bağlantısı kurulamadı. İnternet veya Wi-Fi erişiminizi kontrol edin."
    DataError.Network.SERIALIZATION -> "Sunucudan gelen veri çözümlenemedi."
    DataError.Network.UNAUTHORIZED -> "Oturum süresi doldu veya izin reddedildi. Yeniden bağlanın."
    DataError.Network.RATE_LIMITED -> "Çok fazla istek gönderildi. Kısa süre sonra tekrar deneyin."
    DataError.Network.QUOTA_EXCEEDED -> "Google Drive uygulama depolama kotası dolu."
    DataError.Network.SERVER -> "Uzak sunucu geçici bir hata bildirdi."
    DataError.Network.TLS_ERROR -> "Güvenli HTTPS/TLS doğrulaması başarısız olduğu için bağlantı engellendi."
    DataError.Network.UNKNOWN -> "Beklenmeyen ağ hatası oluştu."
    CodeExecutionError.EmptyCode -> "Çalıştırılacak kod boş olamaz."
    CodeExecutionError.ExecutionTimeout -> "Kod çalışması 10 saniye sınırını aştığı için güvenlik gereği sonlandırıldı."
    CodeExecutionError.JsEngineUnavailable -> "Node.js / JS çalıştırma ortamı bulunamadı."
    is CodeExecutionError.SecurityBlocked -> when (operation) {
        "BASH_CONFIRMATION_REQUIRED" -> "Bash betiği çalıştırmadan önce açık kullanıcı onayı gereklidir."
        else -> "Güvenlik politikası engeli: '$operation' ifadesine izin verilmiyor."
    }
    is CodeExecutionError.RuntimeOrSyntax -> lineHint?.let { "Satır $it: $message" } ?: message
    is CodeExecutionError.StaticSyntaxFailure -> "Statik sözdizimi hatası${line?.let { " (Satır $it)" } ?: ""}: $issueType"
}
