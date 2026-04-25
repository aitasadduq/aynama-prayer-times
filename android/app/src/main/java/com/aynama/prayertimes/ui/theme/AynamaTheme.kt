package com.aynama.prayertimes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1C1A17)
val InkMuted = Color(0xFF6B6560)
val Parchment = Color(0xFFF2EAD8)
val ParchmentMuted = Color(0xFFD4C9B1)
val Saffron = Color(0xFFB87A2E)
val SaffronInk = Color(0xFF8A5A22)

private val LightColors = lightColorScheme(
    primary = Saffron,
    onPrimary = Parchment,
    primaryContainer = ParchmentMuted,
    onPrimaryContainer = Ink,
    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = ParchmentMuted,
    onSurfaceVariant = InkMuted,
    outline = InkMuted,
    outlineVariant = ParchmentMuted,
)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Ink,
    primaryContainer = SaffronInk,
    onPrimaryContainer = Parchment,
    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = InkMuted,
    onSurfaceVariant = ParchmentMuted,
    outline = InkMuted,
    outlineVariant = InkMuted,
)

@Composable
fun AynamaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AynamaTypography,
        content = content,
    )
}
