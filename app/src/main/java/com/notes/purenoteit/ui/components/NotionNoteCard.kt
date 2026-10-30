package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotionNoteCard(
    note: NoteData,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isProtected = note.isPassword == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Primary Icon/Emoji, Pin indicator & Menu button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!note.icon.isNullOrBlank()) {
                        // Prominent Notion Page Emoji
                        Text(
                            text = note.icon ?: "",
                            fontSize = 22.sp
                        )
                    } else {
                        // Category icon pill in PureNoteIt signature orange
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(VibrantOrange),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconRes = when (note.categoryId) {
                                1 -> R.drawable.note
                                2 -> R.drawable.diary
                                3 -> R.drawable.important
                                else -> R.drawable.other
                            }
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pinned Indicator
                    if (note.isPinned) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = "Sabitlendi",
                            tint = VibrantOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // More Menu Button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.menu),
                                contentDescription = "Menü",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(CharcoalGray)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (note.isPinned) "Sabitlemeyi Kaldır" else "Başa Sabitle",
                                        color = PureWhite
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onPinClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pin),
                                        contentDescription = null,
                                        tint = VibrantOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Düzenle", color = PureWhite) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.file_edit),
                                        contentDescription = null,
                                        tint = PureWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Sil", color = Color(0xFFFF5252)) },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.delete),
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = if (isProtected) "******" else (note.title ?: "Başlıksız Not"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Content preview (Bitwarden Mask vs Notion Summary)
            if (isProtected) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.lock),
                        contentDescription = "Kilitli Not",
                        tint = VibrantOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Korumalı Not (AES-256)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val summary = remember(note.description) {
                    val blocks = BlockParser.parse(note.description)
                    BlockParser.toPlainTextSummary(blocks, maxLines = 4)
                }

                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Notion Task Progress Chip (Checklist Badge)
            if (!isProtected && note.totalTasks > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = note.completedTasks.toFloat() / note.totalTasks.toFloat()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_checklist),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Görevler",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${note.completedTasks}/${note.totalTasks}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = VibrantOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Media Indicators (Audio / Images) with Translucent Glass Badges
            val hasImages = !note.images.isNullOrEmpty()
            val hasSounds = !note.sounds.isNullOrEmpty()
            if (!isProtected && (hasImages || hasSounds)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasSounds) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(VibrantOrange.copy(alpha = 0.15f))
                                .border(
                                    0.5.dp,
                                    VibrantOrange.copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.microphone_lines),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${note.sounds?.size} Ses",
                                    fontSize = 10.5.sp,
                                    color = VibrantOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (hasImages) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                                .border(
                                    0.5.dp,
                                    Color(0xFF3B82F6).copy(alpha = 0.35f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.picture),
                                    contentDescription = null,
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${note.images?.size} Görsel",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formatted Date
            val dateStr = remember(note.date) {
                SimpleDateFormat("dd MMM yyyy", Locale("tr")).format(Date(note.date))
            }
            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
