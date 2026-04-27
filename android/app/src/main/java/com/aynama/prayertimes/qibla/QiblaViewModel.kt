package com.aynama.prayertimes.qibla

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.home.PrayerPhase
import com.aynama.prayertimes.home.derivePhase
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.QiblaCalculator
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class SensorAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE }

sealed interface QiblaUiState {
    data object Loading : QiblaUiState
    data object NoProfile : QiblaUiState
    data object NoSensor : QiblaUiState
    data class Ready(
        val unwrappedAzimuth: Float,
        val qiblaBearing: Float,
        val qiblaDegrees: Int,
        val distanceKm: Double,
        val accuracy: SensorAccuracy,
        val phase: PrayerPhase,
    ) : QiblaUiState
}

class QiblaViewModel(
    private val profileRepository: ProfileRepository,
    private val sensorManager: SensorManager,
) : ViewModel() {

    private val adhan = AdhanWrapper()
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var activeProfile: Profile? = null
    private var cachedTimes: Pair<LocalDate, com.aynama.prayertimes.shared.PrayerTimesResult>? = null
    private var qiblaBearing = 0f
    private var distanceKm = 0.0
    private var accuracy = SensorAccuracy.UNRELIABLE
    private var lastRawAzimuth = -1f
    private var unwrappedAzimuth = 0f

    private val _uiState = MutableStateFlow<QiblaUiState>(QiblaUiState.Loading)
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rotMat = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
            val remapped = FloatArray(9)
            SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(remapped, orientation)
            val azimuth = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
            postUpdate(azimuth)
        }

        override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
            accuracy = when (acc) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.HIGH
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.MEDIUM
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.LOW
                else -> SensorAccuracy.UNRELIABLE
            }
        }
    }

    init {
        if (sensor == null) {
            _uiState.value = QiblaUiState.NoSensor
        }

        profileRepository.observeAll()
            .map { profiles -> profiles.firstOrNull { it.isGps } ?: profiles.minByOrNull { it.sortOrder } }
            .onEach { profile ->
                activeProfile = profile
                if (profile == null) {
                    _uiState.value = QiblaUiState.NoProfile
                } else {
                    qiblaBearing = QiblaCalculator.bearingTo(profile.latitude, profile.longitude).toFloat()
                    distanceKm = QiblaCalculator.distanceKm(profile.latitude, profile.longitude)
                    cachedTimes = null
                    if (sensor == null) {
                        _uiState.value = QiblaUiState.NoSensor
                    } else if (_uiState.value is QiblaUiState.Loading) {
                        // Leave as Loading until first sensor event
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun start() {
        sensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
    }

    private fun postUpdate(rawAzimuth: Float) {
        val profile = activeProfile ?: return
        if (sensor == null) return

        if (lastRawAzimuth < 0f) {
            lastRawAzimuth = rawAzimuth
            unwrappedAzimuth = rawAzimuth
        } else {
            val delta = ((rawAzimuth - lastRawAzimuth + 540f) % 360f) - 180f
            unwrappedAzimuth += delta
            lastRawAzimuth = rawAzimuth
        }

        val now = LocalTime.now()
        val today = LocalDate.now()
        val times = cachedTimes?.takeIf { it.first == today }?.second
            ?: adhan.getPrayerTimes(
                latitude = profile.latitude,
                longitude = profile.longitude,
                date = today,
                timezone = ZoneId.systemDefault(),
                method = profile.calculationMethod,
            ).also { cachedTimes = today to it }
        val phase = derivePhase(times, profile.asrMadhab, now)
        _uiState.value = QiblaUiState.Ready(
            unwrappedAzimuth = unwrappedAzimuth,
            qiblaBearing = qiblaBearing,
            qiblaDegrees = qiblaBearing.toInt(),
            distanceKm = distanceKm,
            accuracy = accuracy,
            phase = phase,
        )
    }

    companion object {
        fun factory(app: AynamaApplication): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    QiblaViewModel(
                        app.profileRepository,
                        app.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager,
                    ) as T
            }
    }
}
