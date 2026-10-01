package com.aynama.prayertimes.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer

/** Fraunces glyphs with genuinely fixed digit positions on Android's pixel-snapped renderer. */
@Composable
internal fun CountdownDigits(text: String, spokenLabel: String) {
    val style = MaterialTheme.typography.displayLarge
    val measurer = rememberTextMeasurer()
    val widestDigitPx = remember(measurer, style) {
        ('0'..'9').maxOf { digit ->
            measurer.measure(digit.toString(), style = style, maxLines = 1).size.width
        }
    }
    val digitWidth = with(LocalDensity.current) { widestDigitPx.toDp() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spokenLabel },
    ) {
        text.forEach { character ->
            if (character in '0'..'9') {
                Box(Modifier.width(digitWidth), contentAlignment = Alignment.Center) {
                    Text(character.toString(), style = style, maxLines = 1)
                }
            } else {
                Text(character.toString(), style = style, maxLines = 1)
            }
        }
    }
}
