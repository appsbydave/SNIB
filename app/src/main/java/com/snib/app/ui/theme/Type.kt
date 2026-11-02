package com.snib.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Display: condensed grotesque with weight. Session timer, screen titles, the word SNIBBED.
 * Never body copy.
 *
 * Drop Oswald or Archivo Condensed into res/font and switch [display] over. Until then the
 * platform condensed sans stands in — close enough to design against, not the identity.
 */
private const val USE_BUNDLED_DISPLAY_FONT = false

val display: FontFamily = if (USE_BUNDLED_DISPLAY_FONT) {
    // FontFamily(Font(R.font.oswald_semibold, FontWeight.SemiBold))
    FontFamily.SansSerif
} else {
    FontFamily.SansSerif
}

/** Body and UI. Boring deliberately: the app picker is the one dense screen and needs legibility. */
val body: FontFamily = FontFamily.SansSerif

/** Tabular figures, so the timer doesn't jitter. */
private const val TABULAR = "tnum"

// Scale: 44 / 28 / 20 / 16 / 13. Five sizes, no more.
val SnibType = Typography(
    displayLarge = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
    ),
    headlineMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TABULAR,
    ),
    titleMedium = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
)

val CenteredSmall = SnibType.labelSmall.copy(textAlign = TextAlign.Center)
