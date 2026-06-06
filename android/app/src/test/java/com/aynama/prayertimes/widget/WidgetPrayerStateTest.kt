package com.aynama.prayertimes.widget

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetPrayerStateTest {

    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2026, 6, 5)
    private val tomorrowFajr = LocalTime.of(4, 25)

    private val times = PrayerTimesResult(
        fajr = LocalTime.of(4, 30),
        sunrise = LocalTime.of(6, 10),
        dhuhr = LocalTime.of(12, 15),
        asrShafii = LocalTime.of(15, 45),
        asrHanafi = LocalTime.of(16, 30),
        maghrib = LocalTime.of(19, 50),
        isha = LocalTime.of(21, 20),
    )

    private fun nowAt(h: Int, m: Int): ZonedDateTime =
        date.atTime(h, m).atZone(zone)

    @Test
    fun `mid-day picks next obligatory prayer`() {
        val state = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(13, 0))
        assertEquals("Asr", state.nextName)
        assertEquals("ASR", state.nextAbbrev)
        assertEquals(LocalTime.of(15, 45), state.nextTime)
    }

    @Test
    fun `between fajr and sunrise counts down to sunrise`() {
        val state = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(5, 0))
        assertEquals("Sunrise", state.nextName)
        assertEquals(LocalTime.of(6, 10), state.nextTime)
    }

    @Test
    fun `hanafi madhab selects later asr`() {
        // At 15:50 the Shafii Asr (15:45) has passed but the Hanafi Asr (16:30) is still upcoming.
        val hanafi = buildWidgetState(times, tomorrowFajr, AsrMadhab.HANAFI, nowAt(15, 50))
        assertEquals("Asr", hanafi.nextName)
        assertEquals(LocalTime.of(16, 30), hanafi.schedule.first { it.name == "Asr" }.time)
        val shafii = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(15, 50))
        assertEquals("Maghrib", shafii.nextName)
        assertEquals(LocalTime.of(15, 45), shafii.schedule.first { it.name == "Asr" }.time)
    }

    @Test
    fun `after isha rolls over to tomorrow fajr`() {
        val state = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(22, 0))
        assertEquals("Fajr", state.nextName)
        assertEquals(tomorrowFajr, state.nextTime)
        val expectedEpoch = date.plusDays(1).atTime(tomorrowFajr).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedEpoch, state.nextEpochMs)
    }

    @Test
    fun `next epoch is today when prayer still upcoming`() {
        val state = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(13, 0))
        val expectedEpoch = date.atTime(15, 45).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedEpoch, state.nextEpochMs)
    }

    @Test
    fun `schedule has six rows in order including sunrise`() {
        val state = buildWidgetState(times, tomorrowFajr, AsrMadhab.SHAFII, nowAt(13, 0))
        assertEquals(
            listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
            state.schedule.map { it.name },
        )
        assertEquals(
            listOf("FAJ", "SUN", "DHU", "ASR", "MAG", "ISH"),
            state.schedule.map { it.abbrev },
        )
    }
}
