package com.notes.purenoteit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notes.purenoteit.R
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange

/**
 * Universal Modern Material Note Icon Badge.
 * Replaces legacy raw emojis (📄, 💼, 📝, ⚡, 🔒) with crisp, thematic Material vectors
 * and rounded high-contrast containers across Grid Cards, Lists, Kanban, and Sheets.
 */
@Composable
fun ModernNoteIconBadge(
    iconKey: String?,
    isProtected: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    iconSize: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(7.dp),
    backgroundColor: Color? = null,
    borderColor: Color? = null
) {
    val isDark = isSystemInDarkTheme()
    val defaultBg = backgroundColor ?: if (isDark) Color(0xFF26262B) else PureWhite
    val defaultBorder = borderColor ?: if (isDark) Color(0xFF36363E) else BorderLight.copy(alpha = 0.5f)

    val (drawableRes, tintColor) = when {
        isProtected -> Pair(R.drawable.lock, VibrantOrange)
        iconKey.isNullOrBlank() || iconKey == "📄" || iconKey == "📝" -> Pair(R.drawable.ic_subpage_doc, VibrantOrange)
        iconKey == "💼" || iconKey.contains("toplantı", ignoreCase = true) -> Pair(R.drawable.ic_tpl_meeting, Color(0xFF5D4037))
        iconKey == "⚡" || iconKey.contains("dev", ignoreCase = true) -> Pair(R.drawable.ic_tpl_laptop, Color(0xFF1565C0))
        iconKey == "♾️" || iconKey == "♾" || iconKey.contains("matematik", ignoreCase = true) -> Pair(R.drawable.ic_tpl_ruler, Color(0xFFF57F17))
        iconKey == "📖" || iconKey == "📚" || iconKey.contains("ders", ignoreCase = true) -> Pair(R.drawable.ic_tpl_book, Color(0xFF4A148C))
        iconKey == "🏛️" || iconKey == "🏛" || iconKey.contains("mimari", ignoreCase = true) -> Pair(R.drawable.ic_tpl_architecture, Color(0xFF37474F))
        iconKey == "🗣️" || iconKey == "🗣" || iconKey == "🎙️" || iconKey == "🎙" -> Pair(R.drawable.microphone_lines, Color(0xFF00695C))
        iconKey == "💎" || iconKey.contains("finans", ignoreCase = true) -> Pair(R.drawable.ic_tpl_finance, Color(0xFF00838F))
        iconKey == "🥗" || iconKey.contains("yemek", ignoreCase = true) -> Pair(R.drawable.ic_tpl_nutrition, Color(0xFF2E7D32))
        iconKey == "💪" || iconKey.contains("spor", ignoreCase = true) -> Pair(R.drawable.ic_tpl_fitness, Color(0xFFC2185B))
        iconKey == "🌿" || iconKey.contains("zihin", ignoreCase = true) -> Pair(R.drawable.ic_tpl_mind, Color(0xFF558B2F))
        iconKey == "📊" || iconKey.contains("rapor", ignoreCase = true) -> Pair(R.drawable.ic_stream_timeline, Color(0xFF283593))
        iconKey == "📋" || iconKey.contains("proje", ignoreCase = true) -> Pair(R.drawable.ic_checklist, Color(0xFF4E342E))
        iconKey == "🧭" || iconKey.contains("gezi", ignoreCase = true) -> Pair(R.drawable.ic_tpl_travel, Color(0xFF0277BD))
        iconKey == "🎯" || iconKey.contains("hedef", ignoreCase = true) -> Pair(R.drawable.ic_tpl_target, Color(0xFFE65100))
        iconKey == "📅" -> Pair(R.drawable.calendar, VibrantOrange)
        else -> Pair(R.drawable.note, VibrantOrange)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(defaultBg)
            .border(1.dp, defaultBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Modern Material Callout Badge.
 * Replaces raw callout emojis (like '📅 17', '💡', '⚡') with refined Material vector icons.
 */
@Composable
fun ModernCalloutIconBadge(
    calloutKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    iconSize: Dp = 15.dp
) {
    val (drawableRes, tintColor) = when {
        calloutKey == "📅" || calloutKey.contains("tarih", ignoreCase = true) -> Pair(R.drawable.calendar, Color(0xFFD84315))
        calloutKey == "💡" || calloutKey.contains("fikir", ignoreCase = true) -> Pair(R.drawable.ic_tpl_mind, Color(0xFFF57F17))
        calloutKey == "⚡" -> Pair(R.drawable.ic_tpl_laptop, Color(0xFF1565C0))
        calloutKey == "❝" || calloutKey == "\"" -> Pair(R.drawable.ic_format_quote, Color(0xFF5D4037))
        calloutKey == "🎯" -> Pair(R.drawable.ic_tpl_target, Color(0xFFE65100))
        calloutKey == "🔥" -> Pair(R.drawable.ic_tpl_target, Color(0xFFD84315))
        calloutKey == "🎨" -> Pair(R.drawable.ic_pencil_edit, Color(0xFF7B1FA2))
        calloutKey == "📖" || calloutKey == "📚" -> Pair(R.drawable.ic_tpl_book, Color(0xFF4A148C))
        calloutKey == "🏛️" || calloutKey == "🏛" -> Pair(R.drawable.ic_tpl_architecture, Color(0xFF37474F))
        calloutKey == "🗣️" || calloutKey == "🗣" || calloutKey == "🎙️" -> Pair(R.drawable.microphone_lines, Color(0xFF00695C))
        calloutKey == "💎" -> Pair(R.drawable.ic_tpl_finance, Color(0xFF00838F))
        calloutKey == "🥗" -> Pair(R.drawable.ic_tpl_nutrition, Color(0xFF2E7D32))
        calloutKey == "💪" -> Pair(R.drawable.ic_tpl_fitness, Color(0xFFC2185B))
        calloutKey == "🌿" -> Pair(R.drawable.ic_tpl_mind, Color(0xFF558B2F))
        calloutKey == "📊" -> Pair(R.drawable.ic_stream_timeline, Color(0xFF283593))
        calloutKey == "📋" -> Pair(R.drawable.ic_checklist, Color(0xFF4E342E))
        calloutKey == "🧭" -> Pair(R.drawable.ic_tpl_travel, Color(0xFF0277BD))
        else -> Pair(R.drawable.note, Color(0xFFF57F17))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(tintColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Modern Heading Icon Badge.
 * Automatically resolves and displays a crisp Material vector icon for headings
 * (such as Agenda/Pin, Decisions/Target, Actions/Checklist, Code, Math, Ideas, etc.)
 * so headings have sharp visual hierarchy instead of raw emojis or plain naked text.
 */
@Composable
fun ModernHeadingIconBadge(
    headingType: com.notes.purenoteit.model.BlockType,
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = if (headingType == com.notes.purenoteit.model.BlockType.HEADING_1) 24.dp else 20.dp,
    iconSize: Dp = if (headingType == com.notes.purenoteit.model.BlockType.HEADING_1) 15.dp else 13.dp
) {
    val lower = content.lowercase()
    val (drawableRes, tintColor) = when {
        // Gündem / Agenda / Pin
        lower.contains("gündem") || lower.contains("agenda") || lower.contains("madde") || content.startsWith("📌") ->
            Pair(R.drawable.ic_pin, Color(0xFFE53935))

        // Kararlar / Hedefler / Decisions / Target
        lower.contains("karar") || lower.contains("decision") || lower.contains("hedef") || lower.contains("sprint") || content.startsWith("🎯") ->
            Pair(R.drawable.ic_tpl_target, Color(0xFFE65100))

        // Aksiyon / Görev / Action / Checklist
        lower.contains("aksiyon") || lower.contains("action") || lower.contains("görev") || lower.contains("dağılım") || lower.contains("kontrol") || lower.contains("test") || content.startsWith("✅") ->
            Pair(R.drawable.ic_checklist, Color(0xFF2E7D32))

        // Fikir / Not / Çıkarım / Ideas
        lower.contains("fikir") || lower.contains("takeaway") || lower.contains("özet") || lower.contains("not") || content.startsWith("💡") ->
            Pair(R.drawable.ic_tpl_mind, Color(0xFFF57F17))

        // Kod / Yazılım / Bug / Solution
        lower.contains("kod") || lower.contains("code") || lower.contains("çözüm") || lower.contains("hata") || content.startsWith("⚡") || content.startsWith("💻") ->
            Pair(R.drawable.ic_tpl_laptop, Color(0xFF1565C0))

        // Formül / Matematik / Math
        lower.contains("formül") || lower.contains("matematik") || lower.contains("teorem") || content.startsWith("📐") || content.startsWith("♾") ->
            Pair(R.drawable.ic_tpl_ruler, Color(0xFFF57F17))

        // Tablo / Rapor / Modül / Impact
        lower.contains("tablo") || lower.contains("rapor") || lower.contains("modül") || lower.contains("analiz") || content.startsWith("📊") ->
            Pair(R.drawable.ic_stream_timeline, Color(0xFF5E35B1))

        // Kitap / İnceleme / Ders / Book
        lower.contains("kitap") || lower.contains("ders") || lower.contains("alıntı") || content.startsWith("📖") || content.startsWith("📚") ->
            Pair(R.drawable.ic_tpl_book, Color(0xFF4A148C))

        // Mimari / Sistem / Architecture
        lower.contains("mimari") || lower.contains("sistem") || lower.contains("adr") || content.startsWith("🏛") ->
            Pair(R.drawable.ic_tpl_architecture, Color(0xFF37474F))

        // Default H1 vs H2
        headingType == com.notes.purenoteit.model.BlockType.HEADING_1 ->
            Pair(R.drawable.ic_format_h1, VibrantOrange)

        else ->
            Pair(R.drawable.ic_format_h2, VibrantOrange)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(tintColor.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
