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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class SensorAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE }

sealed interface QiblaUiState {
    data object Loading : QiblaUiState
    data object NoProfile : QiblaUiState
    data object NoSensor : QiblaUiState
    data class Ready(
        val unwrappedAzimuth: Float,
        val azimuth: Float,
        val rawAzimuth: Float,
        val pitch: Float,
        val roll: Float,
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

    // Sensor state
    private var lastRawAzimuth = -1f    // raw, for profile-change replay
    private var lastSmoothed = -1f      // last smoothed value, for unwrap delta
    private var unwrappedAzimuth = 0f

    // Low-pass filter state (sin/cos space to avoid wrap-around errors)
    private var smoothedSin = 0f
    private var smoothedCos = 1f
    private val LP_ALPHA = 0.15f

    private val _uiState = MutableStateFlow<QiblaUiState>(QiblaUiState.Loading)
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rotMat = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
            // No remapping: use rotation matrix directly for flat-phone (face-up) orientation.
            // AXIS_X/AXIS_Z remap is for portrait-vertical use and causes singularity when flat.
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMat, orientation)
            val raw = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            postUpdate(raw, pitch, roll)
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
        android.util.Log.d("Qibla", "VM init hash=${System.identityHashCode(this)}")
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
                    } else if (lastRawAzimuth >= 0f) {
                        postUpdate(lastRawAzimuth, 0f, 0f)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun start() {
        sensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onCleared() {
        sensorManager.unregisterListener(sensorListener)
    }

    private fun applyLowPass(raw: Float): Float {
        val rad = Math.toRadians(raw.toDouble())
        val s = sin(rad).toFloat()
        val c = cos(rad).toFloat()
        if (lastSmoothed < 0f) {
            smoothedSin = s
            smoothedCos = c
        } else {
            smoothedSin = LP_ALPHA * s + (1f - LP_ALPHA) * smoothedSin
            smoothedCos = LP_ALPHA * c + (1f - LP_ALPHA) * smoothedCos
        }
        return ((Math.toDegrees(atan2(smoothedSin.toDouble(), smoothedCos.toDouble())).toFloat() + 360f) % 360f)
    }

    private fun postUpdate(rawAzimuth: Float, pitch: Float, roll: Float) {
        android.util.Log.d("Qibla", "postUpdate raw=$rawAzimuth profile=${activeProfile != null}")
        lastRawAzimuth = rawAzimuth
        val smoothed = applyLowPass(rawAzimuth)

        if (lastSmoothed < 0f) {
            lastSmoothed = smoothed
            unwrappedAzimuth = smoothed
        } else {
            val delta = ((smoothed - lastSmoothed + 540f) % 360f) - 180f
            unwrappedAzimuth += delta
            lastSmoothed = smoothed
        }

        val profile = activeProfile ?: return
        if (sensor == null) return

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
            azimuth = smoothed,
            rawAzimuth = rawAzimuth,
            pitch = pitch,
            roll = roll,
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
