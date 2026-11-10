package com.notes.purenoteit.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object DesktopColors {
    val PureWhite = Color(0xFFFFFFFF)
    val BackgroundLight = Color(0xFFF8F8FA)
    val SidebarLight = Color(0xFFF0F0F5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val BackgroundDark = Color(0xFF1A1A1C)
    val SidebarDark = Color(0xFF202023)
    val SurfaceDark = Color(0xFF242426)
    val SurfaceElevatedDark = Color(0xFF2C2C2F)
    val CharcoalGray = Color(0xFF282828)
    val VibrantOrange = Color(0xFFFF900F)
    val SoftOrangeLight = Color(0xFFFFE8D1)
    val SoftYellow = Color(0xFFF7D786)
    val SoftYellowLight = Color(0xFFFFF7E3)
    val AccentBlue = Color(0xFF3269FF)
    val EmeraldGreen = Color(0xFF10B981)
    val CyanNeon = Color(0xFF06B6D4)
    val BorderLight = Color(0xFFE5E5EA)
    val BorderDark = Color(0xFF3A3A3C)
    val TextPrimaryLight = Color(0xFF1C1C1E)
    val TextSecondaryLight = Color(0xFF6E6E75)
    val TextPrimaryDark = Color(0xFFF5F5F7)
    val TextSecondaryDark = Color(0xFFA0A0A8)
}

private val DesktopLightColorScheme = lightColorScheme(
    primary = DesktopColors.VibrantOrange,
    onPrimary = DesktopColors.PureWhite,
    primaryContainer = DesktopColors.SoftOrangeLight,
    onPrimaryContainer = DesktopColors.CharcoalGray,
    secondary = DesktopColors.CharcoalGray,
    onSecondary = DesktopColors.PureWhite,
    secondaryContainer = DesktopColors.SidebarLight,
    onSecondaryContainer = DesktopColors.TextPrimaryLight,
    tertiary = DesktopColors.AccentBlue,
    onTertiary = DesktopColors.PureWhite,
    background = DesktopColors.BackgroundLight,
    onBackground = DesktopColors.TextPrimaryLight,
    surface = DesktopColors.SurfaceLight,
    onSurface = DesktopColors.TextPrimaryLight,
    surfaceVariant = DesktopColors.SidebarLight,
    onSurfaceVariant = DesktopColors.TextSecondaryLight,
    outline = DesktopColors.BorderLight
)

private val DesktopDarkColorScheme = darkColorScheme(
    primary = DesktopColors.VibrantOrange,
    onPrimary = DesktopColors.PureWhite,
    primaryContainer = Color(0xFF3D2812),
    onPrimaryContainer = DesktopColors.PureWhite,
    secondary = DesktopColors.PureWhite,
    onSecondary = DesktopColors.CharcoalGray,
    secondaryContainer = DesktopColors.SidebarDark,
    onSecondaryContainer = DesktopColors.TextPrimaryDark,
    tertiary = DesktopColors.CyanNeon,
    onTertiary = DesktopColors.CharcoalGray,
    background = DesktopColors.BackgroundDark,
    onBackground = DesktopColors.TextPrimaryDark,
    surface = DesktopColors.SurfaceDark,
    onSurface = DesktopColors.TextPrimaryDark,
    surfaceVariant = DesktopColors.SidebarDark,
    onSurfaceVariant = DesktopColors.TextSecondaryDark,
    outline = DesktopColors.BorderDark
)

private val DesktopShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun PureNoteItDesktopTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DesktopDarkColorScheme else DesktopLightColorScheme,
        shapes = DesktopShapes,
        content = content
    )
}
