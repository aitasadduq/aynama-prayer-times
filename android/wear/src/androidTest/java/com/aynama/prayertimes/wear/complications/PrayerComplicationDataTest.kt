package com.aynama.prayertimes.wear.complications

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.timeline.COUNT_UP_WINDOW
import com.aynama.prayertimes.wear.WearApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the complication says, at each point of the shared countdown rule.
 *
 * The rendering is the watch face's; what has to be right here is the subject, the direction
 * and the instant the system ticks from — the same three things the widget and the live
 * notification are tested on.
 */
@RunWith(AndroidJUnit4::class)
class PrayerComplicationDataTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val app = context as WearApplication

    private val zone = ZoneId.of("Asia/Riyadh")
    private val date = LocalDate.of(2026, 8, 5)
    private val friday = LocalDate.of(2026, 5, 15)

    private lateinit var saved: List<Profile>
    private var savedActive = -1L
    private lateinit var profile: Profile

    @Before
    fun setUp() = runBlocking {
        saved = app.profileRepository.observeAll().first()
        savedActive = app.syncState.activeProfileId

        val id = app.profileRepository.insert(
            Profile(
                name = "Complication test Riyadh",
                latitude = 24.7136,
                longitude = 46.6753,
                calculationMethod = CalculationMethodKey.UMM_AL_QURA,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 800,
                timezone = "Asia/Riyadh",
                useLocationTimezone = true,
            ),
        )
        profile = app.profileRepository.observeAll().first().first { it.id == id }
        app.syncState.activeProfileId = id
    }

    @After
    fun tearDown() = runBlocking {
        app.profileRepository.delete(profile)
        app.syncState.activeProfileId = savedActive
        app.profileRepository.mirror(saved)
    }

    private fun timesOn(day: LocalDate) = AdhanWrapper().getPrayerTimes(
        profile.latitude, profile.longitude, day, zone, profile.calculationMethod,
    )

    private fun instantOf(day: LocalDate, time: java.time.LocalTime) =
        day.atTime(time).atZone(zone).toInstant()

    @Test
    fun beforeAPrayer_countsDownTowardsIt() = runBlocking {
        val dhuhr = instantOf(date, timesOn(date).dhuhr)
        val state = PrayerComplicationData.current(context, dhuhr.minusSeconds(600))!!

        assertEquals("Dhuhr", state.prayerName)
        assertEquals("D", state.initial)
        assertFalse(state.isElapsed)
        // The reference is the prayer's own instant — that is what makes the system's tick
        // agree with the number on the phone.
        assertEquals(dhuhr, state.reference)
    }

    @Test
    fun insideTheWindow_countsUpFromThePrayer() = runBlocking {
        val dhuhr = instantOf(date, timesOn(date).dhuhr)
        val state = PrayerComplicationData.current(context, dhuhr.plusSeconds(600))!!

        assertEquals("Dhuhr", state.prayerName)
        assertTrue(state.isElapsed)
        assertEquals(dhuhr, state.reference)
    }

    @Test
    fun pastTheWindow_movesOnToTheNextPrayer() = runBlocking {
        val dhuhr = instantOf(date, timesOn(date).dhuhr)
        val state = PrayerComplicationData.current(context, dhuhr.plus(COUNT_UP_WINDOW))!!

        assertEquals("Asr", state.prayerName)
        assertFalse(state.isElapsed)
    }

    @Test
    fun fridaysDhuhrIsJumuah() = runBlocking {
        val dhuhr = instantOf(friday, timesOn(friday).dhuhr)
        val state = PrayerComplicationData.current(context, dhuhr.plusSeconds(60))!!

        assertEquals("Jumuah", state.prayerName)
        assertEquals("J", state.initial)
    }

    @Test
    fun theNextChangeIsTheNextStateTransition() = runBlocking {
        val dhuhr = instantOf(date, timesOn(date).dhuhr)

        // Counting down: the prayer's own instant.
        assertEquals(dhuhr, PrayerComplicationData.nextChangeAt(context, dhuhr.minusSeconds(600)))
        // Counting up: the moment the window closes.
        assertEquals(
            dhuhr.plus(COUNT_UP_WINDOW),
            PrayerComplicationData.nextChangeAt(context, dhuhr.plusSeconds(600)),
        )
    }

    @Test
    fun withNoProfilesThereIsNothingToShow() = runBlocking {
        app.profileRepository.mirror(emptyList())

        // Null, so the data source can post an honest blank. Leaving the last value on the
        // watch face would show a stale prayer time indistinguishable from a current one.
        assertNull(PrayerComplicationData.current(context))
        assertNull(PrayerComplicationData.nextChangeAt(context))
    }
}
