package com.aynama.prayertimes.wear

import android.app.Application
import android.util.Log
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The watch keeps its own copy of the profile set and computes prayer times itself, from the
 * same `shared-logic` code the phone runs.
 *
 * It could have asked the phone for times on demand instead. It does not, because a watch is
 * most useful exactly when the phone is not to hand — in the pocket, out of range, or off —
 * and a countdown that stalls whenever the phone is away is worse than no countdown.
 */
class WearApplication : Application() {

    internal val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "background work failed", throwable)
        },
    )

    val db: AynamaDatabase by lazy { AynamaDatabase.build(this) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(db.profileDao()) }
    val syncState: WearSyncState by lazy {
        WearSyncState(getSharedPreferences("aynama_wear", MODE_PRIVATE))
    }

    override fun onCreate() {
        super.onCreate()
        // A watch app opened for the first time, or reinstalled, has an empty mirror and no
        // reason to expect a change event: the phone published before this app existed. Pull
        // whatever is already sitting on the Data Layer.
        appScope.launch {
            if (BuildConfig.DEBUG) seedDebugProfilesIfEmpty()
            WearProfileSync.pullFromPhone(this@WearApplication)
        }
    }

    /**
     * Debug builds only, and only while the mirror is empty.
     *
     * The watch is otherwise undevelopable on its own: without a paired phone there is nothing
     * to render, and pairing two emulators is a slow loop to sit inside for a layout change.
     * A real sync overwrites these — the mirror reconciles by id, and the phone's ids win.
     */
    private suspend fun seedDebugProfilesIfEmpty() {
        if (db.profileDao().count() > 0) return
        profileRepository.insert(
            Profile(
                name = "London",
                latitude = 51.5074,
                longitude = -0.1278,
                calculationMethod = CalculationMethodKey.MWL,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 0,
                timezone = "Europe/London",
                useLocationTimezone = true,
            )
        )
        profileRepository.insert(
            Profile(
                name = "Makkah",
                latitude = 21.4225,
                longitude = 39.8262,
                calculationMethod = CalculationMethodKey.UMM_AL_QURA,
                asrMadhab = AsrMadhab.SHAFII,
                isGps = false,
                sortOrder = 1,
                timezone = "Asia/Riyadh",
                useLocationTimezone = true,
            )
        )
    }

    private companion object {
        const val TAG = "WearApplication"
    }
}
