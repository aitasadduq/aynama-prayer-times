@file:OptIn(ExperimentalTextApi::class)

package com.aynama.prayertimes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aynama.prayertimes.R

internal fun frauncesFamily(opsz: Float) = FontFamily(
    Font(
        R.font.fraunces,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("wght", 400f),
            FontVariation.Setting("opsz", opsz),
        ),
    ),
    Font(
        R.font.fraunces,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("wght", 500f),
            FontVariation.Setting("opsz", opsz),
        ),
    ),
)

val IbmPlexSans = FontFamily(
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 400f)),
    ),
    Font(
        R.font.ibm_plex_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 500f)),
    ),
)

val AynamaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = frauncesFamily(144f),
        fontWeight = FontWeight.Normal,
        fontSize = 72.sp,
        lineHeight = 72.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = frauncesFamily(96f),
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = (48 * 1.05).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = frauncesFamily(48f),
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = (32 * 1.1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = frauncesFamily(20f),
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = (17 * 1.45).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = (15 * 1.45).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = (13 * 1.4).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 17.sp,
    ),
)
