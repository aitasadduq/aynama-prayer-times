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

    fun isRamadanWithOffset(date: LocalDate, offsetDays: Int, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isRamadan(date.minusDays(offsetDays.toLong()), zone)

    internal fun isRamadan(month: Int): Boolean = month == RAMADAN_MONTH

    fun currentHijriYear(): Int = IslamicCalendar().get(Calendar.YEAR)

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
