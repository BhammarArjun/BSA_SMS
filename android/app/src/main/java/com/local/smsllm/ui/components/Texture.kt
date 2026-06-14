package com.local.smsllm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.DebitAmber
import kotlin.random.Random

// ── Grain overlay ─────────────────────────────────────────────────────────────

/**
 * Draws a subtle static noise grain texture over its content.
 * Performance note: the noise is generated once and cached per size — safe on budget phones.
 *
 * @param alpha   Opacity of the grain (0.02–0.06 is typical; higher = grittier).
 * @param density Fraction of pixels that get a noise dot (0.0–1.0).
 */
fun Modifier.grainOverlay(
    alpha: Float = 0.035f,
    density: Float = 0.25f,
): Modifier = this.drawWithCache {
    // Pre-generate grain dots for this size — cached until size changes.
    val totalPixels = (size.width * size.height * density).toInt()
    val rng = Random(seed = 0x4C4544)  // fixed seed → stable across recompositions
    val dots = Array(totalPixels) {
        Offset(
            x = rng.nextFloat() * size.width,
            y = rng.nextFloat() * size.height,
        )
    }
    onDrawWithContent {
        drawContent()
        val grainColor = Color.White.copy(alpha = alpha)
        dots.forEach { dot -> drawCircle(color = grainColor, radius = 0.6f, center = dot) }
    }
}

// ── Hero radial glow ──────────────────────────────────────────────────────────

/**
 * A faint dual-color radial glow composable. Draws a soft emerald glow from the top-left
 * and an amber glow from the bottom-right — evoking credit/debit energy in the background.
 *
 * Cheap to draw: just two radial gradient fills with high transparency.
 */
@Composable
fun HeroGlow(
    modifier: Modifier = Modifier,
    creditColor: Color = CreditGreen,
    debitColor: Color = DebitAmber,
    creditAlpha: Float = 0.12f,
    debitAlpha: Float = 0.08f,
    radius: Dp = 320.dp,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val r = radius.toPx()

        // Emerald glow — upper-left quadrant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(creditColor.copy(alpha = creditAlpha), Color.Transparent),
                center = Offset(x = size.width * 0.15f, y = size.height * 0.20f),
                radius = r,
            ),
            radius = r,
            center = Offset(x = size.width * 0.15f, y = size.height * 0.20f),
        )

        // Amber glow — lower-right quadrant
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(debitColor.copy(alpha = debitAlpha), Color.Transparent),
                center = Offset(x = size.width * 0.85f, y = size.height * 0.78f),
                radius = r,
            ),
            radius = r,
            center = Offset(x = size.width * 0.85f, y = size.height * 0.78f),
        )
    }
}

/**
 * Convenience wrapper: places [HeroGlow] behind [content] in a Box.
 * Apply [grainOverlay] modifier on top for the full layered texture effect.
 */
@Composable
fun HeroBackground(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    glowCreditAlpha: Float = 0.12f,
    glowDebitAlpha: Float = 0.08f,
    grain: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (grain) Modifier.grainOverlay() else Modifier),
        contentAlignment = Alignment.TopStart,
    ) {
        HeroGlow(
            modifier = Modifier.matchParentSize(),
            creditAlpha = glowCreditAlpha,
            debitAlpha = glowDebitAlpha,
        )
        content()
    }
}
