package com.aynama.prayertimes.widgets

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PrayerWidgetTest {
    private val zone = ZoneId.of("Europe/London")
    private val date = LocalDate.of(2025, 6, 15)
    private val profile = Profile(
        id = 1L,
        name = "London",
        latitude = 51.5074,
        longitude = -0.1278,
        calculationMethod = CalculationMethodKey.MWL,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
    )
    private val adhan = AdhanWrapper()
    private val todayTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = date,
        timezone = zone,
        method = profile.calculationMethod,
    )
    private val tomorrowTimes = adhan.getPrayerTimes(
        latitude = profile.latitude,
        longitude = profile.longitude,
        date = date.plusDays(1),
        timezone = zone,
        method = profile.calculationMethod,
    )
    private val widgetTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @Test
    fun `widget state uses next prayer abbreviation and 24-hour time`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("ASR", state.nextPrayerAbbreviation)
        assertEquals("Asr", state.nextPrayerName)
        assertEquals(todayTimes.asrShafii.format(widgetTimeFormatter), state.nextPrayerDisplayTime)
    }

    @Test
    fun `widget schedule includes six rows with sunrise`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(1, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals(listOf("FAJ", "SUN", "DHU", "ASR", "MAG", "ISH"), state.schedule.map { it.abbreviation })
    }

    @Test
    fun `widget state exposes dates and sunrise`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
            hijriDateText = "19 Dhu al-Ḥijjah 1446",
        )

        assertEquals(todayTimes.sunrise.format(widgetTimeFormatter), state.sunriseDisplayTime)
        assertEquals(
            date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
            state.gregorianDateText,
        )
        assertEquals("19 Dhu al-Ḥijjah 1446", state.hijriDateText)
    }

    @Test
    fun `next and current are derived chronologically even when clock order is scrambled`() {
        // Times as they appear for a London profile rendered in a far-off device timezone:
        // not in canonical clock order. Sunrise (22:45) is the only event still ahead at 21:14.
        val scrambled = PrayerTimesResult(
            fajr = LocalTime.of(19, 0),
            sunrise = LocalTime.of(22, 45),
            dhuhr = LocalTime.of(7, 0),
            asrShafii = LocalTime.of(11, 21),
            asrHanafi = LocalTime.of(11, 21),
            maghrib = LocalTime.of(15, 14),
            isha = LocalTime.of(18, 59),
        )

        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = scrambled,
            tomorrowTimes = scrambled,
            now = ZonedDateTime.of(date, LocalTime.of(21, 14), zone),
            elapsedRealtime = 0L,
        )

        // Next is Sunrise (the soonest upcoming event), not tomorrow's Isha.
        assertEquals("Sunrise", state.nextPrayerName)
        assertEquals("SUN", state.nextPrayerAbbreviation)
        // Countdown ~1h31m to 22:45, not ~21h.
        assertTrue(state.countdownBaseElapsedRealtime in 1_000L * 60 * 80..1_000L * 60 * 100)
        // The most recently started obligatory prayer is Fajr (19:00).
        assertEquals("Fajr", state.currentPrayerName)
    }

    @Test
    fun `widget hijri date defaults to empty`() {
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("", state.hijriDateText)
    }

    @Test
    fun `widget update schedule only keeps future prayer changes`() {
        val allUpdates = rolloverScheduleAt(rDate.atStartOfDay(rZone).toInstant().toEpochMilli())
        val now = rDate.atTime(rToday.asrShafii.plusMinutes(1)).atZone(rZone).toInstant().toEpochMilli()
        val updates = rolloverScheduleAt(now)

        assertTrue(updates.size < allUpdates.size)
        assertTrue(updates.all { it.triggerEpochMs > now })
    }

    // --- Rollover: the countdown must never run past zero ------------------------
    // Riyadh, not London: at 51°N in June adhan's high-latitude fallback collapses
    // Isha onto Fajr, so the prayer sequence is degenerate to walk.

    private val rZone = ZoneId.of("Asia/Riyadh")
    private val rDate = LocalDate.of(2026, 8, 5)
    private val rProfile = profile.copy(
        name = "Riyadh",
        latitude = 24.7136,
        longitude = 46.6753,
        calculationMethod = CalculationMethodKey.UMM_AL_QURA,
    )
    private val rToday get() = rTimesFor(rDate)

    private fun rTimesFor(day: LocalDate) = adhan.getPrayerTimes(
        latitude = rProfile.latitude,
        longitude = rProfile.longitude,
        date = day,
        timezone = rZone,
        method = rProfile.calculationMethod,
    )

    // Mirrors what the widget does on the device: recompute from whatever wall
    // clock the alarm actually woke us at, using that instant's own day.
    private fun rolloverStateAt(instant: Instant): PrayerWidgetState {
        val now = instant.atZone(rZone)
        return buildPrayerWidgetState(
            profile = rProfile,
            todayTimes = rTimesFor(now.toLocalDate()),
            tomorrowTimes = rTimesFor(now.toLocalDate().plusDays(1)),
            now = now,
            elapsedRealtime = ROLLOVER_ELAPSED,
        )
    }

    private fun rolloverScheduleAt(nowEpochMs: Long): List<ScheduledWidgetUpdate> {
        val day = Instant.ofEpochMilli(nowEpochMs).atZone(rZone).toLocalDate()
        return buildWidgetUpdateSchedule(
            profile = rProfile,
            date = day,
            times = rTimesFor(day),
            tomorrowTimes = rTimesFor(day.plusDays(1)),
            zone = rZone,
            nowEpochMs = nowEpochMs,
        )
    }

    private fun remainingMs(state: PrayerWidgetState) = state.countdownBaseElapsedRealtime - ROLLOVER_ELAPSED

    @Test
    fun `recomputing exactly at a prayer instant moves on to the following prayer`() {
        val dhuhr = rDate.atTime(rToday.dhuhr).atZone(rZone).toInstant()

        assertEquals("Asr", rolloverStateAt(dhuhr).nextPrayerName)
    }

    @Test
    fun `rollover alarms are armed after the prayer instant, never on it`() {
        val dayStart = rDate.atStartOfDay(rZone).toInstant().toEpochMilli()
        val fajr = rDate.atTime(rToday.fajr).atZone(rZone).toInstant().toEpochMilli()

        assertEquals(fajr + WIDGET_UPDATE_GUARD_MS, rolloverScheduleAt(dayStart).first().triggerEpochMs)
    }

    @Test
    fun `a rollover always leaves the countdown running forwards`() {
        val updates = rolloverScheduleAt(rDate.atStartOfDay(rZone).toInstant().toEpochMilli())
        assertEquals(WIDGET_UPDATE_SLOT_COUNT, updates.size)

        // AlarmManager is allowed to be late, and on some OEM builds marginally
        // early. Every delivery inside that band must still count down, not up.
        val deliveryJitterMs = listOf(-WIDGET_UPDATE_GUARD_MS + 1, 0L, 250L, 5_000L, 60_000L)
        for (update in updates) {
            for (jitter in deliveryJitterMs) {
                val firedAt = Instant.ofEpochMilli(update.triggerEpochMs + jitter)
                val state = rolloverStateAt(firedAt)
                assertTrue(
                    "countdown ran past zero for alarm ${update.requestCode} fired at $firedAt " +
                        "(jitter ${jitter}ms): ${remainingMs(state)}ms to ${state.nextPrayerName}",
                    remainingMs(state) > 0,
                )
            }
        }
    }

    @Test
    fun `walking the alarm chain hands each prayer off to the next`() {
        val deliveryLatencyMs = 250L
        var nowMs = rDate.atStartOfDay(rZone).toInstant().toEpochMilli()
        val handoffs = mutableListOf<String>()

        repeat(WIDGET_UPDATE_SLOT_COUNT) {
            val next = rolloverScheduleAt(nowMs).minByOrNull { it.triggerEpochMs }
            assertNotNull("no rollover alarm pending at ${Instant.ofEpochMilli(nowMs)}", next)

            nowMs = next!!.triggerEpochMs + deliveryLatencyMs
            val state = rolloverStateAt(Instant.ofEpochMilli(nowMs))
            assertTrue("countdown ran past zero at ${Instant.ofEpochMilli(nowMs)}", remainingMs(state) > 0)
            handoffs += state.nextPrayerName
        }

        assertEquals(listOf("Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha", "Fajr", "Sunrise"), handoffs)
    }

    @Test
    fun `the stretch after Isha still has a rollover armed`() {
        val afterIsha = rDate.atTime(rToday.isha).atZone(rZone).toInstant().toEpochMilli() + 60_000L
        val tomorrowFajr = rDate.plusDays(1).atTime(rTimesFor(rDate.plusDays(1)).fajr)
            .atZone(rZone).toInstant().toEpochMilli()

        val updates = rolloverScheduleAt(afterIsha)

        assertEquals(listOf(tomorrowFajr + WIDGET_UPDATE_GUARD_MS), updates.map { it.triggerEpochMs })
        assertEquals("Fajr", rolloverStateAt(Instant.ofEpochMilli(afterIsha)).nextPrayerName)
    }

    @Test
    fun `isha after midnight is armed on the following calendar day`() {
        // Isha (00:35) falls after midnight, so in clock order it precedes Fajr. Every
        // other schedule fixture has Isha in the evening, so this branch is otherwise dead.
        val pastMidnight = PrayerTimesResult(
            fajr = LocalTime.of(3, 40),
            sunrise = LocalTime.of(5, 12),
            dhuhr = LocalTime.of(13, 8),
            asrShafii = LocalTime.of(17, 30),
            asrHanafi = LocalTime.of(18, 40),
            maghrib = LocalTime.of(21, 20),
            isha = LocalTime.of(0, 35),
        )
        val day = LocalDate.of(2026, 6, 21)
        val at2300 = day.atTime(23, 0).atZone(rZone).toInstant().toEpochMilli()

        val updates = buildWidgetUpdateSchedule(
            profile = rProfile,
            date = day,
            times = pastMidnight,
            tomorrowTimes = pastMidnight,
            zone = rZone,
            nowEpochMs = at2300,
        )

        val isha = updates.single { it.requestCode == WIDGET_UPDATE_REQUEST_CODE_BASE + 5 }
        assertEquals(
            day.plusDays(1).atTime(pastMidnight.isha).atZone(rZone).toInstant().toEpochMilli()
                + WIDGET_UPDATE_GUARD_MS,
            isha.triggerEpochMs,
        )
        assertTrue("Isha rollover must still be pending at 23:00", isha.triggerEpochMs > at2300)
    }

    @Test
    fun `each bound profile gets its own chain with no request-code collisions`() {
        // Widgets carry per-instance profiles. One shared chain would leave every widget
        // not on the notification profile with no alarm at its own boundaries.
        val anchorage = rProfile.copy(
            id = 2L,
            name = "Anchorage",
            latitude = 61.2181,
            longitude = -149.9003,
            timezone = "America/Anchorage",
            useLocationTimezone = true,
        )
        val nowMs = rDate.atStartOfDay(rZone).toInstant().toEpochMilli()

        val chains = listOf(rProfile, anchorage).mapIndexed { slot, p ->
            val zone = p.effectiveZoneId()
            val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            fun times(d: LocalDate) =
                adhan.getPrayerTimes(p.latitude, p.longitude, d, zone, p.calculationMethod)
            buildWidgetUpdateSchedule(p, day, times(day), times(day.plusDays(1)), zone, nowMs, slot)
        }

        // Both profiles have rollovers pending; the exact count differs because one
        // global "now" lands at a different local time in each zone.
        chains.forEachIndexed { slot, chain ->
            assertTrue("slot $slot has no rollover armed", chain.isNotEmpty())
            val block = WIDGET_UPDATE_REQUEST_CODE_BASE + slot * WIDGET_UPDATE_SLOT_COUNT
            assertTrue(
                "slot $slot escaped its own request-code block",
                chain.all { it.requestCode in block until block + WIDGET_UPDATE_SLOT_COUNT },
            )
        }
        // The whole point: arming one profile's chain must not cancel another's.
        val codes = chains.flatten().map { it.requestCode }
        assertEquals("chains must not share request codes", codes.size, codes.distinct().size)
        assertTrue(
            "every code must stay inside the reserved widget range",
            codes.all {
                it in WIDGET_UPDATE_REQUEST_CODE_BASE until
                    WIDGET_UPDATE_REQUEST_CODE_BASE + WIDGET_UPDATE_MAX_PROFILES * WIDGET_UPDATE_SLOT_COUNT
            },
        )
    }

    @Test
    fun `alarm chain survives DST transitions`() {
        // Riyadh has no DST and the London fixture is mid-June, so nothing else here
        // crosses a 23h or 25h day where atZone silently shifts a nonexistent wall time.
        val dstZone = ZoneId.of("Europe/London")
        val dstProfile = rProfile.copy(
            name = "London", latitude = 51.5074, longitude = -0.1278,
            timezone = "Europe/London", useLocationTimezone = true,
        )
        fun times(d: LocalDate) = adhan.getPrayerTimes(
            dstProfile.latitude, dstProfile.longitude, d, dstZone, dstProfile.calculationMethod,
        )

        for (day in listOf(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            var nowMs = day.atStartOfDay(dstZone).toInstant().toEpochMilli()
            val armed = buildWidgetUpdateSchedule(
                dstProfile, day, times(day), times(day.plusDays(1)), dstZone, nowMs,
            )
            assertEquals("$day", WIDGET_UPDATE_SLOT_COUNT, armed.size)

            repeat(WIDGET_UPDATE_SLOT_COUNT) {
                val d = Instant.ofEpochMilli(nowMs).atZone(dstZone).toLocalDate()
                val next = buildWidgetUpdateSchedule(
                    dstProfile, d, times(d), times(d.plusDays(1)), dstZone, nowMs,
                ).minByOrNull { it.triggerEpochMs }
                assertNotNull("$day: no rollover pending at ${Instant.ofEpochMilli(nowMs)}", next)

                nowMs = next!!.triggerEpochMs + 250L
                val at = Instant.ofEpochMilli(nowMs).atZone(dstZone)
                val state = buildPrayerWidgetState(
                    profile = dstProfile,
                    todayTimes = times(at.toLocalDate()),
                    tomorrowTimes = times(at.toLocalDate().plusDays(1)),
                    now = at,
                    elapsedRealtime = ROLLOVER_ELAPSED,
                )
                assertTrue(
                    "$day: countdown ran past zero at $at",
                    state.countdownBaseElapsedRealtime - ROLLOVER_ELAPSED > 0,
                )
            }
        }
    }

    @Test
    fun `current prayer name always matches a schedule row and columns drop only sunrise`() {
        // The 4x2 widget drops the sunrise row and highlights the active prayer by string
        // equality across two separately hardcoded name lists (scheduleRows vs
        // timelineEvents). Renaming in one place silently breaks columns or highlighting.
        val state = buildPrayerWidgetState(
            profile = profile,
            todayTimes = todayTimes,
            tomorrowTimes = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertTrue(
            "currentPrayerName '${state.currentPrayerName}' matches no schedule row — " +
                "the full widget highlights by string equality and would highlight nothing",
            state.schedule.any { it.name == state.currentPrayerName },
        )
        assertEquals(
            listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"),
            state.schedule.filter { it.name != "Sunrise" }.map { it.name },
        )
    }

    private companion object {
        const val ROLLOVER_ELAPSED = 1_000L
    }
}
