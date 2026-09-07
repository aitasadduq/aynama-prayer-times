package com.aynama.prayertimes.wear

import androidx.compose.ui.graphics.Color

/*
 * DESIGN.md §7 — at watch scale only three tokens exist.
 *
 * No time-of-day cycle here: the watch renders a solid ink field with parchment text. That
 * saves OLED power and keeps the countdown readable in direct sunlight, which is where a watch
 * gets looked at. The same hex values as the phone, so a glance from one to the other does not
 * read as two apps.
 */

val WearInk = Color(0xFF1C1A17)
val WearParchment = Color(0xFFF2EAD8)
val WearSaffron = Color(0xFFB87A2E)
