package com.local.smsllm.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.smsllm.ui.theme.DividerDark

/**
 * A rounded surface card matching the ledger identity.
 * Default: softly lifted dark surface with a hairline divider border.
 *
 * @param tonalElevation M3 tonal elevation for surface color lift (default 2dp).
 * @param border         Optional explicit border; if null uses the hairline white divider default.
 */
@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 2.dp,
    border: BorderStroke? = BorderStroke(0.5.dp, DividerDark),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = border ?: BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = tonalElevation),
    ) {
        Column(content = content)
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

/**
 * A muted, spaced section header label (Hanken SemiBold, label-large scale).
 * Used above groups of transactions or settings rows.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp,
    )
}

// Need the .sp extension — import workaround (TextUnit factory)
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
