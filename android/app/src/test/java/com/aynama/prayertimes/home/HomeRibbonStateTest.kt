package com.aynama.prayertimes.home

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HomeRibbonStateTest {

    // A Monday: these cases are about ribbon state, not day-dependent naming.
    private val monday: LocalDate = LocalDate.of(2026, 5, 11)

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
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(3, 0), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        prayerRows.forEach { assertEquals(RibbonState.UPCOMING, it.ribbonState) }
    }

    @Test
    fun `after fajr before dhuhr — fajr current rest upcoming`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(8, 0), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
    }

    @Test
    fun `after dhuhr before asr — fajr passed dhuhr current`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(14, 0), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
    }

    @Test
    fun `after isha — fajr through maghrib passed isha current`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(22, 0), monday, false, formatter)
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
        val shafiiRows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(16, 0), monday, false, formatter)
        val hanafiRows = deriveRibbonRows(sampleTimes, AsrMadhab.HANAFI, LocalTime.of(16, 0), monday, false, formatter)

        val shafiiAsr = shafiiRows.filterIsInstance<RibbonRow.PrayerEntry>().first { it.prayer == Prayer.ASR }
        val hanafiAsr = hanafiRows.filterIsInstance<RibbonRow.PrayerEntry>().first { it.prayer == Prayer.ASR }

        assertEquals(RibbonState.CURRENT, shafiiAsr.ribbonState)
        assertEquals(RibbonState.UPCOMING, hanafiAsr.ribbonState)
    }

    @Test
    fun `ramadan adds imsak row before fajr`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(4, 0), monday, true, formatter)
        val imsakRow = rows.filterIsInstance<RibbonRow.ImsakEntry>().firstOrNull()
        assertEquals(rows[0], imsakRow)
        // Imsak = Fajr (4:30) - 10 min = 4:20
        assertEquals(false, imsakRow?.isPast)  // now=4:00, imsak=4:20, not yet past
    }

    @Test
    fun `ramadan imsak marked past when now after imsak time`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(4, 25), monday, true, formatter)
        val imsakRow = rows.filterIsInstance<RibbonRow.ImsakEntry>().first()
        assertEquals(true, imsakRow.isPast)  // now=4:25, imsak=4:20, past
    }

    @Test
    fun `sunrise row always present and not a prayer`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), monday, false, formatter)
        val sunriseRows = rows.filterIsInstance<RibbonRow.SunriseEntry>()
        assertEquals(1, sunriseRows.size)
    }

    @Test
    fun `row order is imsak fajr sunrise dhuhr asr maghrib isha in ramadan`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), monday, true, formatter)
        assertEquals(7, rows.size)
        assert(rows[0] is RibbonRow.ImsakEntry)
        assert(rows[1] is RibbonRow.PrayerEntry && (rows[1] as RibbonRow.PrayerEntry).prayer == Prayer.FAJR)
        assert(rows[2] is RibbonRow.SunriseEntry)
        assert(rows[3] is RibbonRow.PrayerEntry && (rows[3] as RibbonRow.PrayerEntry).prayer == Prayer.DHUHR)
        assert(rows[4] is RibbonRow.PrayerEntry && (rows[4] as RibbonRow.PrayerEntry).prayer == Prayer.ASR)
        assert(rows[5] is RibbonRow.PrayerEntry && (rows[5] as RibbonRow.PrayerEntry).prayer == Prayer.MAGHRIB)
        assert(rows[6] is RibbonRow.PrayerEntry && (rows[6] as RibbonRow.PrayerEntry).prayer == Prayer.ISHA)
    }

    // ---- post-midnight Isha (e.g. London in summer) ----

    private val postMidnightIshaTimes = sampleTimes.copy(
        fajr = LocalTime.of(5, 25),
        sunrise = LocalTime.of(7, 44),
        dhuhr = LocalTime.of(13, 0),
        asrShafii = LocalTime.of(16, 45),
        asrHanafi = LocalTime.of(18, 0),
        maghrib = LocalTime.of(22, 14),
        isha = LocalTime.of(0, 25),
    )

    @Test
    fun `post-midnight isha — ribbon shows isha as upcoming in the evening`() {
        val rows = deriveRibbonRows(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(22, 15), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
    }

    @Test
    fun `post-midnight isha — ribbon shows isha current after it passes`() {
        // now=01:00, isha=00:25 → Isha is current, all daytime prayers passed
        val rows = deriveRibbonRows(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(1, 0), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.FAJR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.DHUHR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.ASR }.ribbonState)
        assertEquals(RibbonState.PASSED, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
    }

    @Test
    fun `post-midnight isha — ribbon shows maghrib current before isha after midnight`() {
        // now=00:05, isha=00:25 → Isha upcoming, Maghrib current
        val rows = deriveRibbonRows(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(0, 5), monday, false, formatter)
        val prayerRows = rows.filterIsInstance<RibbonRow.PrayerEntry>()
        assertEquals(RibbonState.CURRENT, prayerRows.first { it.prayer == Prayer.MAGHRIB }.ribbonState)
        assertEquals(RibbonState.UPCOMING, prayerRows.first { it.prayer == Prayer.ISHA }.ribbonState)
    }

    @Test
    fun `post-midnight isha — phase is MAGHRIB in the evening before midnight`() {
        assertEquals(PrayerPhase.MAGHRIB, derivePhase(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(22, 15)))
    }

    @Test
    fun `post-midnight isha — phase is MAGHRIB before isha passes`() {
        assertEquals(PrayerPhase.MAGHRIB, derivePhase(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(0, 5)))
    }

    @Test
    fun `post-midnight isha — phase is ISHA after isha passes`() {
        assertEquals(PrayerPhase.ISHA, derivePhase(postMidnightIshaTimes, AsrMadhab.SHAFII, LocalTime.of(1, 0)))
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

    // ---- Friday naming ----

    @Test
    fun `ribbon names friday's dhuhr as jumuah`() {
        val friday = LocalDate.of(2026, 5, 15)
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), friday, false, formatter)
        val prayers = rows.filterIsInstance<RibbonRow.PrayerEntry>()

        assertEquals(
            listOf("Fajr", "Jumuah", "Asr", "Maghrib", "Isha"),
            prayers.map { it.displayName },
        )
        // The label changes; the stored prayer does not.
        assertEquals(Prayer.DHUHR, prayers[1].prayer)
    }

    @Test
    fun `ribbon keeps dhuhr on other days`() {
        val rows = deriveRibbonRows(sampleTimes, AsrMadhab.SHAFII, LocalTime.of(10, 0), monday, false, formatter)
        val prayers = rows.filterIsInstance<RibbonRow.PrayerEntry>()

        assertEquals("Dhuhr", prayers[1].displayName)
    }
}
