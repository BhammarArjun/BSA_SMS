package com.local.smsllm.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// ── Indian rupee formatting ───────────────────────────────────────────────────

private val INR_SYMBOLS = DecimalFormatSymbols(Locale("en", "IN"))

/**
 * Formats [amount] as an Indian-grouping rupee string (₹X,XX,XXX.XX).
 *
 * Examples:
 *   1500.0       → "₹1,500.00"
 *   123456.75    → "₹1,23,456.75"
 *   1234567.0    → "₹12,34,567.00"
 *
 * The Indian grouping pattern uses commas at thousands then every two digits:
 * #,##,##0.00
 */
fun formatInr(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val fmt = DecimalFormat("#,##,##0.00", INR_SYMBOLS)
    return "₹${fmt.format(absAmount)}"
}

/**
 * Formats [amount] compactly, omitting paise when they are zero.
 *   1500.0   → "₹1,500"
 *   1500.5   → "₹1,500.50"
 */
fun formatInrCompact(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val hasPaise = (absAmount * 100).toLong() % 100 != 0L
    val pattern = if (hasPaise) "#,##,##0.00" else "#,##,##0"
    return "₹${DecimalFormat(pattern, INR_SYMBOLS).format(absAmount)}"
}

/**
 * Formats large amounts in short form with a suffix (K/L/Cr).
 *   999.0        → "₹999"
 *   1_500.0      → "₹1.5K"
 *   100_000.0    → "₹1.0L"
 *   10_000_000.0 → "₹1.0Cr"
 */
fun formatInrShort(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    return when {
        absAmount >= 1_00_00_000 -> "₹${"%.1f".format(absAmount / 1_00_00_000)}Cr"
        absAmount >= 1_00_000    -> "₹${"%.1f".format(absAmount / 1_00_000)}L"
        absAmount >= 1_000       -> "₹${"%.1f".format(absAmount / 1_000)}K"
        else                     -> "₹${absAmount.toLong()}"
    }
}
