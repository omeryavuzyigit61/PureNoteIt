package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
fun DesktopNotePinUnlockDialog(
    language: String,
    errorMessage: String?,
    cooldownSeconds: Int,
    onUnlock: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = language == "en"
    var pinInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submit() {
        if (pinInput.length == 6 && cooldownSeconds <= 0) {
            val chars = pinInput.toCharArray()
            pinInput = ""
            onUnlock(chars)
        }
    }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 410.dp,
        title = if (isEn) "Protected Note (PIN)" else "Korumalı Not (PIN)",
        subtitle = if (isEn) {
            "Enter your 6-digit PIN synced with your phone to unlock protected notes."
        } else {
            "Korumalı notları açmak için telefonunuzla eşitlenmiş 6 haneli PIN kodunu girin."
        },
        badgeIcon = Icons.Rounded.Lock,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        OutlinedTextField(
            value = pinInput,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(6)
                pinInput = filtered
            },
            label = { Text(if (isEn) "6-Digit PIN" else "6 Haneli PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown &&
                        (ev.key == Key.Enter || ev.key == Key.NumPadEnter)
                    ) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (cooldownSeconds > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isEn) "Too many failed attempts. Wait ${cooldownSeconds}s." else "Çok fazla hatalı deneme. ${cooldownSeconds} sn bekleyin.",
                fontSize = 12.sp,
                color = DesktopColors.VibrantOrange
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "İptal")
            }
            Button(
                enabled = pinInput.length == 6 && cooldownSeconds <= 0,
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                onClick = ::submit
            ) {
                Text(
                    text = if (isEn) "Unlock" else "Kilidi Aç",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DesktopNotePinSetupDialog(
    language: String,
    hasExistingPin: Boolean,
    errorMessage: String?,
    onSavePin: (currentPin: CharArray?, newPin: CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = language == "en"
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    val canSave = newPin.length == 6 &&
        newPin == confirmPin &&
        (!hasExistingPin || currentPin.length == 6)

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 430.dp,
        title = if (hasExistingPin) {
            if (isEn) "Change Note PIN" else "Not PIN Kodunu Değiştir"
        } else {
            if (isEn) "Set 6-Digit Note PIN" else "6 Haneli Not PIN'i Belirle"
        },
        subtitle = if (isEn) {
            "Synced end-to-end encrypted (PBKDF2 verifier only — never stored in plaintext)."
        } else {
            "Telefonunuzla uçtan uca şifreli eşitlenir (yalnızca PBKDF2 doğrulayıcı aktarılır)."
        },
        badgeIcon = Icons.Rounded.Pin,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasExistingPin) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter(Char::isDigit).take(6) },
                    label = { Text(if (isEn) "Current 6-Digit PIN" else "Mevcut 6 Haneli PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(6) },
                label = { Text(if (isEn) "New 6-Digit PIN" else "Yeni 6 Haneli PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(6) },
                label = { Text(if (isEn) "Confirm New PIN" else "Yeni PIN Tekrar") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (newPin.isNotEmpty() && confirmPin.isNotEmpty() && newPin != confirmPin) {
                Text(
                    text = if (isEn) "PIN entries do not match." else "Girilen PIN kodları eşleşmiyor.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text(if (isEn) "Cancel" else "İptal")
            }
            Button(
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                onClick = {
                    val currChars = if (hasExistingPin) currentPin.toCharArray() else null
                    val nextChars = newPin.toCharArray()
                    currentPin = ""
                    newPin = ""
                    confirmPin = ""
                    onSavePin(currChars, nextChars)
                }
            ) {
                Text(
                    text = if (isEn) "Save PIN" else "PIN'i Kaydet",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
