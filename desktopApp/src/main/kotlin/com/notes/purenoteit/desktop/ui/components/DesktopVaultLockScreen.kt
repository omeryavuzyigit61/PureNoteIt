package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors

@Composable
fun DesktopVaultLockScreen(
    errorMessage: String?,
    cooldownSeconds: Int,
    onUnlock: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }

    fun submit() {
        if (password.isNotEmpty() && cooldownSeconds <= 0) {
            val chars = password.toCharArray()
            password = ""
            onUnlock(chars)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(420.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Kasa Kilitli",
                    tint = DesktopColors.VibrantOrange,
                    modifier = Modifier.size(42.dp)
                )
                Text(
                    text = "PureNoteIt Kilitli",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Notlarınıza ve dosyalarınıza erişmek için kasa parolanızı girin.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kasa Ana Parolası") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                                submit()
                                true
                            } else {
                                false
                            }
                        }
                )

                if (cooldownSeconds > 0) {
                    Text(
                        text = "Çok fazla hatalı deneme. $cooldownSeconds saniye bekleyin.",
                        fontSize = 12.sp,
                        color = Color(0xFFF87171)
                    )
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFF87171)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    enabled = password.isNotEmpty() && cooldownSeconds <= 0,
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("Kasayı Aç", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
