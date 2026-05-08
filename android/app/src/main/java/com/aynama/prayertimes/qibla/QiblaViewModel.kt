package com.aynama.prayertimes.qibla

import android.hardware.GeomagneticField
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
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val rotMat = FloatArray(9)
    private val orientation = FloatArray(3)

    private var activeProfile: Profile? = null
    private var cachedTimes: Pair<LocalDate, com.aynama.prayertimes.shared.PrayerTimesResult>? = null
    private var qiblaBearing = 0f
    private var distanceKm = 0.0
    private var accuracy = SensorAccuracy.UNRELIABLE
    // Magnetic declination converts sensor magnetic-north azimuth to true-north
    private var magneticDeclination = 0f

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
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
            // Flat-phone (face-up) is the intended use posture for this compass.
            // AXIS_X/AXIS_Z remap improves portrait-vertical stability but causes singularity when flat.
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
                    magneticDeclination = GeomagneticField(
                        profile.latitude.toFloat(),
                        profile.longitude.toFloat(),
                        0f,
                        System.currentTimeMillis(),
                    ).declination
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
        sensor?.let {
            val ok = sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
            if (!ok) _uiState.value = QiblaUiState.NoSensor
        }
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
        lastRawAzimuth = rawAzimuth
        val trueAzimuth = ((rawAzimuth + magneticDeclination) % 360f + 360f) % 360f
        val smoothed = applyLowPass(trueAzimuth)

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

        val snapshotUnwrapped = unwrappedAzimuth
        val today = LocalDate.now()
        val cached = cachedTimes?.takeIf { it.first == today }?.second

        if (cached != null) {
            emitReady(snapshotUnwrapped, smoothed, rawAzimuth, pitch, roll, cached, profile)
        } else {
            viewModelScope.launch {
                val times = withContext(Dispatchers.Default) {
                    adhan.getPrayerTimes(
                        latitude = profile.latitude,
                        longitude = profile.longitude,
                        date = today,
                        timezone = ZoneId.systemDefault(),
                        method = profile.calculationMethod,
                    )
                }
                cachedTimes = today to times
                emitReady(snapshotUnwrapped, smoothed, rawAzimuth, pitch, roll, times, profile)
            }
        }
    }

    private fun emitReady(
        unwrapped: Float,
        smoothed: Float,
        rawAzimuth: Float,
        pitch: Float,
        roll: Float,
        times: com.aynama.prayertimes.shared.PrayerTimesResult,
        profile: Profile,
    ) {
        val phase = derivePhase(times, profile.asrMadhab, LocalTime.now())
        _uiState.value = QiblaUiState.Ready(
            unwrappedAzimuth = unwrapped,
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
