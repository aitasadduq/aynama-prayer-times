package com.aynama.prayertimes

import com.aynama.prayertimes.notifications.LivePrayerNotification
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.timeline.COUNT_UP_WINDOW
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.TimelineEvent
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.widgets.buildPrayerWidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every phone surface must agree about the prayer state, at every moment of the day.
 *
 * Phase 4B of IMPLEMENTATION_PLAN.md asks that the phone, its widgets and its notifications
 * report one consistent prayer state. They are built by three separate code paths, so agreeing
 * on a handful of hand-picked times proves very little — this walks a whole day and asserts
 * against [countdownAt], the shared rule they are all supposed to be reading.
 *
 * Agreement with the rule is what is asserted, rather than agreement with each other. If two
 * surfaces drifted the same way the second kind of test would still pass; this one would not.
 *
 * The watch's surfaces are checked the same way, in its own module — they cannot be imported
 * here, and the point is the shared rule either way.
 */
class CrossSurfaceConsistencyTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val date: LocalDate = LocalDate.of(2026, 9, 7)
    private val profile = Profile(
        id = 1,
        name = "London",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = "Europe/London",
        useLocationTimezone = true,
    )

    private val adhan = AdhanWrapper()
    private fun timesOn(day: LocalDate): PrayerTimesResult = adhan.getPrayerTimes(
        profile.latitude, profile.longitude, day, zone, profile.calculationMethod,
    )

    /**
     * Yesterday/today/tomorrow around [around], which is what every surface builds on the
     * device — `loadPrayerWidgetState` and `LivePrayerNotification` both derive their window
     * from the current moment. Handing the widget a window fixed to one day while the
     * notification derives its own would manufacture a disagreement that cannot happen in
     * production.
     */
    private fun daysAround(around: LocalDate): Map<LocalDate, PrayerTimesResult> =
        (-1L..1L).associate { around.plusDays(it) to timesOn(around.plusDays(it)) }

    private fun timelineAround(around: LocalDate) =
        buildTimeline(daysAround(around), profile.asrMadhab, zone)

    private val days: Map<LocalDate, PrayerTimesResult> = daysAround(date)
    private val timeline = buildTimeline(days, profile.asrMadhab, zone)

    private val widgetFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /**
     * Every minute of the day, plus the exact boundaries either side of each event.
     *
     * The minute sweep catches drift; the boundary instants catch the off-by-one-second
     * disagreements that a sweep on a round number would step straight over.
     */
    private fun momentsToCheck(): List<Instant> {
        val sweep = (0 until 24 * 60).map {
            date.atStartOfDay(zone).toInstant().plus(Duration.ofMinutes(it.toLong()))
        }
        val boundaries = timeline.flatMap { entry ->
            listOf(
                entry.instant.minusSeconds(1),
                entry.instant,
                entry.instant.plusSeconds(1),
                entry.instant.plus(COUNT_UP_WINDOW).minusSeconds(1),
                entry.instant.plus(COUNT_UP_WINDOW),
                entry.instant.plus(COUNT_UP_WINDOW).plusSeconds(1),
            )
        }
        return (sweep + boundaries).sorted()
    }

    @Test
    fun theWidgetAgreesWithTheSharedRuleAllDay() {
        var checked = 0
        for (now in momentsToCheck()) {
            val today = now.atZone(zone).toLocalDate()
            val expected = countdownAt(timelineAround(today), now) ?: continue
            val state = buildPrayerWidgetState(
                profile = profile,
                days = daysAround(today),
                todayTimes = daysAround(today).getValue(today),
                now = now.atZone(zone),
                elapsedRealtime = 0L,
                timeFormatter = widgetFormatter,
            )

            assertEquals("subject at $now", expected.entry.displayName(), state.countdownPrayerName)
            assertEquals(
                "direction at $now",
                expected is PrayerCountdown.Elapsed,
                state.countdownIsElapsed,
            )
            // The Chronometer base is where the widget's number comes from: in the past by the
            // elapsed amount, or in the future by the remaining one.
            val offset = state.countdownBaseElapsedRealtime
            val magnitude = expected.duration.toMillis()
            assertEquals(
                "tick anchor at $now",
                if (expected is PrayerCountdown.Elapsed) -magnitude else magnitude,
                offset,
            )
            checked++
        }
        assertTrue("expected a full day of moments, checked $checked", checked > 1_400)
    }

    @Test
    fun theLiveNotificationAgreesWithTheSharedRuleAllDay() {
        var checked = 0
        for (now in momentsToCheck()) {
            val today = now.atZone(zone).toLocalDate()
            val expected = countdownAt(timelineAround(today), now) ?: continue
            val content = LivePrayerNotification.buildContent(profile, now) ?: continue

            assertEquals("subject at $now", expected.entry.displayName(), content.title)
            assertEquals(
                "direction at $now",
                expected is PrayerCountdown.Remaining,
                content.countingDown,
            )
            // The notification hands its tick to the system by naming the prayer's instant.
            assertEquals("tick anchor at $now", expected.entry.instant, content.chronometerBase)
            checked++
        }
        assertTrue("expected a full day of moments, checked $checked", checked > 1_400)
    }

    @Test
    fun theWidgetAndTheNotificationNameTheSamePrayerAllDay() {
        // Belt and braces on top of the two above: they are separate code paths, and this is
        // the disagreement a user would actually notice — a widget and a shade saying
        // different things at the same glance.
        for (now in momentsToCheck()) {
            val today = now.atZone(zone).toLocalDate()
            val content = LivePrayerNotification.buildContent(profile, now) ?: continue
            val state = buildPrayerWidgetState(
                profile = profile,
                days = daysAround(today),
                todayTimes = daysAround(today).getValue(today),
                now = now.atZone(zone),
                elapsedRealtime = 0L,
                timeFormatter = widgetFormatter,
            )

            assertEquals("at $now", content.title, state.countdownPrayerName)
            assertEquals("at $now", content.countingDown, !state.countdownIsElapsed)
        }
    }

    @Test
    fun theyAgreeOnFridayToo() {
        // The one day the name depends on the date. A surface that resolved the name from its
        // own clock rather than the prayer's own day would drift only here.
        val friday = LocalDate.of(2026, 5, 15)
        val fridayDays = (-1L..1L).associate { friday.plusDays(it) to timesOn(friday.plusDays(it)) }
        val fridayTimeline = buildTimeline(fridayDays, profile.asrMadhab, zone)
        // Match on the event and its own date, not on the clock time: Dhuhr shifts by only a
        // few seconds a day and truncates to the same LocalTime, so matching on time alone
        // would silently pick Thursday's entry and prove nothing.
        val dhuhr = fridayTimeline.first {
            it.event == TimelineEvent.DHUHR && it.date == friday
        }

        for (offset in listOf(-60L, 0L, 60L, 25 * 60L)) {
            val now = dhuhr.instant.plusSeconds(offset)
            val content = LivePrayerNotification.buildContent(profile, now)!!
            val state = buildPrayerWidgetState(
                profile = profile,
                days = fridayDays,
                todayTimes = fridayDays.getValue(friday),
                now = now.atZone(zone),
                elapsedRealtime = 0L,
                timeFormatter = widgetFormatter,
            )

            assertEquals("Jumuah", content.title)
            assertEquals("Jumuah", state.countdownPrayerName)
            assertEquals("JUM", state.countdownPrayerAbbreviation)
        }
    }
}
