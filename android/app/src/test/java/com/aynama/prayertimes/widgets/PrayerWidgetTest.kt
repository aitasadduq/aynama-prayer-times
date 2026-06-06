package com.aynama.prayertimes.widgets

import androidx.compose.ui.unit.dp
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
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

    @Test
    fun `widget size picks requested layouts`() {
        assertEquals(WidgetSize.SMALL, WidgetSize.from(androidx.compose.ui.unit.DpSize(80.dp, 80.dp)))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.from(androidx.compose.ui.unit.DpSize(160.dp, 160.dp)))
        assertEquals(WidgetSize.LARGE, WidgetSize.from(androidx.compose.ui.unit.DpSize(320.dp, 160.dp)))
    }
}
