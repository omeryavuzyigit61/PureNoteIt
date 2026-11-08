package com.notes.purenoteit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

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
    // 1. Gerçekten saydam taşıyıcı kapsül: arkadaki içerik hissedilir, katman gibi durmaz.
    val glassBgBrush = if (isDarkTheme) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x12FFFFFF),
                Color(0x06FFFFFF)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x47FFFFFF),
                Color(0x26FFFFFF)
            )
        )
    }

    // 2. Çok ince, düşük kontrastlı kenar (sert katman çizgisi yok)
    val glassBorderBrush = Brush.verticalGradient(
        colors = if (isDarkTheme) {
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.03f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.70f),
                Color(0x14000000)
            )
        }
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(brush = glassBgBrush)
            .border(
                width = 0.75.dp,
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
 *
 * Titreme / basıklık düzeltmesi: tüm görsel durum (seçilme oranı, kapı açısı, ölçek)
 * HorizontalPager konumundan (currentPage + offsetFraction) TEK KAYNAKTAN, dallanmasız ve
 * sürekli hesaplanır. Böylece dokunma, liste başı/sonu veya sayfa geçişinde snapTo sıçraması,
 * ölçek farkı ya da layout yeniden akışı oluşmaz; geçiş bitince tüm sekmeler tam düz (0°, 1x)
 * durumda sonlanır. Kapı, geçiş sırasında sin(π·d) eğrisiyle açılıp aynı yumuşaklıkla kapanır.
 * Seçili sekme ekran dışındaysa menü otomatik olarak görünür alana kaydırılır.
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

    // Pager yoksa yedek: parametreye göre yumuşak (tween) seçilme oranı
    val fallbackSelection = remember { Animatable(if (isSelected) 1f else 0f) }
    LaunchedEffect(isSelected, pagerState == null) {
        if (pagerState == null) {
            fallbackSelection.animateTo(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    // 0f..1f seçilme oranı (graphicsLayer / çizim aşamasında okunur; recomposition tetiklemez)
    val selectionFraction: () -> Float = {
        if (pagerState != null) {
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            (1f - abs(position - chipIndex)).coerceIn(0f, 1f)
        } else {
            fallbackSelection.value
        }
    }

    // Sadece renk eşiği için (yerleşimi etkilemez): ayrık geçiş
    val isPillSelected by remember(pagerState, chipIndex) {
        derivedStateOf {
            if (pagerState != null) {
                val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                abs(position - chipIndex) < 0.5f
            } else {
                isSelected
            }
        }
    }

    // Seçili sekme görünür alanda değilse yatay menüyü kaydırarak göster
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var chipWidthPx by remember { mutableIntStateOf(0) }
    var chipHeightPx by remember { mutableIntStateOf(0) }
    val revealMarginPx = with(LocalDensity.current) { 56.dp.toPx() }
    val isTarget = if (pagerState != null) pagerState.targetPage == chipIndex else isSelected
    LaunchedEffect(isTarget, chipWidthPx) {
        if (isTarget && chipWidthPx > 0) {
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = -revealMarginPx,
                    top = 0f,
                    right = chipWidthPx + revealMarginPx,
                    bottom = chipHeightPx.toFloat()
                )
            )
        }
    }

    val chipShape = RoundedCornerShape(20.dp)
    // Saydam zeminin altından gölge sızıp "mat" görünmesin diye gölge kapalı
    val shadowElevationPx = 0f
    val shadowSpot = if (isDarkTheme) Color.Black.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.10f)

    // Seçili sekme cam arka planı (daha saydam, katman hissi vermez)
    val activePillBrush = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0DFFFFFF)))
    } else {
        Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x5CF7F7FA)))
    }
    val activePillBorderBrush = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color(0x26E2E2E8)))
    }

    Box(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged {
                chipWidthPx = it.width
                chipHeightPx = it.height
            }
            .graphicsLayer {
                // Kapı: seçili (d=0) ve tamamen uzak (d>=1) iken düz; geçişte sin(π·d) ile menteşeden açılır
                val d = if (pagerState != null) {
                    val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                    abs(position - chipIndex).coerceIn(0f, 1f)
                } else {
                    1f - fallbackSelection.value
                }
                val swing = sin(PI.toFloat() * d)
                rotationY = swing * 38f
                val scale = 1f - swing * 0.04f
                scaleX = scale
                scaleY = scale
                cameraDistance = 14f * density
                transformOrigin = TransformOrigin(0.08f, 0.5f)
            }
            .clip(chipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        // Cam zemin katmanı: sadece alpha değişir (layout / boyut sabit, sıçrama yok)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val sel = selectionFraction()
                    alpha = sel
                    shadowElevation = shadowElevationPx * sel
                    shape = chipShape
                    clip = false
                    spotShadowColor = shadowSpot
                    ambientShadowColor = shadowSpot
                }
                .clip(chipShape)
                .background(brush = activePillBrush)
                .border(width = 1.dp, brush = activePillBorderBrush, shape = chipShape)
        )

        // İçerik: sabit düzen (sabit font ağırlığı + sabit rozet) -> geçişte genişlik değişmez
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
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
                fontWeight = FontWeight.SemiBold,
                color = if (isPillSelected) {
                    if (isDarkTheme) PureWhite else CharcoalGray
                } else {
                    if (isDarkTheme) Color(0xFFA5A5AB) else Color(0xFF6E6E73)
                }
            )

            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isPillSelected) {
                                if (isDarkTheme) Color(0x1AFFFFFF) else Color(0x0A000000)
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPillSelected) {
                            if (isDarkTheme) PureWhite else CharcoalGray
                        } else {
                            if (isDarkTheme) Color(0xFF7A7A80) else Color(0xFF8E8E93)
                        }
                    )
                }
            }
        }
    }
}
