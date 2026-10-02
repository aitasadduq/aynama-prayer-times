package com.aynama.prayertimes.wear

import android.content.Context
import android.util.Log
import com.aynama.prayertimes.notifications.resolveNotificationProfile
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.sync.ProfileCodec
import com.aynama.prayertimes.shared.sync.WearSyncContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * Publishes the phone's profile set to any paired watch.
 *
 * The watch does not read the phone's database — it keeps its own mirror and computes prayer
 * times itself from the same `shared-logic` code. That is what lets the watch stay right while
 * the phone is out of range, off, or simply not running.
 *
 * A Data Layer item, not a message: the Data Layer keeps the last value and hands it to a
 * watch that connects later, so a watch paired after the fact is populated without the user
 * having to open the phone app again.
 */
object PhoneWearSync {

    /**
     * Publish [profiles] and the active profile id.
     *
     * Failures are logged and swallowed. There is no watch on most installs, and a phone whose
     * profile save fails because nothing is listening would be a much worse bug than a watch
     * that syncs a moment later — every caller here is on a path whose real job is something
     * else.
     */
    suspend fun publish(context: Context, profiles: List<Profile>, activeProfileId: Long) {
        try {
            val request = PutDataMapRequest.create(WearSyncContract.PATH_PROFILES).apply {
                dataMap.putString(WearSyncContract.KEY_PROFILES, ProfileCodec.encode(profiles))
                dataMap.putLong(WearSyncContract.KEY_ACTIVE_PROFILE_ID, activeProfileId)
                // The Data Layer drops an item whose bytes are unchanged. Without this, a
                // re-publish after a watch app reinstall would be suppressed as a duplicate
                // and the fresh watch would never receive the profiles it is missing.
                dataMap.putLong(WearSyncContract.KEY_PUBLISHED_AT, System.currentTimeMillis())
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "could not publish profiles to the watch", e)
        }
    }

    private const val TAG = "PhoneWearSync"
}

/** One profile set and the id of the profile the watch should treat as active. */
internal data class WatchPublication(val profiles: List<Profile>, val activeProfileId: Long)

/**
 * Everything the watch should be sent, re-emitted whenever either input changes.
 *
 * The active profile is the phone's alerts profile, which lives in a preference rather than in
 * the profiles table, so a change made only in Notification settings has to republish too.
 */
internal fun watchPublications(
    profiles: Flow<List<Profile>>,
    notificationProfileId: Flow<Long>,
): Flow<WatchPublication> = combine(profiles, notificationProfileId) { list, savedId ->
    WatchPublication(
        profiles = list,
        activeProfileId = resolveNotificationProfile(savedId, list)?.id ?: WearSyncContract.NO_ACTIVE_PROFILE,
    )
}
