package com.aynama.prayertimes.notifications

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import java.time.LocalDate
import java.time.ZoneId

object RamadanDetector {

    private const val RAMADAN_MONTH = 8 // IslamicCalendar.RAMADAN

    fun isRamadan(date: LocalDate): Boolean = isRamadan(islamicMonthOf(date))

    internal fun isRamadan(month: Int): Boolean = month == RAMADAN_MONTH

    fun currentHijriYear(): Int = IslamicCalendar().get(Calendar.YEAR)

    private fun islamicMonthOf(date: LocalDate): Int {
        val instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        return IslamicCalendar(java.util.Date.from(instant)).get(Calendar.MONTH)
    }
}
