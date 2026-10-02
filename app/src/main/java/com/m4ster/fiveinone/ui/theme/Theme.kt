package com.m4ster.fiveinone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Shared palette: dark arcade background, one accent per player color. */

val AccentGreen = Color(0xFF4ADE80)
val AccentAmber = Color(0xFFFBBF24)
val AccentRed = Color(0xFFF87171)
val AccentBlue = Color(0xFF60A5FA)
val BoardDark = Color(0xFF0B1220)
val BoardAlt = Color(0xFF16213A)
val GridLine = Color(0xFF243152)

private val Colors = darkColorScheme(
    primary = AccentGreen,
    secondary = AccentBlue,
    tertiary = AccentAmber,
    background = BoardDark,
    surface = Color(0xFF111A2E),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
)

@Composable
fun FiveInOneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
