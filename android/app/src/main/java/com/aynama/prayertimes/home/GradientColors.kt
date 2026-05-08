package com.aynama.prayertimes.home

import androidx.compose.ui.graphics.Color

internal fun gradientColorsFor(phase: PrayerPhase): Pair<Color, Color> = when (phase) {
    PrayerPhase.FAJR -> Color(0xFF1C1A17) to Color(0xFF3A3530)
    PrayerPhase.SUNRISE_TRANSITION -> Color(0xFF3A3530) to Color(0xFFE8C89A)
    PrayerPhase.DHUHR -> Color(0xFFF2EAD8) to Color(0xFFEDE1C5)
    PrayerPhase.ASR -> Color(0xFFE8C89A) to Color(0xFFB87A2E)
    PrayerPhase.MAGHRIB -> Color(0xFF6B2E2A) to Color(0xFF1C1A17)
    PrayerPhase.ISHA -> Color(0xFF0F1419) to Color(0xFF1C1A17)
}

internal fun isLightPhase(phase: PrayerPhase): Boolean =
    phase == PrayerPhase.DHUHR || phase == PrayerPhase.ASR || phase == PrayerPhase.SUNRISE_TRANSITION
