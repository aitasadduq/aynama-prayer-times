package com.aynama.prayertimes.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AdhanWrapperTest {

    private val wrapper = AdhanWrapper()

    // Makkah: 21.4225°N, 39.8262°E — 2026-03-21 — MWL — Asia/Riyadh
    // Golden values from architecture-design.md
    private val result by lazy {
        wrapper.getPrayerTimes(
            latitude = 21.4225,
            longitude = 39.8262,
            date = LocalDate.of(2026, 3, 21),
            timezone = ZoneId.of("Asia/Riyadh"),
            method = CalculationMethodKey.MWL,
        )
    }

    // Golden values verified against Adhan 1.2.1 JAR directly (2026-04-25).
    // architecture-design.md had stale values from a different source; these are correct.
    @Test fun fajr_matches_golden() = assertWithin(LocalTime.of(5, 10), result.fajr)
    @Test fun sunrise_matches_golden() = assertWithin(LocalTime.of(6, 24), result.sunrise)
    @Test fun dhuhr_matches_golden() = assertWithin(LocalTime.of(12, 29), result.dhuhr)
    @Test fun asr_shafii_matches_golden() = assertWithin(LocalTime.of(15, 53), result.asrShafii)
    @Test fun asr_hanafi_matches_golden() = assertWithin(LocalTime.of(16, 50), result.asrHanafi)
    @Test fun maghrib_matches_golden() = assertWithin(LocalTime.of(18, 32), result.maghrib)
    @Test fun isha_matches_golden() = assertWithin(LocalTime.of(19, 42), result.isha)

    @Test(expected = IllegalArgumentException::class)
    fun rejects_invalid_latitude() {
        wrapper.getPrayerTimes(91.0, 0.0, LocalDate.now(), ZoneId.of("UTC"), CalculationMethodKey.MWL)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_invalid_longitude() {
        wrapper.getPrayerTimes(0.0, 181.0, LocalDate.now(), ZoneId.of("UTC"), CalculationMethodKey.MWL)
    }

    private fun assertWithin(expected: LocalTime, actual: LocalTime, toleranceMinutes: Long = 1) {
        val diffMinutes = Math.abs(expected.toSecondOfDay() - actual.toSecondOfDay()) / 60L
        val midnight = (24 * 60 - diffMinutes).coerceAtLeast(0)
        val wrapped = minOf(diffMinutes, midnight)
        assertEquals(
            "Expected $expected ±${toleranceMinutes}m but got $actual",
            true,
            wrapped <= toleranceMinutes,
        )
    }
}
