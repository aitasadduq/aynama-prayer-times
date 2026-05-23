package com.aynama.prayertimes.notifications

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // Notification preference filtering tests

    @Test
    fun `buildAlarmSchedule master off returns empty list`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times, masterEnabled = false)
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun `buildAlarmSchedule master off skips Imsak even during Ramadan`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times, masterEnabled = false)
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun `buildAlarmSchedule per-prayer disabled skips that prayer`() {
        val alarms = buildAlarmSchedule(
            profile, date, isRamadan = false, times = times,
            prayerEnabled = { index -> index != PRAYER_INDEX_FAJR },
        )
        assertFalse(alarms.any { it.prayerName == "Fajr" })
        assertEquals(4, alarms.size)
    }

    @Test
    fun `buildAlarmSchedule all prayers disabled returns empty list`() {
        val alarms = buildAlarmSchedule(
            profile, date, isRamadan = false, times = times,
            prayerEnabled = { false },
        )
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun `buildAlarmSchedule imsak disabled skips Imsak during Ramadan`() {
        val alarms = buildAlarmSchedule(
            profile, date, isRamadan = true, times = times,
            imsakEnabled = false,
        )
        assertFalse(alarms.any { it.prayerName == "Imsak" })
        assertEquals(5, alarms.size)
    }

    @Test
    fun `buildAlarmSchedule imsak enabled during Ramadan includes Imsak`() {
        val alarms = buildAlarmSchedule(
            profile, date, isRamadan = true, times = times,
            imsakEnabled = true,
        )
        assertTrue(alarms.any { it.prayerName == "Imsak" })
    }

    @Test
    fun `buildAlarmSchedule default params preserve original behaviour`() {
        val withDefaults = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val explicit = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            masterEnabled = true, prayerEnabled = { true }, imsakEnabled = true)
        assertEquals(withDefaults.size, explicit.size)
        assertEquals(withDefaults.map { it.prayerName }, explicit.map { it.prayerName })
    }

    // Phase 6c: time offset tests

    @Test
    fun `buildAlarmSchedule applies positive offset to prayer trigger time`() {
        val offset = 10
        val base = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val shifted = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { offset })
        val baseFajr = base.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        val shiftedFajr = shifted.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        assertEquals(baseFajr.triggerEpochMs + offset * 60_000L, shiftedFajr.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule applies negative offset to prayer trigger time`() {
        val offset = -5
        val base = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val shifted = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { offset })
        val baseMaghrib = base.first { it.prayerName == "Maghrib" && !it.isEarlyReminder }
        val shiftedMaghrib = shifted.first { it.prayerName == "Maghrib" && !it.isEarlyReminder }
        assertEquals(baseMaghrib.triggerEpochMs + offset * 60_000L, shiftedMaghrib.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule zero offset leaves trigger unchanged`() {
        val base = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        val shifted = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { 0 })
        assertEquals(
            base.map { it.triggerEpochMs },
            shifted.map { it.triggerEpochMs },
        )
    }

    // Phase 6c: early reminder tests

    @Test
    fun `buildAlarmSchedule creates early reminder alarms when earlyReminderMinutes gt 0`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            earlyReminderMinutes = { 10 })
        val earlyAlarms = alarms.filter { it.isEarlyReminder }
        assertEquals(5, earlyAlarms.size)
    }

    @Test
    fun `buildAlarmSchedule creates no early reminder alarms by default`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times)
        assertTrue(alarms.none { it.isEarlyReminder })
    }

    @Test
    fun `buildAlarmSchedule early reminder fires earlyReminderMinutes before adjusted prayer time`() {
        val offsetMinutes = 5
        val earlyMinutes = 10
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { offsetMinutes },
            earlyReminderMinutes = { earlyMinutes })
        val fajr = alarms.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        val fajrEarly = alarms.first { it.prayerName == "Fajr" && it.isEarlyReminder }
        assertEquals(fajr.triggerEpochMs - earlyMinutes * 60_000L, fajrEarly.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule early reminder request codes use EARLY_REMINDER_BASE_INDEX offset`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            earlyReminderMinutes = { 5 })
        val fajrRegular = alarms.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        val fajrEarly = alarms.first { it.prayerName == "Fajr" && it.isEarlyReminder }
        assertEquals(
            fajrRegular.requestCode + EARLY_REMINDER_BASE_INDEX,
            fajrEarly.requestCode,
        )
    }

    @Test
    fun `buildAlarmSchedule no early reminder for disabled prayer`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerEnabled = { index -> index != PRAYER_INDEX_FAJR },
            earlyReminderMinutes = { 10 })
        assertFalse(alarms.any { it.prayerName == "Fajr" })
        assertEquals(4, alarms.filter { !it.isEarlyReminder }.size)
        assertEquals(4, alarms.filter { it.isEarlyReminder }.size)
    }

    @Test
    fun `buildAlarmSchedule all request codes unique with early reminders`() {
        val alarms = buildAlarmSchedule(profile, date, isRamadan = true, times = times,
            earlyReminderMinutes = { 10 })
        val codes = alarms.map { it.requestCode }
        assertEquals(codes.size, codes.toSet().size)
    }

    // Phase 6c: fixed time mode tests

    @Test
    fun `buildAlarmSchedule fixed mode uses fixed time minutes of day`() {
        val fixedMinsOfDay = 5 * 60 + 30 // 05:30
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            alertMode = { AlertTimeMode.FIXED },
            fixedTimeMinutes = { fixedMinsOfDay })
        val fajr = alarms.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        val expectedMs = localTimeToEpochMs(java.time.LocalTime.of(5, 30), date)
        assertEquals(expectedMs, fajr.triggerEpochMs)
    }

    @Test
    fun `buildAlarmSchedule fixed mode not set falls back to offset`() {
        val offset = 5
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            alertMode = { AlertTimeMode.FIXED },
            fixedTimeMinutes = { -1 },
            prayerOffset = { offset })
        val base = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { offset })
        assertEquals(
            base.map { it.triggerEpochMs },
            alarms.map { it.triggerEpochMs },
        )
    }

    @Test
    fun `buildAlarmSchedule offset mode ignores fixed time minutes`() {
        val offset = 10
        val base = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            prayerOffset = { offset })
        val withFixed = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            alertMode = { AlertTimeMode.OFFSET },
            fixedTimeMinutes = { 5 * 60 },
            prayerOffset = { offset })
        assertEquals(base.map { it.triggerEpochMs }, withFixed.map { it.triggerEpochMs })
    }

    // resolveNotificationProfile tests

    private fun makeProfile(id: Long, sortOrder: Int) = profile.copy(id = id, name = "P$id", sortOrder = sortOrder)

    @Test
    fun `resolveNotificationProfile returns null for empty list`() {
        assertNull(resolveNotificationProfile(-1L, emptyList()))
    }

    @Test
    fun `resolveNotificationProfile returns first by sortOrder when savedId is -1`() {
        val profiles = listOf(makeProfile(2L, 1), makeProfile(1L, 0))
        assertEquals(1L, resolveNotificationProfile(-1L, profiles)?.id)
    }

    @Test
    fun `resolveNotificationProfile returns matching profile when savedId is set`() {
        val profiles = listOf(makeProfile(1L, 0), makeProfile(2L, 1))
        assertEquals(2L, resolveNotificationProfile(2L, profiles)?.id)
    }

    @Test
    fun `resolveNotificationProfile falls back to first by sortOrder when savedId not found`() {
        val profiles = listOf(makeProfile(1L, 1), makeProfile(2L, 0))
        assertEquals(2L, resolveNotificationProfile(99L, profiles)?.id)
    }

    @Test
    fun `resolveNotificationProfile returns single profile regardless of savedId`() {
        val profiles = listOf(makeProfile(5L, 0))
        assertEquals(5L, resolveNotificationProfile(-1L, profiles)?.id)
        assertEquals(5L, resolveNotificationProfile(5L, profiles)?.id)
    }

    @Test
    fun `buildAlarmSchedule early reminder fires before fixed time when mode is fixed`() {
        val fixedMinsOfDay = 6 * 60 // 06:00
        val earlyMinutes = 10
        val alarms = buildAlarmSchedule(profile, date, isRamadan = false, times = times,
            alertMode = { AlertTimeMode.FIXED },
            fixedTimeMinutes = { fixedMinsOfDay },
            earlyReminderMinutes = { earlyMinutes })
        val fajr = alarms.first { it.prayerName == "Fajr" && !it.isEarlyReminder }
        val fajrEarly = alarms.first { it.prayerName == "Fajr" && it.isEarlyReminder }
        assertEquals(fajr.triggerEpochMs - earlyMinutes * 60_000L, fajrEarly.triggerEpochMs)
    }
}
