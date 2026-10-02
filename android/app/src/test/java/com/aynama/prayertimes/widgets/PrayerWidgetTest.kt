package com.aynama.prayertimes.widgets

import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.timeline.COUNT_UP_WINDOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** Yesterday/today/tomorrow around [day], the window the countdown timeline needs. */
    private fun threeDays(
        day: LocalDate,
        times: (LocalDate) -> PrayerTimesResult,
    ): Map<LocalDate, PrayerTimesResult> =
        (-1L..1L).associate { day.plusDays(it) to times(day.plusDays(it)) }

    /**
     * Assemble the three-day timeline the widget builds on the device and render one state.
     *
     * [yesterday] matters in the small hours: a late Isha belongs to the following calendar
     * day, so the day before "today" is what the countdown counts from before Fajr.
     */
    private fun stateOf(
        profile: Profile,
        yesterday: PrayerTimesResult,
        today: PrayerTimesResult,
        tomorrow: PrayerTimesResult,
        now: ZonedDateTime,
        elapsedRealtime: Long,
        hijriDateText: String = "",
    ): PrayerWidgetState = buildPrayerWidgetState(
        profile = profile,
        days = mapOf(
            now.toLocalDate().minusDays(1) to yesterday,
            now.toLocalDate() to today,
            now.toLocalDate().plusDays(1) to tomorrow,
        ),
        todayTimes = today,
        now = now,
        elapsedRealtime = elapsedRealtime,
        hijriDateText = hijriDateText,
    )

    @Test
    fun `widget state uses next prayer abbreviation and 24-hour time`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("ASR", state.countdownPrayerAbbreviation)
        assertEquals("Asr", state.countdownPrayerName)
        assertEquals(todayTimes.asrShafii.format(widgetTimeFormatter), state.countdownPrayerDisplayTime)
    }

    @Test
    fun `widget schedule includes six rows with sunrise`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(1, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals(listOf("FAJ", "SUN", "DHU", "ASR", "MAG", "ISH"), state.schedule.map { it.abbreviation })
    }

    @Test
    fun `widget state exposes dates and sunrise`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
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

        val state = stateOf(
            profile = profile,
            yesterday = scrambled,
            today = scrambled,
            tomorrow = scrambled,
            now = ZonedDateTime.of(date, LocalTime.of(21, 14), zone),
            elapsedRealtime = 0L,
        )

        // Next is Sunrise (the soonest upcoming event), not tomorrow's Isha.
        assertEquals("Sunrise", state.countdownPrayerName)
        assertEquals("SUN", state.countdownPrayerAbbreviation)
        // Countdown ~1h31m to 22:45, not ~21h.
        assertTrue(state.countdownBaseElapsedRealtime in 1_000L * 60 * 80..1_000L * 60 * 100)
        // The most recently started event is Fajr (19:00) — today's Sunrise (22:45) is still ahead.
        assertEquals("Fajr", state.currentPrayerName)
    }

    @Test
    fun `sunrise is the current event between sunrise and dhuhr`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, todayTimes.sunrise.plusMinutes(5), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Sunrise", state.currentPrayerName)
        assertEquals("Dhuhr", state.countdownPrayerName)
    }

    @Test
    fun `fajr is current before sunrise`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, todayTimes.sunrise.minusMinutes(5), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Fajr", state.currentPrayerName)
        assertEquals("Sunrise", state.countdownPrayerName)
    }

    // The handover is inclusive: an event becomes current ON its instant, not a tick later.
    // The rollover alarm fires a couple of seconds after the boundary, so if these flipped
    // exclusive the widget would render the previous event for that window.
    @Test
    fun `sunrise becomes current at the exact sunrise instant`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, todayTimes.sunrise, zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Sunrise", state.currentPrayerName)
    }

    @Test
    fun `dhuhr becomes current at the exact dhuhr instant`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, todayTimes.dhuhr, zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Dhuhr", state.currentPrayerName)
    }

    // --- 4x2 render decisions ---------------------------------------------------
    // full() builds RemoteViews and needs a Context, so the decisions it makes are
    // asserted through the pure predicates it calls.

    @Test
    fun `sunrise block is highlighted and no prayer column is, between sunrise and dhuhr`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, todayTimes.sunrise.plusMinutes(5), zone),
            elapsedRealtime = 1_000L,
        )

        assertTrue(isSunriseHighlighted(state))
        assertNull(highlightedColumnIndex(state))
    }

    @Test
    fun `dhuhr column is highlighted and the sunrise block is not, after dhuhr`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertFalse(isSunriseHighlighted(state))
        assertEquals(listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"), columnRows(state).map { it.name })
        assertEquals(1, highlightedColumnIndex(state))
    }

    @Test
    fun `the sunrise block is never highlighted while today's sunrise is still ahead`() {
        // The block always shows TODAY's sunrise, but the current-event search also considers
        // yesterday-dated events. Where the day's events are not in canonical clock order those
        // two can disagree, and the widget must not highlight a sunrise that has not happened.
        val scrambled = PrayerTimesResult(
            fajr = LocalTime.of(19, 0),
            sunrise = LocalTime.of(22, 45),
            dhuhr = LocalTime.of(7, 0),
            asrShafii = LocalTime.of(11, 21),
            asrHanafi = LocalTime.of(11, 21),
            maghrib = LocalTime.of(15, 14),
            isha = LocalTime.of(18, 59),
        )
        val state = stateOf(
            profile = profile,
            yesterday = scrambled,
            today = scrambled,
            tomorrow = scrambled,
            now = ZonedDateTime.of(date, LocalTime.of(0, 10), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Sunrise", state.currentPrayerName)
        assertFalse("today's sunrise (22:45) has not happened at 00:10", state.sunriseHasPassed)
        assertFalse(isSunriseHighlighted(state))
    }

    @Test
    fun `widget state stays coherent at high latitude in midsummer`() {
        // 65°N in June: adhan's high-latitude fallback collapses Fajr and Isha onto the same
        // instant (both 00:46), so the day's events are degenerate. Every other widget fixture
        // is mid-latitude London, where that never happens.
        //
        // 65°N is deliberately just south of the Arctic Circle. At 66°N and above in midsummer
        // adhan returns a null Fajr and AdhanWrapper.getPrayerTimes throws — a pre-existing crash
        // that this suite cannot cover until that is handled. See REVIEW-FINDINGS.md.
        val arcticZone = ZoneId.of("Europe/Oslo")
        val arcticProfile = profile.copy(
            name = "Arctic edge", latitude = 65.0, longitude = 18.9553,
            timezone = "Europe/Oslo", useLocationTimezone = true,
        )
        val midsummer = LocalDate.of(2026, 6, 21)
        fun times(day: LocalDate) = adhan.getPrayerTimes(
            arcticProfile.latitude, arcticProfile.longitude, day, arcticZone, arcticProfile.calculationMethod,
        )

        for (hour in 0..23) {
            val now = ZonedDateTime.of(midsummer, LocalTime.of(hour, 30), arcticZone)
            val state = stateOf(
                profile = arcticProfile,
                yesterday = times(midsummer.minusDays(1)),
                today = times(midsummer),
                tomorrow = times(midsummer.plusDays(1)),
                now = now,
                elapsedRealtime = ROLLOVER_ELAPSED,
            )

            assertTrue("countdown ran past zero at $now", state.countdownBaseElapsedRealtime > ROLLOVER_ELAPSED)
            assertTrue(
                "currentPrayerName '${state.currentPrayerName}' matches no schedule row at $now",
                state.currentPrayerName.isEmpty() || state.schedule.any { it.name == state.currentPrayerName },
            )
            // The sunrise block must never be highlighted while it shows a future time.
            if (isSunriseHighlighted(state)) {
                assertTrue("sunrise highlighted before it happened at $now", state.sunriseHasPassed)
            }
            // At most one column can claim to be current.
            val highlighted = highlightedColumnIndex(state)
            if (highlighted != null) {
                assertEquals(state.currentPrayerName, columnRows(state)[highlighted].name)
            }
        }
    }

    @Test
    fun `widget hijri date defaults to empty`() {
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("", state.hijriDateText)
    }

    @Test
    fun `widget update schedule is a strictly-future ascending window`() {
        val now = rDate.atTime(rToday.asrShafii.plusMinutes(1)).atZone(rZone).toInstant().toEpochMilli()
        val updates = rolloverScheduleAt(now)

        // A rolling window of the next N state changes, not a fixed list of the day's prayers,
        // so it stays full no matter where in the day it is asked.
        assertEquals(WIDGET_UPDATE_SLOT_COUNT, updates.size)
        assertTrue(updates.all { it.triggerEpochMs > now })
        assertEquals(
            updates.map { it.triggerEpochMs }.sorted(),
            updates.map { it.triggerEpochMs },
        )
        assertEquals(updates.size, updates.map { it.triggerEpochMs }.distinct().size)
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
        return stateOf(
            profile = rProfile,
            yesterday = rTimesFor(now.toLocalDate().minusDays(1)),
            today = rTimesFor(now.toLocalDate()),
            tomorrow = rTimesFor(now.toLocalDate().plusDays(1)),
            now = now,
            elapsedRealtime = ROLLOVER_ELAPSED,
        )
    }

    private fun rolloverScheduleAt(nowEpochMs: Long): List<ScheduledWidgetUpdate> {
        val day = Instant.ofEpochMilli(nowEpochMs).atZone(rZone).toLocalDate()
        return buildWidgetUpdateSchedule(
            profile = rProfile,
            days = mapOf(
                day.minusDays(1) to rTimesFor(day.minusDays(1)),
                day to rTimesFor(day),
                day.plusDays(1) to rTimesFor(day.plusDays(1)),
            ),
            zone = rZone,
            nowEpochMs = nowEpochMs,
        )
    }

    private fun offsetMs(state: PrayerWidgetState) = state.countdownBaseElapsedRealtime - ROLLOVER_ELAPSED

    /**
     * The countdown is coherent in whichever direction it is running (DESIGN.md §19).
     *
     * Counting down, the Chronometer base must be strictly in the future — a base at or before
     * now means it already ran through zero and is ticking negative in the launcher. Counting
     * up, the base is in the past by definition, but never by more than the 30-minute window,
     * and only ever from a prayer.
     */
    private fun assertCountdownCoherent(state: PrayerWidgetState, where: String) {
        if (state.countdownIsElapsed) {
            assertTrue(
                "$where: counting up from ${state.countdownPrayerName} by ${-offsetMs(state)}ms, " +
                    "past the 30-minute window",
                offsetMs(state) <= 0 && -offsetMs(state) < COUNT_UP_WINDOW.toMillis(),
            )
            assertTrue(
                "$where: counting up from ${state.countdownPrayerName}, which is not a prayer",
                state.countdownPrayerName != SUNRISE_NAME,
            )
        } else {
            assertTrue(
                "$where: countdown ran past zero — ${offsetMs(state)}ms to ${state.countdownPrayerName}",
                offsetMs(state) > 0,
            )
        }
    }

    @Test
    fun `recomputing exactly at a prayer instant counts up from it`() {
        val dhuhr = rDate.atTime(rToday.dhuhr).atZone(rZone).toInstant()
        val state = rolloverStateAt(dhuhr)

        assertEquals("Dhuhr", state.countdownPrayerName)
        assertTrue(state.countdownIsElapsed)
        assertEquals(0L, offsetMs(state))
    }

    @Test
    fun `thirty minutes after a prayer the widget flips to counting down`() {
        val dhuhr = rDate.atTime(rToday.dhuhr).atZone(rZone).toInstant()

        val justInside = rolloverStateAt(dhuhr.plus(COUNT_UP_WINDOW).minusSeconds(1))
        assertTrue(justInside.countdownIsElapsed)
        assertEquals("Dhuhr", justInside.countdownPrayerName)

        val justOutside = rolloverStateAt(dhuhr.plus(COUNT_UP_WINDOW))
        assertFalse(justOutside.countdownIsElapsed)
        assertEquals("Asr", justOutside.countdownPrayerName)
    }

    @Test
    fun `rollover alarms are armed after the prayer instant, never on it`() {
        val dayStart = rDate.atStartOfDay(rZone).toInstant().toEpochMilli()
        val fajr = rDate.atTime(rToday.fajr).atZone(rZone).toInstant().toEpochMilli()

        assertEquals(fajr + WIDGET_UPDATE_GUARD_MS, rolloverScheduleAt(dayStart).first().triggerEpochMs)
    }

    @Test
    fun `a rollover always leaves the countdown coherent`() {
        val updates = rolloverScheduleAt(rDate.atStartOfDay(rZone).toInstant().toEpochMilli())
        assertEquals(WIDGET_UPDATE_SLOT_COUNT, updates.size)

        // AlarmManager is allowed to be late, and on some OEM builds marginally
        // early. Every delivery inside that band must still render a sane countdown.
        val deliveryJitterMs = listOf(-WIDGET_UPDATE_GUARD_MS + 1, 0L, 250L, 5_000L, 60_000L)
        for (update in updates) {
            for (jitter in deliveryJitterMs) {
                val firedAt = Instant.ofEpochMilli(update.triggerEpochMs + jitter)
                assertCountdownCoherent(
                    rolloverStateAt(firedAt),
                    "alarm ${update.requestCode} fired at $firedAt (jitter ${jitter}ms)",
                )
            }
        }
    }

    @Test
    fun `walking the alarm chain alternates counting up and counting down`() {
        val deliveryLatencyMs = 250L
        var nowMs = rDate.atStartOfDay(rZone).toInstant().toEpochMilli()
        val handoffs = mutableListOf<Pair<String, Boolean>>()

        repeat(WIDGET_UPDATE_SLOT_COUNT) {
            val next = rolloverScheduleAt(nowMs).minByOrNull { it.triggerEpochMs }
            assertNotNull("no rollover alarm pending at ${Instant.ofEpochMilli(nowMs)}", next)

            nowMs = next!!.triggerEpochMs + deliveryLatencyMs
            val state = rolloverStateAt(Instant.ofEpochMilli(nowMs))
            assertCountdownCoherent(state, "chain step at ${Instant.ofEpochMilli(nowMs)}")
            handoffs += state.countdownPrayerName to state.countdownIsElapsed
        }

        // Starting at midnight: down to Fajr, up from Fajr, down to Sunrise (Sunrise never
        // counts up), down to Dhuhr, up from Dhuhr, and so on through the day.
        assertEquals(
            listOf(
                "Fajr" to true,
                "Sunrise" to false,
                "Dhuhr" to false,
                "Dhuhr" to true,
                "Asr" to false,
                "Asr" to true,
                "Maghrib" to false,
                "Maghrib" to true,
                "Isha" to false,
                "Isha" to true,
                "Fajr" to false,
                "Fajr" to true,
            ),
            handoffs,
        )
    }

    @Test
    fun `the stretch after Isha still has a rollover armed`() {
        val ishaMs = rDate.atTime(rToday.isha).atZone(rZone).toInstant().toEpochMilli()
        val afterIsha = ishaMs + 60_000L
        val tomorrowFajr = rDate.plusDays(1).atTime(rTimesFor(rDate.plusDays(1)).fajr)
            .atZone(rZone).toInstant().toEpochMilli()

        val updates = rolloverScheduleAt(afterIsha)
        val triggers = updates.map { it.triggerEpochMs }

        // A minute past Isha the widget is counting up; the next two things that change are
        // the +30 minute flip and then tomorrow's Fajr. Both must be armed.
        assertEquals(ishaMs + COUNT_UP_WINDOW.toMillis() + WIDGET_UPDATE_GUARD_MS, triggers.first())
        assertTrue("tomorrow's Fajr must be armed", triggers.contains(tomorrowFajr + WIDGET_UPDATE_GUARD_MS))

        val state = rolloverStateAt(Instant.ofEpochMilli(afterIsha))
        assertEquals("Isha", state.countdownPrayerName)
        assertTrue(state.countdownIsElapsed)
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
            days = threeDays(day) { pastMidnight },
            zone = rZone,
            nowEpochMs = at2300,
        )

        // Isha is the first transition still ahead at 23:00 — it lands on the following day.
        val ishaAt = day.plusDays(1).atTime(pastMidnight.isha).atZone(rZone).toInstant().toEpochMilli()
        assertEquals(ishaAt + WIDGET_UPDATE_GUARD_MS, updates.first().triggerEpochMs)
        assertTrue("Isha rollover must still be pending at 23:00", updates.first().triggerEpochMs > at2300)
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
            buildWidgetUpdateSchedule(p, threeDays(day, ::times), zone, nowMs, slot)
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
                dstProfile, threeDays(day, ::times), dstZone, nowMs,
            )
            assertEquals("$day", WIDGET_UPDATE_SLOT_COUNT, armed.size)

            repeat(WIDGET_UPDATE_SLOT_COUNT) {
                val d = Instant.ofEpochMilli(nowMs).atZone(dstZone).toLocalDate()
                val next = buildWidgetUpdateSchedule(
                    dstProfile, threeDays(d, ::times), dstZone, nowMs,
                ).minByOrNull { it.triggerEpochMs }
                assertNotNull("$day: no rollover pending at ${Instant.ofEpochMilli(nowMs)}", next)

                nowMs = next!!.triggerEpochMs + 250L
                val at = Instant.ofEpochMilli(nowMs).atZone(dstZone)
                val state = stateOf(
                    profile = dstProfile,
                    yesterday = times(at.toLocalDate().minusDays(1)),
                    today = times(at.toLocalDate()),
                    tomorrow = times(at.toLocalDate().plusDays(1)),
                    now = at,
                    elapsedRealtime = ROLLOVER_ELAPSED,
                )
                assertCountdownCoherent(state, "$day at $at")
            }
        }
    }

    @Test
    fun `current prayer name always matches a schedule row and columns drop only sunrise`() {
        // The 4x2 widget drops the sunrise row and highlights the active prayer by string
        // equality across two separately hardcoded name lists (scheduleRows vs
        // timelineEvents). Renaming in one place silently breaks columns or highlighting.
        val state = stateOf(
            profile = profile,
            yesterday = todayTimes,
            today = todayTimes,
            tomorrow = tomorrowTimes,
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

    // --- Friday naming ----------------------------------------------------------

    @Test
    fun `widgets name friday's dhuhr as jumuah`() {
        val friday = LocalDate.of(2026, 5, 15)
        fun times(d: LocalDate) =
            adhan.getPrayerTimes(profile.latitude, profile.longitude, d, zone, profile.calculationMethod)
        val state = stateOf(
            profile = profile,
            yesterday = times(friday.minusDays(1)),
            today = times(friday),
            tomorrow = times(friday.plusDays(1)),
            now = ZonedDateTime.of(friday, LocalTime.of(11, 0), zone),
            elapsedRealtime = 1_000L,
        )

        // The 2x2 schedule and the 4x2 columns.
        assertTrue("Jumuah" in state.schedule.map { it.name })
        assertFalse("Dhuhr" in state.schedule.map { it.name })
        assertEquals("JUM", state.schedule.first { it.name == "Jumuah" }.abbreviation)
        // The countdown target on a Friday late morning.
        assertEquals("Jumuah", state.countdownPrayerName)
        assertEquals("JUM", state.countdownPrayerAbbreviation)
    }

    @Test
    fun `the highlighted column still matches after the rename`() {
        // currentPrayerName and the column names are produced by different code paths; if only
        // one of them learned about Jumuah the 4x2 widget would highlight nothing all Friday.
        val friday = LocalDate.of(2026, 5, 15)
        fun times(d: LocalDate) =
            adhan.getPrayerTimes(profile.latitude, profile.longitude, d, zone, profile.calculationMethod)
        val today = times(friday)
        val state = stateOf(
            profile = profile,
            yesterday = times(friday.minusDays(1)),
            today = today,
            tomorrow = times(friday.plusDays(1)),
            now = ZonedDateTime.of(friday, today.dhuhr.plusMinutes(45), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Jumuah", state.currentPrayerName)
        assertEquals(1, highlightedColumnIndex(state))
    }

    @Test
    fun `widgets keep dhuhr on other days`() {
        val thursday = LocalDate.of(2026, 5, 14)
        fun times(d: LocalDate) =
            adhan.getPrayerTimes(profile.latitude, profile.longitude, d, zone, profile.calculationMethod)
        val state = stateOf(
            profile = profile,
            yesterday = times(thursday.minusDays(1)),
            today = times(thursday),
            tomorrow = times(thursday.plusDays(1)),
            now = ZonedDateTime.of(thursday, LocalTime.of(11, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals("Dhuhr", state.countdownPrayerName)
        assertEquals("DHU", state.countdownPrayerAbbreviation)
    }

    // --- Tap target: each widget opens its own profile --------------------------

    @Test
    fun `widget state carries the profile it renders`() {
        fun times(d: LocalDate) =
            adhan.getPrayerTimes(profile.latitude, profile.longitude, d, zone, profile.calculationMethod)
        val other = profile.copy(id = 7L, name = "Makkah")

        fun stateFor(p: Profile) = stateOf(
            profile = p,
            yesterday = times(date.minusDays(1)),
            today = times(date),
            tomorrow = times(date.plusDays(1)),
            now = ZonedDateTime.of(date, LocalTime.of(14, 0), zone),
            elapsedRealtime = 1_000L,
        )

        assertEquals(1L, stateFor(profile).profileId)
        assertEquals(7L, stateFor(other).profileId)
    }

    @Test
    fun `taps on widgets bound to different profiles are distinct pending intents`() {
        // Extras are not part of PendingIntent equality. Before this, every widget shared
        // request code 0 and an identical intent, so the last one rendered silently retargeted
        // all the others and every tap opened the same profile.
        val ids = listOf(1L, 2L, 3L, 42L)

        assertEquals(ids.size, ids.map { widgetOpenRequestCode(it) }.distinct().size)
        assertEquals(ids.size, ids.map { widgetOpenDataUri(it) }.distinct().size)
    }

    @Test
    fun `two widgets on the same profile may share one tap intent`() {
        assertEquals(widgetOpenRequestCode(3L), widgetOpenRequestCode(3L))
        assertEquals(widgetOpenDataUri(3L), widgetOpenDataUri(3L))
    }

    @Test
    fun `widget tap request codes stay clear of the rollover alarm range`() {
        // Both live in the same app; an overlap would have one cancel the other.
        val rollovers = WIDGET_UPDATE_REQUEST_CODE_BASE until
            WIDGET_UPDATE_REQUEST_CODE_BASE + WIDGET_UPDATE_MAX_PROFILES * WIDGET_UPDATE_SLOT_COUNT

        assertTrue((0L..999L).none { widgetOpenRequestCode(it) in rollovers })
    }

    @Test
    fun `a widget with no resolvable profile does not share a real profile's tap intent`() {
        // Room ids start at 1, so the "no profile" sentinel must not collide with any of them.
        val real = (1L..100L).map { widgetOpenRequestCode(it) }

        assertFalse(widgetOpenRequestCode(NO_WIDGET_PROFILE) in real)
        assertFalse(widgetOpenDataUri(NO_WIDGET_PROFILE) in (1L..100L).map { widgetOpenDataUri(it) })
    }
}
