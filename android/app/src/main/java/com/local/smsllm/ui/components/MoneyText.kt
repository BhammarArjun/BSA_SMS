package com.local.smsllm.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.local.smsllm.ui.theme.AmountLarge
import com.local.smsllm.ui.theme.AmountMedium
import com.local.smsllm.ui.theme.AmountSmall
import com.local.smsllm.ui.theme.amountColor
import com.local.smsllm.ui.util.formatInr
import com.local.smsllm.ui.util.formatInrCompact
import com.local.smsllm.ui.util.formatInrShort

/**
 * Renders a monetary amount in IBM Plex Mono tabular style, colored by transaction direction.
 *
 * @param amount     Absolute monetary value (sign is communicated by [direction]).
 * @param direction  "credit" / "debit" — drives semantic color. Null → error red.
 * @param style      Text style; defaults to [AmountMedium].
 * @param compact    When true uses [formatInrCompact] (omits trailing .00).
 * @param short      When true uses [formatInrShort] (K/L/Cr suffixes).
 * @param color      Override semantic color (use sparingly — semantic colors are intentional).
 */
@Composable
fun MoneyText(
    amount: Double,
    direction: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountMedium,
    compact: Boolean = false,
    short: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = if (color != Color.Unspecified) color else amountColor(direction)
    val formatted = when {
        short   -> formatInrShort(amount)
        compact -> formatInrCompact(amount)
        else    -> formatInr(amount)
    }
    Text(
        text = formatted,
        modifier = modifier,
        style = style,
        color = resolvedColor,
    )
}

/** Large hero variant of MoneyText. */
@Composable
fun MoneyTextLarge(
    amount: Double,
    direction: String? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    color: Color = Color.Unspecified,
) = MoneyText(
    amount = amount,
    direction = direction,
    modifier = modifier,
    style = AmountLarge,
    compact = compact,
    color = color,
)

/** Small compact variant — for chips, table rows. */
@Composable
fun MoneyTextSmall(
    amount: Double,
    direction: String? = null,
    modifier: Modifier = Modifier,
    short: Boolean = false,
    color: Color = Color.Unspecified,
) = MoneyText(
    amount = amount,
    direction = direction,
    modifier = modifier,
    style = AmountSmall,
    short = short,
    color = color,
)
