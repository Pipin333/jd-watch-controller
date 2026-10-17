package com.example.myapplication.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonMagenta,
    secondary = NeonCyan,
    tertiary = NeonGold,
    background = StageBackground,
    surface = StageSurface,
    onPrimary = TextPrimary,
    onSecondary = StageBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun JDWatchControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
