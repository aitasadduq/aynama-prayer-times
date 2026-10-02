package com.aynama.prayertimes.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AynamaWearTheme { WearHomeScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catch up on anything published while the watch app was not running. Change events
        // only reach a listener that existed when the change happened.
        lifecycleScope.launch { WearProfileSync.pullFromPhone(applicationContext) }
    }
}
