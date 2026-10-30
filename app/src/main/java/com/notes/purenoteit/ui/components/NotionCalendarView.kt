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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.luminance
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

enum class CalendarTabMode(val label: String, val dayCount: Int) {
    FIVE_DAYS("5 Gün", 5),
    THREE_DAYS("3 Gün", 3),
    ONE_DAY("1 Gün", 1),
    MONTH("Aylık", 30)
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
    val lightText: Color,
    val darkText: Color,
    val badgeBgLight: Color,
    val badgeBgDark: Color
)

fun getCategoryPalette(categoryId: Int?, categoryName: String?): NotionCategoryPalette {
    return when (categoryId ?: 1) {
        1 -> NotionCategoryPalette(
            lightBg = Color(0xFFFFF7ED), // Soft Orange Tint
            darkBg = Color(0xFF241E1A),  // Deep Warm Charcoal
            accentBar = Color(0xFFFF7A00), // Vibrant Orange
            lightText = Color(0xFF9A3412),
            darkText = PureWhite,
            badgeBgLight = Color(0xFFFFEDD5),
            badgeBgDark = Color(0xFF382314)
        )
        2 -> NotionCategoryPalette(
            lightBg = Color(0xFFF0FDF4), // Soft Mint
            darkBg = Color(0xFF16231A),  // Deep Green Charcoal
            accentBar = Color(0xFF10B981), // Emerald
            lightText = Color(0xFF065F46),
            darkText = PureWhite,
            badgeBgLight = Color(0xFFD1FAE5),
            badgeBgDark = Color(0xFF133221)
        )
        3 -> NotionCategoryPalette(
            lightBg = Color(0xFFFFF1F2), // Soft Rose
            darkBg = Color(0xFF26181C),  // Deep Rose Charcoal
            accentBar = Color(0xFFF43F5E), // Rose
            lightText = Color(0xFF9F1239),
            darkText = PureWhite,
            badgeBgLight = Color(0xFFFFE4E6),
            badgeBgDark = Color(0xFF3B1720)
        )
        4 -> NotionCategoryPalette(
            lightBg = Color(0xFFF5F3FF), // Soft Purple
            darkBg = Color(0xFF1F1A28),  // Deep Purple Charcoal
            accentBar = Color(0xFF8B5CF6), // Purple
            lightText = Color(0xFF5B21B6),
            darkText = PureWhite,
            badgeBgLight = Color(0xFFEDE9FE),
            badgeBgDark = Color(0xFF2C1F3F)
        )
        5 -> NotionCategoryPalette(
            lightBg = Color(0xFFEFF6FF), // Soft Sky
            darkBg = Color(0xFF171E28),  // Deep Blue Charcoal
            accentBar = Color(0xFF3B82F6), // Blue
            lightText = Color(0xFF1E40AF),
            darkText = PureWhite,
            badgeBgLight = Color(0xFFDBEAFE),
            badgeBgDark = Color(0xFF17283E)
        )
        else -> {
            val hash = (categoryName ?: "").hashCode()
            when (kotlin.math.abs(hash) % 4) {
                0 -> NotionCategoryPalette(Color(0xFFFFFBEB), Color(0xFF242017), Color(0xFFF59E0B), Color(0xFF92400E), PureWhite, Color(0xFFFEF3C7), Color(0xFF362512))
                1 -> NotionCategoryPalette(Color(0xFFF3E8FF), Color(0xFF201828), Color(0xFFA855F7), Color(0xFF6B21A8), PureWhite, Color(0xFFF3E8FF), Color(0xFF311C3D))
                2 -> NotionCategoryPalette(Color(0xFFECFEFF), Color(0xFF152225), Color(0xFF06B6D4), Color(0xFF0E7490), PureWhite, Color(0xFFCFFAFE), Color(0xFF122C33))
                else -> NotionCategoryPalette(Color(0xFFFFF7ED), Color(0xFF241C18), Color(0xFFEA580C), Color(0xFF9A3412), PureWhite, Color(0xFFFFEDD5), Color(0xFF381F17))
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
    var calendarTab by remember { mutableStateOf(CalendarTabMode.FIVE_DAYS) }

    var displayedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    var selectedCalendar by remember {
        mutableStateOf(Calendar.getInstance())
    }

    val currentMonthDays = remember(displayedCalendar.get(Calendar.YEAR), displayedCalendar.get(Calendar.MONTH)) {
        generateMonthDays(displayedCalendar)
    }

    val displayedDays = remember(displayedCalendar.timeInMillis, calendarTab) {
        generateDaysForMode(displayedCalendar, calendarTab)
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // Notion Calendar Header: Month + Tab Switcher (5 Gün / 3 Gün / 1 Gün / Aylık) + Navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) Color(0xFF2A2A2E) else BorderLight.copy(alpha = 0.6f)
            )
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
                                .background(if (isDark) Color(0xFF2E1C12) else SoftOrangeLight)
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Previous Arrow (<)
                        IconButton(
                            onClick = {
                                val newCal = displayedCalendar.clone() as Calendar
                                when (calendarTab) {
                                    CalendarTabMode.FIVE_DAYS -> newCal.add(Calendar.DAY_OF_YEAR, -5)
                                    CalendarTabMode.THREE_DAYS -> newCal.add(Calendar.DAY_OF_YEAR, -3)
                                    CalendarTabMode.ONE_DAY -> newCal.add(Calendar.DAY_OF_YEAR, -1)
                                    CalendarTabMode.MONTH -> newCal.add(Calendar.MONTH, -1)
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
                                when (calendarTab) {
                                    CalendarTabMode.FIVE_DAYS -> newCal.add(Calendar.DAY_OF_YEAR, 5)
                                    CalendarTabMode.THREE_DAYS -> newCal.add(Calendar.DAY_OF_YEAR, 3)
                                    CalendarTabMode.ONE_DAY -> newCal.add(Calendar.DAY_OF_YEAR, 1)
                                    CalendarTabMode.MONTH -> newCal.add(Calendar.MONTH, 1)
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

                Spacer(modifier = Modifier.height(10.dp))

                // View Switcher: "5 Gün", "3 Gün", "1 Gün", "Aylık" (Notion Calendar Style)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF1E1E22) else MaterialTheme.colorScheme.background)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (mode in CalendarTabMode.values()) {
                        val isSelected = calendarTab == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) VibrantOrange else Color.Transparent)
                                .clickable { calendarTab = mode }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PureWhite else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Body: Authentic Notion Multi-Column Schedule vs Month Grid
        if (calendarTab == CalendarTabMode.MONTH) {
            NotionMonthScheduleView(
                monthDays = currentMonthDays,
                notes = notes,
                categories = categories,
                selectedCalendar = selectedCalendar,
                onSelectDay = { selectedCalendar = it },
                onNoteClick = onNoteClick,
                onAddNoteForDate = onAddNoteForDate,
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        } else {
            NotionMultiColumnWeekView(
                days = displayedDays,
                tabMode = calendarTab,
                notes = notes,
                categories = categories,
                selectedCalendar = selectedCalendar,
                onNoteClick = onNoteClick,
                onAddNoteForDate = onAddNoteForDate,
                isDark = isDark,
                modifier = Modifier.weight(1f)
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
    days: List<CalendarDay>,
    tabMode: CalendarTabMode,
    notes: List<NoteData>,
    categories: List<CategoryData>,
    selectedCalendar: Calendar,
    onNoteClick: (NoteData) -> Unit,
    onAddNoteForDate: (Long) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Automatically scroll to Today so user NEVER has to scroll down or search for today
    val todayIndex = remember(days) {
        days.indexOfFirst { isSameDay(it.calendar, Calendar.getInstance()) }
    }

    LaunchedEffect(todayIndex) {
        if (todayIndex >= 0) {
            listState.animateScrollToItem(todayIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                if (isDark) Color(0xFF2A2A2E) else BorderLight.copy(alpha = 0.6f),
                RoundedCornerShape(18.dp)
            ),
        color = if (isDark) Color(0xFF131316) else MaterialTheme.colorScheme.surface
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            items(days) { day ->
                val isToday = isSameDay(day.calendar, Calendar.getInstance())
                val dayNotes = notes.filter { note ->
                    val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                    isSameDay(noteCal, day.calendar)
                }

                val columnWidth = when (tabMode) {
                    CalendarTabMode.ONE_DAY -> 330.dp
                    CalendarTabMode.THREE_DAYS -> 190.dp
                    else -> 145.dp
                }

                val columnBg = if (isToday) {
                    if (isDark) Color(0xFF221A14) else SoftOrangeLight.copy(alpha = 0.35f)
                } else {
                    if (isDark) Color(0xFF18181C) else Color(0xFFFAFAFC)
                }

                val columnBorder = if (isToday) {
                    VibrantOrange.copy(alpha = 0.7f)
                } else {
                    if (isDark) Color(0xFF2A2A2E) else Color(0xFFE5E7EB)
                }

                // Notion Day Column (Notes reside directly inside this column container!)
                Column(
                    modifier = Modifier
                        .width(columnWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                        .border(
                            width = 1.dp,
                            color = columnBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(columnBg)
                ) {
                    // Column Header: Day Name + Date Pill (matching Notion Calendar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isToday) {
                                    if (isDark) Color(0xFF2E1C12) else SoftOrangeLight
                                } else {
                                    if (isDark) Color(0xFF1E1E22) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SimpleDateFormat("EEE", Locale("tr")).format(day.calendar.time),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) VibrantOrange else if (isDark) PureWhite.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                                    color = if (isToday) PureWhite else if (isDark) PureWhite else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Mini "+ Not" button on header
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF2E1C12) else MaterialTheme.colorScheme.surface)
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

                    // Inside the Column: Vertical scrollable list of notes + Notion 24h Timeline
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Day Notes (Rendered directly in the column with crisp contrast)
                        if (dayNotes.isNotEmpty()) {
                            dayNotes.forEach { note ->
                                NotionCalendarEventCard(
                                    note = note,
                                    categories = categories,
                                    onClick = { onNoteClick(note) },
                                    isDark = isDark,
                                    isColumnMode = true
                                )
                            }
                        }

                        // 2. Notion Timeline Hours with Live Now Indicator (Görsel 4'ten Esinlenildi)
                        val timelineMilestones = listOf(9, 11, 13, 15, 17, 19, 21)
                        val nowCal = Calendar.getInstance()
                        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)

                        for (hour in timelineMilestones) {
                            val slotCal = day.calendar.clone() as Calendar
                            slotCal.set(Calendar.HOUR_OF_DAY, hour)
                            slotCal.set(Calendar.MINUTE, 0)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Live "Now" Indicator Line if today and matching current hour block
                                if (isToday && (currentHour == hour || (currentHour > hour && currentHour < hour + 2))) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFEF4444))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(nowCal.time),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            thickness = 1.5.dp,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }

                                // Timeline Slot
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onAddNoteForDate(slotCal.timeInMillis) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${hour.toString().padStart(2, '0')}:00",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) PureWhite.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 6.dp),
                                        thickness = 0.5.dp,
                                        color = if (isDark) Color(0xFF2A2A2E) else Color(0xFFE5E7EB)
                                    )
                                    Text(
                                        text = "+",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VibrantOrange.copy(alpha = 0.7f)
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

/**
 * Notion Monthly Schedule View (Package 1: Event Dots + Unified Page Scroll).
 * - Cells display elegant category event dots (● ● ●) instead of cramped/clipped text blobs.
 * - The entire monthly view scrolls as a single unified vertical document, eliminating the
 *   claustrophobic height trap and allowing selected day notes to expand freely across the screen.
 * - Includes generous 110dp bottom spacing so cards never hide under the floating search dock.
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
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
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
                    color = if (isDark) PureWhite.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Monthly Grid with Event Dots INSIDE Cells
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) Color(0xFF161619) else MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) Color(0xFF2A2A2E) else BorderLight.copy(alpha = 0.6f)
            )
        ) {
            val weeks = remember(monthDays) { monthDays.chunked(7) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        week.forEach { day ->
                            val isSelected = isSameDay(day.calendar, selectedCalendar)
                            val isToday = isSameDay(day.calendar, Calendar.getInstance())

                            val dayNotes = notes.filter { note ->
                                val noteCal = Calendar.getInstance().apply { timeInMillis = note.date }
                                isSameDay(noteCal, day.calendar)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) VibrantOrange else if (isDark) Color(0xFF2A2A2E) else BorderLight.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        when {
                                            isToday -> if (isDark) Color(0xFF2E1C12) else SoftOrangeLight.copy(alpha = 0.5f)
                                            isSelected -> if (isDark) Color(0xFF1F1F24) else Color(0xFFF3F4F6)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectDay(day.calendar.clone() as Calendar) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Day Number
                                    Text(
                                        text = "${day.dayOfMonth}",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isToday -> VibrantOrange
                                            !day.isCurrentMonth -> if (isDark) PureWhite.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            else -> if (isDark) PureWhite.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                                        }
                                    )

                                    // Category Event Dots (● ● ●) instead of cramped text blobs!
                                    if (dayNotes.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(bottom = 2.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val dotsToShow = dayNotes.take(3)
                                            dotsToShow.forEach { note ->
                                                val cat = categories.find { it.id == note.categoryId }
                                                val palette = getCategoryPalette(note.categoryId, cat?.name)
                                                Box(
                                                    modifier = Modifier
                                                        .padding(horizontal = 1.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.accentBar)
                                                )
                                            }
                                            if (dayNotes.size > 3) {
                                                Text(
                                                    text = "+",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) PureWhite.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(start = 1.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) PureWhite else MaterialTheme.colorScheme.onSurface
            )

            // Add note for this date
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(VibrantOrange)
                    .clickable { onAddNoteForDate(selectedCalendar.timeInMillis) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.plus_small),
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Not Ekle",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Detailed Event Cards for the selected day (Full page flow without height trap!)
        if (selectedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bu tarihte kayıtlı bir not yok.",
                    fontSize = 12.5.sp,
                    color = if (isDark) PureWhite.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedNotes.forEach { note ->
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

        // Generous bottom spacer so the last note card NEVER goes underneath the floating search dock!
        Spacer(modifier = Modifier.height(110.dp))
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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2E2E34) else palette.accentBar.copy(alpha = 0.25f)
        )
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
                            color = if (isDark) PureWhite else Color(0xFF18181B),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Time badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isDark) palette.badgeBgDark else palette.badgeBgLight)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) PureWhite else palette.lightText
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
                                .background(if (isDark) palette.badgeBgDark else palette.badgeBgLight)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) PureWhite else palette.lightText
                            )
                        }
                    }

                    // Active Reminder / Alarm Badge
                    if (note.reminderTime != null && note.reminderTime!! > System.currentTimeMillis() && !note.isReminderDone) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(VibrantOrange.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_alarm_clock),
                                    contentDescription = null,
                                    tint = VibrantOrange,
                                    modifier = Modifier.size(9.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(note.reminderTime),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }
                        }
                    }

                    // Task Checklist Counter (e.g. ✓ 2/5)
                    if (note.totalTasks > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isDark) Color(0xFF2E2E34) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "✓ ${note.completedTasks}/${note.totalTasks}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) PureWhite.copy(alpha = 0.85f) else Color(0xFF374151)
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

private fun generateDaysForMode(displayedCalendar: Calendar, mode: CalendarTabMode): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val cal = displayedCalendar.clone() as Calendar

    when (mode) {
        CalendarTabMode.FIVE_DAYS -> {
            // Notion Calendar 5 Days: Mon to Fri (or week start)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
            cal.add(Calendar.DAY_OF_MONTH, diffToMonday)
            for (i in 0 until 5) {
                days.add(
                    CalendarDay(
                        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                        isCurrentMonth = cal.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH),
                        calendar = cal.clone() as Calendar
                    )
                )
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        CalendarTabMode.THREE_DAYS -> {
            for (i in 0 until 3) {
                days.add(
                    CalendarDay(
                        dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                        isCurrentMonth = cal.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH),
                        calendar = cal.clone() as Calendar
                    )
                )
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        CalendarTabMode.ONE_DAY -> {
            days.add(
                CalendarDay(
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    isCurrentMonth = cal.get(Calendar.MONTH) == displayedCalendar.get(Calendar.MONTH),
                    calendar = cal.clone() as Calendar
                )
            )
        }
        CalendarTabMode.MONTH -> {
            return generateMonthDays(displayedCalendar)
        }
    }
    return days
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
