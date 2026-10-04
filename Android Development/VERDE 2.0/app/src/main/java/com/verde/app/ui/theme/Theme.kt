package com.verde.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VerdeGreenDark,
    onPrimary = Color(0xFF0B2E12),             // dark green text on light-green buttons
    secondary = VerdeTealDark,
    onSecondary = Color(0xFF00201C),
    tertiary = VerdeAmberDark,
    onTertiary = Color(0xFF261A00),
    background = VerdeBackgroundDark,
    surface = VerdeSurfaceDark,
    surfaceVariant = Color(0xFF243328),
    onSurfaceVariant = Color(0xFFBFCABF),
    surfaceContainer = Color(0xFF1B281E),
    surfaceContainerHigh = Color(0xFF1F2D23),
    surfaceContainerHighest = Color(0xFF243328), // cards
    outline = Color(0xFF89938A)                  // text field and button borders
)

private val LightColorScheme = lightColorScheme(
    primary = VerdeGreen,
    onPrimary = Color.White,
    secondary = VerdeTeal,
    onSecondary = Color.White,
    tertiary = VerdeAmber,
    onTertiary = Color(0xFF261A00),
    background = VerdeBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFEEF5EC),
    surfaceContainerHigh = Color(0xFFE9F0E7),
    surfaceContainerHighest = Color(0xFFE3EBE1), // cards
    outline = Color(0xFF717970)
)

// VERDE always uses its own colours: light or dark depending on the phone's setting
@Composable
fun VERDETheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}