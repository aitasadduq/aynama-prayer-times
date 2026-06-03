package com.aynama.prayertimes.notifications

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import java.time.LocalDate
import java.time.ZoneId

object RamadanDetector {

    private const val RAMADAN_MONTH = 8 // IslamicCalendar.RAMADAN

    private val HIJRI_MONTHS = listOf(
        "Muḥarram", "Ṣafar", "Rabīʻ I", "Rabīʻ II",
        "Jumādā I", "Jumādā II", "Rajab", "Shaʻbān",
        "Ramaḍān", "Shawwāl", "Dhu al-Qaʻdah", "Dhu al-Ḥijjah",
    )

    fun isRamadan(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isRamadan(islamicMonthOf(date, zone))

    // A positive offset advances the Hijri calendar (the month starts earlier, e.g. moon
    // sighted the night before the calculated date); a negative offset delays it.
    fun isRamadanWithOffset(date: LocalDate, offsetDays: Int, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isRamadan(date.plusDays(offsetDays.toLong()), zone)

    internal fun isRamadan(month: Int): Boolean = month == RAMADAN_MONTH

    fun currentHijriYear(): Int = IslamicCalendar().get(Calendar.YEAR)

    // Stable identifier for a Hijri month (year * 12 + month) on the given civil date.
    fun hijriMonthKey(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val cal = calendarFor(date, zone)
        return cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    }

    // The offset only applies while the adjusted (perceived) Hijri month still matches the month
    // it was set for; once a new month begins it auto-expires to 0. Evaluated with the stored
    // offset constant so the month comparison is independent of the result.
    fun effectiveHijriOffset(offset: Int, monthKey: Int, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int =
        if (offset != 0 && hijriMonthKey(date.plusDays(offset.toLong()), zone) == monthKey) offset else 0

    fun hijriDateWithOffset(date: LocalDate, offsetDays: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        hijriDateOf(date.plusDays(offsetDays.toLong()), zone)

    fun hijriDateOf(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val cal = calendarFor(date, zone)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return "$day ${HIJRI_MONTHS[month]} $year"
    }

    private fun islamicMonthOf(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int =
        calendarFor(date, zone).get(Calendar.MONTH)

    // Noon in the target zone unambiguously falls within `date`, regardless of how the
    // IslamicCalendar's own timezone partitions days — avoids off-by-one near IDL crossings.
    private fun calendarFor(date: LocalDate, zone: ZoneId): IslamicCalendar {
        val instant = date.atTime(12, 0).atZone(zone).toInstant()
        val cal = IslamicCalendar(android.icu.util.TimeZone.getTimeZone(zone.id))
        cal.time = java.util.Date.from(instant)
        return cal
    }
}
