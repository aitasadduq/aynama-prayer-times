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

    fun isRamadan(date: LocalDate): Boolean = isRamadan(islamicMonthOf(date))

    fun isRamadanWithOffset(date: LocalDate, offsetDays: Int): Boolean =
        isRamadan(date.minusDays(offsetDays.toLong()))

    internal fun isRamadan(month: Int): Boolean = month == RAMADAN_MONTH

    fun currentHijriYear(): Int = IslamicCalendar().get(Calendar.YEAR)

    fun hijriDateOf(date: LocalDate): String {
        val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val cal = IslamicCalendar(java.util.Date.from(instant))
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return "$day ${HIJRI_MONTHS[month]} $year"
    }

    private fun islamicMonthOf(date: LocalDate): Int {
        val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        return IslamicCalendar(java.util.Date.from(instant)).get(Calendar.MONTH)
    }
}
