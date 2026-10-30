package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

enum class CalendarTabMode {
    WEEK, MONTH
}

data class CalendarDay(
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val calendar: Calendar
)

data class NotionCategoryPalette(
    val lightBg: Color,
    val darkBg: Color,
    val accentBar: Color,
    val textColor: Color,
    val badgeBg: Color
)

fun getCategoryPalette(categoryId: Int?, categoryName: String?): NotionCategoryPalette {
    return when (categoryId ?: 1) {
        1 -> NotionCategoryPalette(
            lightBg = Color(0xFFFFF3E0), // Soft Peach
            darkBg = Color(0xFF332014),
            accentBar = Color(0xFFFF9800),
            textColor = Color(0xFFE65100),
            badgeBg = Color(0xFFFFE0B2)
        )
        2 -> NotionCategoryPalette(
            lightBg = Color(0xFFE8F5E9), // Soft Mint
            darkBg = Color(0xFF142C19),
            accentBar = Color(0xFF4CAF50),
            textColor = Color(0xFF1B5E20),
            badgeBg = Color(0xFFC8E6C9)
        )
        3 -> NotionCategoryPalette(
            lightBg = Color(0xFFFCE4EC), // Soft Rose
            darkBg = Color(0xFF331620),
            accentBar = Color(0xFFE91E63),
            textColor = Color(0xFF880E4F),
            badgeBg = Color(0xFFF8BBD0)
        )
        4 -> NotionCategoryPalette(
            lightBg = Color(0xFFEDE7F6), // Soft Lavender
            darkBg = Color(0xFF221733),
            accentBar = Color(0xFF9C27B0),
            textColor = Color(0xFF4A148C),
            badgeBg = Color(0xFFD1C4E9)
        )
        5 -> NotionCategoryPalette(
            lightBg = Color(0xFFE1F5FE), // Soft Sky Blue
            darkBg = Color(0xFF132733),
            accentBar = Color(0xFF03A9F4),
            textColor = Color(0xFF01579B),
            badgeBg = Color(0xFFB3E5FC)
        )
        else -> {
            val hash = (categoryName ?: "").hashCode()
            when (kotlin.math.abs(hash) % 4) {
                0 -> NotionCategoryPalette(Color(0xFFFFF8E1), Color(0xFF332A13), Color(0xFFFFB300), Color(0xFFF57F17), Color(0xFFFFECB3))
                1 -> NotionCategoryPalette(Color(0xFFEDE7F6), Color(0xFF211733), Color(0xFF7E57C2), Color(0xFF311B92), Color(0xFFD1C4E9))
                2 -> NotionCategoryPalette(Color(0xFFE0F7FA), Color(0xFF132D30), Color(0xFF00ACC1), Color(0xFF006064), Color(0xFFB2EBF2))
                else -> NotionCategoryPalette(Color(0xFFFBE9E7), Color(0xFF331B15), Color(0xFFFF7043), Color(0xFFBF360C), Color(0xFFFFCCBC))
            }
        }
    }
}

/**
 * Full Notion Calendar Implementation.
 * Supports Week / Month switcher, pastel event card blocks with left accent bars,
 * checklist indicators, category pills, and quick note creation.
 */
@Composable
fun NotionCalendarView(
    notes: List<NoteData>,
    categories: List<CategoryData>,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (targetDateMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var calendarTab by remember { mutableStateOf(CalendarTabMode.WEEK) }

    var displayedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    val currentMonthDays = remember(displayedCalendar.get(Calendar.YEAR), displayedCalendar.get(Calendar.MONTH)) {
        generateMonthDays(displayedCalendar)
    }

    // Days for the currently displayed week
    val currentWeekDays = remember(displayedCalendar.get(Calendar.YEAR), displayedCalendar.get(Calendar.WEEK_OF_YEAR)) {
        generateWeekDays(displayedCalendar)
    }

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        // Notion Calendar Header: Month + Tab Switcher (Haftalık / Aylık) + Navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Top row: Month/Year Title + Prev / Next + Bugün
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${MONTH_NAMES[displayedCalendar.get(Calendar.MONTH)]} ${displayedCalendar.get(Calendar.YEAR)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // "Bugün" (Today) Shortcut
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftOrangeLight)
                                .clickable {
                                    val now = Calendar.getInstance()
                                    displayedCalendar = now.clone() as Calendar
                                    selectedCalendar = now.clone() as Calendar
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

                        Spacer(modifier = Modifier.width(4.dp))

                        // Previous
                        IconButton(
                            onClick = {
                                val newCal = displayedCalendar.clone() as Calendar
                                if (calendarTab == CalendarTabMode.WEEK) {
                                    newCal.add(Calendar.WEEK_OF_YEAR, -1)
                                } else {
                                    newCal.add(Calendar.MONTH, -1)
                                }
                                displayedCalendar = newCal
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_left),
                                contentDescription = "Önceki",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Next
                        IconButton(
                            onClick = {
                                val newCal = displayedCalendar.clone() as Calendar
                                if (calendarTab == CalendarTabMode.WEEK) {
                                    newCal.add(Calendar.WEEK_OF_YEAR, 1)
                                } else {
                                    newCal.add(Calendar.MONTH, 1)
                                }
                                displayedCalendar = newCal
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_left),
                                contentDescription = "Sonraki",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(16.dp)
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // View Switcher: "Haftalık" vs "Aylık" (Notion Calendar Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (calendarTab == CalendarTabMode.WEEK) VibrantOrange else Color.Transparent)
                            .clickable { calendarTab = CalendarTabMode.WEEK }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Haftalık Görünüm",
                            fontSize = 12.sp,
                            fontWeight = if (calendarTab == CalendarTabMode.WEEK) FontWeight.Bold else FontWeight.Medium,
                            color = if (calendarTab == CalendarTabMode.WEEK) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (calendarTab == CalendarTabMode.MONTH) VibrantOrange else Color.Transparent)
                            .clickable { calendarTab = CalendarTabMode.MONTH }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aylık Görünüm",
                            fontSize = 12.sp,
                            fontWeight = if (calendarTab == CalendarTabMode.MONTH) FontWeight.Bold else FontWeight.Medium,
                            color = if (calendarTab == CalendarTabMode.MONTH) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Body: WEEK VIEW vs MONTH VIEW
        if (calendarTab == CalendarTabMode.WEEK) {
            // Notion Weekly Timeline with full Event Card Blocks
            NotionWeekScheduleView(
                weekDays = currentWeekDays,
                notes = notes,
                categories = categories,
                selectedCalendar = selectedCalendar,
                onSelectDay = { selectedCalendar = it },
                onNoteClick = onNoteClick,
                onAddNoteForDate = onAddNoteForDate,
                isDark = isDark
            )
        } else {
            // Notion Monthly Calendar Grid with Day Card Blocks
            NotionMonthScheduleView(
                monthDays = currentMonthDays,
                notes = notes,
                categories = categories,
                selectedCalendar = selectedCalendar,
                onSelectDay = { selectedCalendar = it },
                onNoteClick = onNoteClick,
                onAddNoteForDate = onAddNoteForDate,
                isDark = isDark
            )
        }
    }
}

/**
 * Notion Week Timeline View.
 * Displays horizontal day pill selectors, followed by a vertical breakdown of the days
 * and their rich Notion event cards (matching media_1793352090586_1eaa0937.png).
 */
@Composable
private fun NotionWeekScheduleView(
    weekDays: List<CalendarDay>,
    notes: List<NoteData>,
    categories: List<CategoryData>,
    selectedCalendar: Calendar,
    onSelectDay: (Calendar) -> Unit,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (Long) -> Unit,
    isDark: Boolean
) {
    // 7-Day Header Selector Row
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDays.forEachIndexed { index, day ->
                val isSelected = isSameDay(day.calendar, selectedCalendar)
                val isToday = isSameDay(day.calendar, Calendar.getInstance())

                val dayNotesCount = notes.count { note ->
                    val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                    isSameDay(noteCal, day.calendar)
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isSelected -> VibrantOrange
                                isToday -> SoftOrangeLight
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelectDay(day.calendar.clone() as Calendar) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = DAY_LABELS[index % 7],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isSelected -> PureWhite
                            isToday -> VibrantOrange
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${day.dayOfMonth}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> PureWhite
                            isToday -> VibrantOrange
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (dayNotesCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) PureWhite else VibrantOrange)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(5.dp))
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Days & Event Blocks LazyColumn
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(weekDays) { day ->
            val isToday = isSameDay(day.calendar, Calendar.getInstance())
            val dayNotes = notes.filter { note ->
                val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                isSameDay(noteCal, day.calendar)
            }

            val dayTitle = SimpleDateFormat("d MMMM EEEE", Locale("tr")).format(day.calendar.time)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                // Day Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(VibrantOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = dayTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) VibrantOrange else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // "+ Not" shortcut button for this day
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, BorderLight.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onAddNoteForDate(day.calendar.timeInMillis) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ Not",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VibrantOrange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Event Block Cards
                if (dayNotes.isEmpty()) {
                    // Subtle dashed empty placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                            .border(1.dp, BorderLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable { onAddNoteForDate(day.calendar.timeInMillis) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bu gün için planlanan not yok • Not eklemek için dokun",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayNotes.forEach { note ->
                            NotionCalendarEventCard(
                                note = note,
                                categories = categories,
                                onClick = { onNoteClick(note) },
                                isDark = isDark
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Notion Monthly Schedule View.
 * Displays 7-column calendar grid, and rich Notion Event Cards for the selected day below.
 */
@Composable
private fun NotionMonthScheduleView(
    monthDays: List<CalendarDay>,
    notes: List<NoteData>,
    categories: List<CategoryData>,
    selectedCalendar: Calendar,
    onSelectDay: (Calendar) -> Unit,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (Long) -> Unit,
    isDark: Boolean
) {
    // Weekday Header Row
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

    Spacer(modifier = Modifier.height(4.dp))

    // Monthly Grid
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
            items(monthDays) { day ->
                val isSelected = isSameDay(day.calendar, selectedCalendar)
                val isToday = isSameDay(day.calendar, Calendar.getInstance())

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
                        .clickable { onSelectDay(day.calendar.clone() as Calendar) }
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

                        // Colored Category Event Indicators
                        if (dayNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                val displayNotes = dayNotes.take(3)
                                displayNotes.forEach { note ->
                                    val cat = categories.find { it.id == note.categoryId }
                                    val palette = getCategoryPalette(note.categoryId, cat?.name)
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) PureWhite else palette.accentBar)
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
    val selectedNotes = notes.filter { note ->
        val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
        isSameDay(noteCal, selectedCalendar)
    }

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

    // List of Notion Event Cards for the selected day
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
                NotionCalendarEventCard(
                    note = note,
                    categories = categories,
                    onClick = { onNoteClick(note) },
                    isDark = isDark
                )
            }
        }
    }
}

/**
 * Rich Notion Calendar Event Block Card.
 * Matches Notion Calendar / Google Calendar layout:
 * - Solid category color stripe on the left (4dp)
 * - Soft pastel background tint
 * - Time badge, emoji, title, category pill, checklist status
 */
@Composable
fun NotionCalendarEventCard(
    note: NoteData,
    categories: List<CategoryData>,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cat = categories.find { it.id == note.categoryId }
    val palette = getCategoryPalette(note.categoryId, cat?.name)

    val bgColor = if (isDark) palette.darkBg else palette.lightBg
    val timeLabel = SimpleDateFormat("HH:mm", Locale("tr")).format(note.date)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accentBar.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Notion Signature Left Accent Stripe (4dp solid bar)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(64.dp)
                    .background(palette.accentBar)
            )

            // Card Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: Title + Time badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!note.icon.isNullOrBlank()) {
                            Text(text = note.icon!!, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.badgeBg.copy(alpha = if (isDark) 0.3f else 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) PureWhite else palette.textColor
                        )
                    }
                }

                // Bottom row: Category Chip + Tasks Progress Badge + Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Category Chip
                    if (cat != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.accentBar.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) PureWhite else palette.textColor
                            )
                        }
                    }

                    // Task Checklist Counter (e.g. ✓ 2/5)
                    if (note.totalTasks > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✓ ${note.completedTasks}/${note.totalTasks}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Pinned badge
                    if (note.isPinned) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SoftOrangeLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Sabitlendi",
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

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun generateWeekDays(displayedCalendar: Calendar): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val cal = displayedCalendar.clone() as Calendar

    // Find the Monday of this week
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
    cal.add(Calendar.DAY_OF_MONTH, diffToMonday)

    for (i in 0 until 7) {
        days.add(
            CalendarDay(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                isCurrentMonth = cal.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH),
                calendar = cal.clone() as Calendar
            )
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return days
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
