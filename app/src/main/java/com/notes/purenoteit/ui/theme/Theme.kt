package com.notes.purenoteit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = VibrantOrange,
    onPrimary = PureWhite,
    primaryContainer = SoftOrangeLight,
    onPrimaryContainer = CharcoalGray,
    secondary = CharcoalGray,
    onSecondary = PureWhite,
    secondaryContainer = BorderLight,
    onSecondaryContainer = CharcoalGray,
    tertiary = SoftYellow,
    onTertiary = CharcoalGray,
    tertiaryContainer = SoftYellowLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = VibrantOrange,
    onPrimary = PureWhite,
    primaryContainer = CharcoalGray,
    onPrimaryContainer = PureWhite,
    secondary = PureWhite,
    onSecondary = CharcoalGray,
    secondaryContainer = SurfaceDark,
    onSecondaryContainer = PureWhite,
    tertiary = SoftYellow,
    onTertiary = CharcoalGray,
    tertiaryContainer = CharcoalGray,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark
)

@Composable
fun PureNoteItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PureNoteItShapes,
        content = content
    )
}
