package com.aynama.prayertimes.shared.timeline

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PrayerTimelineTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val today: LocalDate = LocalDate.of(2026, 5, 12)

    // A plainly-ordered day. Isha 21:45.
    private val day = PrayerTimesResult(
        fajr = LocalTime.of(3, 20),
        sunrise = LocalTime.of(5, 12),
        dhuhr = LocalTime.of(12, 58),
        asrShafii = LocalTime.of(17, 5),
        asrHanafi = LocalTime.of(18, 20),
        maghrib = LocalTime.of(20, 40),
        isha = LocalTime.of(21, 45),
    )

    private fun timeline(
        madhab: AsrMadhab = AsrMadhab.SHAFII,
        days: Map<LocalDate, PrayerTimesResult> = mapOf(
            today.minusDays(1) to day,
            today to day,
            today.plusDays(1) to day,
        ),
    ) = buildTimeline(days, madhab, zone)

    private fun at(time: LocalTime, date: LocalDate = today): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun countdownText(time: LocalTime, date: LocalDate = today): String =
        countdownAt(timeline(), at(time, date))!!.format()

    // --- Direction and sign -----------------------------------------------------

    @Test
    fun beforePrayer_countsDownWithMinusSign() {
        // 12:45:25 → Dhuhr 12:58:00 is 12m35s away.
        assertEquals("-00:12:35", countdownText(LocalTime.of(12, 45, 25)))
    }

    @Test
    fun atPrayerInstant_signDropsAndClockReadsZero() {
        assertEquals("00:00:00", countdownText(LocalTime.of(12, 58, 0)))
    }

    @Test
    fun oneSecondAfterPrayer_countsUp() {
        assertEquals("00:00:01", countdownText(LocalTime.of(12, 58, 1)))
    }

    @Test
    fun withinFirstThirtyMinutes_keepsCountingUp() {
        assertEquals("00:15:42", countdownText(LocalTime.of(13, 13, 42)))
    }

    @Test
    fun hoursAndMinutesAreZeroPadded() {
        // Under an hour: the hours field is still present.
        assertEquals("-00:07:18", countdownText(LocalTime.of(16, 57, 42)))
        // Over an hour: Asr 17:05 + 30m = 17:35, then Maghrib 20:40 is 3h05m out.
        assertEquals("-03:05:00", countdownText(LocalTime.of(17, 35, 0)))
    }

    // --- The 30-minute boundary --------------------------------------------------

    @Test
    fun atExactlyThirtyMinutes_flipsToCountingDownTowardsNext() {
        // Dhuhr 12:58 + 30m = 13:28:00 exactly. Asr is 17:05, so 3h37m away.
        val state = countdownAt(timeline(), at(LocalTime.of(13, 28, 0)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.ASR, state!!.entry.event)
        assertEquals("-03:37:00", state.format())
    }

    @Test
    fun oneSecondBeforeThirtyMinutes_stillCountingUp() {
        val state = countdownAt(timeline(), at(LocalTime.of(13, 27, 59)))
        assertTrue(state is PrayerCountdown.Elapsed)
        assertEquals("00:29:59", state!!.format())
    }

    @Test
    fun countUpEndsEarlyWhenNextEventArrivesFirst() {
        // Fajr 03:20 → Sunrise 05:12 is far apart here, so build a compressed day where the
        // next event lands 10 minutes after Fajr. The count-up must not outlive it.
        val tight = day.copy(fajr = LocalTime.of(5, 2), sunrise = LocalTime.of(5, 12))
        val tl = buildTimeline(mapOf(today to tight), AsrMadhab.SHAFII, zone)

        val duringFajr = countdownAt(tl, at(LocalTime.of(5, 8)))
        assertTrue(duringFajr is PrayerCountdown.Elapsed)
        assertEquals(TimelineEvent.FAJR, duringFajr!!.entry.event)

        // Sunrise has now passed, still inside Fajr's 30-minute window — but Fajr is over.
        val afterSunrise = countdownAt(tl, at(LocalTime.of(5, 20)))
        assertTrue(afterSunrise is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.DHUHR, afterSunrise!!.entry.event)
    }

    // --- Sunrise is a target, never a source ------------------------------------

    @Test
    fun sunriseIsCountedDownTo() {
        val state = countdownAt(timeline(), at(LocalTime.of(5, 0)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.SUNRISE, state!!.entry.event)
        assertEquals("-00:12:00", state.format())
    }

    @Test
    fun sunriseNeverCountsUp() {
        // One minute after sunrise: nothing began, so the clock is already counting to Dhuhr.
        val state = countdownAt(timeline(), at(LocalTime.of(5, 13)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.DHUHR, state!!.entry.event)
    }

    // --- Date boundaries ---------------------------------------------------------

    @Test
    fun afterIsha_countsDownToTomorrowsFajr() {
        val state = countdownAt(timeline(), at(LocalTime.of(23, 30)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.FAJR, state!!.entry.event)
        assertEquals(today.plusDays(1), state.entry.date)
        // 23:30 → 03:20 next day = 3h50m.
        assertEquals("-03:50:00", state.format())
    }

    @Test
    fun justAfterMidnight_stillCountsDownToTodaysFajr() {
        val state = countdownAt(timeline(), at(LocalTime.of(0, 5)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.FAJR, state!!.entry.event)
        assertEquals(today, state.entry.date)
        assertEquals("-03:15:00", state.format())
    }

    // --- Late Isha (rolls onto the following calendar day) -----------------------

    private val lateIsha = day.copy(maghrib = LocalTime.of(22, 50), isha = LocalTime.of(0, 25))

    private fun lateIshaTimeline() = buildTimeline(
        mapOf(today.minusDays(1) to lateIsha, today to lateIsha, today.plusDays(1) to lateIsha),
        AsrMadhab.SHAFII,
        zone,
    )

    @Test
    fun lateIsha_isDatedToTheDayItActuallyOccursOn() {
        val ishaOfToday = buildTimeline(mapOf(today to lateIsha), AsrMadhab.SHAFII, zone)
            .single { it.event == TimelineEvent.ISHA }
        assertEquals(today.plusDays(1), ishaOfToday.date)
        assertEquals(at(LocalTime.of(0, 25), today.plusDays(1)), ishaOfToday.instant)
    }

    @Test
    fun lateIsha_smallHoursCountUpFromIshaNotBackToFajr() {
        val state = countdownAt(lateIshaTimeline(), at(LocalTime.of(0, 35)))
        assertTrue(state is PrayerCountdown.Elapsed)
        assertEquals(TimelineEvent.ISHA, state!!.entry.event)
        assertEquals("00:10:00", state.format())
    }

    @Test
    fun lateIsha_theEveningCountsDownToIshaNotToTomorrowsFajr() {
        // 23:30 on `today`: past Maghrib's 22:50 + 30m window, Isha lands at 00:25 tomorrow.
        val state = countdownAt(lateIshaTimeline(), at(LocalTime.of(23, 30)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.ISHA, state!!.entry.event)
        assertEquals(today.plusDays(1), state.entry.date)
        assertEquals("-00:55:00", state.format())
    }

    @Test
    fun lateIsha_afterTheCountUpWindowTheNextTargetIsFajr() {
        // Isha 00:25 + 30m = 00:55, then Fajr 03:20 the same morning.
        val state = countdownAt(lateIshaTimeline(), at(LocalTime.of(1, 0)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.FAJR, state!!.entry.event)
        assertEquals("-02:20:00", state.format())
    }

    @Test
    fun beforeFajr_theCurrentEventIsYesterdaysIsha() {
        // Requires yesterday in the timeline; without it there is no event before now at 01:00.
        val state = countdownAt(timeline(), at(LocalTime.of(1, 0)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(today, state!!.entry.date)
        assertEquals(TimelineEvent.FAJR, state.entry.event)
    }

    @Test
    fun dstSpringForward_countdownMatchesElapsedRealTimeNotWallClock() {
        // Europe/London jumps 01:00 → 02:00 on 2026-03-29. Fajr at 05:00 is 3 real hours after
        // 01:00 UTC-wall 00:00... the point is only that the gap is measured on instants.
        val dstDay = LocalDate.of(2026, 3, 29)
        val tl = buildTimeline(mapOf(dstDay to day), AsrMadhab.SHAFII, zone)
        val before = dstDay.atTime(0, 30).atZone(zone).toInstant()
        val state = countdownAt(tl, before)!!
        val expected = Duration.between(before, tl.first { it.event == TimelineEvent.FAJR }.instant)
        assertEquals(expected, state.duration)
    }

    // --- Madhab ------------------------------------------------------------------

    @Test
    fun hanafiAsrShiftsTheTimeline() {
        val state = countdownAt(timeline(AsrMadhab.HANAFI), at(LocalTime.of(17, 30)))
        assertTrue(state is PrayerCountdown.Remaining)
        assertEquals(TimelineEvent.ASR, state!!.entry.event)
        assertEquals(LocalTime.of(18, 20), state.entry.time)
    }

    // --- Transitions -------------------------------------------------------------

    @Test
    fun nextTransition_whileCountingDown_isTheNextEvent() {
        assertEquals(
            at(LocalTime.of(12, 58)),
            nextTransition(timeline(), at(LocalTime.of(12, 45))),
        )
    }

    @Test
    fun nextTransition_whileCountingUp_isThirtyMinutesAfterThePrayer() {
        assertEquals(
            at(LocalTime.of(13, 28)),
            nextTransition(timeline(), at(LocalTime.of(13, 0))),
        )
    }

    @Test
    fun nextTransition_whileCountingUp_isTheNextEventWhenItComesSooner() {
        val tight = day.copy(fajr = LocalTime.of(5, 2), sunrise = LocalTime.of(5, 12))
        val tl = buildTimeline(mapOf(today to tight), AsrMadhab.SHAFII, zone)
        assertEquals(at(LocalTime.of(5, 12)), nextTransition(tl, at(LocalTime.of(5, 5))))
    }

    // --- Empty / short timelines -------------------------------------------------

    @Test
    fun emptyTimelineHasNoCountdown() {
        assertNull(countdownAt(emptyList(), at(LocalTime.of(12, 0))))
        assertNull(nextTransition(emptyList(), at(LocalTime.of(12, 0))))
    }

    @Test
    fun pastTheEndOfTheTimelineHasNoCountdown() {
        val tl = buildTimeline(mapOf(today to day), AsrMadhab.SHAFII, zone)
        assertNull(countdownAt(tl, at(LocalTime.of(23, 59))))
    }

    // --- Degenerate high-latitude days ------------------------------------------

    @Test
    fun collapsedFajrAndIsha_resolveToFajr() {
        // Above ~48° adhan's high-latitude fallback can put Fajr and Isha on the same instant.
        // "The most recent event" is then ambiguous; the day's canonical order breaks the tie.
        val collapsed = day.copy(fajr = LocalTime.of(2, 45), isha = LocalTime.of(2, 45))
        val tl = buildTimeline(
            mapOf(today.minusDays(1) to collapsed, today to collapsed),
            AsrMadhab.SHAFII,
            zone,
        )
        assertEquals(TimelineEvent.FAJR, currentEntry(tl, at(LocalTime.of(4, 38)))!!.event)
        val state = countdownAt(tl, at(LocalTime.of(2, 45)))
        assertTrue(state is PrayerCountdown.Elapsed)
        assertEquals(TimelineEvent.FAJR, state!!.entry.event)
    }

    // --- Ordering ----------------------------------------------------------------

    @Test
    fun everyEventHasADisplayName() {
        assertEquals(
            listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"),
            buildTimeline(mapOf(today to day), AsrMadhab.SHAFII, zone).map { it.displayName() },
        )
    }

    @Test
    fun timelineIsSortedByInstantAcrossDays() {
        val tl = timeline()
        assertEquals(18, tl.size)
        assertTrue(tl.zipWithNext().all { (a, b) -> !a.instant.isAfter(b.instant) })
    }
}
