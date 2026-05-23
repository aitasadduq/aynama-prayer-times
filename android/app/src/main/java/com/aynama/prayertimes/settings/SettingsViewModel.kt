package com.aynama.prayertimes.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.notifications.NotificationPreferences
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: ProfileRepository,
    private val notifPrefs: NotificationPreferences,
    private val context: Context,
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(profile: Profile) {
        viewModelScope.launch {
            if (profile.id == 0L) {
                val sortOrder = profiles.value.size
                repo.insert(profile.copy(sortOrder = sortOrder))
            } else {
                repo.update(profile)
            }
            val allProfiles = repo.observeAll().first()
            AlarmScheduler.scheduleAll(context, allProfiles)
        }
    }

    fun delete(profile: Profile) {
        viewModelScope.launch {
            repo.delete(profile)
            if (notifPrefs.notificationProfileId == profile.id) {
                notifPrefs.notificationProfileId = -1L
            }
            val allProfiles = repo.observeAll().first()
            AlarmScheduler.scheduleAll(context, allProfiles)
        }
    }

    companion object {
        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(app.profileRepository, app.notificationPreferences, app) as T
            }
    }
}
