package com.aynama.prayertimes.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection

/** Fraunces glyphs with genuinely fixed digit positions on Android's pixel-snapped renderer. */
@Composable
internal fun CountdownDigits(
    text: String,
    spokenLabel: String,
    onGlyphLayout: (Int, TextLayoutResult) -> Unit = { _, _ -> },
) {
    // The page may be RTL, but a duration's sign, digits and Latin units retain their order.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val baseStyle = MaterialTheme.typography.displayLarge
        val measurer = rememberTextMeasurer()
        BoxWithConstraints {
            // Keep the size stable as digits tick; only the number of cells/units matters.
            val shape = text.map { if (it in '0'..'9') '0' else it }.joinToString("")
            val sizing = remember(measurer, baseStyle, shape, constraints.maxWidth) {
                fitCountdown(measurer, baseStyle, shape, constraints.maxWidth)
            }
            val digitWidth = with(LocalDensity.current) { sizing.digitWidthPx.toDp() }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spokenLabel },
            ) {
                text.forEachIndexed { index, character ->
                    // Read the whole duration once, without exposing individual glyphs to TalkBack.
                    val glyph = Modifier.clearAndSetSemantics { testTag = "countdown-glyph-$index" }
                    if (character in '0'..'9') {
                        Box(Modifier.width(digitWidth), contentAlignment = Alignment.Center) {
                            Text(character.toString(), modifier = glyph, style = sizing.style, maxLines = 1,
                                onTextLayout = { onGlyphLayout(index, it) })
                        }
                    } else {
                        Text(character.toString(), modifier = glyph, style = sizing.style, maxLines = 1,
                            onTextLayout = { onGlyphLayout(index, it) })
                    }
                }
            }
        }
    }
}

private data class CountdownSizing(val style: TextStyle, val digitWidthPx: Int, val totalWidthPx: Int)

private fun fitCountdown(measurer: TextMeasurer, baseStyle: TextStyle, shape: String, maxWidthPx: Int): CountdownSizing {
    fun measure(scale: Float): CountdownSizing {
        val style = baseStyle.copy(fontSize = baseStyle.fontSize * scale, lineHeight = baseStyle.lineHeight * scale)
        fun width(character: Char) = measurer.measure(character.toString(), style = style, maxLines = 1).size.width
        val digitWidth = ('0'..'9').maxOf(::width)
        return CountdownSizing(style, digitWidth, shape.sumOf { if (it == '0') digitWidth else width(it) })
    }
    val fullSize = measure(1f)
    if (fullSize.totalWidthPx <= maxWidthPx) return fullSize
    var lower = 0.01f
    var upper = 1f
    var fitted = measure(lower)
    repeat(14) {
        val scale = (lower + upper) / 2
        val candidate = measure(scale)
        if (candidate.totalWidthPx <= maxWidthPx) {
            lower = scale
            fitted = candidate
        } else {
            upper = scale
        }
    }
    return fitted
}
