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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val DAY_LABELS = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val MONTH_NAMES = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
)

data class CalendarDay(
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val calendar: Calendar
)

@Composable
fun NotionCalendarView(
    notes: List<NoteData>,
    categories: List<CategoryData>,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (targetDateMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    val currentMonthDays = remember(displayedCalendar.get(Calendar.YEAR), displayedCalendar.get(Calendar.MONTH)) {
        generateMonthDays(displayedCalendar)
    }

    // Filter notes matching selected date (same year, month, day)
    val selectedNotes = remember(notes, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.DAY_OF_YEAR)) {
        notes.filter { note ->
            val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
            noteCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR) &&
                    noteCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Notion Calendar Header: Month Year + Previous/Next + Today Button
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Month and Year Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${MONTH_NAMES[displayedCalendar.get(Calendar.MONTH)]} ${displayedCalendar.get(Calendar.YEAR)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "Bugün" (Today) Shortcut
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftOrangeLight)
                            .clickable {
                                displayedCalendar = Calendar.getInstance()
                                selectedCalendar = Calendar.getInstance()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bugün",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VibrantOrange
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Previous Month
                    IconButton(
                        onClick = {
                            val newCal = displayedCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            displayedCalendar = newCal
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Önceki Ay",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Next Month
                    IconButton(
                        onClick = {
                            val newCal = displayedCalendar.clone() as Calendar
                            newCal.add(Calendar.MONTH, 1)
                            displayedCalendar = newCal
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.arrow_left),
                            contentDescription = "Sonraki Ay",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(16.dp)
                                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Weekday header row (Pzt, Sal, Çar, Per, Cum, Cmt, Paz)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            for (label in DAY_LABELS) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Calendar Grid
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(currentMonthDays) { day ->
                    val isSelected = isSameDay(day.calendar, selectedCalendar)
                    val isToday = isSameDay(day.calendar, Calendar.getInstance())

                    // Count notes for this day
                    val dayNotes = notes.filter { note ->
                        val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                        isSameDay(noteCal, day.calendar)
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isSelected -> VibrantOrange
                                    isToday -> SoftOrangeLight
                                    else -> Color.Transparent
                                }
                            )
                            .clickable {
                                selectedCalendar = day.calendar.clone() as Calendar
                            }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "${day.dayOfMonth}",
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> PureWhite
                                    !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    isToday -> VibrantOrange
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )

                            // Little note indicator dot(s)
                            if (dayNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val dotCount = dayNotes.size.coerceAtMost(3)
                                    repeat(dotCount) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) PureWhite else VibrantOrange)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Date Header + Add Note Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateLabel = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr")).format(selectedCalendar.time)
            Text(
                text = dateLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Add note for this date
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CharcoalGray)
                    .clickable { onAddNoteForDate(selectedCalendar.timeInMillis) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus_small),
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Not Ekle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List of notes for the selected date
        if (selectedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bu tarihte kayıtlı bir not yok.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(selectedNotes, key = { it.id }) { note ->
                    val cat = categories.find { it.id == note.categoryId }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNoteClick(note) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!note.icon.isNullOrBlank()) {
                                    Text(text = note.icon!!, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (cat != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SoftOrangeLight)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun generateMonthDays(displayedCalendar: Calendar): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()

    val cal = displayedCalendar.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    // Monday is first day of week in Turkey / European standard
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
    val offset = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    // Previous month padding
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    for (i in 0 until offset) {
        days.add(
            CalendarDay(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false,
                calendar = cal.clone() as Calendar
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    // Current month days
    val currentMonth = displayedCalendar.get(Calendar.MONTH)
    while (cal.get(Calendar.MONTH) == currentMonth) {
        days.add(
            CalendarDay(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = true,
                calendar = cal.clone() as Calendar
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    // Remaining trailing days to complete full 35 or 42 grid
    while (days.size % 7 != 0) {
        days.add(
            CalendarDay(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = false,
                calendar = cal.clone() as Calendar
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }

    return days
}
