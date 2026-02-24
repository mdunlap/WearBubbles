package com.wearbubbles.companion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueBubble = Color(0xFF1982FC)
private val BlueBubbleDark = Color(0xFF0A5DC2)

private val ColorScheme = darkColorScheme(
    primary = BlueBubble,
    onPrimary = Color.White,
    primaryContainer = BlueBubbleDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF3A3A3C),
    onSecondary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE5E5EA),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989D),
)

@Composable
fun WearBubblesCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
