package com.aynama.prayertimes.widget

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class WidgetRow(val name: String, val abbrev: String, val time: LocalTime)

data class WidgetPrayerState(
    val nextName: String,
    val nextAbbrev: String,
    val nextTime: LocalTime,
    val nextEpochMs: Long,
    val schedule: List<WidgetRow>,
)

private val ABBREV = mapOf(
    "Fajr" to "FAJ",
    "Sunrise" to "SUN",
    "Dhuhr" to "DHU",
    "Asr" to "ASR",
    "Maghrib" to "MAG",
    "Isha" to "ISH",
)

// Pure function — tested without Android runtime.
// `tomorrowFajr` is used when all of today's prayers have passed (after Isha → tomorrow's Fajr).
fun buildWidgetState(
    times: PrayerTimesResult,
    tomorrowFajr: LocalTime,
    asrMadhab: AsrMadhab,
    now: ZonedDateTime,
): WidgetPrayerState {
    val asr = if (asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
    val schedule = listOf(
        row("Fajr", times.fajr),
        row("Sunrise", times.sunrise),
        row("Dhuhr", times.dhuhr),
        row("Asr", asr),
        row("Maghrib", times.maghrib),
        row("Isha", times.isha),
    )
    // Countdown targets every schedule row, including Sunrise (matches the Home ribbon).
    val prayers = listOf(
        "Fajr" to times.fajr,
        "Sunrise" to times.sunrise,
        "Dhuhr" to times.dhuhr,
        "Asr" to asr,
        "Maghrib" to times.maghrib,
        "Isha" to times.isha,
    )
    val nowTime = now.toLocalTime()
    val zone = now.zone
    val upcoming = prayers.firstOrNull { (_, t) -> t > nowTime }
    val (nextName, nextTime, nextDate) = if (upcoming != null) {
        Triple(upcoming.first, upcoming.second, now.toLocalDate())
    } else {
        Triple("Fajr", tomorrowFajr, now.toLocalDate().plusDays(1))
    }
    val nextEpochMs = nextDate.atTime(nextTime).atZone(zone).toInstant().toEpochMilli()
    return WidgetPrayerState(
        nextName = nextName,
        nextAbbrev = ABBREV.getValue(nextName),
        nextTime = nextTime,
        nextEpochMs = nextEpochMs,
        schedule = schedule,
    )
}

private fun row(name: String, time: LocalTime) = WidgetRow(name, ABBREV.getValue(name), time)

internal val WIDGET_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
