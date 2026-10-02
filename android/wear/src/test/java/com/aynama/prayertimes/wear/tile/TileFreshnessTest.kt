package com.aynama.prayertimes.wear.tile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * How long a tile claims to stay true.
 *
 * A tile is a static layout: it shows what was true when it was built. Getting this wrong in
 * one direction wakes the watch for nothing, and in the other leaves a finished prayer on
 * screen — so both ends are pinned, not just the happy path.
 */
class TileFreshnessTest {

    private val now: Instant = Instant.parse("2026-09-07T12:00:00Z")

    private fun freshnessIn(duration: Duration) =
        PrayerTileService.freshnessFor(now.plus(duration), now)

    @Test
    fun anOrdinaryGapRefreshesWhenThePrayerChanges() {
        assertEquals(Duration.ofMinutes(25).toMillis(), freshnessIn(Duration.ofMinutes(25)))
    }

    @Test
    fun aBoundaryMomentsAwayIsFlooredAtAMinute() {
        // Without the floor a tile that just missed a transition would ask to be rebuilt
        // immediately, and again, and again.
        assertEquals(Duration.ofMinutes(1).toMillis(), freshnessIn(Duration.ofSeconds(2)))
    }

    @Test
    fun aDistantBoundaryIsCappedAtAnHour() {
        // The cap is a safety net, not an optimisation: if the transition is ever missed the
        // tile still corrects itself rather than showing yesterday's Isha indefinitely.
        assertEquals(Duration.ofHours(1).toMillis(), freshnessIn(Duration.ofHours(6)))
    }

    @Test
    fun noKnownBoundaryFallsBackToTheCap() {
        assertEquals(
            Duration.ofHours(1).toMillis(),
            PrayerTileService.freshnessFor(nextChange = null, now = now),
        )
    }

    @Test
    fun freshnessIsNeverZero() {
        // Zero means "never refresh" to the tile system — the one value that must not slip out.
        for (seconds in longArrayOf(0, 1, 30, 59)) {
            val value = PrayerTileService.freshnessFor(now.plusSeconds(seconds), now)
            assertEquals(Duration.ofMinutes(1).toMillis(), value)
        }
    }
}
