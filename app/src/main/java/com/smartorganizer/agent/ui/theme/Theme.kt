package com.smartorganizer.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PaperVariant = Color(0xFFE8DFD0)

private val Scheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = InkSoft,
    onPrimaryContainer = Paper,
    secondary = Gold,
    onSecondary = Ink,
    background = Paper,
    onBackground = Bark,
    surface = PaperCard,
    onSurface = Bark,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = InkSoft,
    error = Danger,
    onError = Paper,
)

@Composable
fun SmartOrganizerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
