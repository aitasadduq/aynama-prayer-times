package com.aynama.prayertimes.shared.timeline

import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Prayer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PrayerNamingTest {

    private val friday = LocalDate.of(2026, 5, 15)
    private val thursday = friday.minusDays(1)
    private val saturday = friday.plusDays(1)

    @Test
    fun theFixtureDatesAreTheDaysTheyClaimToBe() {
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
    }

    @Test
    fun fridayDhuhrIsJumuah() {
        assertEquals(JUMUAH, prayerDisplayName(TimelineEvent.DHUHR, friday))
        assertEquals(JUMUAH, prayerDisplayName(Prayer.DHUHR, friday))
    }

    @Test
    fun everyOtherDayKeepsDhuhr() {
        assertEquals("Dhuhr", prayerDisplayName(TimelineEvent.DHUHR, thursday))
        assertEquals("Dhuhr", prayerDisplayName(TimelineEvent.DHUHR, saturday))
        assertEquals("Dhuhr", prayerDisplayName(Prayer.DHUHR, thursday))
        assertEquals("Dhuhr", prayerDisplayName(Prayer.DHUHR, saturday))
    }

    @Test
    fun noOtherPrayerChangesOnFriday() {
        val unchanged = listOf(
            TimelineEvent.FAJR to "Fajr",
            TimelineEvent.SUNRISE to "Sunrise",
            TimelineEvent.ASR to "Asr",
            TimelineEvent.MAGHRIB to "Maghrib",
            TimelineEvent.ISHA to "Isha",
        )
        for ((event, name) in unchanged) {
            assertEquals(name, prayerDisplayName(event, friday))
        }
        for (prayer in Prayer.entries.filter { it != Prayer.DHUHR }) {
            assertEquals(prayer.canonicalName(), prayerDisplayName(prayer, friday))
        }
    }

    @Test
    fun canonicalNamesStayDayIndependent() {
        // Recurring settings rows use these: a row that governs all seven days must not be
        // renamed because today happens to be Friday.
        assertEquals("Dhuhr", TimelineEvent.DHUHR.canonicalName())
        assertEquals("Dhuhr", Prayer.DHUHR.canonicalName())
    }

    // --- Through the timeline ----------------------------------------------------

    private val zone: ZoneId = ZoneId.of("Europe/London")

    private val day = PrayerTimesResult(
        fajr = LocalTime.of(3, 20),
        sunrise = LocalTime.of(5, 12),
        dhuhr = LocalTime.of(12, 58),
        asrShafii = LocalTime.of(17, 5),
        asrHanafi = LocalTime.of(18, 20),
        maghrib = LocalTime.of(20, 40),
        isha = LocalTime.of(21, 45),
    )

    @Test
    fun timelineEntriesNameThemselvesByTheirOwnDay() {
        val timeline = buildTimeline(
            mapOf(thursday to day, friday to day, saturday to day),
            AsrMadhab.SHAFII,
            zone,
        )
        val dhuhrNames = timeline.filter { it.event == TimelineEvent.DHUHR }
            .associate { it.date to it.displayName() }

        assertEquals(mapOf(thursday to "Dhuhr", friday to JUMUAH, saturday to "Dhuhr"), dhuhrNames)
    }

    @Test
    fun theCountdownNamesFridaysDhuhrAsJumuah() {
        val timeline = buildTimeline(mapOf(friday to day), AsrMadhab.SHAFII, zone)
        val beforeDhuhr = friday.atTime(12, 0).atZone(zone).toInstant()

        assertEquals(JUMUAH, countdownAt(timeline, beforeDhuhr)!!.entry.displayName())
    }

    @Test
    fun aLateIshaIsNamedForTheDayItLandsOn() {
        // Thursday's Isha at 00:25 occurs on Friday. It is Isha either way — only Dhuhr moves —
        // but this pins that names follow the occurrence date, not the calculation date.
        val lateIsha = day.copy(maghrib = LocalTime.of(22, 50), isha = LocalTime.of(0, 25))
        val entry = buildTimeline(mapOf(thursday to lateIsha), AsrMadhab.SHAFII, zone)
            .single { it.event == TimelineEvent.ISHA }

        assertEquals(friday, entry.date)
        assertEquals("Isha", entry.displayName())
    }
}
