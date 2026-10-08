package com.maxlutz.instasaved.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// The colors of the agreed UI mockup (docs/prototype/ui-mockup.html): Instagram's black on white, or white on black.
private val Light = lightColorScheme(
    primary = Color(0xFF0095F6),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFF2F2F2),
    onSecondaryContainer = Color(0xFF111111),
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF737373),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF2F2F2),
    outline = Color(0xFFC7C7C7),
    outlineVariant = Color(0xFFDBDBDB),
    error = Color(0xFFD93025),
    onError = Color.White,
    inverseSurface = Color(0xFF323232),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF7CC4FF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3EA6FF),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFF1C1C1E),
    onSecondaryContainer = Color(0xFFF5F5F5),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color.Black,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF2C2C2E),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFFF6B60),
    onError = Color.Black,
    inverseSurface = Color(0xFF323232),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF7CC4FF),
)

/** The color of a warning that is not an error, like the stale-Export one. */
val ColorScheme.warning: Color
    get() = if (surface.luminance() < 0.5f) Color(0xFFFFCF7A) else Color(0xFF8A5A00)

/** The app's look, following the phone's light or dark setting. */
@Composable
fun InstaSavedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
