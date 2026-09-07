package com.aynama.prayertimes

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.notifications.NotificationHelper
import com.aynama.prayertimes.notifications.resolveNotificationProfile
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import com.aynama.prayertimes.shared.sync.WearSyncContract
import com.aynama.prayertimes.wear.PhoneWearSync
import com.aynama.prayertimes.widgets.updateAllPrayerWidgets
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AynamaApplication : Application() {

    // SupervisorJob keeps one failed child from cancelling its siblings, but it does not stop an
    // uncaught throw from reaching the thread's default handler and killing the process. Startup
    // work here touches every profile, so a single unsupported one would take the app down on
    // launch. Log and carry on instead.
    internal val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e("AynamaApplication", "background work failed", throwable)
        },
    )

    val db: AynamaDatabase by lazy { AynamaDatabase.build(this) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(db.profileDao()) }
    val qazaRepository: QazaRepository by lazy { QazaRepository(db.qazaEntryDao()) }
    val prefs: SharedPreferences by lazy { getSharedPreferences("aynama_prefs", MODE_PRIVATE) }
    val notificationPreferences: com.aynama.prayertimes.notifications.NotificationPreferences by lazy {
        com.aynama.prayertimes.notifications.NotificationPreferences(prefs)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        appScope.launch {
            if (BuildConfig.DEBUG) seedDebugProfilesIfEmpty()
            val profiles = profileRepository.observeAll().first()
            AlarmScheduler.scheduleAll(this@AynamaApplication, profiles)
            updateAllPrayerWidgets(this@AynamaApplication)
        }
        publishProfilesToWatch()
    }

    /**
     * Keep any paired watch's mirror current.
     *
     * Collected from the repository rather than called at each mutation site: creating,
     * editing and deleting a profile all end in a Flow emission, so one collector covers every
     * path — including ones added later, which is exactly where a per-call-site hook would rot.
     */
    private fun publishProfilesToWatch() {
        appScope.launch {
            profileRepository.observeAll().collect { profiles ->
                PhoneWearSync.publish(
                    context = this@AynamaApplication,
                    profiles = profiles,
                    activeProfileId = resolveNotificationProfile(
                        notificationPreferences.notificationProfileId, profiles,
                    )?.id ?: WearSyncContract.NO_ACTIVE_PROFILE,
                )
            }
        }
    }

    private suspend fun seedDebugProfilesIfEmpty() {
        if (db.profileDao().count() > 0) return
        profileRepository.insert(
            com.aynama.prayertimes.shared.data.entity.Profile(
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
            com.aynama.prayertimes.shared.data.entity.Profile(
                name = "London (Ḥanafī)",
                latitude = 51.5074,
                longitude = -0.1278,
                calculationMethod = CalculationMethodKey.MWL,
                asrMadhab = AsrMadhab.HANAFI,
                isGps = false,
                sortOrder = 1,
                timezone = "Europe/London",
                useLocationTimezone = true,
            )
        )
    }
}
