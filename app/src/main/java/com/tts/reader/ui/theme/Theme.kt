package com.tts.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    secondary = AmberSecondary,
    tertiary = AmberGlow,
    background = ObsidianBackground,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onPrimary = ObsidianBackground,
    onSecondary = ObsidianBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun NeuralTTSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
