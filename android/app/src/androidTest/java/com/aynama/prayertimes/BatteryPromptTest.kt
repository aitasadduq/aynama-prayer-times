package com.aynama.prayertimes

import android.accessibilityservice.AccessibilityService
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The battery-optimisation exemption is asked for even when there is no notification
 * permission result to wait for.
 *
 * It used to be requested only from that result, which Android 8–12 never produce and which
 * Android 13+ skip once notifications are allowed — so most devices never saw the prompt.
 */
@RunWith(AndroidJUnit4::class)
class BatteryPromptTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as AynamaApplication
    private var savedFlag = false

    @Before
    fun setUp() {
        savedFlag = app.prefs.getBoolean(MainActivity.KEY_BATTERY_OPT_REQUESTED, false)
        app.prefs.edit().remove(MainActivity.KEY_BATTERY_OPT_REQUESTED).commit()
    }

    @After
    fun tearDown() {
        app.prefs.edit().putBoolean(MainActivity.KEY_BATTERY_OPT_REQUESTED, savedFlag).commit()
    }

    @Test
    fun theBatteryPromptIsRequestedWhenNotificationsAreAlreadyAllowed() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(
                app.packageName, android.Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        assumeFalse(
            "already exempt: the app never shows the prompt",
            app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(
                "the battery prompt was never requested",
                app.prefs.getBoolean(MainActivity.KEY_BATTERY_OPT_REQUESTED, false),
            )
            val automation = instrumentation.uiAutomation
            automation.waitForIdle(500, 10_000)
            // Global Back is asynchronous. Keep this Activity alive until the system
            // prompt has closed, so the action cannot dismiss the next test's Activity.
            automation.executeAndWaitForEvent(
                {
                    assertTrue(
                        "the battery prompt could not be dismissed",
                        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                    )
                },
                { event ->
                    (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                        event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) &&
                        automation.rootInActiveWindow?.packageName?.toString() == app.packageName
                },
                10_000,
            ).recycle()
        }
    }
}
