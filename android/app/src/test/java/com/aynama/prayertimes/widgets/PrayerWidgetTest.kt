package com.aynama.prayertimes.widgets

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PrayerWidgetTest {
    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2025, 6, 15)
    private val profile = Profile(
        id = 1L,
        name = "London",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
    )
    private val adhan = AdhanWrapper()
    private val todayTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = date,
        timezone = zone,
        method = profile.calculationMethod,
    )
    private val tomorrowTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = date.plusDays(1),
        timezone = zone,
        method = profile.calculationMethod,
    )
    private val widgetTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Test
    fun `widget state uses next prayer abbreviation and 24-hour time`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("ASR", state.nextPrayerAbbreviation)
        assertEquals("Asr", state.nextPrayerName)
        assertEquals(todayTimes.asrShafii.format(widgetTimeFormatter), state.nextPrayerDisplayTime)
    }

    @Test
    fun `widget schedule includes six rows with sunrise`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(1, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals(listOf("FAJ", "SUN", "DHU", "ASR", "MAG", "ISH"), state.schedule.map { it.abbreviation })
    }

    @Test
    fun `widget state exposes dates and sunrise`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
            hijriDateText = "19 Dhu al-Ḥijjah 1446",
        )

        assertEquals(todayTimes.sunrise.format(widgetTimeFormatter), state.sunriseDisplayTime)
        assertEquals(
            date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
            state.gregorianDateText,
        )
        assertEquals("19 Dhu al-Ḥijjah 1446", state.hijriDateText)
    }

    @Test
    fun `next and current are derived chronologically even when clock order is scrambled`() {
        // Times as they appear for a London profile rendered in a far-off device timezone:
        // not in canonical clock order. Sunrise (22:45) is the only event still ahead at 21:14.
        val scrambled = PrayerTimesResult(
            fajr = LocalTime.of(19, 0),
            sunrise = LocalTime.of(22, 45),
            dhuhr = LocalTime.of(7, 0),
            asrShafii = LocalTime.of(11, 21),
            asrHanafi = LocalTime.of(11, 21),
            maghrib = LocalTime.of(15, 14),
            isha = LocalTime.of(18, 59),
        )

        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = scrambled,
            tomorrowTimes = scrambled,
            now = ZonedDateTime.of(date, LocalTime.of(21, 14), zone),
            elapsedRealtime = 0L,
        )

        // Next is Sunrise (the soonest upcoming event), not tomorrow's Isha.
        assertEquals("Sunrise", state.nextPrayerName)
        assertEquals("SUN", state.nextPrayerAbbreviation)
        // Countdown ~1h31m to 22:45, not ~21h.
        assertTrue(state.countdownBaseElapsedRealtime in 1_000L * 60 * 80..1_000L * 60 * 100)
        // The most recently started obligatory prayer is Fajr (19:00).
        assertEquals("Fajr", state.currentPrayerName)
    }

    @Test
    fun `widget hijri date defaults to empty`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("", state.hijriDateText)
    }

    @Test
    fun `widget update schedule only keeps future prayer changes`() {
        val allUpdates = buildWidgetUpdateSchedule(
            profile = profile,
            date = date,
            times = todayTimes,
            zone = zone,
            nowEpochMs = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        val now = date.atTime(todayTimes.asrShafii.plusMinutes(1)).atZone(zone).toInstant().toEpochMilli()
        val updates = buildWidgetUpdateSchedule(profile, date, todayTimes, zone, now)

        assertTrue(updates.size < allUpdates.size)
        assertTrue(updates.all { it.triggerEpochMs > now })
    }
}
