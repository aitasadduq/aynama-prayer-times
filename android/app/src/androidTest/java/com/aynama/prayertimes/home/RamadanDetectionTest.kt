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

    // Positive offset pulls Ramadan earlier (moon sighted the night before calc).

    @Test
    fun offset1_dayBeforeCalcStart_returnsTrue() {
        // Mar 10 with offset=+1: checks Mar 11 (first calc day) → Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 10), 1))
    }

    @Test
    fun offset1_twoDaysBeforeCalcStart_returnsFalse() {
        // Mar 9 with offset=+1: checks Mar 10 (Shaban) → not Ramadan
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 9), 1))
    }

    @Test
    fun offset1_lastCalcDay_returnsFalse() {
        // Apr 9 with offset=+1: checks Apr 10 (Shawwal) → window also ends a day earlier
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 4, 9), 1))
    }

    @Test
    fun offset2_twoDaysBeforeCalcStart_returnsTrue() {
        // Mar 9 with offset=+2: checks Mar 11 (first calc day) → Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 9), 2))
    }

    // Negative offset delays Ramadan (moon sighted the night after calc).

    @Test
    fun offsetMinus1_firstCalcDay_returnsFalse() {
        // Mar 11 with offset=-1: checks Mar 10 (Shaban) → not yet Ramadan
        assertFalse(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 11), -1))
    }

    @Test
    fun offsetMinus1_secondCalcDay_returnsTrue() {
        // Mar 12 with offset=-1: checks Mar 11 (first calc day) → Ramadan
        assertTrue(RamadanDetector.isRamadanWithOffset(LocalDate.of(2024, 3, 12), -1))
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

    // hijriDateWithOffset — offset shifts the displayed Hijri date across all months.

    @Test
    fun hijriDateWithOffset_positive_matchesLaterCivilDate() {
        // Mar 10 with offset=+1 resolves to Mar 11 → 1 Ramaḍān 1445
        val shifted = RamadanDetector.hijriDateWithOffset(LocalDate.of(2024, 3, 10), 1)
        assertEquals(RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 11)), shifted)
        assertTrue("expected Ramaḍān in '$shifted'", shifted.contains("Ramaḍān"))
    }

    @Test
    fun hijriDateWithOffset_negative_matchesEarlierCivilDate() {
        // Mar 11 with offset=-1 resolves to Mar 10 → late Shaʻbān 1445 (pre-Ramadan)
        val shifted = RamadanDetector.hijriDateWithOffset(LocalDate.of(2024, 3, 11), -1)
        assertEquals(RamadanDetector.hijriDateOf(LocalDate.of(2024, 3, 10)), shifted)
        assertFalse("expected pre-Ramadan date in '$shifted'", shifted.contains("Ramaḍān"))
    }

    // effectiveHijriOffset — offset auto-expires once the perceived Hijri month changes.
    // Ramadan 1445 (calc): Mar 11 – Apr 9, 2024; Shawwāl 1445 begins Apr 10.

    private val utc = ZoneId.of("UTC")
    private val ramadan1445Key get() = RamadanDetector.hijriMonthKey(LocalDate.of(2024, 3, 20), utc)

    @Test
    fun hijriMonthKey_consecutiveMonths_differByOne() {
        val ramadan = RamadanDetector.hijriMonthKey(LocalDate.of(2024, 3, 20), utc)
        val shawwal = RamadanDetector.hijriMonthKey(LocalDate.of(2024, 4, 20), utc)
        assertEquals(1, shawwal - ramadan)
    }

    @Test
    fun effectiveOffset_midMonth_appliesOffset() {
        // Mar 19 with offset=+1 → adjusted Mar 20 is Ramadan → offset stands
        assertEquals(1, RamadanDetector.effectiveHijriOffset(1, ramadan1445Key, LocalDate.of(2024, 3, 19), utc))
    }

    @Test
    fun effectiveOffset_firstPerceivedDay_appliesOffset() {
        // Mar 10 (calc 30 Shaʻbān) with offset=+1 → adjusted Mar 11 is Ramadan → Imsak active on day 1
        assertEquals(1, RamadanDetector.effectiveHijriOffset(1, ramadan1445Key, LocalDate.of(2024, 3, 10), utc))
    }

    @Test
    fun effectiveOffset_beforePerceivedStart_returnsZero() {
        // Mar 9 with offset=+1 → adjusted Mar 10 still Shaʻbān → not yet active
        assertEquals(0, RamadanDetector.effectiveHijriOffset(1, ramadan1445Key, LocalDate.of(2024, 3, 9), utc))
    }

    @Test
    fun effectiveOffset_newPerceivedMonth_resetsToZero() {
        // Apr 9 with offset=+1 is the user's perceived 1 Shawwāl (Eid); adjusted Apr 10 is Shawwāl,
        // no longer the Ramadan it was set for, so the offset auto-resets to 0.
        assertEquals(0, RamadanDetector.effectiveHijriOffset(1, ramadan1445Key, LocalDate.of(2024, 4, 9), utc))
    }

    @Test
    fun effectiveOffset_negative_appliesThroughPerceivedMonthThenResets() {
        // -1 set in Ramadan keeps the same month key. Apr 10 (calc 1 Shawwāl) is the user's
        // perceived 30 Ramadan → still active; Apr 11 is their 1 Shawwāl → resets.
        assertEquals(-1, RamadanDetector.effectiveHijriOffset(-1, ramadan1445Key, LocalDate.of(2024, 4, 10), utc))
        assertEquals(0, RamadanDetector.effectiveHijriOffset(-1, ramadan1445Key, LocalDate.of(2024, 4, 11), utc))
    }

    @Test
    fun effectiveOffset_zeroOffsetOrNoAnchor_returnsZero() {
        assertEquals(0, RamadanDetector.effectiveHijriOffset(0, ramadan1445Key, LocalDate.of(2024, 3, 19), utc))
        // monthKey 0 = none (e.g. legacy/pre-anchor value) → treated as expired
        assertEquals(0, RamadanDetector.effectiveHijriOffset(1, 0, LocalDate.of(2024, 3, 19), utc))
    }
}
