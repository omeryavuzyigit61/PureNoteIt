package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

@Composable
fun DesktopVaultSettingsDialog(
    state: DesktopUiState,
    onEnableMasterLock: (CharArray, Int) -> Unit,
    onDisableMasterLock: (CharArray) -> Unit,
    onUpdateAutoLockMinutes: (Int) -> Unit,
    onLockNow: () -> Unit,
    onDismiss: () -> Unit
) {
    var passInput by remember { mutableStateOf("") }
    var passConfirm by remember { mutableStateOf("") }
    var selectedMinutes by remember(state.autoLockMinutes) { mutableIntStateOf(state.autoLockMinutes) }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 520.dp
    ) {
                Text(
                    text = "Uygulama Kilidi & Gizlilik",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.isMasterLockEnabled) {
                        "Açılış parolası koruması aktif — kilitlendiğinde notlarınız gizlenir."
                    } else {
                        "Verileriniz cihazda şifreli saklanır. Ekstra koruma için açılış parolası ekleyebilirsiniz."
                    },
                    fontSize = 12.sp,
                    color = if (state.isMasterLockEnabled) DesktopColors.EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(14.dp))
                Text("Otomatik Kilitleme Süresi (Hareketsizlik):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "1 dk", 5 to "5 dk", 15 to "15 dk", 0 to "Yalnızca Manuel").forEach { (mins, label) ->
                        val selected = selectedMinutes == mins
                        OutlinedButton(
                            onClick = {
                                selectedMinutes = mins
                                onUpdateAutoLockMinutes(mins)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                if (!state.isMasterLockEnabled) {
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Yeni Açılış Ana Parolası (en az 6 karakter)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passConfirm,
                        onValueChange = { passConfirm = it },
                        label = { Text("Parolayı Tekrar Girin") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = passInput.length >= 6 && passInput == passConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                        onClick = {
                            val chars = passInput.toCharArray()
                            passInput = ""
                            passConfirm = ""
                            onEnableMasterLock(chars, selectedMinutes)
                        }
                    ) {
                        Text("Ana Parola Kilidini Aktif Et")
                    }
                } else {
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Devre Dışı Bırakmak İçin Mevcut Parola") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                            onClick = onLockNow
                        ) {
                            Text("Şimdi Kasayı Kilitle (Ctrl+L)")
                        }
                        OutlinedButton(
                            enabled = passInput.isNotEmpty(),
                            onClick = {
                                val chars = passInput.toCharArray()
                                passInput = ""
                                onDisableMasterLock(chars)
                            }
                        ) {
                            Text("Parola Kilidini Kaldır")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Kapat") }
                }
    }
}
