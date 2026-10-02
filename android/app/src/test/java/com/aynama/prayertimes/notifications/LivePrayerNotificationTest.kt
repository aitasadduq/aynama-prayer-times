package com.aynama.prayertimes.notifications

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.timeline.COUNT_UP_WINDOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The notification's content, which is the part that is ours. The tick itself is the system's
 * chronometer — what has to be right here is the subject, the direction, and the instant the
 * chronometer counts from or towards.
 */
class LivePrayerNotificationTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val date = LocalDate.of(2026, 8, 5)
    private val profile = Profile(
        id = 1L,
        name = "Riyadh",
        latitude = 24.7136,
        longitude = 46.6753,
        calculationMethod = CalculationMethodKey.UMM_AL_QURA,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
        timezone = "Asia/Riyadh",
        useLocationTimezone = true,
    )

    private val times = AdhanWrapper().getPrayerTimes(
        profile.latitude, profile.longitude, date, zone, profile.calculationMethod,
    )

    private fun at(instant: Instant) = LivePrayerNotification.buildContent(profile, instant)!!
    private fun instantOf(d: LocalDate, t: java.time.LocalTime) = d.atTime(t).atZone(zone).toInstant()

    private val dhuhr = instantOf(date, times.dhuhr)
    private val asr = instantOf(date, times.asrShafii)

    @Test
    fun beforeAPrayer_countsDownTowardsIt() {
        val content = at(dhuhr.minusSeconds(600))

        assertEquals("Dhuhr", content.title)
        assertTrue(content.countingDown)
        // The chronometer counts down to the prayer's own instant — that is what makes the
        // system tick match the app's number without anything of ours running.
        assertEquals(dhuhr, content.chronometerBase)
    }

    @Test
    fun atThePrayerInstant_flipsToCountingUpFromIt() {
        val content = at(dhuhr)

        assertEquals("Dhuhr", content.title)
        assertFalse(content.countingDown)
        assertEquals(dhuhr, content.chronometerBase)
    }

    @Test
    fun insideTheWindow_stillCountsUpFromThePrayer() {
        val content = at(dhuhr.plusSeconds(15 * 60))

        assertEquals("Dhuhr", content.title)
        assertFalse(content.countingDown)
        assertEquals(dhuhr, content.chronometerBase)
    }

    @Test
    fun pastTheWindow_countsDownTowardsTheNextPrayer() {
        val content = at(dhuhr.plus(COUNT_UP_WINDOW))

        assertEquals("Asr", content.title)
        assertTrue(content.countingDown)
        assertEquals(asr, content.chronometerBase)
    }

    @Test
    fun theDirectionIsAlsoCarriedInWords() {
        // The system chronometer has no sign, so the text is what tells a reader which way it
        // is running. DESIGN.md §19 platform note.
        assertTrue(at(dhuhr.minusSeconds(600)).text.startsWith("At "))
        assertTrue(at(dhuhr.plusSeconds(60)).text.startsWith("Began at "))
    }

    @Test
    fun fridayDhuhrIsAnnouncedAsJumuah() {
        val friday = LocalDate.of(2026, 5, 15)
        val fridayTimes = AdhanWrapper().getPrayerTimes(
            profile.latitude, profile.longitude, friday, zone, profile.calculationMethod,
        )
        val content = at(instantOf(friday, fridayTimes.dhuhr).plusSeconds(60))

        assertEquals("Jumuah", content.title)
    }

    @Test
    fun aLocationWithNoTimesHasNoNotification() {
        // Tromsø at midsummer: the sun neither rises nor sets, so there is nothing to count to.
        val polar = profile.copy(
            latitude = 69.6, longitude = 18.95, timezone = "Europe/Oslo",
        )
        val midsummer = LocalDate.of(2026, 6, 21).atTime(12, 0)
            .atZone(ZoneId.of("Europe/Oslo")).toInstant()

        assertNull(LivePrayerNotification.buildContent(polar, midsummer))
    }
}
