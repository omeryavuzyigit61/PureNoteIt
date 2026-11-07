package com.notes.purenoteit.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.reminder.AlarmSoundPlayer
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SoundOption(val key: String, val labelRes: Int)

val SOUND_OPTIONS = listOf(
    SoundOption("default", R.string.reminder_sound_default),
    SoundOption("chime", R.string.reminder_sound_chime),
    SoundOption("digital", R.string.reminder_sound_digital),
    SoundOption("gentle", R.string.reminder_sound_gentle)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderBottomSheet(
    initialTimeMs: Long?,
    initialType: String?,
    initialSound: String?,
    onSaveReminder: (reminderTimeMs: Long, reminderType: String, soundType: String) -> Unit,
    onRemoveReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val currentCal = remember {
        Calendar.getInstance().apply {
            if (initialTimeMs != null && initialTimeMs > System.currentTimeMillis()) {
                timeInMillis = initialTimeMs
            } else {
                // Default to next hour
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
        }
    }

    var selectedCalendar by remember { mutableStateOf(currentCal.clone() as Calendar) }
    var selectedType by remember { mutableStateOf(initialType ?: "ALARM") } // "ALARM" or "NOTIFICATION"
    var selectedSound by remember { mutableStateOf(initialSound ?: "default") }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            AlarmSoundPlayer.stop()
        }
    }

    // Native Date & Time dialogs
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = selectedCalendar.clone() as Calendar
                newCal.set(Calendar.YEAR, year)
                newCal.set(Calendar.MONTH, month)
                newCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                selectedCalendar = newCal
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newCal = selectedCalendar.clone() as Calendar
                newCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                newCal.set(Calendar.MINUTE, minute)
                newCal.set(Calendar.SECOND, 0)
                selectedCalendar = newCal
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            true
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Icon + Title + Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) VibrantOrange.copy(alpha = 0.18f)
                            else SoftOrangeLight
                        )
                        .border(
                            1.dp,
                            if (isDark) VibrantOrange.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_alarm_clock),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.reminder_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.reminder_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Presets Row
            Text(
                text = stringResource(R.string.reminder_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // +1 Hour
                QuickPresetChip(label = stringResource(R.string.reminder_quick_1h)) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.HOUR_OF_DAY, 1)
                    selectedCalendar = cal
                }
                // Tonight 20:00
                QuickPresetChip(label = stringResource(R.string.reminder_quick_tonight)) {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, 20)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    selectedCalendar = cal
                }
                // Tomorrow 09:00
                QuickPresetChip(label = stringResource(R.string.reminder_quick_tomorrow)) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 9)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    selectedCalendar = cal
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date and Time Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { datePickerDialog.show() },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.12f) else BorderLight.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.select_date),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(selectedCalendar.time),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Time Box
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { timePickerDialog.show() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color(0xFFFFF8F2),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, VibrantOrange)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.select_time),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFFFA756) else Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedCalendar.time),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) PureWhite else CharcoalGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification Mode Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Alarm Mode Card
                val isAlarm = selectedType == "ALARM"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            selectedType = "ALARM"
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isAlarm && isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        isAlarm -> Color(0xFFFFF8F2)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.35f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        if (isAlarm) 1.5.dp else 1.dp,
                        if (isAlarm) VibrantOrange else if (isDark) Color.White.copy(alpha = 0.12f) else BorderLight.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔔 " + stringResource(R.string.reminder_type_alarm),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isAlarm && isDark -> PureWhite
                                isAlarm -> CharcoalGray
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.reminder_type_alarm_desc),
                            fontSize = 10.5.sp,
                            color = if (isAlarm && isDark) Color(0xFFD4D4D8) else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Silent Notification Mode Card
                val isNotif = selectedType == "NOTIFICATION"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            selectedType = "NOTIFICATION"
                            AlarmSoundPlayer.stop()
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isNotif && isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                        isNotif -> Color(0xFFFFF8F2)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.45f else 0.35f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        if (isNotif) 1.5.dp else 1.dp,
                        if (isNotif) VibrantOrange else if (isDark) Color.White.copy(alpha = 0.12f) else BorderLight.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔕 " + stringResource(R.string.reminder_type_notif),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isNotif && isDark -> PureWhite
                                isNotif -> CharcoalGray
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.reminder_type_notif_desc),
                            fontSize = 10.5.sp,
                            color = if (isNotif && isDark) Color(0xFFD4D4D8) else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Sound Selector
            AnimatedVisibility(
                visible = selectedType == "ALARM",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = stringResource(R.string.reminder_sound_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SOUND_OPTIONS.forEach { soundOption ->
                            val isSelected = selectedSound == soundOption.key

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedSound = soundOption.key
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSelected && isDark -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    isSelected -> Color(0xFFFFF8F2)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.4f else 0.35f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) VibrantOrange else if (isDark) Color.White.copy(alpha = 0.1f) else BorderLight.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    2.dp,
                                                    if (isSelected) VibrantOrange else if (isDark) Color.White.copy(alpha = 0.4f) else BorderLight,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(VibrantOrange)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stringResource(soundOption.labelRes),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected && isDark -> PureWhite
                                                isSelected -> CharcoalGray
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }

                                    // Preview Listen Button (▶)
                                    IconButton(
                                        onClick = {
                                            if (AlarmSoundPlayer.isPlaying()) {
                                                AlarmSoundPlayer.stop()
                                                isPreviewPlaying = false
                                            } else {
                                                AlarmSoundPlayer.play(context, soundOption.key)
                                                isPreviewPlaying = true
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.play_pause),
                                            contentDescription = null,
                                            tint = VibrantOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Button(
                onClick = {
                    AlarmSoundPlayer.stop()
                    onSaveReminder(selectedCalendar.timeInMillis, selectedType, selectedSound)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VibrantOrange,
                    contentColor = PureWhite
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_alarm_clock),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.reminder_btn_set),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (initialTimeMs != null && initialTimeMs > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        AlarmSoundPlayer.stop()
                        onRemoveReminder()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.reminder_btn_remove),
                        color = Color(0xFFEF4444),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.15f) else BorderLight.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
