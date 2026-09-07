package com.aynama.prayertimes.wear

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.aynama.prayertimes.shared.sync.ProfileCodec
import com.aynama.prayertimes.wear.complications.ComplicationUpdateScheduler
import com.aynama.prayertimes.shared.sync.WearSyncContract
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * What the watch remembers about the last time it heard from the phone.
 *
 * The screen needs this to tell "no profiles yet" from "profiles, phone quiet for a while" —
 * two states that look identical from the data alone and mean very different things to a user
 * wondering whether to trust the times in front of them.
 */
class WearSyncState(private val prefs: SharedPreferences) {

    var activeProfileId: Long
        get() = prefs.getLong(KEY_ACTIVE, WearSyncContract.NO_ACTIVE_PROFILE)
        set(value) = prefs.edit().putLong(KEY_ACTIVE, value).apply()

    /** Epoch millis of the last successful sync, or 0 if the phone has never been heard from. */
    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    private companion object {
        const val KEY_ACTIVE = "active_profile_id"
        const val KEY_LAST_SYNC = "last_synced_at"
    }
}

object WearProfileSync {

    /**
     * Apply a published profile set to the watch's mirror.
     *
     * A payload that cannot be decoded is ignored, not treated as an empty set: one damaged or
     * newer-versioned message must not wipe a watch that is working perfectly well from the
     * last good one.
     */
    suspend fun apply(context: Context, dataMap: DataMap) {
        val app = context.applicationContext as WearApplication
        val profiles = ProfileCodec.decode(dataMap.getString(WearSyncContract.KEY_PROFILES))
        if (profiles == null) {
            Log.w(TAG, "ignoring a profile payload this build cannot read")
            return
        }
        app.profileRepository.mirror(profiles)
        app.syncState.activeProfileId =
            dataMap.getLong(WearSyncContract.KEY_ACTIVE_PROFILE_ID, WearSyncContract.NO_ACTIVE_PROFILE)
        app.syncState.lastSyncedAt = System.currentTimeMillis()
        // The profile set decides which prayer times a complication shows, so a sync that
        // changed it must not wait for the next armed refresh to reach the watch face.
        ComplicationUpdateScheduler.requestUpdateNow(context)
    }

    /**
     * Read whatever the phone has already published.
     *
     * Change events only reach a listener that was installed when the change happened. A watch
     * app opened for the first time — or reinstalled — has missed every one of them, and would
     * otherwise sit empty until the user next edited a profile on the phone.
     */
    suspend fun pullFromPhone(context: Context) {
        try {
            val uri = Uri.Builder().scheme("wear").path(WearSyncContract.PATH_PROFILES).build()
            val items = Wearable.getDataClient(context).getDataItems(uri).await()
            items.use { buffer ->
                buffer.firstOrNull()?.let { apply(context, DataMapItem.fromDataItem(it).dataMap) }
            }
        } catch (e: Exception) {
            // No paired phone is the normal case for a watch used standalone, and it must not
            // take the process down from Application.onCreate.
            Log.w(TAG, "could not read profiles from the phone", e)
        }
    }

    private const val TAG = "WearProfileSync"
}

/** Receives profile changes pushed from the phone. */
class WearProfileSyncService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != WearSyncContract.PATH_PROFILES) continue
            // WearableListenerService callbacks already run off the main thread and the
            // service stays alive for their duration, so blocking here is the contract rather
            // than a shortcut — launching into a scope would race the service being torn down.
            runBlocking {
                WearProfileSync.apply(applicationContext, DataMapItem.fromDataItem(event.dataItem).dataMap)
            }
        }
    }
}
