package com.aynama.prayertimes.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aynama.prayertimes.ui.theme.AynamaTypography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountdownLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingDigitsKeepsTheFrauncesCountdownAtTheSameWidth() {
        val countdown = mutableStateOf("-1h 10m")
        compose.setContent {
            MaterialTheme(typography = AynamaTypography) {
                CountdownDigits(countdown.value, spokenLabel = "Countdown")
            }
        }

        fun width() = compose.onNodeWithContentDescription("Countdown")
            .getUnclippedBoundsInRoot().let { it.right - it.left }

        val first = width()
        for (value in listOf("-3h 33m", "-8h 88m")) {
            compose.runOnIdle { countdown.value = value }
            assertEquals("The countdown shifted for $value", first, width())
        }

        compose.runOnIdle { countdown.value = "-10m 25s" }
        val minutesWidth = width()
        compose.runOnIdle { countdown.value = "-88m 88s" }
        assertEquals("Minute and second digits shifted", minutesWidth, width())
    }
}
