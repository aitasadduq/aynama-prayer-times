package com.aynama.prayertimes.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aynama.prayertimes.notifications.RamadanDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RamadanDetectionTest {

    @Test
    fun midRamadan1445_returnsTrue() {
        // Ramadan 1445 ran approximately March 11 – April 9, 2024
        assertTrue(RamadanDetector.isRamadan(LocalDate.of(2024, 3, 20)))
    }

    @Test
    fun firstDayRamadan1445_returnsTrue() {
        assertTrue(RamadanDetector.isRamadan(LocalDate.of(2024, 3, 11)))
    }

    @Test
    fun lastDayRamadan1445_returnsTrue() {
        assertTrue(RamadanDetector.isRamadan(LocalDate.of(2024, 4, 9)))
    }

    @Test
    fun dayAfterRamadan1445_returnsFalse() {
        assertFalse(RamadanDetector.isRamadan(LocalDate.of(2024, 4, 10)))
    }

    @Test
    fun shawwal1445_returnsFalse() {
        assertFalse(RamadanDetector.isRamadan(LocalDate.of(2024, 4, 15)))
    }

    @Test
    fun nonRamadanDate_returnsFalse() {
        assertFalse(RamadanDetector.isRamadan(LocalDate.of(2024, 1, 1)))
    }

    @Test
    fun ramadan1446_returnsTrue() {
        // Ramadan 1446 started approximately March 1, 2025
        assertTrue(RamadanDetector.isRamadan(LocalDate.of(2025, 3, 10)))
    }

    // isRamadanWithOffset — Ramadan 1445: Mar 11 – Apr 9, 2024

    @Test
    fun offset0_firstDay_returnsTrue() {
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 11), 0))
    }

    @Test
    fun offset0_dayBefore_returnsFalse() {
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 10), 0))
    }

    @Test
    fun offset1_firstCalcDay_returnsFalse() {
        // With offset=1, Ramadan effectively starts Mar 12; Mar 11 is pre-Ramadan
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 11), 1))
    }

    @Test
    fun offset1_secondCalcDay_returnsTrue() {
        // Mar 12 with offset=1: checks Mar 11 (first calc day) → Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 12), 1))
    }

    @Test
    fun offset1_dayAfterCalcEnd_returnsTrue() {
        // Apr 10 with offset=1: checks Apr 9 (last calc day) → still Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 4, 10), 1))
    }

    @Test
    fun offset1_twoDaysAfterCalcEnd_returnsFalse() {
        // Apr 11 with offset=1: checks Apr 10 (Shawwal) → not Ramadan
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 4, 11), 1))
    }

    @Test
    fun offset2_secondCalcDay_returnsFalse() {
        // Mar 12 with offset=2: checks Mar 10 (Shaban) → not Ramadan
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 12), 2))
    }

    @Test
    fun offset2_thirdCalcDay_returnsTrue() {
        // Mar 13 with offset=2: checks Mar 11 (first calc day) → Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 13), 2))
    }

    // hijriDateOf — civil Islamic calendar maps Mar 11, 2024 → Ramaḍān 1445

    @Test
    fun hijriDateOf_firstDayRamadan1445_containsRamadanAndYear() {
        val result = RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 11))
        assertTrue("expected Ramaḍān in '$result'", result.contains("Ramaḍān"))
        assertTrue("expected 1445 in '$result'", result.contains("1445"))
    }

    @Test
    fun hijriDateOf_dayAfterRamadan1445_isShawwal() {
        val result = RamadanDetector.hijriDateOf(LocalDate.of(2024, 4, 10))
        assertTrue("expected Shawwāl in '$result'", result.contains("Shawwāl"))
    }

    @Test
    fun hijriDateOf_zoneIndependentForFarApartZones_returnsSameDateLabel() {
        // Mar 20, 2024 noon-local is mid-Ramadan in any zone — both should resolve to Ramaḍān 1445.
        val utc = RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 20), ZoneId.of("UTC"))
        val tokyo = RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 20), ZoneId.of("Asia/Tokyo"))
        val la = RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 20), ZoneId.of("America/Los_Angeles"))
        assertEquals(utc, tokyo)
        assertEquals(utc, la)
        assertTrue(utc.contains("Ramaḍān"))
    }

    @Test
    fun hijriDateOf_format_isDayMonthYear() {
        val result = RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 11))
        // Expect "<digits> <month> <year>"
        assertTrue("expected '<day> <month> <year>' format, got '$result'", result.matches(Regex("\\d{1,2} .+ \\d{4}")))
    }
}
