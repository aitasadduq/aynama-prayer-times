package com.aynama.prayertimes.home

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HomeRibbonStateTest {

    private val formatter = DateTimeFormatter.ofPattern("h:mm a")

    private val sampleTimes = PrayerTimesResult(
        fajr = LocalTime.of(4, 30),
        sunrise = LocalTime.of(6, 10),
        dhuhr = LocalTime.of(12, 15),
        asrShafii = LocalTime.of(15, 45),
        asrHanafi = LocalTime.of(16, 30),
        maghrib = LocalTime.of(19, 50),
        isha = LocalTime.of(21, 20),
    )

    @Test
    fun `before fajr — all prayers upcoming`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(3, 0), false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        prayerRows.forEach { assertEquals(RibbonState.UPCOMING, it.ribbonState) }
    }

    @Test
    fun `after fajr before dhuhr — fajr current rest upcoming`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(8, 0), false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
    }

    @Test
    fun `after dhuhr before asr — fajr passed dhuhr current`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(14, 0), false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
    }

    @Test
    fun `after isha — fajr through maghrib passed isha current`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(22, 0), false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
    }

    @Test
    fun `hanafi madhab uses asrHanafi time`() {
        // Hanafi Asr is at 16:30, Shafii at 15:45
        // At 16:00, Shafii Asr is current, Hanafi Asr is upcoming
        val shafiiRows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(16, 0), false, formatter)
        val hanafiRows = deriveRibbonRows(sampleTimes, AsrMadhab.HANAFI, LocalTime.of(16, 0), false, formatter)

        val shafiiAsr = shafiiRows.filterIsInstance<RibbonRow.PrayerEntry>().first { it.prayer == Prayer.ASR }
        val hanafiAsr = hanafiRows.filterIsInstance<RibbonRow.PrayerEntry>().first { it.prayer == Prayer.ASR }

        assertEquals(RibbonState.CURRENT, shafiiAsr.ribbonState)
        assertEquals(RibbonState.UPCOMING, hanafiAsr.ribbonState)
    }

    @Test
    fun `ramadan adds imsak row before fajr`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(4, 0), true, formatter)
        val imsakRow = rows.filterIsInstance<RibbonRow.ImsakEntry>().firstOrNull()
        assertEquals(rows[0], imsakRow)
        // Imsak = Fajr (4:30) - 10 min = 4:20
        assertEquals(false, imsakRow?.isPast)  // now=4:00, imsak=4:20, not yet past
    }

    @Test
    fun `ramadan imsak marked past when now after imsak time`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(4, 25), true, formatter)
        val imsakRow = rows.filterIsInstance<RibbonRow.ImsakEntry>().first()
        assertEquals(true, imsakRow.isPast)  // now=4:25, imsak=4:20, past
    }

    @Test
    fun `sunrise row always present and not a prayer`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), false, formatter)
        val sunriseRows = rows.filterIsInstance<RibbonRow.SunriseEntry>()
        assertEquals(1, sunriseRows.size)
    }

    @Test
    fun `row order is imsak fajr sunrise dhuhr asr maghrib isha in ramadan`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), true, formatter)
        assertEquals(7, rows.size)
        assert(rows[0] is RibbonRow.ImsakEntry)
        assert(rows[1] is RibbonRow.PrayerEntry && (rows[1] as RibbonRow.PrayerEntry).prayer == Prayer.FAJR)
        assert(rows[2] is RibbonRow.SunriseEntry)
        assert(rows[3] is RibbonRow.PrayerEntry && (rows[3] as RibbonRow.PrayerEntry).prayer == Prayer.DHUHR)
        assert(rows[4] is RibbonRow.PrayerEntry && (rows[4] as RibbonRow.PrayerEntry).prayer == Prayer.ASR)
        assert(rows[5] is RibbonRow.PrayerEntry && (rows[5] as RibbonRow.PrayerEntry).prayer == Prayer.MAGHRIB)
        assert(rows[6] is RibbonRow.PrayerEntry && (rows[6] as RibbonRow.PrayerEntry).prayer == Prayer.ISHA)
    }

    @Test
    fun `countdown shows time until next prayer`() {
        // now = 10:00, next = dhuhr = 12:15 → 2h 15m = 8100s
        val result = deriveCountdown(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0))
        assertEquals("02:15:00", result)
    }

    @Test
    fun `countdown wraps to tomorrow fajr after isha`() {
        // now = 23:00, all prayers passed → next = tomorrow fajr (approximate via midnight wrap)
        val result = deriveCountdown(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(23, 0))
        // 1h to midnight + 4h30m to fajr = 5h30m = "05:30:00"
        assertEquals("05:30:00", result)
    }

    @Test
    fun `next prayer name before fajr returns fajr`() {
        assertEquals("Fajr", deriveNextPrayerName(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(3, 0)))
    }

    @Test
    fun `next prayer name after all prayers returns fajr`() {
        assertEquals("Fajr", deriveNextPrayerName(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(23, 0)))
    }

    @Test
    fun `phase derivation maps prayer windows correctly`() {
        assertEquals(PrayerPhase.ISHA, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(3, 0)))
        assertEquals(PrayerPhase.FAJR, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(5, 0)))
        assertEquals(PrayerPhase.SUNRISE_TRANSITION, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(9, 0)))
        assertEquals(PrayerPhase.DHUHR, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(13, 0)))
        assertEquals(PrayerPhase.ASR, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(17, 0)))
        assertEquals(PrayerPhase.MAGHRIB, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(20, 30)))
        assertEquals(PrayerPhase.ISHA, derivePhase(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(22, 0)))
    }
}
