package com.local.smsllm.ui.theme

import androidx.compose.ui.graphics.Color

// ── Canvas & Surface ────────────────────────────────────────────────────────
val CanvasDark = Color(0xFF0D0E0C)
val Surface1Dark = Color(0xFF161815)   // ~+1 dp elevation
val Surface2Dark = Color(0xFF1C1E1A)   // ~+2 dp elevation
val Surface3Dark = Color(0xFF232520)   // ~+4 dp elevation

val CanvasLight = Color(0xFFFAF9F5)
val Surface1Light = Color(0xFFF2F1ED)
val Surface2Light = Color(0xFFE9E8E3)
val Surface3Light = Color(0xFFE0DFD9)

// ── Text ─────────────────────────────────────────────────────────────────────
val OnSurfaceDark = Color(0xFFECEBE6)    // warm off-white
val OnSurfaceMutedDark = Color(0xFF9A9A93)
val OnSurfaceLight = Color(0xFF1A1C18)
val OnSurfaceMutedLight = Color(0xFF6B6B63)

// ── Hairline dividers ────────────────────────────────────────────────────────
val DividerDark = Color(0x14FFFFFF)   // white @ ~8%
val DividerLight = Color(0x14000000)  // black @ ~8%

// ── Brand ────────────────────────────────────────────────────────────────────
val BrandGold = Color(0xFFC9A227)
val BrandGoldMuted = Color(0xFF8A6E1A)

// ── Semantic money — FIXED, never overridden by dynamic color ────────────────
/** Credit / incoming — emerald green */
val CreditGreen = Color(0xFF34D399)
val CreditGreenSubtle = Color(0x2234D399)  // 13% alpha for backgrounds

/** Debit / outgoing — warm amber */
val DebitAmber = Color(0xFFF5A524)
val DebitAmberSubtle = Color(0x22F5A524)

/** Error / failed — warm red */
val ErrorRed = Color(0xFFF4503E)
val ErrorRedSubtle = Color(0x22F4503E)

// ── M3 seed colors (used when dynamic color is unavailable / API < 31) ───────
// These map to M3 role names used in the ColorScheme below
val Md3Primary = BrandGold
val Md3OnPrimary = Color(0xFF1A1200)
val Md3PrimaryContainer = Color(0xFF3A2E00)
val Md3OnPrimaryContainer = Color(0xFFEDD166)

val Md3Secondary = Color(0xFFBBC890)
val Md3OnSecondary = Color(0xFF282F12)
val Md3SecondaryContainer = Color(0xFF3E4626)
val Md3OnSecondaryContainer = Color(0xFFD7E4AA)

val Md3Tertiary = Color(0xFFA0CFBD)
val Md3OnTertiary = Color(0xFF003829)
val Md3TertiaryContainer = Color(0xFF004E3A)
val Md3OnTertiaryContainer = Color(0xFFBCEDD9)

val Md3Error = ErrorRed
val Md3OnError = Color(0xFF2D0000)
val Md3ErrorContainer = Color(0xFF4D0000)
val Md3OnErrorContainer = Color(0xFFFFDAD6)

val Md3Background = CanvasDark
val Md3OnBackground = OnSurfaceDark
val Md3Surface = Surface1Dark
val Md3OnSurface = OnSurfaceDark
val Md3SurfaceVariant = Surface2Dark
val Md3OnSurfaceVariant = OnSurfaceMutedDark
val Md3Outline = OnSurfaceMutedDark
val Md3OutlineVariant = DividerDark

// Light equivalents
val Md3BackgroundLight = CanvasLight
val Md3OnBackgroundLight = OnSurfaceLight
val Md3SurfaceLight = Surface1Light
val Md3OnSurfaceLight = OnSurfaceLight
val Md3SurfaceVariantLight = Surface2Light
val Md3OnSurfaceVariantLight = OnSurfaceMutedLight
val Md3OutlineLight = OnSurfaceMutedLight
val Md3OutlineVariantLight = DividerLight
