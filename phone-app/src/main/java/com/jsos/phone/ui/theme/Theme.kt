package com.jsos.phone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = JsosPalette.Green,
    secondary = JsosPalette.Cyan,
    tertiary = JsosPalette.Yellow,
    background = JsosPalette.ScreenTop,
    surface = JsosPalette.Card,
    surfaceVariant = JsosPalette.CardAlt,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = JsosPalette.Text,
    onSurface = JsosPalette.Text,
    onSurfaceVariant = JsosPalette.Muted,
    outline = JsosPalette.Border,
    error = JsosPalette.Red
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D7377),
    secondary = Color(0xFF2B5797),
    tertiary = Color(0xFF8B7355),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun JsosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use dark theme for terminal app
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = JsosPalette.ScreenTop.toArgb()
            window.navigationBarColor = JsosPalette.ScreenTop.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
