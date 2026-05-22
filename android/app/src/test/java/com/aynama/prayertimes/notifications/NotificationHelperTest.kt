package com.aynama.prayertimes.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun `shouldVibrate ALWAYS returns true for every voice`() {
        AdhanVoice.entries.forEach { voice ->
            assertTrue(
                "ALWAYS + $voice should vibrate",
                NotificationHelper.shouldVibrate(VibrationMode.ALWAYS, voice),
            )
        }
    }

    @Test
    fun `shouldVibrate NEVER returns false for every voice`() {
        AdhanVoice.entries.forEach { voice ->
            assertFalse(
                "NEVER + $voice should not vibrate",
                NotificationHelper.shouldVibrate(VibrationMode.NEVER, voice),
            )
        }
    }

    @Test
    fun `shouldVibrate WITH_SOUND returns false when voice is NONE`() {
        assertFalse(NotificationHelper.shouldVibrate(VibrationMode.WITH_SOUND, AdhanVoice.NONE))
    }

    @Test
    fun `shouldVibrate WITH_SOUND returns true for every audible voice`() {
        AdhanVoice.entries.filter { it != AdhanVoice.NONE }.forEach { voice ->
            assertTrue(
                "WITH_SOUND + $voice should vibrate",
                NotificationHelper.shouldVibrate(VibrationMode.WITH_SOUND, voice),
            )
        }
    }
}
