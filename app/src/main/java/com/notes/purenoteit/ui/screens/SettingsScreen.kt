package com.notes.purenoteit.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.components.LanguageSelectionSheet
import com.notes.purenoteit.ui.mvi.CloudSyncUiEffect
import com.notes.purenoteit.ui.mvi.CloudSyncUiEvent
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import com.notes.purenoteit.util.ThemeMode
import com.notes.purenoteit.util.AppUpdateData
import com.notes.purenoteit.util.AppUpdateHelper
import com.notes.purenoteit.util.Constants
import com.notes.purenoteit.util.LanguageManager
import com.notes.purenoteit.util.SharedPrefencesUtil
import com.notes.purenoteit.util.showToastShort
import com.notes.purenoteit.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    isDarkTheme: Boolean,
    onShowUpdateDialog: (AppUpdateData, Boolean) -> Unit,
    onNavigateToPasswordSettings: () -> Unit = {},
    onOpenVaultBackupRestore: () -> Unit = {},
    onDismiss: () -> Unit,
    cloudSyncViewModel: CloudSyncViewModel? = null,
    onNotesSynced: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentActivity = context as? Activity
    val prefs = remember { SharedPrefencesUtil(context) }

    var selectedRetentionDays by remember {
        mutableIntStateOf(prefs.get(Constants.KEY_TRASH_RETENTION_DAYS, Constants.DEFAULT_TRASH_RETENTION_DAYS))
    }
    var showLanguageSheet by remember { mutableStateOf(false) }

    val retentionOptions = listOf<Pair<Int, String>>(
        Pair(7, stringResource(R.string.settings_trash_7_days)),
        Pair(14, stringResource(R.string.settings_trash_14_days)),
        Pair(30, stringResource(R.string.settings_trash_30_days)),
        Pair(60, stringResource(R.string.settings_trash_60_days)),
        Pair(90, stringResource(R.string.settings_trash_90_days)),
        Pair(0, stringResource(R.string.settings_trash_never))
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SoftOrangeLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_drawer_settings),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "PureNoteIt v${AppUpdateHelper.getCurrentVersionName(context)}",
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

            // SECTION 1: Genel Ayarlar
            Text(
                text = stringResource(R.string.settings_section_general),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VibrantOrange
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Görünüm Teması Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.theme),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.theme_mode_title),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (currentThemeMode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system) + " (" + (if (isDarkTheme) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light)) + ")"
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3-Option Segmented Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val modes = listOf<Triple<ThemeMode, String, String>>(
                            Triple(ThemeMode.SYSTEM, "🌓", stringResource(R.string.theme_system)),
                            Triple(ThemeMode.LIGHT, "☀️", stringResource(R.string.theme_light)),
                            Triple(ThemeMode.DARK, "🌙", stringResource(R.string.theme_dark))
                        )
                        for ((mode, emoji, label) in modes) {
                            val isSelected = currentThemeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) VibrantOrange else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onThemeModeChange(mode) }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(text = emoji, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Dil Seçimi Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showLanguageSheet = true },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_language),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_language),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = LanguageManager.getLanguageLabel(context),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 2: Güvenlik & Kasa
            Text(
                text = stringResource(R.string.settings_section_security),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VibrantOrange
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. PIN & Kasa Kilidi Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onNavigateToPasswordSettings()
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.lock),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_pin_vault),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.drawer_pin_vault_sub),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 3: Yedekleme & Veri
            Text(
                text = stringResource(R.string.settings_section_backup),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VibrantOrange
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (cloudSyncViewModel != null) {
                CloudSyncSettingsSection(
                    viewModel = cloudSyncViewModel,
                    onNotesSynced = onNotesSynced
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 1. Yedekle ve Geri Yükle Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onOpenVaultBackupRestore()
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_backup_restore),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_vault_center),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.drawer_vault_center_sub),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 4: Çöp Kutusu & Veri Saklama
            Text(
                text = stringResource(R.string.settings_section_trash),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VibrantOrange
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Çöp Saklama Süresi Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.delete),
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_trash_retention_title),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_trash_retention_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Retention Options Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        retentionOptions.forEach { (days, label) ->
                            val isSelected = selectedRetentionDays == days
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedRetentionDays = days
                                        prefs.addLocal(Constants.KEY_TRASH_RETENTION_DAYS, days)
                                        context.showToastShort(
                                            context.getString(R.string.settings_trash_retention_applied, label)
                                        )
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) VibrantOrange else BorderLight.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_trash_info),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 3: Uygulama & Güncellemeler
            Text(
                text = stringResource(R.string.settings_section_system),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VibrantOrange
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Güncellemeleri Denetle Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (currentActivity != null) {
                            context.showToastShort(context.getString(R.string.checking_updates_toast))
                            AppUpdateHelper.checkForAppUpdate(
                                activity = currentActivity,
                                isManual = true,
                                onUpdateFound = { data ->
                                    onShowUpdateDialog(data, false)
                                },
                                onUpToDate = {
                                    context.showToastShort(
                                        context.getString(
                                            R.string.app_up_to_date_toast,
                                            "v" + AppUpdateHelper.getCurrentVersionName(context)
                                        )
                                    )
                                }
                            )
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_update_check),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.update_check_menu),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.update_check_menu_sub),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(VibrantOrange)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${AppUpdateHelper.getCurrentVersionName(context)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Sürüm Notları (Yenilikler) Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        val releaseData = AppUpdateHelper.getReleaseHighlights(context)
                        onShowUpdateDialog(releaseData, true)
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_drawer_release_notes),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.drawer_release_notes),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.drawer_release_notes_sub, AppUpdateHelper.getCurrentVersionName(context)),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            onDismiss = { showLanguageSheet = false }
        )
    }
}

@Composable
private fun CloudSyncSettingsSection(
    viewModel: CloudSyncViewModel,
    onNotesSynced: () -> Unit
) {
    val context = LocalContext.current
    val currentActivity = context as? Activity
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.refreshStatus()
        withContext(Dispatchers.Main.immediate) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is CloudSyncUiEffect.ShowMessage -> {
                        val message = context.getString(effect.messageRes, effect.arg1, effect.arg2)
                        context.showToastShort(message)
                        if (effect.messageRes == R.string.cloud_sync_completed_summary && effect.arg2 > 0) {
                            onNotesSynced()
                        }
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftOrangeLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lock),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cloud_sync_card_title),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            if (uiState.isDriveConnected) {
                                R.string.cloud_sync_card_connected
                            } else {
                                R.string.cloud_sync_card_disconnected
                            }
                        ),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    if (uiState.lastSyncPushed != null && uiState.lastSyncPulled != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.cloud_sync_completed_summary,
                                uiState.lastSyncPushed ?: 0,
                                uiState.lastSyncPulled ?: 0
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = VibrantOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!uiState.isDriveConnected) {
                    Button(
                        onClick = {
                            viewModel.onEvent(
                                CloudSyncUiEvent.ConnectGoogleDrive(currentActivity ?: context)
                            )
                        },
                        enabled = !uiState.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibrantOrange,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.cloud_sync_connect_btn),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.onEvent(CloudSyncUiEvent.TriggerSyncNow) },
                        enabled = !uiState.isSyncing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VibrantOrange,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.cloud_sync_now_btn),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.onEvent(CloudSyncUiEvent.DisconnectGoogleDrive) },
                        enabled = !uiState.isSyncing,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cloud_sync_disconnect_btn),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
