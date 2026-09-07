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

/*
 * Every Material role is assigned, not just the dozen the app names directly.
 *
 * A role left unset keeps Material 3's baseline value, which is purple — and DESIGN.md §10
 * forbids purple and indigo outright. That is not theoretical: NavigationBar draws its
 * background from `surfaceContainer` and its selected-item pill from `secondaryContainer`,
 * neither of which used to be set, so the bottom nav rendered lavender on every screen of the
 * app in both themes. Error stays on the system red, per §3.
 */

private val LightColors = lightColorScheme(
    primary = Saffron,
    onPrimary = Parchment,
    primaryContainer = ParchmentMuted,
    onPrimaryContainer = Ink,
    inversePrimary = Saffron,

    // There is no second accent (§3), so secondary and tertiary are saffron and its pressed
    // shade rather than new hues. Leaving them unset is what put Material's baseline lavender
    // in the navigation bar.
    secondary = SaffronInk,
    onSecondary = Parchment,
    secondaryContainer = ParchmentMuted,
    onSecondaryContainer = Ink,
    tertiary = SaffronInk,
    onTertiary = Parchment,
    tertiaryContainer = ParchmentMuted,
    onTertiaryContainer = Ink,

    background = Parchment,
    onBackground = Ink,
    surface = Parchment,
    onSurface = Ink,
    surfaceVariant = ParchmentMuted,
    onSurfaceVariant = InkMuted,
    surfaceTint = Saffron,
    inverseSurface = Ink,
    inverseOnSurface = Parchment,

    // Material's elevation ladder, flattened onto two tones. The palette has no tint scale to
    // climb, and §2 asks utilitarian surfaces to stay on one stable ground.
    surfaceBright = Parchment,
    surfaceDim = ParchmentMuted,
    surfaceContainerLowest = Parchment,
    surfaceContainerLow = Parchment,
    surfaceContainer = Parchment,
    surfaceContainerHigh = ParchmentMuted,
    surfaceContainerHighest = ParchmentMuted,

    outline = InkMuted,
    outlineVariant = ParchmentMuted,
    scrim = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Saffron,
    onPrimary = Ink,
    primaryContainer = SaffronInk,
    onPrimaryContainer = Parchment,
    inversePrimary = SaffronInk,

    secondary = Saffron,
    onSecondary = Ink,
    secondaryContainer = InkMuted,
    onSecondaryContainer = Parchment,
    tertiary = Saffron,
    onTertiary = Ink,
    tertiaryContainer = InkMuted,
    onTertiaryContainer = Parchment,

    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = InkMuted,
    onSurfaceVariant = ParchmentMuted,
    surfaceTint = Saffron,
    inverseSurface = Parchment,
    inverseOnSurface = Ink,

    surfaceBright = InkMuted,
    surfaceDim = Ink,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Ink,
    surfaceContainer = Ink,
    surfaceContainerHigh = InkMuted,
    surfaceContainerHighest = InkMuted,

    outline = InkMuted,
    outlineVariant = InkMuted,
    scrim = Ink,
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
