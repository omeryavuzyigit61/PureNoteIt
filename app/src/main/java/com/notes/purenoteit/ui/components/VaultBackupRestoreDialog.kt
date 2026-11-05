package com.notes.purenoteit.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.VaultBackupManager
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.NoteViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VaultBackupRestoreDialog(
    noteViewModel: NoteViewModel,
    totalNotesCount: Int,
    totalCategoriesCount: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isAutoSyncEnabled by remember { mutableStateOf(VaultBackupManager.isAutoSyncEnabled(context)) }
    var connectedFolderName by remember { mutableStateOf(VaultBackupManager.getConnectedFolderName(context)) }
    var lastSyncTime by remember { mutableStateOf(VaultBackupManager.getLastSyncTime(context)) }
    var isSyncingNow by remember { mutableStateOf(false) }

    // Persistent Tree Uri folder picker for Google Drive / Cloud E2EE Auto-Sync
    val selectFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let { uri ->
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                val doc = DocumentFile.fromTreeUri(context, uri)
                val folderTitle = doc?.name ?: "Google Drive"
                VaultBackupManager.saveConnectedFolder(context, uri, folderTitle)
                connectedFolderName = folderTitle
                isAutoSyncEnabled = true
                context.showToastShort("Bulut klasörü bağlandı: $folderTitle")

                // Immediately trigger first auto-sync
                isSyncingNow = true
                noteViewModel.syncVaultNow { success ->
                    isSyncingNow = false
                    lastSyncTime = VaultBackupManager.getLastSyncTime(context)
                    if (success) {
                        context.showToastShort("İlk şifreli yedek başarıyla Drive klasörüne eşitlendi!")
                    }
                }
            } catch (e: Exception) {
                context.showToastShort("Klasör izni alınamadı: ${e.message}")
            }
        }
    }

    // Multi-MIME file picker that opens .purevault and .zip from Drive, Downloads, Telegram, etc.
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isRestoring = true
            noteViewModel.restoreVault(
                context = context,
                uri = selectedUri,
                onSuccess = { notesCount, catsCount ->
                    isRestoring = false
                    restoreSuccessMessage = context.getString(R.string.vault_restore_success_desc, notesCount, catsCount)
                    context.showToastShort(context.getString(R.string.vault_restore_success_toast))
                },
                onError = { err ->
                    isRestoring = false
                    context.showToastShort(err.message ?: "")
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.lock),
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.vault_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.vault_subtitle),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.cancel),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Current Vault Stats Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalNotesCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                        Text(
                            text = stringResource(R.string.tab_cards),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalCategoriesCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF388E3C)
                        )
                        Text(
                            text = stringResource(R.string.category_setting),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "AES-256",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2)
                        )
                        Text(
                            text = "Vault",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Drive & Cloud E2EE Auto-Sync Center Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (connectedFolderName != null) Color(0xFF10B981).copy(alpha = 0.6f) else VibrantOrange.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row with Cloud Icon, Title, and E2EE Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_backup_restore),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.vault_drive_auto_title),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "E2EE (AES-256) Korumalı",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (connectedFolderName != null) {
                            Switch(
                                checked = isAutoSyncEnabled,
                                onCheckedChange = { checked ->
                                    isAutoSyncEnabled = checked
                                    VaultBackupManager.setAutoSyncEnabled(context, checked)
                                    if (checked) {
                                        noteViewModel.triggerAutoSync()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = PureWhite,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (connectedFolderName != null) {
                        // Connected Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${stringResource(R.string.vault_drive_folder_connected)}: $connectedFolderName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = stringResource(R.string.vault_drive_disconnect),
                                fontSize = 11.sp,
                                color = Color.Red.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        VaultBackupManager.disconnectFolder(context)
                                        connectedFolderName = null
                                        isAutoSyncEnabled = false
                                        context.showToastShort("Klasör bağlantısı kesildi")
                                    }
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Last Synced Text
                        val lastSyncStr = if (lastSyncTime > 0) {
                            SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(lastSyncTime))
                        } else {
                            stringResource(R.string.vault_drive_never_synced)
                        }
                        Text(
                            text = stringResource(R.string.vault_drive_last_sync, lastSyncStr),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action Buttons: "Şimdi Eşitle" and "Drive'dan Geri Yükle"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Sync Now Button
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isSyncingNow) {
                                        isSyncingNow = true
                                        noteViewModel.syncVaultNow { success ->
                                            isSyncingNow = false
                                            lastSyncTime = VaultBackupManager.getLastSyncTime(context)
                                            if (success) {
                                                context.showToastShort("Kasa başarıyla Google Drive'a eşitlendi!")
                                            } else {
                                                context.showToastShort("Eşitleme başarısız oldu!")
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSyncingNow) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = PureWhite,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.vault_drive_syncing),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PureWhite
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_redo),
                                            contentDescription = null,
                                            tint = PureWhite,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.vault_drive_sync_now),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PureWhite
                                        )
                                    }
                                }
                            }

                            // Restore from Drive Auto Backup Button
                            val autoBackupUri = remember(lastSyncTime, connectedFolderName) {
                                VaultBackupManager.getAutoBackupUri(context)
                            }
                            if (autoBackupUri != null) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(enabled = !isRestoring) {
                                            isRestoring = true
                                            noteViewModel.restoreVault(
                                                context = context,
                                                uri = autoBackupUri,
                                                onSuccess = { notesCount, catsCount ->
                                                    isRestoring = false
                                                    restoreSuccessMessage = context.getString(R.string.vault_restore_success_desc, notesCount, catsCount)
                                                    context.showToastShort(context.getString(R.string.vault_restore_success_toast))
                                                },
                                                onError = { err ->
                                                    isRestoring = false
                                                    context.showToastShort(err.message ?: "")
                                                }
                                            )
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_drawer_backup_restore),
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.vault_drive_restore_btn),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Disconnected / Initial State
                        Text(
                            text = stringResource(R.string.vault_drive_auto_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectFolderLauncher.launch(null)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = VibrantOrange,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 11.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.category),
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.vault_drive_connect_btn),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Create Backup (.purevault)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isBackingUp && !isRestoring) {
                        isBackingUp = true
                        noteViewModel.backupVault(
                            context = context,
                            onSuccess = { file ->
                                isBackingUp = false
                                VaultBackupManager.shareBackupFile(context, file)
                                context.showToastShort(context.getString(R.string.vault_backup_success_toast))
                            },
                            onError = { err ->
                                isBackingUp = false
                                context.showToastShort(err.message ?: "")
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isBackingUp) SoftOrangeLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftOrangeLight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = VibrantOrange,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.share_square),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBackingUp) stringResource(R.string.vault_export_backing_up) else stringResource(R.string.vault_export_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.vault_export_desc),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 2: Restore from Backup (.purevault / .zip)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isBackingUp && !isRestoring) {
                        restoreFileLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*")
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isRestoring) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF388E3C),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_backup_restore),
                                contentDescription = null,
                                tint = Color(0xFF388E3C),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRestoring) stringResource(R.string.vault_restore_restoring) else stringResource(R.string.vault_restore_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.vault_restore_desc),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Success Confirmation Dialog
    if (restoreSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { restoreSuccessMessage = null },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_checklist),
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.vault_restore_success_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = restoreSuccessMessage!!,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreSuccessMessage = null
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold, color = VibrantOrange)
                }
            }
        )
    }
}
