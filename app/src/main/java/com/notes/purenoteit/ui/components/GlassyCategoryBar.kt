package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.notes.purenoteit.data.db.NoteData
import com.notes.purenoteit.ui.theme.CharcoalGray
import com.notes.purenoteit.ui.theme.PureWhite
import com.notes.purenoteit.ui.theme.VibrantOrange

/**
 * Glassy Navigation Tabs (V7) - Glassmorphism & 3D Door-Flip Category Bar
 * Videodaki ("code.xr - Glassy Navigation Tabs V7") yarı saydam buğulu cam (frosted glass)
 * ve 3D menteşeli kapı (door-flip) rotasyon animasyonuna sahip kategori navigasyon çubuğu.
 */
@Composable
fun GlassyCategoryBar(
    categories: List<CategoryData>,
    selectedCategory: String?,
    totalNotesCount: Int,
    activeNotes: List<NoteData>,
    pagerState: PagerState? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    getCategoryDotColor: (String) -> Color?,
    onAllSelected: () -> Unit,
    onCategorySelected: (categoryName: String, isSame: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. Frosted Glass Dış Taşıyıcı Kapsül (Smoky/Milky Acrylic Glass Background)
    val glassBgBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x35FFFFFF),
                Color(0x15FFFFFF)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xE6FFFFFF),
                Color(0xB3E5E5ED)
            )
        )
    }

    // 2. Cam Işık Kırılması / Parlama Kenarlığı (Specular Highlight Rim)
    val glassBorderBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                Color.White.copy(alpha = 0.32f),
                Color.White.copy(alpha = 0.08f)
            )
        } else {
            listOf(
                Color.White,
                Color.White.copy(alpha = 0.75f),
                Color(0x22000000)
            )
        }
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.12f),
                ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(brush = glassBgBrush)
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
            // "Tümü" Sekmesi (İndeks 0)
            val isAllSelected = selectedCategory == null
            GlassyDoorFlipChip(
                label = stringResource(R.string.all_categories),
                isSelected = isAllSelected,
                pagerState = pagerState,
                chipIndex = 0,
                count = totalNotesCount,
                isDarkTheme = isDarkTheme,
                onClick = onAllSelected
            )

            // Dinamik Kategoriler (İndeks 1..N)
            categories.forEachIndexed { index, category ->
                val catCount = remember(activeNotes, category.id) {
                    activeNotes.count { it.categoryId == category.id }
                }
                val isSelected = selectedCategory == category.name

                GlassyDoorFlipChip(
                    label = category.name,
                    isSelected = isSelected,
                    pagerState = pagerState,
                    chipIndex = index + 1,
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
 * Seçilme anında sol kenar menteşesi etrafında (Y ekseni) bir oda kapısı gibi
 * 3D açılarak yaylanır (rotationY: 60° -> 0° spring bounce).
 * HorizontalPager jesti sırasında parmak hareketini gerçek zamanlı takip eder;
 * parmak durdurulduğunda iki açı arasında havada kalır ve bırakıldığında yaylanarak kapanır.
 */
@Composable
fun GlassyDoorFlipChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    chipIndex: Int = 0,
    isLocked: Boolean = false,
    count: Int? = null,
    dotColor: Color? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    // Pager bağlı ise seçili durumu aktif sayfaya göre, yoksa parametreye göre belirlenir
    val isPillSelected = if (pagerState != null) {
        pagerState.currentPage == chipIndex
    } else {
        isSelected
    }

    // Kapı Menteşe Açısı (Y-Rotasyonu) & Ölçek Animasyonu
    val doorAngle = remember { Animatable(if (isPillSelected) 0f else 25f) }
    val pillScale = remember { Animatable(if (isPillSelected) 1f else 0.95f) }

    LaunchedEffect(isPillSelected) {
        if (isPillSelected) {
            // Kapı dışarıdan yaylanarak açılıp yerine oturur (3D Door Swing In)
            doorAngle.snapTo(60f)
            pillScale.snapTo(0.92f)
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
            // Yumuşak geri kapanma
            doorAngle.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            pillScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    // Pager parmak hareketi bittiğinde ve bu sayfaya yerleştiğinde kapıyı yaylandırarak yerine oturtur
    LaunchedEffect(pagerState?.settledPage) {
        if (pagerState != null && pagerState.settledPage == chipIndex && !pagerState.isScrollInProgress) {
            doorAngle.snapTo(25f)
            doorAngle.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Seçili iken hafif genişleyen iç dolgu
    val horizontalPadding by animateDpAsState(
        targetValue = if (isPillSelected) 14.dp else 11.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillPadding"
    )

    // Seçili Sekme Cam Arka Planı (Elevated Frosted Glass Pill)
    val activePillBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color(0x4DFFFFFF),
                Color(0x24FFFFFF)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF7F7FA)
            )
        )
    }

    // Seçili Sekme Kenar Pırıltısı (Specular Highlight Rim)
    val activePillBorderBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White,
                Color(0xFFE2E2E8)
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val currentRotation: Float
                val currentScale: Float

                // HorizontalPager kaydırma halindeyse parmağın gerçek zamanlı konumunu menteşeye bağla
                if (pagerState != null && (pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f)) {
                    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    val delta = pagePosition - chipIndex
                    val absDelta = kotlin.math.abs(delta).coerceIn(0f, 1f)

                    // 3D kapı açılma: Sayfa tam ortadayken (absDelta == 0) kapı düz durur (0°).
                    // Parmak kaydırıldıkça sol menteşe etrafında kapı gibi dışarı doğru açılır (maks 55°).
                    currentRotation = absDelta * 55f
                    currentScale = 1f - (absDelta * 0.06f)
                } else {
                    currentRotation = doorAngle.value
                    currentScale = pillScale.value
                }

                // 3D Kapı Perspektifi (Y ekseni etrafında menteşe dönüşü)
                rotationY = currentRotation
                scaleX = currentScale
                scaleY = currentScale
                cameraDistance = 14f * density // 3D alan derinliği
                transformOrigin = TransformOrigin(0.12f, 0.5f) // Sol menteşe kapı ekseni
            }
            .shadow(
                elevation = if (isPillSelected) 3.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isDarkTheme) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.14f),
                ambientColor = if (isDarkTheme) Color.Black.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.06f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = if (isPillSelected) activePillBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .then(
                if (isPillSelected) {
                    Modifier.border(
                        width = 1.dp,
                        brush = activePillBorderBrush,
                        shape = RoundedCornerShape(20.dp)
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
                    tint = if (isDarkTheme) Color(0xFFFFA756) else VibrantOrange,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            } else if (dotColor != null) {
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
                fontWeight = if (isPillSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isPillSelected) {
                    if (isDarkTheme) PureWhite else CharcoalGray
                } else {
                    if (isDarkTheme) Color(0xFFA5A5AB) else Color(0xFF6E6E73)
                }
            )

            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                if (isPillSelected) {
                    // Seçili cam rozet (Frosted Badge)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isDarkTheme) Color(0x28FFFFFF) else Color(0x0E000000)
                            )
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) PureWhite else CharcoalGray
                        )
                    }
                } else {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isDarkTheme) Color(0xFF7A7A80) else Color(0xFF8E8E93)
                    )
                }
            }
        }
    }
}
