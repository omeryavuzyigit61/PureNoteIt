package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState
import com.notes.purenoteit.desktop.ui.contract.SyncTransportMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopSyncDialog(
    state: DesktopUiState,
    onSelectTransport: (SyncTransportMode) -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onTriggerSync: () -> Unit,
    onForcePushAll: () -> Unit = {},
    onCleanOrphanMedia: () -> Unit = {},
    onStartLanServer: (CharArray) -> Unit,
    onStopLanServer: () -> Unit,
    onConnectAndSyncLan: (remoteUrl: String, pairPinChars: CharArray, e2eePassChars: CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var passphraseInput by remember { mutableStateOf("") }
    var lanPinInput by remember { mutableStateOf("") }
    var lanRemoteUrlInput by remember { mutableStateOf(state.lanRemoteUrl) }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 540.dp
    ) {
                Text(
                    text = "Bulut Eşitleme ve Paylaşım",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                PrimaryTabRow(selectedTabIndex = state.syncTransportMode.ordinal) {
                    Tab(
                        selected = state.syncTransportMode == SyncTransportMode.GOOGLE_DRIVE,
                        onClick = { onSelectTransport(SyncTransportMode.GOOGLE_DRIVE) },
                        text = { Text("Google Drive") }
                    )
                    Tab(
                        selected = state.syncTransportMode == SyncTransportMode.LAN_WIFI,
                        onClick = { onSelectTransport(SyncTransportMode.LAN_WIFI) },
                        text = { Text("Aynı Ağ Wi-Fi") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (state.syncTransportMode) {
                    SyncTransportMode.GOOGLE_DRIVE -> {
                        Text(
                            text = if (state.isDriveConnected) {
                                "Durum: Google Drive bağlı · Otomatik şifreli eşitleme aktif."
                            } else {
                                "Tek tıkla Google hesabınızı onaylayın; notlarınız cihazlar arasında otomatik eşitleme ile korunur."
                            },
                            fontSize = 13.sp,
                            color = if (state.isDriveConnected) DesktopColors.EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!state.isDriveConnected) {
                                Button(
                                    enabled = !state.isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                                    onClick = onConnectDrive
                                ) {
                                    Text("Google Drive ile Bağlan")
                                }
                            } else {
                                Button(
                                    enabled = !state.isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                                    onClick = onTriggerSync
                                ) {
                                    Text("Şimdi Eşitle")
                                }
                                OutlinedButton(
                                    enabled = !state.isSyncing,
                                    onClick = onDisconnectDrive
                                ) {
                                    Text("Çıkış Yap")
                                }
                            }
                        }

                        if (state.isDriveConnected) {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    val usage = state.driveMediaUsage
                                    val totalBytes = (usage?.totalMediaBytes ?: 0L) + (usage?.totalDeltaBytes ?: 0L)
                                    val sizeLabel = when {
                                        usage == null -> "Hesaplanıyor..."
                                        totalBytes < 1024L * 1024L -> "${(totalBytes / 1024.0).let { "%.1f".format(it) }} KB (${usage.mediaFileCount} medya)"
                                        else -> "${(totalBytes / (1024.0 * 1024.0)).let { "%.2f".format(it) }} MB (${usage.mediaFileCount} medya)"
                                    }
                                    Text(
                                        text = "Drive Depolama: $sizeLabel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Not fotoğrafları gönderilmeden önce otomatik küçültülüp optimize edilir. Drive alanınız kısıtlıysa çöp kutusundan silinmiş notların eski medyalarını temizleyebilir veya Aynı Ağ Wi-Fi sekmesini sıfır kota ile kullanabilirsiniz.",
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        enabled = !state.isSyncing && !state.isCleaningCloudMedia,
                                        onClick = onForcePushAll
                                    ) {
                                        Text(
                                            text = "Tüm Notları Buluta Yeniden Gönder",
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    OutlinedButton(
                                        enabled = !state.isSyncing && !state.isCleaningCloudMedia,
                                        onClick = onCleanOrphanMedia
                                    ) {
                                        if (state.isCleaningCloudMedia) {
                                            CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = if (state.isCleaningCloudMedia) "Temizleniyor..." else "Gereksiz Medyaları Temizle",
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }

                    SyncTransportMode.LAN_WIFI -> {
                        Text(
                            text = "Aynı Wi-Fi ağındaki cihazlar arasında şifreli not aktarımı.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = lanPinInput,
                            onValueChange = { lanPinInput = it },
                            label = { Text("Wi-Fi Eşleşme Kodu (en az 8 karakter)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!state.lanServerRunning) {
                                Button(onClick = {
                                    val chars = lanPinInput.toCharArray()
                                    onStartLanServer(chars)
                                }) {
                                    Text("Paylaşım Sunucusunu Başlat")
                                }
                            } else {
                                OutlinedButton(onClick = onStopLanServer) {
                                    Text("Sunucuyu Durdur")
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Adres: ${state.lanServerUrl.orEmpty()}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopColors.EmeraldGreen
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        OutlinedTextField(
                            value = lanRemoteUrlInput,
                            onValueChange = { lanRemoteUrlInput = it },
                            label = { Text("Karşı cihaz adresi (sunucuda gösterilen https://…#… adresini aynen yapıştırın)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passphraseInput,
                            onValueChange = { passphraseInput = it },
                            label = { Text("Ortak Güvenlik Parolası") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            enabled = !state.isSyncing,
                            onClick = {
                                val pinChars = lanPinInput.toCharArray()
                                val e2eeChars = passphraseInput.toCharArray()
                                lanPinInput = ""
                                passphraseInput = ""
                                onConnectAndSyncLan(lanRemoteUrlInput, pinChars, e2eeChars)
                            }
                        ) {
                            Text("Wi-Fi Üzerinden Eşitle")
                        }
                    }
                }

                if (state.isSyncing) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Şifreli eşitleme çalışıyor...", fontSize = 12.sp)
                    }
                }

                state.lastSyncSummary?.let { summary ->
                    Spacer(Modifier.height(8.dp))
                    Text(summary, fontSize = 12.sp, color = DesktopColors.EmeraldGreen)
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Kapat") }
                }
    }
}
