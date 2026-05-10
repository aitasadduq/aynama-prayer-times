package com.aynama.prayertimes.notifications

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AlarmSchedulerTest {

    private val date = LocalDate.of(2025, 6, 15) // non-Ramadan date

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

    private val times = AdhanWrapper().getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = date,
        timezone = ZoneId.of("Europe/London"),
        method = profile.calculationMethod,
    )

    @Test
    fun `buildAlarmSchedule returns 5 alarms for non-Ramadan`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        assertEquals(5, alarms.size)
    }

    @Test
    fun `buildAlarmSchedule returns 6 alarms during Ramadan (Imsak included)`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times)
        assertEquals(6, alarms.size)
    }

    @Test
    fun `buildAlarmSchedule Imsak trigger is Fajr minus 10 minutes`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times)
        val fajr = alarms.first { it.prayerName == "Fajr" }
        val imsak = alarms.first { it.prayerName == "Imsak" }
        assertEquals(fajr.triggerEpochMs - 10 * 60 * 1000L, imsak.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule has no duplicate request codes`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times)
        val codes = alarms.map { it.requestCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `buildAlarmSchedule non-Ramadan has no Imsak alarm`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        assertFalse(alarms.any { it.prayerName == "Imsak" })
    }

    @Test
    fun `buildAlarmSchedule Ramadan contains Imsak alarm`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times)
        assertTrue(alarms.any { it.prayerName == "Imsak" })
    }

    @Test
    fun `buildAlarmSchedule contains all 5 prayer names`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val names = alarms.map { it.prayerName }.toSet()
        assertTrue(names.containsAll(listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")))
    }

    @Test
    fun `buildAlarmSchedule Hanafi uses asrHanafi time`() {
        val hanafi = profile.copy(asrMadhab = AsrMadhab.HANAFI)
        val alarms = buildAlarmSchedule(hanafi, date, isRamadan = false, times = times)
        val asrAlarm = alarms.first { it.prayerName == "Asr" }
        val expectedMs = localTimeToEpochMs(times.asrHanafi, date)
        assertEquals(expectedMs, asrAlarm.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule Shafii uses asrShafii time`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val asrAlarm = alarms.first { it.prayerName == "Asr" }
        val expectedMs = localTimeToEpochMs(times.asrShafii, date)
        assertEquals(expectedMs, asrAlarm.triggerEpochMs)
    }
}
