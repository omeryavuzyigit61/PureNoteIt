package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notes.purenoteit.R
import com.notes.purenoteit.data.db.CategoryData
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange

/**
 * Glassy Navigation Tabs (V7) - Glassmorphism & 3D Door-Flip Category Bar
 * Videodaki 3D açılan kapı (door-flip / y-axis rotation) animasyonu ve
 * buğulu cam (glassmorphism) tasarımına sahip üst kategori filtre menüsü.
 */
@Composable
fun GlassyCategoryBar(
    categories: List<CategoryData>,
    selectedCategory: String?,
    totalNotesCount: Int,
    activeNotes: List<com.notes.purenoteit.data.db.NoteData>,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    getCategoryDotColor: (String) -> Color?,
    onAllSelected: () -> Unit,
    onCategorySelected: (categoryName: String, isSame: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Dış Cam Kapsül Renkleri (Glassmorphism Frosted Background)
    val glassBgColor = if (isDarkTheme) {
        Color(0xDD1E1E22)
    } else {
        Color(0xEEF8F8FA)
    }

    val glassBorderBrush = Brush.linearGradient(
        colors = if (isDarkTheme) {
            listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
        } else {
            listOf(Color.White.copy(alpha = 0.90f), Color.White.copy(alpha = 0.35f))
        }
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.06f),
                ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.03f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(glassBgColor)
            .border(
                width = 1.dp,
                brush = glassBorderBrush,
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. "Tümü" Sekmesi
            val isAllSelected = selectedCategory == null
            GlassyDoorFlipChip(
                label = stringResource(R.string.all_categories),
                isSelected = isAllSelected,
                count = totalNotesCount,
                isDarkTheme = isDarkTheme,
                onClick = onAllSelected
            )

            // 2. Dinamik Sıralı Kategoriler
            categories.forEach { category ->
                val catCount = remember(activeNotes, category.id) {
                    activeNotes.count { it.categoryId == category.id }
                }
                val isSelected = selectedCategory == category.name

                GlassyDoorFlipChip(
                    label = category.name,
                    isSelected = isSelected,
                    isLocked = category.isPassword == true,
                    count = catCount,
                    dotColor = getCategoryDotColor(category.name),
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        onCategorySelected(category.name, selectedCategory == category.name)
                    }
                )
            }
        }
    }
}

/**
 * 3D Kapı Efektli Sekme Elemanı (Door-Flip Pill)
 * Seçilme anında Y ekseni (düşey eksen) etrafında bir kapı menteşesi gibi
 * 3D açılarak yaylanır (rotationY: 70° -> 0° spring bounce).
 */
@Composable
fun GlassyDoorFlipChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    count: Int? = null,
    dotColor: Color? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    // Kapı Menteşe Açısı (Y-Rotasyonu) Animasyonu
    val doorAngle = remember { Animatable(if (isSelected) 0f else 25f) }
    val pillScale = remember { Animatable(if (isSelected) 1f else 0.95f) }

    LaunchedEffect(isSelected) {
        if (isSelected) {
            // Kapı dışarıdan yaylanarak açılıp yerine oturur (Door Swing In)
            doorAngle.snapTo(65f)
            pillScale.snapTo(0.90f)
            doorAngle.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            pillScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            // Sakin pozisyona dönüş
            doorAngle.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            pillScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    // Seçili iken yumuşak genişleme
    val horizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 15.dp else 12.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillPadding"
    )

    // Seçili hap arka plan degrade fırçası (Luminous Amber / Orange Glow)
    val activePillBrush = Brush.horizontalGradient(
        colors = listOf(
            VibrantOrange,
            Color(0xFFFF9436)
        )
    )

    val activeUnderglowColor = if (isDarkTheme) {
        VibrantOrange.copy(alpha = 0.38f)
    } else {
        VibrantOrange.copy(alpha = 0.22f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                // 3D Kapı Perspektifi (Y ekseni etrafında menteşe dönüşü)
                rotationY = doorAngle.value
                scaleX = pillScale.value
                scaleY = pillScale.value
                cameraDistance = 14f * density // 3D alan derinliği
                transformOrigin = TransformOrigin(0.15f, 0.5f) // Sol menteşe kapı ekseni
            }
            .drawBehind {
                if (isSelected) {
                    // Videodaki difüz alt ışık (Ambient Underglow)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(activeUnderglowColor, Color.Transparent),
                            radius = size.maxDimension * 0.85f
                        )
                    )
                }
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = if (isSelected) activePillBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(PureWhite.copy(alpha = 0.45f), PureWhite.copy(alpha = 0.10f))
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLocked) {
                Icon(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = null,
                    tint = if (isSelected) PureWhite else if (isDarkTheme) Color(0xFFFFA756) else VibrantOrange,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (dotColor != null && !isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) {
                    PureWhite
                } else if (isDarkTheme) {
                    Color(0xFFE2E2E6)
                } else {
                    CharcoalGray
                }
            )

            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PureWhite.copy(alpha = 0.28f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                } else {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color(0xFF909096) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
