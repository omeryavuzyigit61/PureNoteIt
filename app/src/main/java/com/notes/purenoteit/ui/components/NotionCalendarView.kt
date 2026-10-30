package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
 * Authentic Notion Calendar Implementation.
 * Faithful to Notion Calendar / Cron (media_1793353365373_803ced5b.png):
 * - Multi-column timeline grid where notes sit DIRECTLY INSIDE the calendar columns.
 * - Auto-scrolls and prioritizes TODAY at the front so users never have to search for today.
 * - Correct directional navigation arrows (R.drawable.arrow_left and R.drawable.arrow_right).
 * - Month view with notes rendered as colored blocks inside the calendar day cells.
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

    // Days for the currently displayed week (Monday to Sunday)
    val currentWeekDays = remember(displayedCalendar.get(Calendar.YEAR), displayedCalendar.get(Calendar.WEEK_OF_YEAR)) {
        generateWeekDays(displayedCalendar)
    }

    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
                    .padding(horizontal = 14.dp, vertical = 10.dp)
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
                                .padding(horizontal = 9.dp, vertical = 4.dp),
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

                        // Previous Arrow (<)
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

                        // Next Arrow (->) pointing right
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
                                painter = painterResource(id = R.drawable.arrow_right),
                                contentDescription = "Sonraki",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
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
                            text = "Haftalık Kolon Takvim",
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
                            text = "Aylık Tablo Görünümü",
                            fontSize = 12.sp,
                            fontWeight = if (calendarTab == CalendarTabMode.MONTH) FontWeight.Bold else FontWeight.Medium,
                            color = if (calendarTab == CalendarTabMode.MONTH) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Body: Authentic Notion Multi-Column Week Grid vs Month Grid
        if (calendarTab == CalendarTabMode.WEEK) {
            NotionMultiColumnWeekView(
                weekDays = currentWeekDays,
                notes = notes,
                categories = categories,
                selectedCalendar = selectedCalendar,
                onNoteClick = onNoteClick,
                onAddNoteForDate = onAddNoteForDate,
                isDark = isDark
            )
        } else {
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
 * Authentic Notion Calendar Multi-Column Week Grid.
 * Displays horizontal day columns with vertical grid dividers (matching media_1793353365373_803ced5b.png).
 * Notes sit DIRECTLY INSIDE the day columns side-by-side.
 * Today is automatically prioritized and scrolled into view at the start!
 */
@Composable
private fun NotionMultiColumnWeekView(
    weekDays: List<CalendarDay>,
    notes: List<NoteData>,
    categories: List<CategoryData>,
    selectedCalendar: Calendar,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (Long) -> Unit,
    isDark: Boolean
) {
    val listState = rememberLazyListState()

    // Automatically scroll to Today so user NEVER has to scroll down or search for today
    val todayIndex = remember(weekDays) {
        weekDays.indexOfFirst { isSameDay(it.calendar, Calendar.getInstance()) }
    }

    LaunchedEffect(todayIndex) {
        if (todayIndex >= 0) {
            listState.animateScrollToItem(todayIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, BorderLight.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(weekDays) { day ->
                val isToday = isSameDay(day.calendar, Calendar.getInstance())
                val dayNotes = notes.filter { note ->
                    val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                    isSameDay(noteCal, day.calendar)
                }

                // Notion Day Column (Notes reside directly inside this column container!)
                Column(
                    modifier = Modifier
                        .width(175.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                        .border(
                            width = 1.dp,
                            color = if (isToday) VibrantOrange.copy(alpha = 0.4f) else BorderLight.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isToday) SoftOrangeLight.copy(alpha = 0.25f) else Color.Transparent)
                ) {
                    // Column Header: Day Name + Date Pill (matching Notion Calendar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isToday) SoftOrangeLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SimpleDateFormat("EEE", Locale("tr")).format(day.calendar.time),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) VibrantOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isToday) VibrantOrange else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${day.dayOfMonth}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isToday) PureWhite else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Mini "+ Not" button on header
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onAddNoteForDate(day.calendar.timeInMillis) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+ Not",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange
                            )
                        }
                    }

                    // Inside the Column: Vertical scrollable list of notes for this day
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (dayNotes.isEmpty()) {
                            // Dashed Empty Slot inside column (tap to add)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .border(1.dp, BorderLight.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable { onAddNoteForDate(day.calendar.timeInMillis) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Not Ekle",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            dayNotes.forEach { note ->
                                NotionCalendarEventCard(
                                    note = note,
                                    categories = categories,
                                    onClick = { onNoteClick(note) },
                                    isDark = isDark,
                                    isColumnMode = true
                                )
                            }

                            // Quick add button under notes
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAddNoteForDate(day.calendar.timeInMillis) }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Not Ekle",
                                    fontSize = 10.sp,
                                    color = VibrantOrange,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Notion Monthly Schedule View.
 * In the month grid, notes appear DIRECTLY INSIDE the calendar day cells as colored chips!
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

    // Monthly Grid with Event Pills INSIDE Cells
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
                .padding(6.dp),
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
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isSelected) VibrantOrange else BorderLight.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            when {
                                isToday -> SoftOrangeLight.copy(alpha = 0.5f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelectDay(day.calendar.clone() as Calendar) }
                        .padding(2.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Day Number
                        Text(
                            text = "${day.dayOfMonth}",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isToday -> VibrantOrange
                                !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(start = 2.dp, top = 1.dp)
                        )

                        // Colored Event Blocks DIRECTLY INSIDE the Calendar Cell!
                        if (dayNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val displayNotes = dayNotes.take(2)
                            displayNotes.forEach { note ->
                                val cat = categories.find { it.id == note.categoryId }
                                val palette = getCategoryPalette(note.categoryId, cat?.name)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(palette.accentBar.copy(alpha = 0.85f))
                                        .padding(horizontal = 2.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (note.title.isNullOrBlank()) "Not" else note.title!!,
                                        fontSize = 8.sp,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

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
                .padding(horizontal = 10.dp, vertical = 4.dp),
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

    Spacer(modifier = Modifier.height(6.dp))

    // Detailed Event Cards for the selected day
    if (selectedNotes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Bu tarihte kayıtlı bir not yok.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(selectedNotes, key = { it.id }) { note ->
                NotionCalendarEventCard(
                    note = note,
                    categories = categories,
                    onClick = { onNoteClick(note) },
                    isDark = isDark,
                    isColumnMode = false
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
    isColumnMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cat = categories.find { it.id == note.categoryId }
    val palette = getCategoryPalette(note.categoryId, cat?.name)

    val bgColor = if (isDark) palette.darkBg else palette.lightBg
    val timeLabel = SimpleDateFormat("HH:mm", Locale("tr")).format(note.date)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.accentBar.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Notion Signature Left Accent Stripe (4dp solid bar)
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .height(if (isColumnMode) 68.dp else 56.dp)
                    .background(palette.accentBar)
            )

            // Card Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                            Text(text = note.icon!!, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (note.title.isNullOrBlank()) "Başlıksız" else note.title!!,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.badgeBg.copy(alpha = if (isDark) 0.3f else 0.8f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) PureWhite else palette.textColor
                        )
                    }
                }

                // Bottom row: Category Chip + Tasks Progress Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Category Chip
                    if (cat != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(palette.accentBar.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) PureWhite else palette.textColor
                            )
                        }
                    }

                    // Task Checklist Counter (e.g. ✓ 2/5)
                    if (note.totalTasks > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "✓ ${note.completedTasks}/${note.totalTasks}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

    // Find Monday of this week
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
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
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
