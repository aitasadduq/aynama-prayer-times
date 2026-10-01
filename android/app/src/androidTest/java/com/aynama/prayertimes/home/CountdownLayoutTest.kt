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
        val countdown = mutableStateOf("-00:00:00")
        compose.setContent {
            MaterialTheme(typography = AynamaTypography) {
                CountdownDigits(countdown.value, spokenLabel = "Countdown")
            }
        }

        fun width() = compose.onNodeWithContentDescription("Countdown")
            .getUnclippedBoundsInRoot().width

        val first = width()
        for (value in listOf("-11:11:11", "-33:33:33", "-88:88:88")) {
            compose.runOnIdle { countdown.value = value }
            assertEquals("The countdown shifted for $value", first, width())
        }
    }
}
