package com.aynama.prayertimes.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * When the watch admits the phone has gone quiet.
 *
 * The times themselves stay right while the phone is away — the watch calculates them from the
 * mirrored profiles — so this is a note about the *profile set*, not about the countdown, and
 * it is deliberately slow to appear.
 */
class WearStalenessTest {

    private val now: Instant = Instant.parse("2026-09-07T12:00:00Z")

    private fun stalenessAfter(hours: Long) = WearHomeViewModel.stalenessOf(
        lastSyncedAt = now.minusSeconds(hours * 3600).toEpochMilli(),
        now = now,
    )

    @Test
    fun aRecentSyncIsFresh() {
        assertEquals(WearHomeState.Staleness.FRESH, stalenessAfter(1))
    }

    @Test
    fun anOrdinaryDayApartIsStillFresh() {
        // A watch worn all day with the phone in a bag must not nag. Prayer times do not
        // depend on the phone.
        assertEquals(WearHomeState.Staleness.FRESH, stalenessAfter(24))
    }

    @Test
    fun justInsideTheWindowIsFresh() {
        assertEquals(WearHomeState.Staleness.FRESH, stalenessAfter(47))
    }

    @Test
    fun pastTheWindowIsStale() {
        assertEquals(WearHomeState.Staleness.STALE, stalenessAfter(49))
    }

    @Test
    fun neverHavingSyncedIsStale() {
        // Nothing has ever arrived, so nothing about the profile set can be trusted.
        assertEquals(
            WearHomeState.Staleness.STALE,
            WearHomeViewModel.stalenessOf(lastSyncedAt = 0L, now = now),
        )
    }

    @Test
    fun theWindowIsMeasuredInDaysNotMinutes() {
        // Guards the intent, not the number: a threshold of minutes would cry wolf on every
        // commute, and someone tuning this later should have to think about that.
        assertEquals(2L, WearHomeViewModel.STALE_AFTER.toDays())
    }
}
