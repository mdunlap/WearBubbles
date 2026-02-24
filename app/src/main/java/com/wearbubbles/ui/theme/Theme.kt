package com.wearbubbles.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

val BlueBubble = Color(0xFF1982FC)
val BlueBubbleDark = Color(0xFF0A5DC2)
val GrayBubble = Color(0xFF3A3A3C)
val SurfaceDark = Color(0xFF1C1C1E)
val OnSurfaceLight = Color(0xFFE5E5EA)

private val WearBubblesColors = Colors(
    primary = BlueBubble,
    primaryVariant = BlueBubbleDark,
    secondary = GrayBubble,
    background = Color.Black,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = Color(0xFF98989D)
)

@Composable
fun WearBubblesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearBubblesColors,
        content = content
    )
}
