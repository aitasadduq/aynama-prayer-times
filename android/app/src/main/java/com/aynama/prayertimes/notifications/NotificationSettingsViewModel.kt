package com.aynama.prayertimes.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
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
)

data class NotificationSettingsUiState(
    val permissionGranted: Boolean = true,
    val masterEnabled: Boolean = true,
    val prayerRows: List<PrayerRowData> = emptyList(),
    val imsakEnabled: Boolean = true,
    val adhanVoice: AdhanVoice = AdhanVoice.MAKKAH,
    val vibration: VibrationMode = VibrationMode.WITH_SOUND,
    val isRamadan: Boolean = false,
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
        prefs.setPrayerEnabled(index, enabled)
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
            val firstProfile = profiles.minByOrNull { it.sortOrder } ?: return@launch
            val date = LocalDate.now()
            val isRamadan = RamadanDetector.isRamadan(date)
            val times = AdhanWrapper().getPrayerTimes(
                latitude = firstProfile.latitude,
                longitude = firstProfile.longitude,
                date = date,
                timezone = ZoneId.systemDefault(),
                method = firstProfile.calculationMethod,
            )
            val asr = if (firstProfile.asrMadhab == AsrMadhab.HANAFI) times.asrHanafi else times.asrShafii
            val prayerEntries = listOf(
                PRAYER_INDEX_FAJR to times.fajr,
                PRAYER_INDEX_DHUHR to times.dhuhr,
                PRAYER_INDEX_ASR to asr,
                PRAYER_INDEX_MAGHRIB to times.maghrib,
                PRAYER_INDEX_ISHA to times.isha,
            )
            _state.update { state ->
                state.copy(
                    isRamadan = isRamadan,
                    prayerRows = prayerEntries.map { (index, time) ->
                        PrayerRowData(
                            index = index,
                            name = PRAYER_NAMES[index]!!,
                            time = time.format(TIME_FMT),
                            enabled = prefs.isPrayerEnabled(index),
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
