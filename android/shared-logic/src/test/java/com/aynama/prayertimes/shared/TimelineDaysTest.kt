package com.aynama.prayertimes.shared

import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The yesterday/today/tomorrow window every countdown surface builds its timeline from: the
 * widgets, the live notification, the watch app and its complication.
 */
class TimelineDaysTest {

    private val adhan = AdhanWrapper()
    private val today = LocalDate.of(2026, 3, 21)

    private val makkah = Profile(
        name = "Makkah",
        latitude = 21.4225,
        longitude = 39.8262,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = "Asia/Riyadh",
        useLocationTimezone = true,
    )

    @Test
    fun coversYesterdayTodayAndTomorrowInTheProfilesZone() {
        val days = adhan.timelineDays(makkah, today)

        assertEquals(listOf(today.minusDays(1), today, today.plusDays(1)), days.keys.toList())
        days.forEach { (date, times) ->
            val expected = adhan.getPrayerTimes(
                makkah.latitude, makkah.longitude, date, ZoneId.of("Asia/Riyadh"), makkah.calculationMethod,
            )
            assertEquals(expected, times)
        }
    }

    @Test
    fun dropsOnlyTheDaysWithNoTimes() {
        // Around the edge of polar day a single date can be undefined while its neighbours are
        // fine. The window keeps exactly the dates adhan can answer for.
        val edge = makkah.copy(name = "Arctic edge", latitude = 66.0, longitude = 15.0, timezone = "Europe/Oslo")
        var mixedWindows = 0
        var date = LocalDate.of(2026, 5, 1)
        while (date.isBefore(LocalDate.of(2026, 8, 1))) {
            val answerable = (-1L..1L).map { date.plusDays(it) }.filter { day ->
                runCatching {
                    adhan.getPrayerTimes(edge.latitude, edge.longitude, day, ZoneId.of("Europe/Oslo"), edge.calculationMethod)
                }.isSuccess
            }
            assertEquals(answerable, adhan.timelineDays(edge, date).keys.toList())
            if (answerable.size in 1..2) mixedWindows++
            date = date.plusDays(1)
        }
        assertTrue("expected a window with some days undefined", mixedWindows > 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeCoordinatesStillThrow() {
        // A corrupt profile, not a polar one: it must not look like "no times here".
        adhan.timelineDays(makkah.copy(latitude = 91.0), today)
    }
}
