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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
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
    searchQuery: String = "",
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
                onStatusChange = { newStatus -> onStatusChange(note, newStatus) },
                searchQuery = searchQuery
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
    onStatusChange: (String) -> Unit,
    searchQuery: String = ""
) {
    var showMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    val category = categories.find { it.id == note.categoryId }

    val currentStatusDef = KANBAN_COLUMNS.find { it.statusKey == (note.status ?: "TODO") } ?: KANBAN_COLUMNS[0]
    val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(note.date))

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

            // Title + tasks count + search snippet
            val isProtected = note.isPassword == true || note.isEncrypted || category?.isPassword == true
            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = when {
                    isProtected -> "******"
                    note.title.isNullOrBlank() -> stringResource(R.string.untitled_note)
                    else -> note.title!!
                }
                val titleAnnotated = remember(displayTitle, searchQuery, isProtected) {
                    if (searchQuery.isNotBlank() && !isProtected) {
                        val lowerText = displayTitle.lowercase(Locale.getDefault())
                        val lowerQ = searchQuery.trim().lowercase(Locale.getDefault())
                        val builder = AnnotatedString.Builder()
                        var curr = 0
                        while (curr < displayTitle.length) {
                            val idx = lowerText.indexOf(lowerQ, curr)
                            if (idx == -1) {
                                builder.append(displayTitle.substring(curr))
                                break
                            }
                            if (idx > curr) {
                                builder.append(displayTitle.substring(curr, idx))
                            }
                            val end = (idx + lowerQ.length).coerceAtMost(displayTitle.length)
                            builder.pushStyle(
                                SpanStyle(
                                    background = Color(0xFFFFE082),
                                    color = Color(0xFFD84315),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            builder.append(displayTitle.substring(idx, end))
                            builder.pop()
                            curr = end
                        }
                        builder.toAnnotatedString()
                    } else {
                        AnnotatedString(displayTitle)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = titleAnnotated,
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
                            contentDescription = stringResource(R.string.pin_to_top),
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (note.isPassword == true) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.lock),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (note.reminderTime != null && note.reminderTime!! > System.currentTimeMillis() && !note.isReminderDone) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_alarm_clock),
                            contentDescription = null,
                            tint = VibrantOrange,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // In-content deep search snippet
                val searchSnippetMatch = remember(note.description, searchQuery) {
                    if (searchQuery.isNotBlank() && !isProtected) {
                        BlockParser.findSearchSnippet(note.description, searchQuery)
                    } else null
                }
                if (searchSnippetMatch != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val snippetText = searchSnippetMatch.snippet
                    val start = searchSnippetMatch.matchStart
                    val end = searchSnippetMatch.matchEnd
                    val annotatedSnippet = remember(snippetText, start, end) {
                        buildAnnotatedString {
                            if (start in 0..snippetText.length && end in start..snippetText.length) {
                                append(snippetText.substring(0, start))
                                pushStyle(
                                    SpanStyle(
                                        background = Color(0xFFFFE082),
                                        color = Color(0xFFB71C1C),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                append(snippetText.substring(start, end))
                                pop()
                                append(snippetText.substring(end))
                            } else {
                                append(snippetText)
                            }
                        }
                    }
                    Text(
                        text = annotatedSnippet,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (note.totalTasks > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${note.completedTasks}/${note.totalTasks}",
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
                        text = stringResource(currentStatusDef.titleRes),
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
                            text = { Text(stringResource(col.titleRes)) },
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (note.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin_to_top)) },
                        onClick = {
                            showMenu = false
                            onPinClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = Color(0xFFE53935)) },
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
