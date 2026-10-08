package com.seasons.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBackground = Color(0xFF121212)
val AppSurface = Color(0xFF1E1E1E)
val Grey1 = Color(0xFF9E9E9E)
val Grey2 = Color(0xFF616161)
val Grey3 = Color(0xFF2C2C2C)
val Amber = Color(0xFFFFB300)
val TextMain = Color(0xFFE0E0E0)

private val colors = darkColorScheme(
    primary = TextMain,
    onPrimary = AppBackground,
    background = AppBackground,
    onBackground = TextMain,
    surface = AppSurface,
    onSurface = TextMain,
    surfaceVariant = AppSurface,
    onSurfaceVariant = Grey1,
    secondaryContainer = Grey3,
    onSecondaryContainer = TextMain,
    outline = Grey2,
    // Material's default dark containers are purple-tinted. The spec says grey.
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = AppSurface,
    surfaceContainer = AppSurface,
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Grey3,
    surfaceTint = Color.Transparent,
    error = Amber,
)

@Composable
fun SeasonsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
