package com.aynama.prayertimes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.mutableStateOf
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.navigation.NavGraph
import com.aynama.prayertimes.ui.theme.AynamaTheme
import com.aynama.prayertimes.widgets.EXTRA_WIDGET_PROFILE_ID
import com.aynama.prayertimes.widgets.NO_WIDGET_PROFILE
import com.aynama.prayertimes.widgets.updateAllPrayerWidgets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var notifPermLauncher: ActivityResultLauncher<String>

    /**
     * The profile a widget tap asked for, until the home pager has shown it.
     *
     * Held as Compose state rather than read from the intent at composition time: the launch
     * intent sticks around for the life of the activity, so re-reading it would drag the user
     * back to the widget's profile every recomposition, and after a rotation or a return from
     * Settings.
     */
    private val requestedProfileId = mutableStateOf(NO_WIDGET_PROFILE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        notifPermLauncher = registerForActivityResult(RequestPermission()) { granted ->
            if (granted) requestBatteryOptExemptionOnce()
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestedProfileId.value = widgetProfileFrom(intent)

        setContent {
            AynamaTheme {
                NavGraph(
                    requestedProfileId = requestedProfileId.value,
                    onProfileShown = { requestedProfileId.value = NO_WIDGET_PROFILE },
                )
            }
        }
    }

    // The widget's PendingIntent is FLAG_ACTIVITY_SINGLE_TOP, so tapping a widget while the app
    // is already open arrives here rather than through onCreate. Without this, a second widget's
    // profile would never be shown.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedProfileId.value = widgetProfileFrom(intent)
    }

    private fun widgetProfileFrom(intent: Intent?): Long =
        intent?.getLongExtra(EXTRA_WIDGET_PROFILE_ID, NO_WIDGET_PROFILE) ?: NO_WIDGET_PROFILE

    override fun onResume() {
        super.onResume()
        val app = application as AynamaApplication
        app.appScope.launch {
            val profiles = app.profileRepository.observeAll().first()
            AlarmScheduler.scheduleAll(this@MainActivity, profiles)
            updateAllPrayerWidgets(this@MainActivity)
        }
    }

    private fun requestBatteryOptExemptionOnce() {
        val prefs = (application as AynamaApplication).prefs
        if (prefs.getBoolean(KEY_BATTERY_OPT_REQUESTED, false)) return
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            prefs.edit().putBoolean(KEY_BATTERY_OPT_REQUESTED, true).apply()
        }
    }

    companion object {
        private const val KEY_BATTERY_OPT_REQUESTED = "battery_opt_requested"
    }
}
