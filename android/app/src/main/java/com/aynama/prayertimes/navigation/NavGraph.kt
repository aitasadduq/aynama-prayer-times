package com.aynama.prayertimes.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aynama.prayertimes.R
import com.aynama.prayertimes.home.HomeScreen
import com.aynama.prayertimes.qibla.QiblaScreen

private enum class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    QIBLA("qibla", R.string.nav_qibla, Icons.Default.Explore),
    TRACKER("tracker", R.string.nav_tracker, Icons.Default.DateRange),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = minOf(density.fontScale, 1.3f),
                )
            ) {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = { Text(stringResource(screen.labelRes)) },
                            icon = { Icon(screen.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(onNavigateToSettings = {
                    navController.navigate(Screen.SETTINGS.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Screen.QIBLA.route) { QiblaScreen() }
            composable(Screen.TRACKER.route) { PlaceholderScreen("Tracker") }
            composable(Screen.SETTINGS.route) { PlaceholderScreen("Settings") }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
