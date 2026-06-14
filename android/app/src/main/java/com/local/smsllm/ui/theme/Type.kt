package com.local.smsllm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.local.smsllm.R

// ── Font families ─────────────────────────────────────────────────────────────
// Graceful fallback: if the bundled TTF is missing, the Font() constructor is
// called with the resource ID from R.font. If the resource doesn't exist the
// build fails, so we catch that with runCatching and fall back to system families.

private fun safeFont(resId: Int, weight: FontWeight = FontWeight.Normal): Font? =
    runCatching { Font(resId, weight) }.getOrNull()

/**
 * Fraunces — editorial optical-size serif for display/hero text.
 * Falls back to FontFamily.Serif if TTF is unavailable.
 */
val FrauncesFamily: FontFamily = run {
    val regular = safeFont(R.font.fraunces, FontWeight.Normal)
    val semiBold = safeFont(R.font.fraunces, FontWeight.SemiBold)
    val bold = safeFont(R.font.fraunces, FontWeight.Bold)
    val fonts = listOfNotNull(regular, semiBold, bold)
    if (fonts.isEmpty()) FontFamily.Serif else FontFamily(fonts)
}

/**
 * Hanken Grotesk — clean geometric sans for body and UI labels.
 * Falls back to FontFamily.SansSerif.
 */
val HankenFamily: FontFamily = run {
    val light = safeFont(R.font.hanken_grotesk, FontWeight.Light)
    val regular = safeFont(R.font.hanken_grotesk, FontWeight.Normal)
    val medium = safeFont(R.font.hanken_grotesk, FontWeight.Medium)
    val semiBold = safeFont(R.font.hanken_grotesk, FontWeight.SemiBold)
    val bold = safeFont(R.font.hanken_grotesk, FontWeight.Bold)
    val fonts = listOfNotNull(light, regular, medium, semiBold, bold)
    if (fonts.isEmpty()) FontFamily.SansSerif else FontFamily(fonts)
}

/**
 * IBM Plex Mono — tabular fixed-width for all monetary amounts.
 * Falls back to FontFamily.Monospace.
 */
val IbmPlexMonoFamily: FontFamily = run {
    val regular = safeFont(R.font.ibm_plex_mono_regular, FontWeight.Normal)
    val medium = safeFont(R.font.ibm_plex_mono_medium, FontWeight.Medium)
    val fonts = listOfNotNull(regular, medium)
    if (fonts.isEmpty()) FontFamily.Monospace else FontFamily(fonts)
}

// ── Material 3 Typography ────────────────────────────────────────────────────
val SmsLlmTypography = Typography(
    // Display (Fraunces — hero numbers, big section titles)
    displayLarge = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    // Headline (Fraunces for section titles)
    headlineLarge = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    // Title (Hanken — card titles, nav labels)
    titleLarge = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Body (Hanken — body copy)
    bodyLarge = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Label (Hanken — chips, tags, captions)
    labelLarge = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HankenFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ── Extra text styles (not in M3 Typography) ─────────────────────────────────

/** Large hero amount (IBM Plex Mono, tabular figures) — e.g., ₹1,23,456 */
val AmountLarge = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 40.sp,
    lineHeight = 48.sp,
    letterSpacing = (-0.5).sp,
    textAlign = TextAlign.Start,
)

/** Medium amount — transaction rows */
val AmountMedium = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.25).sp,
)

/** Small amount — compact cells, chips */
val AmountSmall = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

/** Fraunces hero serif — large branding / onboarding headline */
val HeroSerif = TextStyle(
    fontFamily = FrauncesFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    letterSpacing = (-0.5).sp,
)
