package com.aynama.prayertimes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aynama.prayertimes.navigation.NavGraph
import com.aynama.prayertimes.ui.theme.AynamaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AynamaTheme {
                NavGraph()
            }
        }
    }
}
