package com.aynama.prayertimes.wear

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.timeline.COUNT_UP_WINDOW
import com.aynama.prayertimes.shared.timeline.PrayerCountdown
import com.aynama.prayertimes.shared.timeline.buildTimeline
import com.aynama.prayertimes.shared.timeline.countdownAt
import com.aynama.prayertimes.shared.timeline.displayName
import com.aynama.prayertimes.shared.timeline.format
import com.aynama.prayertimes.wear.complications.PrayerComplicationData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The watch's own surfaces must agree with the phone's, all day.
 *
 * They cannot be compared to the phone's directly — different module, different process — so
 * both sides are held to the same thing instead: [countdownAt], the rule they all read. The
 * phone's half of this lives in `CrossSurfaceConsistencyTest`.
 *
 * The tile is not asserted separately because it renders exactly what
 * [PrayerComplicationData.current] returns; asserting that source covers both.
 */
@RunWith(AndroidJUnit4::class)
class WearSurfaceConsistencyTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val app = context as WearApplication

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val date: LocalDate = LocalDate.of(2026, 9, 7)

    private lateinit var saved: List<Profile>
    private var savedActive = -1L
    private lateinit var profile: Profile

    @Before
    fun setUp() = runBlocking {
        saved = app.profileRepository.observeAll().first()
        savedActive = app.syncState.activeProfileId
        val id = app.profileRepository.insert(
            Profile(
                name = "Consistency London",
                latitude = 51.5074,
                longitude = -0.1278,
                calculationMethod = CalculationMethodKey.MWL,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 700,
                timezone = "Europe/London",
                useLocationTimezone = true,
            ),
        )
        profile = app.profileRepository.observeAll().first().first { it.id == id }
        app.syncState.activeProfileId = id
        // Only this profile, so the complication's active-profile resolution is unambiguous.
        app.profileRepository.mirror(listOf(profile))
    }

    @After
    fun tearDown() = runBlocking {
        app.profileRepository.mirror(saved)
        app.syncState.activeProfileId = savedActive
    }

    private fun timesOn(day: LocalDate): PrayerTimesResult = AdhanWrapper().getPrayerTimes(
        profile.latitude, profile.longitude, day, zone, profile.calculationMethod,
    )

    private fun timelineAround(around: LocalDate) = buildTimeline(
        (-1L..1L).associate { around.plusDays(it) to timesOn(around.plusDays(it)) },
        profile.asrMadhab,
        zone,
    )

    /** Every minute of the day, plus the exact instants either side of every transition. */
    private fun momentsToCheck(): List<Instant> {
        val sweep = (0 until 24 * 60).map {
            date.atStartOfDay(zone).toInstant().plus(Duration.ofMinutes(it.toLong()))
        }
        val boundaries = timelineAround(date).flatMap { entry ->
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
    fun theComplicationAgreesWithTheSharedRuleAllDay() = runBlocking {
        var checked = 0
        for (now in momentsToCheck()) {
            val today = now.atZone(zone).toLocalDate()
            val expected = countdownAt(timelineAround(today), now) ?: continue
            val state = PrayerComplicationData.current(context, now) ?: continue

            assertEquals("subject at $now", expected.entry.displayName(), state.prayerName)
            assertEquals(
                "direction at $now",
                expected is PrayerCountdown.Elapsed,
                state.isElapsed,
            )
            // The reference is what the system ticks from — get it wrong and the number on the
            // watch face drifts from the number in the app.
            assertEquals("tick anchor at $now", expected.entry.instant, state.reference)
            checked++
        }
        assertTrue("expected a full day of moments, checked $checked", checked > 1_400)
    }

    @Test
    fun theWatchScreenAgreesWithTheSharedRuleAllDay() = runBlocking {
        val vm = WearHomeViewModel(app.profileRepository, app.syncState)
        var checked = 0
        for (now in momentsToCheck()) {
            val today = now.atZone(zone).toLocalDate()
            val expected = countdownAt(timelineAround(today), now) ?: continue
            val page = vm.buildPage(profile, now) ?: continue

            assertEquals("subject at $now", expected.entry.displayName(), page.countdownPrayerName)
            assertEquals(
                "direction at $now",
                expected is PrayerCountdown.Elapsed,
                page.countdownIsElapsed,
            )
            // The screen is the one surface that shows the signed, padded form verbatim.
            assertEquals("text at $now", expected.format(), page.countdownText)
            checked++
        }
        assertTrue("expected a full day of moments, checked $checked", checked > 1_400)
    }

    @Test
    fun theWatchScreenAndTheComplicationNameTheSamePrayer() = runBlocking {
        val vm = WearHomeViewModel(app.profileRepository, app.syncState)
        for (now in momentsToCheck()) {
            val page = vm.buildPage(profile, now) ?: continue
            val state = PrayerComplicationData.current(context, now) ?: continue

            // The disagreement a user would actually notice: a watch face and the opened app
            // naming different prayers at the same glance.
            assertEquals("at $now", page.countdownPrayerName, state.prayerName)
            assertEquals("at $now", page.countdownIsElapsed, state.isElapsed)
        }
    }
}
