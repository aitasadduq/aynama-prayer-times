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
import com.aynama.prayertimes.shared.QiblaSensorState
import com.aynama.prayertimes.shared.SensorAccuracy
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
    private val adhan: AdhanWrapper = AdhanWrapper(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotMat = FloatArray(9)
    private val orientation = FloatArray(3)

    // Cross-thread fields: written on Main (profile observer / coroutine), read on the
    // sensor binder thread. @Volatile gives JMM visibility on ARM weak memory models.
    @Volatile private var activeProfile: Profile? = null
    @Volatile private var cachedTimes: Pair<LocalDate, com.aynama.prayertimes.shared.PrayerTimesResult>? = null
    @Volatile private var qiblaBearing = 0f
    @Volatile private var distanceKm = 0.0
    // ROTATION_VECTOR is a fused virtual sensor; some OEM stacks (Samsung, Huawei) never
    // emit an initial onAccuracyChanged, so default UNRELIABLE would pin the calibration
    // banner forever. Default HIGH and let onAccuracyChanged drop us if real calibration
    // is needed.
    @Volatile private var accuracy = SensorAccuracy.HIGH
    // Magnetic declination converts sensor magnetic-north azimuth to true-north.
    @Volatile private var magneticDeclination = 0f

    // Single-flight prayer-times job. Cancelled on profile change so a stale coroutine
    // can't write the old profile's times into the new profile's cache slot.
    private var timesJob: Job? = null

    // LP filter + unwrap state. Single-threaded (sensor thread only).
    private val sensorState = QiblaSensorState()

    private val _uiState = MutableStateFlow<QiblaUiState>(QiblaUiState.Loading)
    val uiState: StateFlow<QiblaUiState> = _uiState.asStateFlow()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotMat, event.values)
            // Intentionally NOT calling remapCoordinateSystem(AXIS_X, AXIS_Z) here — it
            // would improve vertical-portrait stability but introduces a singularity in
            // the flat-phone (face-up) posture this compass targets.
            SensorManager.getOrientation(rotMat, orientation)
            val raw = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
            val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            postUpdate(raw, pitch, roll)
        }

        override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
            accuracy = SensorAccuracy.fromAndroid(acc)
        }
    }

    init {
        if (sensor == null) {
            _uiState.value = QiblaUiState.NoSensor
        }

        profileRepository.observeAll()
            .map { profiles -> profiles.firstOrNull { it.isGps } ?: profiles.minByOrNull { it.sortOrder } }
            .onEach { profile ->
                // Cancel any in-flight prayer-times fetch tied to the previous profile so
                // it can't race the new profile's cache slot.
                timesJob?.cancel()
                timesJob = null
                cachedTimes = null
                activeProfile = profile
                if (profile == null) {
                    _uiState.value = QiblaUiState.NoProfile
                } else {
                    qiblaBearing = QiblaCalculator.bearingTo(profile.latitude, profile.longitude).toFloat()
                    distanceKm = QiblaCalculator.distanceKm(profile.latitude, profile.longitude)
                    // GeomagneticField throws when the bundled WMM model is past expiry
                    // (e.g., WMM2020 expired 2025 on stale Android images). Falling back
                    // to 0° declination is wrong by up to ~25° at extreme latitudes but
                    // beats crashing the screen.
                    magneticDeclination = try {
                        GeomagneticField(
                            profile.latitude.toFloat(),
                            profile.longitude.toFloat(),
                            0f,
                            System.currentTimeMillis(),
                        ).declination
                    } catch (_: IllegalArgumentException) {
                        0f
                    }
                    if (sensor == null) {
                        _uiState.value = QiblaUiState.NoSensor
                    }
                    // Don't replay sensor state from Main — the next sensor frame
                    // (~50ms at SENSOR_DELAY_UI) will emit fresh state with the new
                    // qibla bearing. Replaying from a different thread races the
                    // sensor thread's mutations of the LP filter and unwrap counter.
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

    private fun postUpdate(rawAzimuth: Float, pitch: Float, roll: Float) {
        val smoothed = sensorState.update(rawAzimuth, magneticDeclination)
        val snapshotUnwrapped = sensorState.unwrapped

        val profile = activeProfile ?: return
        if (sensor == null) return

        val today = LocalDate.now(clock)
        val cached = cachedTimes?.takeIf { it.first == today }?.second

        if (cached != null) {
            emitReady(snapshotUnwrapped, smoothed, rawAzimuth, pitch, roll, cached, profile)
            return
        }

        // Single-flight: skip if a fetch is already in progress. Prevents a coroutine
        // flood at midnight rollover when every sensor frame would otherwise launch a
        // duplicate Adhan computation until the first one populates the cache.
        if (timesJob?.isActive == true) return

        val capturedProfileId = profile.id
        timesJob = viewModelScope.launch {
            val times = withContext(computeDispatcher) {
                adhan.getPrayerTimes(
                    latitude = profile.latitude,
                    longitude = profile.longitude,
                    date = today,
                    timezone = ZoneId.systemDefault(),
                    method = profile.calculationMethod,
                )
            }
            // Profile may have changed while we were computing. Only write the cache if
            // we're still on the same profile.
            if (activeProfile?.id == capturedProfileId) {
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
        val phase = derivePhase(times, profile.asrMadhab, LocalTime.now(clock))
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
