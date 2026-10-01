package com.aynama.prayertimes.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aynama.prayertimes.ui.theme.AynamaTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun completeCountdownFitsNarrowScreensAtLargeFontScalesAndKeepsLtrOrder() {
        val fontScale = mutableStateOf(1f)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val countdown = mutableStateOf("-10m 25s")
        val layouts = mutableMapOf<Int, TextLayoutResult>()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale.value),
                LocalLayoutDirection provides direction.value,
            ) {
                MaterialTheme(typography = AynamaTypography) {
                    // 320dp phone minus Home's two 24dp margins.
                    Box(Modifier.width(272.dp).testTag("viewport")) {
                        CountdownDigits(countdown.value, spokenLabel = "Countdown") { index, result ->
                            layouts[index] = result
                        }
                    }
                }
            }
        }

        for (scale in listOf(1f, 1.5f, 2f)) {
            for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                for (text in listOf("-10m 25s", "-1h 10m", "10s")) {
                    compose.runOnIdle {
                        fontScale.value = scale
                        direction.value = layout
                        countdown.value = text
                    }
                    val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
                    var previousRight = viewport.left
                    text.indices.forEach { index ->
                        val glyph = compose.onNodeWithTag("countdown-glyph-$index", useUnmergedTree = true)
                            .getUnclippedBoundsInRoot()
                        val description = "$text, fontScale=$scale, $layout, glyph=$index"
                        assertTrue("Clipped or reversed: $description", glyph.left >= previousRight - 0.5.dp)
                        assertTrue("Offscreen: $description", glyph.right <= viewport.right + 0.5.dp)
                        assertTrue("Missing glyph: $description", glyph.width > 0.dp)
                        compose.runOnIdle {
                            assertFalse("Partially clipped: $description", layouts.getValue(index).hasVisualOverflow)
                        }
                        previousRight = glyph.right
                    }
                }
            }
        }
    }
}
