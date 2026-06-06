package com.aynama.prayertimes

import android.app.Application
import android.content.SharedPreferences
import androidx.glance.appwidget.updateAll
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.notifications.NotificationHelper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.db.AynamaDatabase
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import com.aynama.prayertimes.shared.data.repository.QazaRepository
import com.aynama.prayertimes.widgets.PrayerGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AynamaApplication : Application() {

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            PrayerGlanceWidget().updateAll(this@AynamaApplication)
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
            )
        )
    }
}
