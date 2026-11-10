package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

@Composable
fun DesktopSyncDialog(
    state: DesktopUiState,
    onSelectTransport: (SyncTransportMode) -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onTriggerSync: () -> Unit,
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
                            label = { Text("Wi-Fi Eşleşme Kodu (en az 4 karakter)") },
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
                            label = { Text("Karşı Cihaz URL (ör. http://192.168.1.45:8765)") },
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
