package com.local.smsllm.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.smsllm.domain.Category
import com.local.smsllm.ui.theme.PillShape

/**
 * Renders a category's emoji inside a tinted circle.
 * The tint color cycles through a small palette keyed to the category ordinal
 * to give visual variety without requiring per-category color definitions.
 *
 * @param category The [Category] to display; pass null to show a generic "?" glyph.
 * @param size     Diameter of the circle. Default 40.dp.
 */
@Composable
fun CategoryGlyph(
    category: Category?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val emoji = category?.emoji ?: "❓"
    val tint = categoryTint(category)

    Box(
        modifier = modifier
            .size(size)
            .clip(PillShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.45f).sp,
            lineHeight = (size.value * 0.45f).sp,
        )
    }
}

/** Derives a subtle tinted background for a category — stable, deterministic. */
@Composable
private fun categoryTint(category: Category?): Color {
    val base = MaterialTheme.colorScheme.surfaceVariant
    if (category == null) return base

    // A small palette of tints (all at low alpha so they read on dark/light surfaces).
    val palette = listOf(
        Color(0x2234D399), // emerald  — income-ish
        Color(0x22F5A524), // amber    — spend-ish
        Color(0x22C9A227), // gold     — investment-ish
        Color(0x22A0CFBD), // teal     — health-ish
        Color(0x22BBC890), // olive    — bills-ish
        Color(0x22F4503E), // red      — urgent-ish
        Color(0x220090FF), // blue     — travel-ish
        Color(0x22CF9FFF), // purple   — entertainment-ish
    )
    return palette[category.ordinal % palette.size]
}
