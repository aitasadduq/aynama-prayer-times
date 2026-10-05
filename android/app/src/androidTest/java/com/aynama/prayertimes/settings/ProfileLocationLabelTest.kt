package com.aynama.prayertimes.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aynama.prayertimes.shared.CalculationMethodKey
import com.aynama.prayertimes.shared.data.entity.AsrMadhab
import com.aynama.prayertimes.shared.data.entity.Profile
import com.aynama.prayertimes.ui.theme.AynamaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLocationLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun cachedCityAndCountryAppearInTheFormAndSettingsRow() {
        val initial = Profile(name = "Home", latitude = 21.3871, longitude = 39.8688,
            calculationMethod = CalculationMethodKey.UMM_AL_QURA, asrMadhab = AsrMadhab.SHAFII,
            isGps = false, sortOrder = 0, timezone = "Asia/Riyadh", locationName = "Makkah, Saudi Arabia")
        compose.setContent {
            var saved by remember { mutableStateOf<Profile?>(null) }
            AynamaTheme {
                if (saved == null) {
                    ProfileFormSheet(initial = initial, onSave = {
                        assertEquals("Makkah, Saudi Arabia", it.locationName)
                        saved = it
                    }, onDelete = {}, onDismiss = {})
                } else {
                    ProfileRow(saved!!) {}
                }
            }
        }
        compose.onNodeWithText("Makkah, Saudi Arabia").assertIsDisplayed()
        compose.onNodeWithText("21.3871, 39.8688").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Makkah, Saudi Arabia · Umm al-Qurā").assertIsDisplayed()
        compose.onNodeWithText("21.3871, 39.8688 · Umm al-Qurā").assertDoesNotExist()
    }
}
