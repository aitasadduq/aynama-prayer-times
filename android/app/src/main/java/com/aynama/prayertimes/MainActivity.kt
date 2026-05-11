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
import com.aynama.prayertimes.notifications.AlarmScheduler
import com.aynama.prayertimes.navigation.NavGraph
import com.aynama.prayertimes.ui.theme.AynamaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var notifPermLauncher: ActivityResultLauncher<String>

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

        setContent {
            AynamaTheme {
                NavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val app = application as AynamaApplication
        app.appScope.launch {
            val profiles = app.profileRepository.observeAll().first()
            AlarmScheduler.scheduleAll(this@MainActivity, profiles)
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
