package com.notes.purenoteit.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.desktop.ui.DesktopStrings
import com.notes.purenoteit.desktop.ui.contract.DesktopUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopSettingsDialog(
    state: DesktopUiState,
    strings: DesktopStrings,
    onSelectLanguage: (String) -> Unit,
    onSelectRetentionDays: (Int) -> Unit,
    onToggleHideLockedNotes: (Boolean) -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenMasterLockSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 560.dp,
        title = strings.settingsTitle,
        subtitle = strings.settingsSubtitle,
        badgeIcon = Icons.Rounded.Settings,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        Text(
            text = strings.settingsLanguageTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = strings.settingsLanguageDesc,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("tr" to strings.settingsTurkish, "en" to strings.settingsEnglish).forEach { (code, label) ->
                val selected = state.language == code
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectLanguage(code) }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

        Text(
            text = strings.settingsTrashRetentionTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = strings.settingsTrashRetentionDesc,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(7, 14, 30, 60, 90, 0).forEach { days ->
                val selected = state.trashRetentionDays == days
                val label = if (days == 0) {
                    if (state.language == "en") "Never" else "Asla"
                } else {
                    strings.formatDays(days)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectRetentionDays(days) }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

        Text(
            text = strings.settingsProtectedModeTitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to strings.settingsProtectedModeMasked, true to strings.settingsProtectedModeHidden).forEach { (hide, label) ->
                val selected = state.hideLockedNotes == hide
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleHideLockedNotes(hide) }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenPinSetup()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (state.isNotePinSet) strings.settingsPinChangeAction else strings.settingsPinSetAction,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenMasterLockSettings()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(strings.settingsMasterLockTitle, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenSyncSettings()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(strings.settingsCloudSyncTitle, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(strings.closeBtn, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
