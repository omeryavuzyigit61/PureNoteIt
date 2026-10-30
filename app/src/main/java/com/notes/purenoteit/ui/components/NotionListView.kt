package com.notes.purenoteit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotionListView(
    notes: List<NoteData>,
    categories: List<CategoryData>,
    onNoteClick: (NoteData) -> Unit,
    onPinClick: (NoteData) -> Unit,
    onDeleteClick: (NoteData) -> Unit,
    onStatusChange: (NoteData, String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        items(notes, key = { it.id }) { note ->
            NotionListRow(
                note = note,
                categories = categories,
                onNoteClick = { onNoteClick(note) },
                onPinClick = { onPinClick(note) },
                onDeleteClick = { onDeleteClick(note) },
                onStatusChange = { newStatus -> onStatusChange(note, newStatus) }
            )
        }
    }
}

@Composable
private fun NotionListRow(
    note: NoteData,
    categories: List<CategoryData>,
    onNoteClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    val category = categories.find { it.id == note.categoryId }

    val currentStatusDef = KANBAN_COLUMNS.find { it.statusKey == (note.status ?: "TODO") } ?: KANBAN_COLUMNS[0]
    val dateStr = SimpleDateFormat("dd MMM", Locale("tr")).format(Date(note.date))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onNoteClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f)),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or default note icon
            if (!note.icon.isNullOrBlank()) {
                Text(text = note.icon!!, fontSize = 18.sp)
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftOrangeLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.note),
                        contentDescription = null,
                        tint = VibrantOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title + tasks count
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = "Sabitlendi",
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (note.isPassword == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.lock),
                            contentDescription = "Kilitli",
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (note.totalTasks > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Görevler: ${note.completedTasks}/${note.totalTasks}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Status Pill (Clickable to switch status)
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(currentStatusDef.badgeBgColor)
                        .clickable { showStatusMenu = true }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentStatusDef.title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentStatusDef.badgeTextColor
                    )
                }

                DropdownMenu(
                    expanded = showStatusMenu,
                    onDismissRequest = { showStatusMenu = false }
                ) {
                    for (col in KANBAN_COLUMNS) {
                        DropdownMenuItem(
                            text = { Text(col.title) },
                            onClick = {
                                showStatusMenu = false
                                onStatusChange(col.statusKey)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Category Badge
            if (category != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SoftOrangeLight)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = category.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VibrantOrange
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Date
            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )

            // More Options Dropdown
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.category_alt),
                        contentDescription = "Seçenekler",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (note.isPinned) "Sabitlemeyi Kaldır" else "Başa Sabitle") },
                        onClick = {
                            showMenu = false
                            onPinClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sil", color = Color(0xFFE53935)) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}
