package com.notes.purenoteit.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors

@Composable
fun DesktopAnimatedDialog(
    onDismiss: () -> Unit,
    width: Dp = 440.dp,
    title: String? = null,
    subtitle: String? = null,
    badgeIcon: ImageVector? = null,
    badgeTint: Color = DesktopColors.VibrantOrange,
    showCloseIcon: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = remember { Animatable(0.95f) }
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val modalColor = if (isDarkSurface) DesktopColors.SurfaceElevatedDark else DesktopColors.PureWhite

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(20.dp)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = modalColor,
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .widthIn(max = width)
                .fillMaxWidth(0.94f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (title != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (badgeIcon != null) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeTint.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    tint = badgeTint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (showCloseIcon) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Kapat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
                content()
            }
        }
    }
}

@Composable
fun CreateCategoryDialog(
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = categoryName.trim()
    val isDuplicate = remember(trimmed, existingNames) {
        trimmed.isNotEmpty() && existingNames.any { it.equals(trimmed, ignoreCase = true) }
    }
    val canCreate = trimmed.isNotEmpty() && !isDuplicate

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 420.dp,
        title = "Yeni Kategori",
        subtitle = "Notlarınızı daha kolay gruplamak için yeni bir klasör oluşturun.",
        badgeIcon = Icons.Rounded.CreateNewFolder,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        OutlinedTextField(
            value = categoryName,
            onValueChange = { categoryName = it.take(40) },
            label = { Text("Kategori adı") },
            placeholder = { Text("Örn. Projeler, Dersler, Fikirler") },
            singleLine = true,
            isError = isDuplicate,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (canCreate) onConfirm(trimmed) }
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DesktopColors.VibrantOrange,
                focusedLabelColor = DesktopColors.VibrantOrange,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                        canCreate
                    ) {
                        onConfirm(trimmed)
                        true
                    } else {
                        false
                    }
                }
        )

        if (isDuplicate) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bu isimde bir kategori zaten mevcut.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("İptal", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { if (canCreate) onConfirm(trimmed) },
                enabled = canCreate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopColors.VibrantOrange,
                    contentColor = Color.White
                )
            ) {
                Text("Oluştur", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CreateSubPageDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = title.trim()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 430.dp,
        title = "Alt Sayfa Ekle",
        subtitle = "Mevcut notunuzun altına bağlı yeni bir çalışma sayfası oluşturun.",
        badgeIcon = Icons.AutoMirrored.Rounded.NoteAdd,
        badgeTint = DesktopColors.AccentBlue
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            label = { Text("Alt sayfa başlığı") },
            placeholder = { Text("Örn. Toplantı Detayları, Görev Listesi") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { onConfirm(trimmed.ifEmpty { "Alt Sayfa" }) }
            ),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DesktopColors.VibrantOrange,
                focusedLabelColor = DesktopColors.VibrantOrange,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    ) {
                        onConfirm(trimmed.ifEmpty { "Alt Sayfa" })
                        true
                    } else {
                        false
                    }
                }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("İptal", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = { onConfirm(trimmed.ifEmpty { "Alt Sayfa" }) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DesktopColors.VibrantOrange,
                    contentColor = Color.White
                )
            ) {
                Text("Oluştur", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val DESKTOP_NOTE_EMOJIS = listOf(
    "📝", "📌", "💡", "🚀", "📅", "✅",
    "💻", "🔐", "🎯", "📚", "🎨", "⚡",
    "💰", "🗂️", "✨", "🔥", "🌿", "🔍"
)

private fun resolveDesktopNoteIconSpec(
    iconKey: String?,
    isLocked: Boolean
): Pair<ImageVector, Color> = when {
    isLocked -> Icons.Rounded.Lock to DesktopColors.VibrantOrange
    iconKey.isNullOrBlank() || iconKey == "📝" || iconKey == "📄" ->
        Icons.Rounded.Description to DesktopColors.VibrantOrange
    iconKey == "📅" -> Icons.Rounded.CalendarToday to DesktopColors.VibrantOrange
    iconKey == "💰" || iconKey == "💎" -> Icons.Rounded.AccountBalanceWallet to Color(0xFF00897B)
    iconKey == "✅" || iconKey == "📋" -> Icons.Rounded.TaskAlt to DesktopColors.EmeraldGreen
    iconKey == "💼" -> Icons.Rounded.WorkOutline to Color(0xFF795548)
    iconKey == "⚡" || iconKey == "💻" -> Icons.Rounded.Code to DesktopColors.AccentBlue
    iconKey == "🚀" -> Icons.Rounded.RocketLaunch to DesktopColors.VibrantOrange
    iconKey == "🔐" || iconKey == "🛡️" || iconKey == "🔒" ->
        Icons.Rounded.Shield to Color(0xFF8E24AA)
    iconKey == "📌" -> Icons.Rounded.PushPin to DesktopColors.VibrantOrange
    iconKey == "💡" || iconKey == "🧠" -> Icons.Rounded.Lightbulb to Color(0xFFF9A825)
    iconKey == "🎨" -> Icons.Rounded.Palette to Color(0xFF8E24AA)
    iconKey == "📚" || iconKey == "📖" -> Icons.AutoMirrored.Rounded.MenuBook to Color(0xFF3949AB)
    iconKey == "🎯" -> Icons.Rounded.TrackChanges to Color(0xFFE65100)
    iconKey == "🔥" -> Icons.Rounded.LocalFireDepartment to Color(0xFFD32F2F)
    iconKey == "🌱" || iconKey == "🌿" -> Icons.Rounded.Spa to DesktopColors.EmeraldGreen
    iconKey == "🗂️" -> Icons.Rounded.FolderCopy to Color(0xFF546E7A)
    iconKey == "📊" -> Icons.Rounded.BarChart to DesktopColors.AccentBlue
    iconKey == "🎙️" || iconKey == "🗣️" -> Icons.Rounded.Mic to Color(0xFF00796B)
    iconKey == "🥗" -> Icons.Rounded.Restaurant to Color(0xFF43A047)
    iconKey == "💪" -> Icons.Rounded.FitnessCenter to Color(0xFFC2185B)
    iconKey == "🧭" -> Icons.Rounded.Explore to Color(0xFF0277BD)
    iconKey == "🔍" -> Icons.Rounded.Search to Color(0xFF607D8B)
    iconKey == "✨" -> Icons.Rounded.AutoAwesome to Color(0xFFFF8F00)
    else -> Icons.Rounded.Description to DesktopColors.VibrantOrange
}

@Composable
fun DesktopNoteIconBadge(
    iconKey: String?,
    isLocked: Boolean = false,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val (vector, tint) = resolveDesktopNoteIconSpec(iconKey, isLocked)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(tint.copy(alpha = 0.14f))
    ) {
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun DesktopTaskProgressBadge(
    completed: Int,
    total: Int,
    suffix: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircleOutline,
            contentDescription = null,
            tint = DesktopColors.EmeraldGreen,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (suffix.isBlank()) "$completed/$total" else "$completed/$total $suffix",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = DesktopColors.EmeraldGreen,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun NoteEmojiPickerDialog(
    currentEmoji: String,
    onSelectEmoji: (String) -> Unit,
    onDismiss: () -> Unit
) {
    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 390.dp,
        title = "Sayfa Simgesi",
        subtitle = "Notunuza modern bir Material ikon atayın.",
        badgeIcon = Icons.Rounded.Mood,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DESKTOP_NOTE_EMOJIS.chunked(6).forEach { rowEmojis ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowEmojis.forEach { emoji ->
                        val isSelected = emoji == currentEmoji
                        val bgColor by animateColorAsState(
                            if (isSelected) DesktopColors.VibrantOrange.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        val borderColor by animateColorAsState(
                            if (isSelected) DesktopColors.VibrantOrange
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = bgColor,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectEmoji(emoji) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                DesktopNoteIconBadge(
                                    iconKey = emoji,
                                    size = 32.dp,
                                    iconSize = 18.dp,
                                    cornerRadius = 9.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Kapat", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = if (isDestructive) MaterialTheme.colorScheme.error else DesktopColors.VibrantOrange
    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 410.dp,
        title = title,
        badgeIcon = if (isDestructive) Icons.Rounded.DeleteOutline else Icons.Rounded.WarningAmber,
        badgeTint = accent
    ) {
        Text(
            text = message,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("İptal", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
