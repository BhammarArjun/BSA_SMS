package com.local.smsllm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 shape scale tuned for the "editorial fintech ledger" identity.
 * Large rounding throughout — feels modern and trustworthy.
 *
 *   extraSmall  =  8dp   (input fields, chips at small scale)
 *   small       = 12dp
 *   medium      = 18dp
 *   large       = 24dp
 *   extraLarge  = 32dp   (bottom sheets, modal drawers)
 */
val SmsLlmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Pill shape — for status chips and category badges. */
val PillShape = RoundedCornerShape(50)
