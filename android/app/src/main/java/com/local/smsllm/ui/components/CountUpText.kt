package com.local.smsllm.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.local.smsllm.ui.theme.AmountLarge
import com.local.smsllm.ui.theme.amountColor
import com.local.smsllm.ui.util.formatInrCompact

/**
 * Animates a number from 0 to [target] over [durationMs] milliseconds
 * using a smooth decelerate curve, then displays it as a formatted INR string.
 *
 * Used for dashboard hero totals (e.g., net spend / income this month).
 *
 * @param target      Final value to count up to.
 * @param direction   "credit" or "debit" — drives semantic color.
 * @param durationMs  Animation duration. Default 600ms.
 * @param style       Text style; defaults to [AmountLarge].
 * @param color       Override color (defaults to semantic [amountColor]).
 */
@Composable
fun CountUpText(
    target: Double,
    direction: String? = null,
    modifier: Modifier = Modifier,
    durationMs: Int = 600,
    style: TextStyle = AmountLarge,
    color: Color = Color.Unspecified,
) {
    val animatable = remember(target) { Animatable(0f) }

    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    val resolvedColor = if (color != Color.Unspecified) color else amountColor(direction)
    Text(
        text = formatInrCompact(animatable.value.toDouble()),
        modifier = modifier,
        style = style,
        color = resolvedColor,
    )
}
