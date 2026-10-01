package com.aynama.prayertimes.home

import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aynama.prayertimes.R
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class CountdownFontTest {
    @Test
    fun frauncesCountdownDigitsHaveEqualAdvance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val face = requireNotNull(ResourcesCompat.getFont(context, R.font.aynama_countdown))
        val paint = Paint().apply {
            typeface = face
            textSize = 72f
        }
        val zeroWidth = paint.measureText("0")
        for (digit in '1'..'9') {
            assertEquals("$digit should occupy the same width as 0", zeroWidth, paint.measureText("$digit"), 0.01f)
        }
        assertEquals(paint.measureText("-00:00:00"), paint.measureText("-11:11:11"), 0.01f)
    }
}
