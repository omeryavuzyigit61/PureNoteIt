package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.AppUpdateData
import com.notes.purenoteit.util.LanguageManager

/**
 * Modern In-App Update Dialog ("Yeni Sürüm Bulundu!")
 * Displays version tag, feature highlights, and direct update triggers.
 */
@Composable
fun UpdateAvailableDialog(
    updateData: AppUpdateData,
    isReleaseNotesOnly: Boolean = false,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    val context = LocalContext.current
    val currentLang = LanguageManager.getSelectedLanguage(context)
    val isEnglish = currentLang == LanguageManager.LANG_EN
    val notesList = if (isEnglish) updateData.releaseNotesEn else updateData.releaseNotesTr

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn(initialScale = 0.88f) + fadeIn()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Glowing Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        VibrantOrange,
                                        Color(0xFFFF5E00)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isReleaseNotesOnly) R.drawable.ic_drawer_release_notes else R.drawable.ic_drawer_update_check
                            ),
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = if (isReleaseNotesOnly) stringResource(R.string.release_notes_dialog_title) else stringResource(R.string.update_dialog_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle
                    Text(
                        text = if (isReleaseNotesOnly) stringResource(R.string.release_notes_dialog_subtitle, updateData.currentVersionName) else stringResource(R.string.update_dialog_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Version Tags Row (Evrensel: 1.1.8, 2.1.8 veya gelecekteki her sürüm için güvenilir gösterim)
                    val currentDisplay = "v" + updateData.currentVersionName.removePrefix("v").trim()
                    val targetDisplay = if (updateData.availableVersionName.isNotBlank() && updateData.availableVersionName != currentDisplay) {
                        updateData.availableVersionName
                    } else if (updateData.availableVersionCode > updateData.currentVersionCode) {
                        "Google Play En Son Sürüm"
                    } else {
                        currentDisplay
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentDisplay,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isReleaseNotesOnly && targetDisplay != currentDisplay) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_right),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = targetDisplay,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(VibrantOrange.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.update_version_chip),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = VibrantOrange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // "What's New" Section Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_release_notes),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.update_whats_new_header),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notesList) { noteItem ->
                                val (title, description) = parseReleaseNote(noteItem)
                                val iconRes = resolveReleaseNoteIcon(noteItem)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(VibrantOrange.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (title.isNotBlank()) {
                                            Text(
                                                text = title,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 16.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                        Text(
                                            text = description,
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Buttons
                    if (isReleaseNotesOnly) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VibrantOrange,
                                contentColor = PureWhite
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.close),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onUpdateClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VibrantOrange,
                                contentColor = PureWhite
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_drawer_update_check),
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.update_now_btn),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.update_later_btn),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveReleaseNoteIcon(text: String): Int {
    return when {
        text.contains("Sayfa Dizini", ignoreCase = true) ||
        text.contains("Page Index", ignoreCase = true) ||
        text.contains("📑") -> R.drawable.ic_hub_index

        text.contains("Üst Sayfa", ignoreCase = true) ||
        text.contains("Parent Page", ignoreCase = true) ||
        text.contains("Hiyerarşi", ignoreCase = true) ||
        text.contains("📂") -> R.drawable.ic_move_up

        text.contains("Kayan Not", ignoreCase = true) ||
        text.contains("Sticky", ignoreCase = true) ||
        text.contains("✨") -> R.drawable.ic_drawer_release_notes

        text.contains("Bağlantı", ignoreCase = true) ||
        text.contains("Linking", ignoreCase = true) ||
        text.contains("🔗") -> R.drawable.ic_link_hub

        text.contains("Kilit", ignoreCase = true) ||
        text.contains("Kasa", ignoreCase = true) ||
        text.contains("Lock", ignoreCase = true) -> R.drawable.lock

        text.contains("Yedek", ignoreCase = true) ||
        text.contains("Backup", ignoreCase = true) -> R.drawable.ic_drawer_backup_restore

        else -> R.drawable.ic_drawer_release_notes
    }
}

private fun parseReleaseNote(rawText: String): Pair<String, String> {
    var clean = rawText.trimStart('•', ' ', '-', '*', ':').trim()
    // Strip any leading emoji or symbol (surrogate pairs or unicode symbols)
    clean = clean.replace(Regex("^[\\p{So}\\p{Sk}\\p{Cn}\\p{Cs}\\s]+"), "").trim()
    val colonIdx = clean.indexOf(':')
    return if (colonIdx > 0) {
        val title = clean.substring(0, colonIdx).trim()
        val desc = clean.substring(colonIdx + 1).trim()
        Pair(title, desc)
    } else {
        Pair("", clean)
    }
}
