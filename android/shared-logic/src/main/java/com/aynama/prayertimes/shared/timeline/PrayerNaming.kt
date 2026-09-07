package com.aynama.prayertimes.shared.timeline

import com.aynama.prayertimes.shared.data.entity.Prayer
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What a prayer is called on a given day.
 *
 * The only day-dependent name is Dhuhr's: on Friday the congregation prays Jumu'ah in its
 * place, and every surface that shows a specific day's prayer should say so — the app, the
 * widgets, the notifications, the watch apps and their complications.
 *
 * This is a label, not a prayer. Nothing downstream branches on it: Jumu'ah is scheduled,
 * calculated, tracked and notified as Dhuhr, and [Prayer.DHUHR] stays the stored value.
 * Duplicating the calculation to carry a different name would be the wrong trade every time.
 *
 * Recurring *settings* keep the canonical name. A row that configures the Dhuhr alert governs
 * all seven days, so calling it "Jumuah" because today happens to be Friday would misdescribe
 * what the toggle does. Day-specific displays are day-aware; slot configuration is not.
 */
const val JUMUAH = "Jumuah"

/** The name for [event] as it falls on [date]. */
fun prayerDisplayName(event: TimelineEvent, date: LocalDate): String = when {
    event == TimelineEvent.DHUHR && date.isFriday() -> JUMUAH
    else -> event.canonicalName()
}

/** The name for [prayer] as it falls on [date]. */
fun prayerDisplayName(prayer: Prayer, date: LocalDate): String = when {
    prayer == Prayer.DHUHR && date.isFriday() -> JUMUAH
    else -> prayer.canonicalName()
}

/**
 * The day-independent name, for surfaces that configure a prayer across every day rather
 * than displaying one occurrence of it.
 */
fun TimelineEvent.canonicalName(): String = when (this) {
    TimelineEvent.FAJR -> "Fajr"
    TimelineEvent.SUNRISE -> "Sunrise"
    TimelineEvent.DHUHR -> "Dhuhr"
    TimelineEvent.ASR -> "Asr"
    TimelineEvent.MAGHRIB -> "Maghrib"
    TimelineEvent.ISHA -> "Isha"
}

/** @see canonicalName */
fun Prayer.canonicalName(): String = when (this) {
    Prayer.FAJR -> "Fajr"
    Prayer.DHUHR -> "Dhuhr"
    Prayer.ASR -> "Asr"
    Prayer.MAGHRIB -> "Maghrib"
    Prayer.ISHA -> "Isha"
}

private fun LocalDate.isFriday(): Boolean = dayOfWeek == DayOfWeek.FRIDAY
