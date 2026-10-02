package com.aynama.prayertimes.shared.sync

/**
 * The Data Layer paths and keys the phone writes and the watch reads.
 *
 * Both sides compile against these constants rather than repeating string literals. A typo in
 * a Data Layer path does not fail to compile and does not throw — the watch simply never hears
 * from the phone, which looks exactly like a watch that has not been paired.
 */
object WearSyncContract {

    /** The phone's whole profile set, encoded by [ProfileCodec]. */
    const val PATH_PROFILES = "/aynama/profiles"

    /** [ProfileCodec] payload. */
    const val KEY_PROFILES = "profiles"

    /**
     * Which profile the phone currently treats as the active one, so the watch opens on the
     * same place. -1 when the phone has none.
     */
    const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

    /**
     * When the phone published. The Data Layer suppresses an item whose bytes are unchanged,
     * so without a moving field a re-publish after a watch reinstall would be dropped as a
     * duplicate and the watch would sit empty.
     */
    const val KEY_PUBLISHED_AT = "published_at"

    const val NO_ACTIVE_PROFILE = -1L
}
