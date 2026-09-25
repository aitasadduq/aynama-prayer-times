package com.aynama.prayertimes.qibla

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the Qibla screen asks for on first open.
 *
 * Fine and coarse must go in one request: that is what shows Android 12+'s Precise/Approximate
 * choice. A fine-only request is logged as an error there, and some Android 12 releases drop it
 * without showing a dialog, leaving the compass on the profile's saved city.
 *
 * Requests are caught at the [ActivityResultRegistry], so no system dialog opens, and the
 * device's real grants are replaced by a context that reports only [granted][showQibla].
 */
@RunWith(AndroidJUnit4::class)
class QiblaLocationPermissionTest {

    @get:Rule
    val compose = createComposeRule()

    private val requests = mutableListOf<Any?>()

    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                requests += input
            }
        }
    }

    @Test
    fun asksForFineAndCoarseInOneRequest() {
        showQibla(granted = emptySet())

        assertEquals(listOf(setOf(FINE, COARSE)), requests.map { (it as? Array<*>)?.toSet() })
    }

    @Test
    fun approximateAloneIsEnough() {
        showQibla(granted = setOf(COARSE))

        assertEquals(emptyList<Any?>(), requests)
    }

    private fun showQibla(granted: Set<String>) {
        compose.setContent {
            val base = LocalContext.current
            val context = remember {
                object : ContextWrapper(base) {
                    override fun checkPermission(permission: String, pid: Int, uid: Int) =
                        if (permission in granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                }
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides registryOwner,
            ) {
                QiblaScreen()
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
        const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
