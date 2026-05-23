package com.aynama.prayertimes.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.entity.effectiveZoneId
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class PrayerRowData(
    val index: Int,
    val name: String,
    val time: String,
    val enabled: Boolean,
    val offset: Int = 0,
    val earlyReminder: Int = 0,
    val alertMode: AlertTimeMode = AlertTimeMode.OFFSET,
    val fixedTimeMinutes: Int = -1,
)

data class NotificationSettingsUiState(
    val permissionGranted: Boolean = true,
    val masterEnabled: Boolean = true,
    val prayerRows: List<PrayerRowData> = emptyList(),
    val imsakEnabled: Boolean = true,
    val adhanVoice: AdhanVoice = AdhanVoice.MAKKAH,
    val vibration: VibrationMode = VibrationMode.WITH_SOUND,
    val isRamadan: Boolean = false,
    val selectedPrayerIndex: Int? = null,
    val profiles: List<Profile> = emptyList(),
    val notificationProfile: Profile? = null,
    val showProfilePicker: Boolean = false,
)

class NotificationSettingsViewModel(
    private val prefs: NotificationPreferences,
    private val repo: ProfileRepository,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationSettingsUiState())
    val state: StateFlow<NotificationSettingsUiState> = _state

    init {
        loadPrefs()
        loadPrayerTimes()
    }

    fun refreshPermission() {
        _state.update { it.copy(permissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    }

    fun refreshFromPrefs() {
        loadPrefs()
    }

    fun setMaster(enabled: Boolean) {
        prefs.masterEnabled = enabled
        _state.update { it.copy(masterEnabled = enabled) }
        rescheduleAll()
    }

    fun setPrayerEnabled(index: Int, enabled: Boolean) {
        val pid = currentProfileId() ?: return
        prefs.setPrayerEnabled(pid, index, enabled)
        _state.update { state ->
            state.copy(prayerRows = state.prayerRows.map { row ->
                if (row.index == index) row.copy(enabled = enabled) else row
            })
        }
        rescheduleAll()
    }

    fun setImsakEnabled(enabled: Boolean) {
        prefs.imsakEnabled = enabled
        _state.update { it.copy(imsakEnabled = enabled) }
        rescheduleAll()
    }

    fun setVibration(mode: VibrationMode) {
        prefs.vibration = mode
        _state.update { it.copy(vibration = mode) }
    }

    fun setAdhanVoice(voice: AdhanVoice) {
        prefs.adhanVoice = voice
        _state.update { it.copy(adhanVoice = voice) }
    }

    fun openPrayerDetail(index: Int) {
        _state.update { it.copy(selectedPrayerIndex = index) }
    }

    fun closePrayerDetail() {
        _state.update { it.copy(selectedPrayerIndex = null) }
        rescheduleAll()
    }

    fun setPrayerOffset(index: Int, minutes: Int) {
        val pid = currentProfileId() ?: return
        prefs.setPrayerOffset(pid, index, minutes)
        _state.update { state ->
            state.copy(prayerRows = state.prayerRows.map { row ->
                if (row.index == index) row.copy(offset = minutes) else row
            })
        }
    }

    fun setPrayerEarlyReminder(index: Int, minutes: Int) {
        val pid = currentProfileId() ?: return
        prefs.setPrayerEarlyReminder(pid, index, minutes)
        _state.update { state ->
            state.copy(prayerRows = state.prayerRows.map { row ->
                if (row.index == index) row.copy(earlyReminder = minutes) else row
            })
        }
    }

    fun setAlertMode(index: Int, mode: AlertTimeMode) {
        val pid = currentProfileId() ?: return
        prefs.setAlertMode(pid, index, mode)
        _state.update { state ->
            state.copy(prayerRows = state.prayerRows.map { row ->
                if (row.index == index) row.copy(alertMode = mode) else row
            })
        }
    }

    fun setFixedTime(index: Int, minutesOfDay: Int) {
        val pid = currentProfileId() ?: return
        prefs.setFixedTimeMinutes(pid, index, minutesOfDay)
        prefs.setAlertMode(pid, index, AlertTimeMode.FIXED)
        _state.update { state ->
            state.copy(prayerRows = state.prayerRows.map { row ->
                if (row.index == index) row.copy(fixedTimeMinutes = minutesOfDay, alertMode = AlertTimeMode.FIXED) else row
            })
        }
        rescheduleAll()
    }

    fun openProfilePicker() {
        _state.update { it.copy(showProfilePicker = true) }
    }

    fun closeProfilePicker() {
        _state.update { it.copy(showProfilePicker = false) }
    }

    fun setNotificationProfile(profileId: Long) {
        prefs.notificationProfileId = profileId
        val profile = _state.value.profiles.firstOrNull { it.id == profileId }
        _state.update { it.copy(notificationProfile = profile, showProfilePicker = false) }
        loadPrayerTimes()
        rescheduleAll()
    }

    private fun currentProfileId(): Long? = _state.value.notificationProfile?.id

    private fun loadPrefs() {
        _state.update {
            it.copy(
                permissionGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                masterEnabled = prefs.masterEnabled,
                imsakEnabled = prefs.imsakEnabled,
                adhanVoice = prefs.adhanVoice,
                vibration = prefs.vibration,
            )
        }
    }

    private fun loadPrayerTimes() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = repo.observeAll().first()
            val notificationProfile = resolveNotificationProfile(prefs.notificationProfileId, profiles) ?: return@launch
            val date = LocalDate.now()
            val isRamadan = RamadanDetector.isRamadan(date)
            val times = AdhanWrapper().getPrayerTimes(
                latitude = notificationProfile.latitude,
                longitude = notificationProfile.longitude,
                date = date,
                timezone = notificationProfile.effectiveZoneId(),
                method = notificationProfile.calculationMethod,
            )
            val asr = if (notificationProfile.asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
            val prayerEntries = listOf(
                PRAYER_INDEX_FAJR to times.fajr,
                PRAYER_INDEX_DHUHR to times.dhuhr,
                PRAYER_INDEX_ASR to asr,
                PRAYER_INDEX_MAGHRIB to times.maghrib,
                PRAYER_INDEX_ISHA to times.isha,
            )
            val pid = notificationProfile.id
            _state.update { state ->
                state.copy(
                    isRamadan = isRamadan,
                    profiles = profiles,
                    notificationProfile = notificationProfile,
                    prayerRows = prayerEntries.map { (index, time) ->
                        val mode = prefs.getAlertMode(pid, index)
                        val fixedMins = prefs.getFixedTimeMinutes(pid, index)
                        val displayTime = if (mode == AlertTimeMode.FIXED && fixedMins >= 0) {
                            java.time.LocalTime.of(fixedMins / 60, fixedMins % 60).format(TIME_FMT)
                        } else {
                            time.plusMinutes(prefs.getPrayerOffset(pid, index).toLong()).format(TIME_FMT)
                        }
                        PrayerRowData(
                            index = index,
                            name = PRAYER_NAMES[index]!!,
                            time = displayTime,
                            enabled = prefs.isPrayerEnabled(pid, index),
                            offset = prefs.getPrayerOffset(pid, index),
                            earlyReminder = prefs.getPrayerEarlyReminder(pid, index),
                            alertMode = mode,
                            fixedTimeMinutes = fixedMins,
                        )
                    },
                )
            }
        }
    }

    private fun rescheduleAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = repo.observeAll().first()
            AlarmScheduler.scheduleAll(context, profiles)
        }
    }

    companion object {
        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotificationSettingsViewModel(
                        app.notificationPreferences,
                        app.profileRepository,
                        app,
                    ) as T
            }
    }
}
