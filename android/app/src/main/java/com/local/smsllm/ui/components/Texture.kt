package com.local.smsllm.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.DebitAmber
import kotlin.random.Random

// ── Grain overlay ─────────────────────────────────────────────────────────────

/**
 * Draws a subtle static noise grain texture over its content.
 *
 * A small [GRAIN_TILE_PX]² noise tile is generated once and tiled across the surface with a
 * repeating shader, so the per-frame cost is a single [drawRect] regardless of screen size.
 * (An earlier version drew one circle per ~quarter of *every* screen pixel — hundreds of
 * thousands of draws and allocations per frame — which stalled the very first frame on real
 * devices and showed a permanent black screen.)
 *
 * @param alpha   Opacity of the grain (0.02–0.06 is typical; higher = grittier).
 * @param density Fraction of tile pixels that get a noise dot (0.0–1.0).
 */
fun Modifier.grainOverlay(
    alpha: Float = 0.035f,
    density: Float = 0.25f,
): Modifier = this.drawWithCache {
    // Tile is independent of size, so this only rebuilds when the modifier's size changes.
    val brush = ShaderBrush(
        ImageShader(buildGrainTile(alpha, density), TileMode.Repeated, TileMode.Repeated),
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush)
    }
}

private const val GRAIN_TILE_PX = 96

/** Builds a small ARGB noise tile: [density] of pixels are white at [alpha], the rest transparent. */
private fun buildGrainTile(alpha: Float, density: Float): ImageBitmap {
    val rng = Random(seed = 0x4C4544) // fixed seed → identical grain every build
    val grain = Color.White.copy(alpha = alpha).toArgb()
    val bitmap = Bitmap.createBitmap(GRAIN_TILE_PX, GRAIN_TILE_PX, Bitmap.Config.ARGB_8888)
    for (y in 0 until GRAIN_TILE_PX) {
        for (x in 0 until GRAIN_TILE_PX) {
            if (rng.nextFloat() < density) bitmap.setPixel(x, y, grain)
        }
    }
    return bitmap.asImageBitmap()
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
