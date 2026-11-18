package com.notes.purenoteit.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.desktop.ui.DesktopColors
import com.notes.purenoteit.domain.model.Category
import com.notes.purenoteit.domain.model.Note
import com.notes.purenoteit.model.BlockType
import com.notes.purenoteit.model.NoteBlock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CalendarDisplayMode(val label: String) {
    MONTH("Aylık"),
    FIVE_DAYS("5 Gün"),
    THREE_DAYS("3 Gün"),
    ONE_DAY("Günlük")
}

private val TR_LOCALE: Locale = Locale.forLanguageTag("tr-TR")
private val MONTH_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", TR_LOCALE)
private val DAY_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", TR_LOCALE)
private val SHORT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", TR_LOCALE)
private val REMINDER_INPUT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", TR_LOCALE)

private fun noteLocalDate(note: Note): LocalDate {
    val ts = note.reminderTime ?: note.date
    val millis = if (ts > 0L) ts else note.updatedAt
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

@Composable
fun DesktopCalendarView(
    notes: List<Note>,
    categories: List<Category>,
    onSelectNote: (String) -> Unit,
    onCreateNoteForDay: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var displayMode by remember { mutableStateOf(CalendarDisplayMode.MONTH) }

    val notesByDate = remember(notes) {
        notes.groupBy { noteLocalDate(it) }
    }
    val selectedDayNotes = remember(notesByDate, selectedDate) {
        notesByDate[selectedDate].orEmpty().sortedBy { it.reminderTime ?: it.date }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Left Calendar Surface
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(1.35f).fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                if (displayMode == CalendarDisplayMode.MONTH) {
                                    visibleMonth = visibleMonth.minusMonths(1)
                                } else {
                                    selectedDate = selectedDate.minusDays(1)
                                    visibleMonth = YearMonth.from(selectedDate)
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Önceki")
                        }
                        Text(
                            text = visibleMonth.atDay(1).format(MONTH_YEAR_FORMAT).replaceFirstChar { it.uppercase() },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                if (displayMode == CalendarDisplayMode.MONTH) {
                                    visibleMonth = visibleMonth.plusMonths(1)
                                } else {
                                    selectedDate = selectedDate.plusDays(1)
                                    visibleMonth = YearMonth.from(selectedDate)
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Sonraki")
                        }
                        OutlinedButton(
                            onClick = {
                                selectedDate = today
                                visibleMonth = YearMonth.from(today)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Bugün", fontSize = 11.5.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalendarDisplayMode.entries.forEach { m ->
                            val active = m == displayMode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) DesktopColors.VibrantOrange.copy(alpha = 0.16f) else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { displayMode = m }
                            ) {
                                Text(
                                    text = m.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (active) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (displayMode == CalendarDisplayMode.MONTH) {
                    val dayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        dayLabels.forEach { d ->
                            Text(
                                text = d,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    val firstDay = visibleMonth.atDay(1)
                    val offset = (firstDay.dayOfWeek.value - 1).coerceAtLeast(0)
                    val totalDays = visibleMonth.lengthOfMonth()
                    val cells = remember(visibleMonth) {
                        val list = mutableListOf<LocalDate>()
                        val gridStart = firstDay.minusDays(offset.toLong())
                        val totalCells = if (offset + totalDays > 35) 42 else 35
                        for (i in 0 until totalCells) {
                            list += gridStart.plusDays(i.toLong())
                        }
                        list
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        cells.chunked(7).forEach { week ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                week.forEach { day ->
                                    val inMonth = day.month == visibleMonth.month
                                    val isSelected = day == selectedDate
                                    val isToday = day == today
                                    val count = notesByDate[day]?.size ?: 0
                                    val hasReminder = notesByDate[day]?.any { it.reminderTime != null } == true

                                    val cellBg by animateColorAsState(
                                        when {
                                            isSelected -> DesktopColors.VibrantOrange.copy(alpha = 0.22f)
                                            isToday -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = cellBg,
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isSelected -> DesktopColors.VibrantOrange
                                                isToday -> DesktopColors.VibrantOrange.copy(alpha = 0.45f)
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedDate = day }
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxSize().padding(6.dp)
                                        ) {
                                            Text(
                                                text = "${day.dayOfMonth}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> DesktopColors.VibrantOrange
                                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                            if (count > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = if (hasReminder) DesktopColors.VibrantOrange else DesktopColors.AccentBlue
                                                ) {
                                                    Text(
                                                        text = "$count",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else {
                                                Spacer(Modifier.height(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val span = when (displayMode) {
                        CalendarDisplayMode.FIVE_DAYS -> 5
                        CalendarDisplayMode.THREE_DAYS -> 3
                        else -> 1
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        for (i in 0 until span) {
                            val d = selectedDate.plusDays(i.toLong())
                            val dNotes = notesByDate[d].orEmpty()
                            val isActiveDay = d == selectedDate
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isActiveDay) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable { selectedDate = d }
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    Text(
                                        text = d.format(DateTimeFormatter.ofPattern("d MMM EEE", TR_LOCALE)),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (d == today) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                                        items(dNotes, key = { "calcol_${d}_${it.syncUuid}" }) { n ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                                modifier = Modifier.fillMaxWidth().clickable { onSelectNote(n.syncUuid) }
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "${n.icon ?: "📝"} ${n.title?.ifBlank { "Başlıksız" } ?: "Başlıksız"}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 2,
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
                }
            }
        }

        // Right Selected Day Agenda List
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.weight(0.95f).fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = DesktopColors.VibrantOrange)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedDate.format(DAY_HEADER_FORMAT).replaceFirstChar { it.uppercase() },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selectedDayNotes.size} plan / not",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        val noonMillis = selectedDate.atTime(LocalTime.of(12, 0))
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        onCreateNoteForDay(noonMillis)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bu Güne Not / Plan Ekle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))

                if (selectedDayNotes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Bu güne ait planlanmış not yok.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(selectedDayNotes, key = { "day_${it.syncUuid}" }) { note ->
                            val catName = categories.firstOrNull { it.syncUuid == note.categorySyncUuid }?.name
                            val timeStr = remember(note.reminderTime) {
                                note.reminderTime?.let {
                                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(SHORT_TIME_FORMAT)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectNote(note.syncUuid) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                    DesktopNoteIconBadge(
                                        iconKey = note.icon,
                                        isLocked = note.isPassword == true || note.isEncrypted,
                                        size = 30.dp,
                                        iconSize = 16.dp,
                                        cornerRadius = 8.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.title?.ifBlank { "Başlıksız Not" } ?: "Başlıksız Not",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (timeStr != null) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Icon(
                                                        Icons.Rounded.Alarm,
                                                        contentDescription = null,
                                                        tint = DesktopColors.VibrantOrange,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = timeStr,
                                                        fontSize = 11.sp,
                                                        color = DesktopColors.VibrantOrange
                                                    )
                                                }
                                            }
                                            if (!catName.isNullOrBlank()) {
                                                Text(
                                                    text = catName,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

enum class DesktopTemplateCategory(val label: String) {
    ALL("Tümü"),
    TECH("Teknoloji"),
    EDUCATION("Eğitim"),
    WORK("İş & Plan"),
    LIFE("Yaşam")
}

data class DesktopRichTemplate(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val category: DesktopTemplateCategory,
    val tint: Color,
    val buildBlocks: () -> List<NoteBlock>
)

val DESKTOP_ALL_TEMPLATES: List<DesktopRichTemplate> = listOf(
    DesktopRichTemplate(
        id = "dev_bug_tracker",
        icon = "⚡",
        title = "Hata Analizi & Kod Çözümü",
        description = "Kök neden analizi, kod bloğu, modül etki tablosu ve doğrulama adımları",
        category = DesktopTemplateCategory.TECH,
        tint = DesktopColors.AccentBlue,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "⚡", content = "Konu: Bellek & Eşzamanlılık İyileştirmesi"),
                NoteBlock(type = BlockType.HEADING_2, content = "Çözüm Kodu"),
                NoteBlock(type = BlockType.CODE, codeLanguage = "kotlin", content = "suspend fun syncSafe() {\n    // Güvenli işlem akışı\n}"),
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("Modül", "Öncelik", "Durum"), listOf("Çekirdek", "P0", "Aktif"))),
                NoteBlock(type = BlockType.TODO, content = "Regresyon testlerini çalıştır")
            )
        }
    ),
    DesktopRichTemplate(
        id = "architecture_adr",
        icon = "🏛️",
        title = "Mimari Karar Kaydı (ADR)",
        description = "Bağlam, değerlendirilen alternatifler ve teknik karar özeti",
        category = DesktopTemplateCategory.TECH,
        tint = DesktopColors.CyanNeon,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "🏛️", content = "Durum: Kabul Edildi · Mimari İnceleme"),
                NoteBlock(type = BlockType.HEADING_2, content = "Bağlam ve Problem"),
                NoteBlock(type = BlockType.PARAGRAPH, content = "Çözülmesi hedeflenen mimari ihtiyaç..."),
                NoteBlock(type = BlockType.HEADING_2, content = "Sonuçlar ve Riskler"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "Yüksek bakım kolaylığı")
            )
        }
    ),
    DesktopRichTemplate(
        id = "math_notebook",
        icon = "♾️",
        title = "Matematik & Formül Defteri",
        description = "Teorem özeti, matematiksel ifadeler ve örnek soru çözümü",
        category = DesktopTemplateCategory.EDUCATION,
        tint = DesktopColors.VibrantOrange,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Temel Formüller"),
                NoteBlock(type = BlockType.MATH, content = "E = m * c^2"),
                NoteBlock(type = BlockType.HEADING_2, content = "Çözüm Adımları"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "Değişkenleri tanımla"),
                NoteBlock(type = BlockType.TODO, content = "5 ek alıştırma çöz")
            )
        }
    ),
    DesktopRichTemplate(
        id = "cornell_notes",
        icon = "📖",
        title = "Cornell Ders Notu",
        description = "Anahtar sorular, ders özeti ve hızlı tekrar maddeleri",
        category = DesktopTemplateCategory.EDUCATION,
        tint = DesktopColors.EmeraldGreen,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Anahtar Kavramlar & Sorular"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "Temel tanım nedir?"),
                NoteBlock(type = BlockType.HEADING_2, content = "Detaylı Ders Notları"),
                NoteBlock(type = BlockType.PARAGRAPH, content = "Konunun ana fikrini buraya özetle..."),
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "💡", content = "Bölüm Özeti: Sınav öncesi gözden geçir")
            )
        }
    ),
    DesktopRichTemplate(
        id = "language_learning",
        icon = "🗣️",
        title = "Dil Öğrenimi & Kelime Defteri",
        description = "Yeni kelimeler, deyimler, örnek cümle tablosu ve günlük pratik",
        category = DesktopTemplateCategory.EDUCATION,
        tint = DesktopColors.AccentBlue,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("İfade", "Anlamı", "Örnek"), listOf("Break a leg", "Başarılar", "Good luck today!"))),
                NoteBlock(type = BlockType.TODO, content = "15 dakika konuşma pratiği yap")
            )
        }
    ),
    DesktopRichTemplate(
        id = "lean_canvas",
        icon = "💎",
        title = "Proje Fikir Tuvali (Lean Canvas)",
        description = "Problem, çözüm, değer önerisi ve hedef kitle analizi",
        category = DesktopTemplateCategory.WORK,
        tint = DesktopColors.VibrantOrange,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("Problem", "Değer Önerisi", "Çözüm"), listOf("Dağınık notlar", "Tek güvenli çalışma alanı", "Şifreli KMP ekosistem"))),
                NoteBlock(type = BlockType.TODO, content = "İlk kullanıcı görüşmelerini planla")
            )
        }
    ),
    DesktopRichTemplate(
        id = "meeting",
        icon = "📋",
        title = "Toplantı Notu & Yol Haritası",
        description = "Gündem maddeleri, alınan kararlar ve sorumlular",
        category = DesktopTemplateCategory.WORK,
        tint = DesktopColors.AccentBlue,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Gündem"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "İlerleme değerlendirmesi"),
                NoteBlock(type = BlockType.HEADING_2, content = "Aksiyonlar"),
                NoteBlock(type = BlockType.TODO, content = "Sonraki sprint hedeflerini netleştir"),
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "📌", content = "Karar: Yayın tarihi onaylandı")
            )
        }
    ),
    DesktopRichTemplate(
        id = "budget",
        icon = "📊",
        title = "Aylık Bütçe Planı",
        description = "Gelir/gider kalemleri, hedefler ve tasarruf oranı tablosu",
        category = DesktopTemplateCategory.WORK,
        tint = DesktopColors.EmeraldGreen,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("Kalem", "Planlanan", "Gerçekleşen"), listOf("Sabit Giderler", "₺0", "₺0"), listOf("Birikim", "₺0", "₺0"))),
                NoteBlock(type = BlockType.TODO, content = "Ay sonu kapanışını güncelle")
            )
        }
    ),
    DesktopRichTemplate(
        id = "goals",
        icon = "🎯",
        title = "Haftalık Hedefler & Sprint",
        description = "3 ana hedef, kilometre taşları ve haftalık kontrol",
        category = DesktopTemplateCategory.WORK,
        tint = DesktopColors.VibrantOrange,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "🎯", content = "Haftanın Ana Odağı"),
                NoteBlock(type = BlockType.TODO, content = "Kritik hedef 1"),
                NoteBlock(type = BlockType.TODO, content = "Kritik hedef 2"),
                NoteBlock(type = BlockType.TODO, content = "Kritik hedef 3")
            )
        }
    ),
    DesktopRichTemplate(
        id = "nutrition_meal",
        icon = "🥗",
        title = "Haftalık Beslenme Planı",
        description = "Öğün planlaması, kalori dengesi ve alışveriş listesi",
        category = DesktopTemplateCategory.LIFE,
        tint = DesktopColors.EmeraldGreen,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("Gün", "Kahvaltı", "Akşam"), listOf("Pazartesi", "Yulaf & Meyve", "Izgara Sebze"))),
                NoteBlock(type = BlockType.TODO, content = "Haftalık market alışverişi")
            )
        }
    ),
    DesktopRichTemplate(
        id = "workout_log",
        icon = "💪",
        title = "Antrenman Günlüğü",
        description = "Hareketler, set/tekrar sayısı ve kişisel rekor takibi",
        category = DesktopTemplateCategory.LIFE,
        tint = DesktopColors.VibrantOrange,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.TABLE, tableData = listOf(listOf("Egzersiz", "Set x Tekrar", "Ağırlık"), listOf("Bench Press", "4x8", "kg"), listOf("Squat", "4x10", "kg"))),
                NoteBlock(type = BlockType.TODO, content = "Esneme & soğuma 10 dk")
            )
        }
    ),
    DesktopRichTemplate(
        id = "gratitude_journal",
        icon = "🌿",
        title = "Zihin & Şükran Günlüğü",
        description = "Günün olumlu anları, farkındalık notları ve akşam değerlendirmesi",
        category = DesktopTemplateCategory.LIFE,
        tint = DesktopColors.EmeraldGreen,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "🌿", content = "Bugün minnettar olduğum 3 şey"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "1. ..."),
                NoteBlock(type = BlockType.BULLET_LIST, content = "2. ..."),
                NoteBlock(type = BlockType.PARAGRAPH, content = "Günün huzur veren anı...")
            )
        }
    ),
    DesktopRichTemplate(
        id = "travel",
        icon = "🧭",
        title = "Seyahat Planı & Rota",
        description = "Uçuş/konaklama bilgileri, gezilecek yerler ve valiz listesi",
        category = DesktopTemplateCategory.LIFE,
        tint = DesktopColors.AccentBlue,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.HEADING_2, content = "Gezilecek Yerler"),
                NoteBlock(type = BlockType.BULLET_LIST, content = "Tarihi merkez & müzeler"),
                NoteBlock(type = BlockType.HEADING_2, content = "Valiz Kontrolü"),
                NoteBlock(type = BlockType.TODO, content = "Pasaport, şarj aleti, biletler")
            )
        }
    ),
    DesktopRichTemplate(
        id = "review",
        icon = "📚",
        title = "Kitap İncelemesi & Özet",
        description = "Temel mesajlar, favori alıntılar ve uygulanacak dersler",
        category = DesktopTemplateCategory.LIFE,
        tint = DesktopColors.VibrantOrange,
        buildBlocks = {
            listOf(
                NoteBlock(type = BlockType.CALLOUT, calloutIcon = "📚", content = "Kitap & Yazar Adı"),
                NoteBlock(type = BlockType.HEADING_2, content = "Öne Çıkan Alıntılar"),
                NoteBlock(type = BlockType.PARAGRAPH, content = "En etkileyici fikir..."),
                NoteBlock(type = BlockType.TODO, content = "Hayatıma uygulayacağım 1 alışkanlık")
            )
        }
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopTemplateGalleryDialog(
    onSelectTemplate: (DesktopRichTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCat by remember { mutableStateOf(DesktopTemplateCategory.ALL) }
    val filtered = remember(selectedCat) {
        if (selectedCat == DesktopTemplateCategory.ALL) DESKTOP_ALL_TEMPLATES
        else DESKTOP_ALL_TEMPLATES.filter { it.category == selectedCat }
    }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 840.dp,
        title = "Şablon Galerisi",
        subtitle = "14 hazır profesyonel şablon ile tek tıkla düzenli notlar oluşturun.",
        badgeIcon = Icons.Rounded.DashboardCustomize,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DesktopTemplateCategory.entries) { cat ->
                val active = cat == selectedCat
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) DesktopColors.VibrantOrange else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { selectedCat = cat }
                ) {
                    Text(
                        text = cat.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 210.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(380.dp)
        ) {
            items(filtered, key = { it.id }) { tpl ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectTemplate(tpl) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(tpl.tint.copy(alpha = 0.14f))
                            ) {
                                Text(tpl.icon, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tpl.title,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                lineHeight = 16.sp,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = tpl.description,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Kapat")
            }
        }
    }
}

@Composable
fun DesktopCategoryManagementDialog(
    categories: List<Category>,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onToggleCategoryLock: (String) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var editingUuid by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 500.dp,
        title = "Kategori Yönetimi",
        subtitle = "Klasörlerinizi yeniden adlandırın, kilitleyin veya yenisini ekleyin.",
        badgeIcon = Icons.Rounded.Folder,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(40) },
                placeholder = { Text("Yeni kategori adı") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val clean = newName.trim()
                    if (clean.isNotEmpty()) {
                        onCreateCategory(clean)
                        newName = ""
                    }
                },
                enabled = newName.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Ekle")
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(260.dp)) {
            items(categories, key = { "catmgr_${it.syncUuid}" }) { cat ->
                val isProtected = cat.name in setOf("Not", "Günlük", "Önemli")
                val isLocked = cat.isPassword == true
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (editingUuid == cat.syncUuid) {
                            OutlinedTextField(
                                value = editingText,
                                onValueChange = { editingText = it.take(40) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (editingText.isNotBlank()) onRenameCategory(cat.syncUuid, editingText.trim())
                                editingUuid = null
                            }) {
                                Icon(Icons.Rounded.Check, contentDescription = "Kaydet", tint = DesktopColors.EmeraldGreen)
                            }
                        } else {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = DesktopColors.VibrantOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { onToggleCategoryLock(cat.syncUuid) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = "Kategori Kilidi",
                                    tint = if (isLocked) DesktopColors.EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingUuid = cat.syncUuid
                                    editingText = cat.name
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Yeniden Adlandır", modifier = Modifier.size(16.dp))
                            }
                            if (!isProtected) {
                                IconButton(
                                    onClick = { onDeleteCategory(cat) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Sil",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Kapat")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopReminderDialog(
    currentReminderMillis: Long?,
    onSaveReminder: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialInput = remember(currentReminderMillis) {
        val base = currentReminderMillis ?: (System.currentTimeMillis() + 3_600_000L)
        Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault()).format(REMINDER_INPUT_FORMAT)
    }
    var dateTimeText by remember { mutableStateOf(initialInput) }
    var parseError by remember { mutableStateOf(false) }

    DesktopAnimatedDialog(
        onDismiss = onDismiss,
        width = 430.dp,
        title = "Hatırlatıcı & Takvim Tarihi",
        subtitle = "Belirlediğiniz tarih hem Takvim görünümüne hem de telefona senkronize olur.",
        badgeIcon = Icons.Rounded.Alarm,
        badgeTint = DesktopColors.VibrantOrange
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val now = LocalDateTime.now()
            val presets = listOf(
                "1 Saat Sonra" to now.plusHours(1),
                "Yarın 09:00" to LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0)),
                "Gelecek Hafta" to LocalDateTime.of(now.toLocalDate().plusDays(7), LocalTime.of(9, 0))
            )
            presets.forEach { (label, dt) ->
                OutlinedButton(
                    onClick = {
                        dateTimeText = dt.format(REMINDER_INPUT_FORMAT)
                        parseError = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(label, fontSize = 11.5.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = dateTimeText,
            onValueChange = {
                dateTimeText = it
                parseError = false
            },
            label = { Text("Tarih & Saat (GG.AA.YYYY SS:DD)") },
            isError = parseError,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (parseError) {
            Spacer(Modifier.height(4.dp))
            Text("Örnek biçim: 15.11.2026 09:30", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentReminderMillis != null) {
                OutlinedButton(onClick = { onSaveReminder(null) }, shape = RoundedCornerShape(12.dp)) {
                    Text("Kaldır", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                    Text("İptal")
                }
                Button(
                    onClick = {
                        val parsed = runCatching {
                            LocalDateTime.parse(dateTimeText.trim(), REMINDER_INPUT_FORMAT)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }.getOrNull()
                        if (parsed == null) {
                            parseError = true
                        } else {
                            onSaveReminder(parsed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.VibrantOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Kaydet")
                }
            }
        }
    }
}
