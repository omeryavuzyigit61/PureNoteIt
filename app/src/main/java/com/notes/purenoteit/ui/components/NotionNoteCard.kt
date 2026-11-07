package com.notes.purenoteit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.model.BlockParser
import androidx.compose.animation.core.snap
import com.notes.purenoteit.ui.animation.LocalNavAnimatedVisibilityScope
import com.notes.purenoteit.ui.animation.LocalPagerTransitionCoordinator
import com.notes.purenoteit.ui.animation.LocalSharedTransitionScope
import com.notes.purenoteit.ui.theme.BorderLight
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.SoftOrangeLight
import com.notes.purenoteit.ui.theme.VibrantOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun NotionNoteCard(
    note: NoteData,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isCategoryLocked: Boolean = false,
    searchQuery: String = "",
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    sharedScopeKey: String? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isProtected = note.isPassword == true || note.isEncrypted || isCategoryLocked
    val haptic = LocalHapticFeedback.current

    // Gentle tactile micro-wiggle when selected
    val wiggleRotation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            wiggleRotation.snapTo(0f)
            wiggleRotation.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.keyframes {
                    durationMillis = 280
                    0f at 0
                    -2.0f at 60 using FastOutSlowInEasing
                    1.8f at 130 using FastOutSlowInEasing
                    -0.8f at 200 using FastOutSlowInEasing
                    0f at 280 using FastOutSlowInEasing
                }
            )
        } else {
            wiggleRotation.snapTo(0f)
        }
    }

    // Smooth liquid color transitions (Water tint wash)
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFF7EE) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 260),
        label = "note_card_bg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 260),
        label = "note_card_border"
    )

    // Gentle water droplet ripple progression, constrained strictly inside the card
    val rippleProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "water_ripple"
    )

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    val pagerCoordinator = LocalPagerTransitionCoordinator.current

    val cardScale = remember { androidx.compose.animation.core.Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()
    var isThisNoteTarget by remember { mutableStateOf(false) }

    var wasTransitionActive by remember { mutableStateOf(false) }
    LaunchedEffect(sharedTransitionScope?.isTransitionActive, pagerCoordinator.suppressOverlay) {
        val isActive = sharedTransitionScope?.isTransitionActive == true
        if (pagerCoordinator.suppressOverlay) {
            isThisNoteTarget = false
            cardScale.snapTo(1f)
        } else if (isThisNoteTarget && wasTransitionActive && !isActive) {
            // SADECE tıklanan bu kart için gecikmesiz, soft, pamuksu yerleşme sekmesi
            cardScale.animateTo(0.985f, spring(stiffness = Spring.StiffnessMedium))
            cardScale.animateTo(
                1f,
                spring(
                    dampingRatio = 0.82f,
                    stiffness = 320f
                )
            )
            isThisNoteTarget = false
        }
        wasTransitionActive = isActive
    }

    val handleCardClick: () -> Unit = {
        if (isSelectionMode) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleSelect()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isThisNoteTarget = true
            coroutineScope.launch {
                // Soft, elastik ve pamuksu içe esneme (Sıfır katılık, sıfır açısal sarsıntı)
                cardScale.animateTo(
                    targetValue = 0.965f,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                cardScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            onClick()
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    val sharedBoundsModifier = if (sharedScopeKey != null && sharedTransitionScope != null && animatedVisibilityScope != null && note.id > 0) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(
                    key = "note_card_${sharedScopeKey}_${note.id}"
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    if (pagerCoordinator.suppressOverlay) {
                        snap()
                    } else {
                        spring(
                            dampingRatio = 0.82f,
                            stiffness = 380f
                        )
                    }
                },
                renderInOverlayDuringTransition = !pagerCoordinator.suppressOverlay,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp))
            )
        }
    } else {
        Modifier
    }

    Card(
        modifier = modifier
            .then(sharedBoundsModifier)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale.value
                scaleY = cardScale.value
                rotationZ = wiggleRotation.value
            }
            .resilientCombinedClickable(
                onClick = handleCardClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else {
                        onLongClick()
                    }
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedBgColor
        ),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            animatedBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (rippleProgress > 0.01f && rippleProgress < 0.99f) {
                        val originX = size.width - 24.dp.toPx()
                        val originY = 24.dp.toPx()
                        val maxRadius = size.width * 0.75f
                        val currentRadius = rippleProgress * maxRadius
                        val alpha = (1f - rippleProgress) * 0.15f
                        drawCircle(
                            color = VibrantOrange.copy(alpha = alpha),
                            radius = currentRadius,
                            center = Offset(originX, originY)
                        )
                    }
                }
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
                    ModernNoteIconBadge(
                        iconKey = note.icon,
                        isProtected = isProtected,
                        size = 28.dp,
                        iconSize = 16.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pinned Indicator
                    if (note.isPinned && !isSelectionMode) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pin),
                            contentDescription = "Sabitlendi",
                            tint = VibrantOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (isSelectionMode) {
                        // Multi-selection checkmark bead with water droplet spring bounce
                        val badgeScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.82f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "badge_scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = badgeScale
                                    scaleY = badgeScale
                                }
                                .clip(CircleShape)
                                .background(if (isSelected) VibrantOrange else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    1.2.dp,
                                    if (isSelected) VibrantOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seçildi",
                                    tint = PureWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else {
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
                                        if (note.isPinned) stringResource(R.string.unpin) else stringResource(R.string.pin_to_top),
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
                                text = { Text(stringResource(R.string.edit), color = PureWhite) },
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
                                text = { Text(stringResource(R.string.delete), color = Color(0xFFFF5252)) },
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title with search query highlighting
            val displayTitle = if (isProtected) "******" else (note.title ?: stringResource(R.string.untitled_note))
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

            Text(
                text = titleAnnotated,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Content preview (Bitwarden Mask vs Deep Search Snippet vs Notion Summary)
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
                val searchSnippetMatch = remember(note.description, searchQuery) {
                    if (searchQuery.isNotBlank()) {
                        BlockParser.findSearchSnippet(note.description, searchQuery)
                    } else null
                }

                if (searchSnippetMatch != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoftOrangeLight.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, VibrantOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🔍", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.search_in_content_found),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VibrantOrange
                                )
                            }
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
                                style = MaterialTheme.typography.bodySmall.copy(
                                    lineHeight = 16.sp,
                                    fontSize = 11.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

            // Subpage count (Seçenek A: Notion Hiyerarşisi Rozeti)
            val subpageCount = remember(note.description) {
                if (isProtected) 0 else {
                    try {
                        val blocks = BlockParser.parse(note.description ?: "")
                        blocks.count { it.type == com.notes.purenoteit.model.BlockType.SUB_PAGE && it.content.isNotBlank() }
                    } catch (e: Exception) {
                        0
                    }
                }
            }

            // Smart Hashtags (Feature 3)
            val hashtags = remember(note.title, note.description) {
                if (isProtected) emptyList() else {
                    val text = (note.title ?: "") + " " + (note.description ?: "")
                    Regex("""#([a-zA-Z0-9çğıöşüÇĞİÖŞÜ_-]+)""")
                        .findAll(text)
                        .map { "#" + it.groupValues[1] }
                        .distinct()
                        .take(3)
                        .toList()
                }
            }

            if (subpageCount > 0 || hashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subpage pill
                    if (subpageCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEDE7F6),
                            border = BorderStroke(0.5.dp, Color(0xFFB39DDB))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_subpage_doc),
                                    contentDescription = null,
                                    tint = Color(0xFF5E35B1),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$subpageCount " + stringResource(R.string.subpage_badge),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5E35B1)
                                )
                            }
                        }
                    }

                    for (tag in hashtags) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SoftOrangeLight,
                            border = BorderStroke(0.5.dp, VibrantOrange.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = tag,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VibrantOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formatted Date and Active Reminder Badge
            val dateStr = remember(note.date) {
                SimpleDateFormat("dd MMM yyyy", Locale("tr")).format(Date(note.date))
            }
            val hasActiveReminder = note.reminderTime != null && note.reminderTime!! > System.currentTimeMillis() && !note.isReminderDone

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (hasActiveReminder) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(VibrantOrange.copy(alpha = 0.15f))
                            .border(0.5.dp, VibrantOrange.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_alarm_clock),
                                contentDescription = null,
                                tint = VibrantOrange,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(note.reminderTime),
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
