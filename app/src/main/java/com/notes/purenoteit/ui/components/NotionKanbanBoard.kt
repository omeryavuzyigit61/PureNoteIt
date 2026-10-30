package com.notes.purenoteit.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange

data class KanbanColumnDef(
    val statusKey: String,
    val title: String,
    val badgeBgColor: Color,
    val badgeTextColor: Color,
    val dotColor: Color
)

val KANBAN_COLUMNS = listOf(
    KanbanColumnDef(
        statusKey = "TODO",
        title = "Yapılacaklar",
        badgeBgColor = Color(0xFFF3E8FF),
        badgeTextColor = Color(0xFF7E22CE),
        dotColor = Color(0xFFA855F7)
    ),
    KanbanColumnDef(
        statusKey = "IN_PROGRESS",
        title = "Devam Edenler",
        badgeBgColor = Color(0xFFFEF3C7),
        badgeTextColor = Color(0xFFB45309),
        dotColor = Color(0xFFF59E0B)
    ),
    KanbanColumnDef(
        statusKey = "IN_REVIEW",
        title = "İnceleniyor",
        badgeBgColor = Color(0xFFE0F2FE),
        badgeTextColor = Color(0xFF0369A1),
        dotColor = Color(0xFF0EA5E9)
    ),
    KanbanColumnDef(
        statusKey = "DONE",
        title = "Tamamlandı",
        badgeBgColor = Color(0xFFDCFCE7),
        badgeTextColor = Color(0xFF15803D),
        dotColor = Color(0xFF22C55E)
    )
)

@Composable
fun NotionKanbanBoard(
    notes: List<NoteData>,
    categories: List<CategoryData>,
    onNoteClick: (NoteData) -> Unit,
    onStatusChange: (NoteData, String) -> Unit,
    onAddNewNoteWithStatus: (status: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (column in KANBAN_COLUMNS) {
            val columnNotes = notes.filter { (it.status ?: "TODO") == column.statusKey }

            KanbanColumn(
                columnDef = column,
                notes = columnNotes,
                categories = categories,
                onNoteClick = onNoteClick,
                onStatusChange = onStatusChange,
                onAddNewNote = { onAddNewNoteWithStatus(column.statusKey) },
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun KanbanColumn(
    columnDef: KanbanColumnDef,
    notes: List<NoteData>,
    categories: List<CategoryData>,
    onNoteClick: (NoteData) -> Unit,
    onStatusChange: (NoteData, String) -> Unit,
    onAddNewNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Column Header: Status Badge + Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge with dot
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(columnDef.badgeBgColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(columnDef.dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = columnDef.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = columnDef.badgeTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Count Badge
                    Text(
                        text = "${notes.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Add button
                IconButton(
                    onClick = onAddNewNote,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus_small),
                        contentDescription = "Not Ekle",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cards in column
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    KanbanNoteCard(
                        note = note,
                        categories = categories,
                        currentColumn = columnDef,
                        onNoteClick = { onNoteClick(note) },
                        onStatusChange = { newStatus -> onStatusChange(note, newStatus) }
                    )
                }

                // Quick "+ Yeni Not" button at column bottom like Notion
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddNewNote() }
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.plus_small),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Yeni Not Ekle",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanNoteCard(
    note: NoteData,
    categories: List<CategoryData>,
    currentColumn: KanbanColumnDef,
    onNoteClick: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    val category = categories.find { it.id == note.categoryId }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onNoteClick() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.7f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Emoji / Icon + Title + Status Switcher Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!note.icon.isNullOrBlank()) {
                        Text(text = note.icon!!, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Switcher Pill (Click to move to another column!)
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(currentColumn.badgeBgColor)
                            .clickable { showStatusMenu = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentColumn.title,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentColumn.badgeTextColor
                        )
                    }

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        for (col in KANBAN_COLUMNS) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(col.dotColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(col.title)
                                    }
                                },
                                onClick = {
                                    showStatusMenu = false
                                    onStatusChange(col.statusKey)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer info: Category Tag + To-do progress + Lock indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category tag
                if (category != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SoftOrangeLight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Task Progress (e.g. 2/5)
                    if (note.totalTasks > 0) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_checklist),
                            contentDescription = null,
                            tint = if (note.completedTasks == note.totalTasks) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${note.completedTasks}/${note.totalTasks}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Encrypted lock icon
                    if (note.isPassword == true) {
                        Icon(
                            painter = painterResource(id = R.drawable.lock),
                            contentDescription = "Kilitli",
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
