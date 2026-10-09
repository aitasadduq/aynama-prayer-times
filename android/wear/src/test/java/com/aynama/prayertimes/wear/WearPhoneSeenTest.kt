package com.aynama.prayertimes.wear

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a sync counts as hearing from the phone.
 *
 * A pull re-reads the watch's own copy of the Data Layer, which succeeds with the phone switched
 * off. Stamping every pull kept the "Phone not seen recently" note from ever appearing.
 */
class WearPhoneSeenTest {

    @Test
    fun aConnectedPhoneCountsAsSeen() = runTest {
        assertTrue(isPhoneConnected { listOf("phone-node") })
    }

    @Test
    fun noConnectedNodesIsNotSeen() = runTest {
        assertFalse(isPhoneConnected { emptyList<Any>() })
    }

    @Test
    fun aFailingNodeLookupIsNotSeenAndDoesNotThrow() = runTest {
        // A watch without Play services, or a node API that errors, must not crash the pull.
        assertFalse(isPhoneConnected { throw IllegalStateException("Play services unavailable") })
    }

    @Test
    fun seenMovesTheStampToNow() {
        assertEquals(2_000L, nextSyncStamp(previous = 1_000L, phoneSeen = true, now = 2_000L))
    }

    @Test
    fun unseenKeepsTheLastStamp() {
        assertEquals(1_000L, nextSyncStamp(previous = 1_000L, phoneSeen = false, now = 2_000L))
        assertEquals(0L, nextSyncStamp(previous = 0L, phoneSeen = false, now = 2_000L))
    }

    @Test
    fun aLocalPublicationDoesNotCountAsHearingFromThePhone() {
        val phoneSeen = isRemoteProfilePublication(publisherId = "watch-node", localNodeId = "watch-node")
        assertFalse(phoneSeen)
        assertEquals(0L, nextSyncStamp(previous = 0L, phoneSeen = phoneSeen, now = 2_000L))
    }

    @Test
    fun aRemotePublicationCountsAsHearingFromThePhone() {
        val phoneSeen = isRemoteProfilePublication(publisherId = "phone-node", localNodeId = "watch-node")
        assertTrue(phoneSeen)
        assertEquals(2_000L, nextSyncStamp(previous = 0L, phoneSeen = phoneSeen, now = 2_000L))
    }

    @Test
    fun anUnknownPublisherDoesNotCountAsHearingFromThePhone() {
        assertFalse(isRemoteProfilePublication(publisherId = null, localNodeId = "watch-node"))
        assertFalse(isRemoteProfilePublication(publisherId = "", localNodeId = "watch-node"))
    }
}
