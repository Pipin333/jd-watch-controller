package com.example.myapplication.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val WearColorPalette = Colors(
    primary = Color(0xFF64B5F6),
    primaryVariant = Color(0xFF1E88E5),
    secondary = Color(0xFF81C784),
    secondaryVariant = Color(0xFF388E3C),
    error = Color(0xFFE57373),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onError = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF1E293B),
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
