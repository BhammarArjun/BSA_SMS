package com.local.smsllm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ── Static dark color scheme (pre-Android 12 / when dynamic color disabled) ──
private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Md3Primary,
    onPrimary = Md3OnPrimary,
    primaryContainer = Md3PrimaryContainer,
    onPrimaryContainer = Md3OnPrimaryContainer,
    secondary = Md3Secondary,
    onSecondary = Md3OnSecondary,
    secondaryContainer = Md3SecondaryContainer,
    onSecondaryContainer = Md3OnSecondaryContainer,
    tertiary = Md3Tertiary,
    onTertiary = Md3OnTertiary,
    tertiaryContainer = Md3TertiaryContainer,
    onTertiaryContainer = Md3OnTertiaryContainer,
    error = Md3Error,
    onError = Md3OnError,
    errorContainer = Md3ErrorContainer,
    onErrorContainer = Md3OnErrorContainer,
    background = Md3Background,
    onBackground = Md3OnBackground,
    surface = Md3Surface,
    onSurface = Md3OnSurface,
    surfaceVariant = Md3SurfaceVariant,
    onSurfaceVariant = Md3OnSurfaceVariant,
    outline = Md3Outline,
    outlineVariant = Md3OutlineVariant,
)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = BrandGold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFFEDD166),
    onPrimaryContainer = Color(0xFF221A00),
    secondary = Color(0xFF5C6444),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E4AA),
    onSecondaryContainer = Color(0xFF191E08),
    tertiary = Color(0xFF006B52),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEDD9),
    onTertiaryContainer = Color(0xFF002117),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF2D0000),
    background = Md3BackgroundLight,
    onBackground = Md3OnBackgroundLight,
    surface = Md3SurfaceLight,
    onSurface = Md3OnSurfaceLight,
    surfaceVariant = Md3SurfaceVariantLight,
    onSurfaceVariant = Md3OnSurfaceVariantLight,
    outline = Md3OutlineLight,
    outlineVariant = Md3OutlineVariantLight,
)

// ── Semantic money colors (fixed — not subject to dynamic color) ──────────────
@Stable
class MoneyColors(
    val credit: Color,
    val debit: Color,
    val error: Color,
    val creditSubtle: Color,
    val debitSubtle: Color,
    val errorSubtle: Color,
)

private val FixedMoneyColors = MoneyColors(
    credit = CreditGreen,
    debit = DebitAmber,
    error = ErrorRed,
    creditSubtle = CreditGreenSubtle,
    debitSubtle = DebitAmberSubtle,
    errorSubtle = ErrorRedSubtle,
)

val LocalMoneyColors = staticCompositionLocalOf { FixedMoneyColors }

/** Convenience accessor: `MaterialTheme.money.credit` etc. */
val MaterialTheme.money: MoneyColors
    @Composable @ReadOnlyComposable get() = LocalMoneyColors.current

/**
 * Returns the semantic color for a transaction direction string.
 * "credit" / "CREDIT" → emerald; "debit" / "DEBIT" → amber; else error red.
 */
@Composable
fun amountColor(direction: String?): Color {
    val money = MaterialTheme.money
    return when (direction?.lowercase()) {
        "credit", "in", "incoming", "received" -> money.credit
        "debit", "out", "outgoing", "sent" -> money.debit
        else -> money.error
    }
}

// ── Root theme composable ─────────────────────────────────────────────────────
@Composable
fun SmsLlmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalMoneyColors provides FixedMoneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmsLlmTypography,
            shapes = SmsLlmShapes,
            content = content,
        )
    }
}
