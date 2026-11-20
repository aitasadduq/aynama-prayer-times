package com.aynama.prayertimes.home

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.AynamaApplication
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.AynamaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Prayers screen's FAB flow: open the sheet, save a new profile, land on its page.
 *
 * The location comes from a test provider rather than the geocoder, so the flow runs offline.
 * "Use current location" reads the last known fix, which the test provider supplies.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AddProfileFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val app = context as AynamaApplication
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val name = "FAB test ${System.currentTimeMillis()}"
    private val seededIds = mutableListOf<Long>()

    @Before
    fun setUp() {
        val pkg = context.packageName
        shell("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")
        shell("appops set $pkg android:mock_location allow")
        shell("cmd location set-location-enabled true")
        PROVIDERS.forEach(::installTestProvider)
        runBlocking {
            // The FAB lives on the pager, which only exists once there is a profile.
            if (app.profileRepository.observeAll().first().isEmpty()) {
                seededIds += app.profileRepository.insert(SEED)
            }
        }
    }

    @After
    fun tearDown() {
        PROVIDERS.forEach { runCatching { locationManager.removeTestProvider(it) } }
        shell("appops set ${context.packageName} android:mock_location default")
        runBlocking {
            val repository = app.profileRepository
            repository.observeAll().first()
                .filter { it.name == name || it.id in seededIds }
                .forEach {
                    AlarmScheduler.cancelForProfile(context, it.id)
                    repository.delete(it)
                }
        }
    }

    @Test
    fun savingANewProfileLandsThePagerOnIt() {
        compose.setContent { AynamaTheme { HomeScreen() } }
        compose.waitUntilAtLeastOneExists(hasContentDescription("Add profile"), TIMEOUT_MS)

        compose.onNodeWithContentDescription("Add profile").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        compose.onNodeWithText("Save").assertIsNotEnabled()
        publishAndAwaitTestLocation()
        compose.onNodeWithText("Use current location").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Save") and isEnabled(), TIMEOUT_MS)
        compose.onNodeWithText("21.4225, 39.8262").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()

        // The pager composes only the page on screen, so finding the new page means landing on it.
        compose.waitUntilAtLeastOneExists(hasContentDescription(": $name", substring = true), TIMEOUT_MS)
        val count = runBlocking { app.profileRepository.observeAll().first().size }
        compose.onNode(hasContentDescription("Profile page $count of $count: $name", substring = true))
            .assertIsDisplayed()
    }

    @Suppress("DEPRECATION")
    private fun installTestProvider(provider: String) {
        runCatching { locationManager.removeTestProvider(provider) }
        locationManager.addTestProvider(
            provider, false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_COARSE,
        )
        locationManager.setTestProviderEnabled(provider, true)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun publishAndAwaitTestLocation() {
        // Provider installation and location delivery are separate. Publish after the activity
        // is foregrounded, and wait until the same last-known API used by the sheet can read it.
        // A one-off fix in @Before can otherwise leave Save disabled on API 31.
        compose.waitUntil("The mock location is available to the profile sheet", TIMEOUT_MS) {
            PROVIDERS.forEach(::publishTestLocation)
            // The sheet prefers the network provider; don't accept readiness of GPS alone.
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { fix ->
                    fix.isFromMockProvider &&
                        SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos < FIX_MAX_AGE_NANOS
                } == true
        }
    }

    private fun publishTestLocation(provider: String) {
        locationManager.setTestProviderLocation(
            provider,
            Location(provider).apply {
                latitude = 21.4225
                longitude = 39.8262
                accuracy = 50f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val FIX_MAX_AGE_NANOS = 30L * 1_000_000_000
        val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        val SEED = Profile(
            name = "Seed",
            latitude = 51.5074,
            longitude = -0.1278,
            calculationMethod = CalculationMethodKey.MWL,
            asrMadhab = AsrMadhab.SHAFII,
            isGps = false,
            sortOrder = 0,
            timezone = "Europe/London",
            useLocationTimezone = true,
        )
    }
}
