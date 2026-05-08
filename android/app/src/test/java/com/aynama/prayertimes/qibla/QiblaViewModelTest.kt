package com.aynama.prayertimes.qibla

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aynama.prayertimes.shared.AdhanWrapper
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.PrayerTimesResult
import com.aynama.prayertimes.shared.SensorAccuracy
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.shared.data.repository.ProfileRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class QiblaViewModelTest {

    private val sensorManager = mockk<SensorManager>(relaxed = true)
    private val sensor = mockk<Sensor>(relaxed = true)
    private val adhan = mockk<AdhanWrapper>()
    private val repo = mockk<ProfileRepository>()
    private val profileFlow = MutableStateFlow<List<Profile>>(emptyList())

    // 2026-05-08 noon UTC — fixed for deterministic LocalDate.now(clock).
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-08T12:00:00Z"), ZoneId.of("UTC"))

    private val fakeTimes = PrayerTimesResult(
        fajr = LocalTime.of(5, 12),
        sunrise = LocalTime.of(6, 30),
        dhuhr = LocalTime.of(12, 30),
        asrShafii = LocalTime.of(15, 50),
        asrHanafi = LocalTime.of(16, 50),
        maghrib = LocalTime.of(18, 30),
        isha = LocalTime.of(19, 45),
    )

    @Before
    fun setUp() {
        // Default to Unconfined for the simple state-transition tests; cancellation
        // and single-flight tests override to StandardTestDispatcher inline.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        every { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns sensor
        every { sensorManager.registerListener(any(), any<Sensor>(), any<Int>()) } returns true
        every { repo.observeAll() } returns profileFlow

        // Mock the static SensorManager helpers used inside onSensorChanged so we don't
        // have to construct real SensorEvents (its constructor is package-private).
        mockkStatic(SensorManager::class)
        every { SensorManager.getRotationMatrixFromVector(any(), any()) } answers {
            val rotMat = firstArg<FloatArray>()
            rotMat.fill(0f)
            // Identity rotation: device pointing north, flat.
            rotMat[0] = 1f; rotMat[4] = 1f; rotMat[8] = 1f
            true
        }
        every { SensorManager.getOrientation(any(), any()) } answers {
            val orientation = secondArg<FloatArray>()
            orientation[0] = 0f // azimuth (radians)
            orientation[1] = 0f
            orientation[2] = 0f
            orientation
        }

        // Mock GeomagneticField construction to avoid the Android-SDK stub crash.
        mockkConstructor(GeomagneticField::class)
        every { anyConstructed<GeomagneticField>().declination } returns 0f
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun makeProfile(
        id: Long,
        latitude: Double = 51.5074,
        longitude: Double = -0.1278,
    ) = Profile(
        id = id,
        name = "Profile$id",
        latitude = latitude,
        longitude = longitude,
        calculationMethod = CalculationMethodKey.ISNA,
        asrMadhab = AsrMadhab.SHAFII,
        isGps = false,
        sortOrder = 0,
    )

    private fun newVm(computeDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
        QiblaViewModel(
            profileRepository = repo,
            sensorManager = sensorManager,
            adhan = adhan,
            clock = fixedClock,
            computeDispatcher = computeDispatcher,
        )

    private fun captureSensorListener(): CapturingSlot<SensorEventListener> {
        val captured = slot<SensorEventListener>()
        every {
            sensorManager.registerListener(capture(captured), any<Sensor>(), any<Int>())
        } returns true
        return captured
    }

    private fun fakeSensorEvent(): SensorEvent = mockk(relaxed = true)

    // ---------- State transitions ----------

    @Test
    fun `no rotation-vector sensor sets NoSensor state`() = runTest {
        // Override the specific stub registered in setUp (not via any()).
        every { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } returns null
        val vm = newVm()
        // Load a profile so the observer runs the else branch where the sensor null
        // check sets NoSensor. (When there are zero profiles AND no sensor, the
        // observer's null-profile branch sets NoProfile, which is also a valid UI
        // outcome but not what we're verifying here.)
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()
        assert(vm.uiState.value == QiblaUiState.NoSensor) {
            "expected NoSensor, got ${vm.uiState.value}"
        }
    }

    @Test
    fun `no profiles in repository sets NoProfile state`() = runTest {
        val vm = newVm()
        profileFlow.value = emptyList()
        advanceUntilIdle()
        assert(vm.uiState.value == QiblaUiState.NoProfile) {
            "expected NoProfile, got ${vm.uiState.value}"
        }
    }

    @Test
    fun `profile loaded plus sensor event produces Ready state with correct bearing`() = runTest {
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1, latitude = 51.5074, longitude = -0.1278))
        advanceUntilIdle()

        vm.start()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        val state = vm.uiState.value
        assert(state is QiblaUiState.Ready) { "expected Ready, got $state" }
        val ready = state as QiblaUiState.Ready
        // London → Mecca great-circle bearing is ~119°
        assert(ready.qiblaBearing in 117f..121f) {
            "expected qiblaBearing ~119° for London, got ${ready.qiblaBearing}"
        }
        // London → Mecca distance is ~4800 km
        assert(ready.distanceKm in 4500.0..5100.0) {
            "expected distance ~4800km, got ${ready.distanceKm}"
        }
    }

    // ---------- C7: Initial accuracy = HIGH (not UNRELIABLE) ----------

    @Test
    fun `initial accuracy is HIGH not UNRELIABLE`() = runTest {
        // OEM stacks (Samsung, Huawei) sometimes never emit an initial onAccuracyChanged.
        // Defaulting UNRELIABLE would pin the calibration banner forever. We default HIGH.
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        val ready = vm.uiState.value as QiblaUiState.Ready
        assert(ready.accuracy == SensorAccuracy.HIGH) {
            "expected HIGH accuracy by default, got ${ready.accuracy}"
        }
    }

    @Test
    fun `onAccuracyChanged updates accuracy`() = runTest {
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        captured.captured.onAccuracyChanged(sensor, SensorAccuracy.ANDROID_LOW)
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        val ready = vm.uiState.value as QiblaUiState.Ready
        assert(ready.accuracy == SensorAccuracy.LOW) {
            "expected LOW accuracy after onAccuracyChanged, got ${ready.accuracy}"
        }
    }

    // ---------- C5: GeomagneticField crash fallback ----------

    @Test
    fun `GeomagneticField IllegalArgumentException falls back to zero declination`() = runTest {
        // Stale Android images (WMM2020 expired 2025) throw on current dates.
        // Verify we don't crash — the ViewModel keeps loading.
        every { anyConstructed<GeomagneticField>().declination } throws
            IllegalArgumentException("WMM model expired")
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        assert(vm.uiState.value is QiblaUiState.Ready) {
            "expected Ready despite Geo crash, got ${vm.uiState.value}"
        }
    }

    // ---------- C3: Single-flight prayer-times fetch ----------

    @Test
    fun `repeated sensor events with cache hit do not refetch prayer times`() = runTest {
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        // First sensor event populates cache + fetches.
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()
        // 99 more events should hit the cache.
        repeat(99) {
            captured.captured.onSensorChanged(fakeSensorEvent())
        }
        advanceUntilIdle()

        verify(exactly = 1) {
            adhan.getPrayerTimes(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `concurrent in-flight events do not spawn duplicate fetches`() = runTest {
        // Use StandardTestDispatcher so launched coroutines QUEUE (don't run eagerly).
        // Then fire many sensor events back-to-back: the first launch is queued, and
        // subsequent postUpdate calls see timesJob.isActive==true and short-circuit.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm(computeDispatcher = StandardTestDispatcher(testScheduler))
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()
        vm.start()

        // Fire 50 events while no work has run yet.
        repeat(50) { captured.captured.onSensorChanged(fakeSensorEvent()) }
        advanceUntilIdle()

        verify(exactly = 1) {
            adhan.getPrayerTimes(any(), any(), any(), any(), any())
        }
    }

    // ---------- C1: Stale coroutine cancellation on profile change ----------

    @Test
    fun `profile change clears cache so next fetch targets new profile`() = runTest {
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        val profileA = makeProfile(id = 1, latitude = 51.5074, longitude = -0.1278) // London
        val profileB = makeProfile(id = 2, latitude = 40.7128, longitude = -74.0060) // NYC

        profileFlow.value = listOf(profileA)
        advanceUntilIdle()
        vm.start()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        // First fetch was for profile A.
        verify(exactly = 1) {
            adhan.getPrayerTimes(latitude = 51.5074, any(), any(), any(), any())
        }

        // Switch profile → cache should clear, next sensor event should refetch.
        profileFlow.value = listOf(profileB)
        advanceUntilIdle()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        verify(exactly = 1) {
            adhan.getPrayerTimes(latitude = 40.7128, any(), any(), any(), any())
        }

        // Ready state reflects profile B's bearing.
        val ready = vm.uiState.value as QiblaUiState.Ready
        // NYC → Mecca great-circle bearing is ~58°
        assert(ready.qiblaBearing in 56f..60f) {
            "expected qiblaBearing ~58° for NYC, got ${ready.qiblaBearing}"
        }
    }

    @Test
    fun `profile change recomputes qibla bearing immediately`() = runTest {
        // The profile observer recomputes qiblaBearing/distanceKm/magneticDeclination
        // synchronously when a new profile is emitted, before any sensor event. Verify
        // the next sensor event after profile change emits state with the NEW bearing.
        every { adhan.getPrayerTimes(any(), any(), any(), any(), any()) } returns fakeTimes
        val captured = captureSensorListener()

        val vm = newVm()
        val profileA = makeProfile(id = 1, latitude = 51.5074, longitude = -0.1278) // London
        val profileB = makeProfile(id = 2, latitude = 40.7128, longitude = -74.0060) // NYC

        profileFlow.value = listOf(profileA)
        advanceUntilIdle()
        vm.start()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        // After profile A, bearing is ~119° (London → Mecca)
        val readyA = vm.uiState.value as QiblaUiState.Ready
        assert(readyA.qiblaBearing in 117f..121f)

        // Change profile, fire next sensor event
        profileFlow.value = listOf(profileB)
        advanceUntilIdle()
        captured.captured.onSensorChanged(fakeSensorEvent())
        advanceUntilIdle()

        // Bearing flipped to NYC's value immediately on the very next emission.
        val readyB = vm.uiState.value as QiblaUiState.Ready
        assert(readyB.qiblaBearing in 56f..60f) {
            "expected qiblaBearing for NYC ~58°, got ${readyB.qiblaBearing}"
        }
        // And the distance updates too (NYC is much further from Mecca than London).
        assert(readyB.distanceKm in 10000.0..10600.0) {
            "expected distance for NYC ~10300km, got ${readyB.distanceKm}"
        }
    }

    // ---------- Lifecycle ----------

    @Test
    fun `start registers sensor listener and stop unregisters it`() = runTest {
        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        verify(exactly = 1) {
            sensorManager.registerListener(any<SensorEventListener>(), sensor, SensorManager.SENSOR_DELAY_UI)
        }

        vm.stop()
        verify(atLeast = 1) {
            sensorManager.unregisterListener(any<SensorEventListener>())
        }
    }

    @Test
    fun `start with sensor that fails to register sets NoSensor state`() = runTest {
        every { sensorManager.registerListener(any(), any<Sensor>(), any<Int>()) } returns false
        val vm = newVm()
        profileFlow.value = listOf(makeProfile(id = 1))
        advanceUntilIdle()

        vm.start()
        assert(vm.uiState.value == QiblaUiState.NoSensor) {
            "expected NoSensor when registerListener returns false, got ${vm.uiState.value}"
        }
    }
}
