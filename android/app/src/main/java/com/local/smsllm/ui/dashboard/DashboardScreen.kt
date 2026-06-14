package com.local.smsllm.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.Category
import com.local.smsllm.ui.components.CategoryGlyph
import com.local.smsllm.ui.components.CountUpText
import com.local.smsllm.ui.components.HeroBackground
import com.local.smsllm.ui.components.LedgerCard
import com.local.smsllm.ui.components.MoneyText
import com.local.smsllm.ui.components.MoneyTextSmall
import com.local.smsllm.ui.components.SectionHeader
import com.local.smsllm.ui.components.grainOverlay
import com.local.smsllm.ui.theme.AmountMedium
import com.local.smsllm.ui.theme.BrandGold
import com.local.smsllm.ui.theme.CreditGreen
import com.local.smsllm.ui.theme.DebitAmber
import com.local.smsllm.ui.theme.FrauncesFamily
import com.local.smsllm.ui.theme.HeroSerif
import com.local.smsllm.ui.theme.OnSurfaceMutedDark
import com.local.smsllm.ui.theme.PillShape
import com.local.smsllm.ui.theme.amountColor
import com.local.smsllm.ui.theme.money
import com.local.smsllm.ui.util.formatInrShort
import kotlinx.coroutines.delay

// ── Relative time helper ────────────────────────────────────────────────────────

fun relativeTime(epochMs: Long): String {
    if (epochMs == 0L) return "never"
    val diffMs = System.currentTimeMillis() - epochMs
    val diffSec = diffMs / 1_000L
    val diffMin = diffSec / 60L
    val diffH = diffMin / 60L
    val diffD = diffH / 24L
    return when {
        diffSec < 60 -> "just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffH < 24   -> "${diffH}h ago"
        else         -> "${diffD}d ago"
    }
}

/** Human "next run" label for a future epoch-millis, or null when not scheduled. */
fun nextRunText(epochMs: Long?): String {
    if (epochMs == null) return "not scheduled"
    val diffMs = epochMs - System.currentTimeMillis()
    if (diffMs <= 0L) return "due now"
    val diffMin = diffMs / 60_000L
    val diffH = diffMin / 60L
    return when {
        diffMin < 1  -> "in <1m"
        diffMin < 60 -> "in ${diffMin}m"
        else         -> "in ${diffH}h ${diffMin % 60}m"
    }
}

// ── Donut chart palette ─────────────────────────────────────────────────────────

private val DONUT_PALETTE = listOf(
    Color(0xFFF5A524), // amber
    Color(0xFF34D399), // emerald
    Color(0xFFC9A227), // gold
    Color(0xFFA0CFBD), // teal
    Color(0xFFBBC890), // olive
    Color(0xFFF4503E), // red
    Color(0xFF0090FF), // blue
    Color(0xFFCF9FFF), // purple
    Color(0xFF80CAFF), // sky
    Color(0xFFFFB3BA), // pink
)

// ── Root screen ─────────────────────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val data = state.data

    // Staggered entrance — one animatable per card section
    val cardAlphas = remember { List(8) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        cardAlphas.forEachIndexed { i, anim ->
            delay(i * 80L)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            )
        }
    }

    // Show snackbar-style queued confirmation
    LaunchedEffect(state.processNowQueued) {
        if (state.processNowQueued) {
            delay(2_200)
            viewModel.clearProcessNowQueued()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HeroBackground(
            modifier = Modifier.fillMaxSize(),
            glowCreditAlpha = 0.10f,
            glowDebitAlpha = 0.07f,
            grain = true,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1 ── Hero section
                item {
                    HeroSection(
                        state = state,
                        onPeriodSelected = viewModel::setPeriod,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .animFade(cardAlphas[0].value),
                    )
                }

                // Empty state
                if (data.txnCount == 0) {
                    item {
                        EmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[1].value),
                        )
                    }
                } else {
                    // 2 ── Summary cards
                    item {
                        SummaryRow(
                            data = data,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[1].value),
                        )
                    }

                    // 3a ── Spend over time
                    item {
                        SpendOverTimeCard(
                            buckets = data.spendBuckets,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[2].value),
                        )
                    }

                    // 3b ── Category breakdown (donut)
                    item {
                        CategoryDonutCard(
                            breakdown = data.categoryBreakdown,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[3].value),
                        )
                    }

                    // 3c ── Debit vs credit split bar
                    item {
                        DebitCreditSplitCard(
                            totalSpent = data.totalSpent,
                            totalReceived = data.totalReceived,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[4].value),
                        )
                    }

                    // 3d ── Top counterparties
                    item {
                        TopCounterpartiesCard(
                            counterparties = data.topCounterparties,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[5].value),
                        )
                    }
                }

                // 4 ── Status strip (always visible)
                item {
                    StatusStripCard(
                        pending = state.pending,
                        lastRunAt = state.lastRunAt,
                        nextRunAt = state.nextRunAt,
                        processNowQueued = state.processNowQueued,
                        onProcessNow = viewModel::processNow,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animFade(cardAlphas[6].value),
                    )
                }

                // 5 ── Recent transactions (only when there are some)
                if (data.recentTransactions.isNotEmpty()) {
                    item {
                        RecentTransactionsCard(
                            transactions = data.recentTransactions,
                            onTransactionClick = onTransactionClick,
                            onSeeAll = onSeeAll,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animFade(cardAlphas[7].value),
                        )
                    }
                }
            }
        }
    }
}

// ── Animation helper modifier ──────────────────────────────────────────────────

private fun Modifier.animFade(alpha: Float): Modifier =
    this.graphicsLayer(
        alpha = alpha,
        translationY = (1f - alpha) * 24f,
    )

// ── Hero section ───────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(
    state: DashboardUiState,
    onPeriodSelected: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val net = state.data.net
    val netDirection = if (net >= 0) "credit" else "debit"
    val periodLabel = when (state.period) {
        Period.THIS_MONTH -> "This month"
        Period.LAST_MONTH -> "Last month"
        Period.ALL -> "All time"
    }

    Column(
        modifier = modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Ledger",
            style = HeroSerif,
            color = BrandGold,
        )
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceMutedDark,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))
        CountUpText(
            target = kotlin.math.abs(net),
            direction = netDirection,
            durationMs = 700,
            style = HeroSerif.copy(fontSize = 40.sp),
        )
        Text(
            text = if (net >= 0) "net received" else "net spent",
            style = MaterialTheme.typography.bodySmall,
            color = if (net >= 0) CreditGreen.copy(alpha = 0.7f) else DebitAmber.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(16.dp))
        PeriodSelector(
            selected = state.period,
            onSelect = onPeriodSelected,
        )
    }
}

@Composable
private fun PeriodSelector(
    selected: Period,
    onSelect: (Period) -> Unit,
) {
    val periods = listOf(
        Period.THIS_MONTH to "This month",
        Period.LAST_MONTH to "Last month",
        Period.ALL to "All time",
    )

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            periods.forEach { (period, label) ->
                val isSelected = period == selected
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            if (isSelected) BrandGold.copy(alpha = 0.15f)
                            else Color.Transparent,
                        )
                        .clickable { onSelect(period) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) BrandGold else OnSurfaceMutedDark,
                    )
                }
            }
        }
    }
}

// ── Summary row ────────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(
    data: DashboardData,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryCard(
            label = "Spent",
            amount = data.totalSpent,
            direction = "debit",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Received",
            amount = data.totalReceived,
            direction = "credit",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            label = "Net",
            amount = kotlin.math.abs(data.net),
            direction = if (data.net >= 0) "credit" else "debit",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amount: Double,
    direction: String,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceMutedDark,
            )
            Spacer(Modifier.height(4.dp))
            MoneyText(
                amount = amount,
                direction = direction,
                style = AmountMedium.copy(fontSize = 14.sp),
                compact = true,
            )
        }
    }
}

// ── Spend over time (Canvas line chart) ────────────────────────────────────────

@Composable
private fun SpendOverTimeCard(
    buckets: List<SpendBucket>,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        SectionHeader(title = "Spend over time")
        if (buckets.isEmpty()) {
            EmptyChartMessage(modifier = Modifier.padding(16.dp))
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                drawSpendLineChart(buckets)
            }
            // X-axis labels (first, mid, last)
            if (buckets.size >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = buckets.first().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMutedDark,
                    )
                    if (buckets.size > 2) {
                        Text(
                            text = buckets[buckets.size / 2].label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                        )
                    }
                    Text(
                        text = buckets.last().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMutedDark,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawSpendLineChart(buckets: List<SpendBucket>) {
    val maxAmount = buckets.maxOf { it.amount }.takeIf { it > 0 } ?: return
    val n = buckets.size
    if (n < 2) return

    val w = size.width
    val h = size.height
    val padTop = 12f
    val padBottom = 8f
    val usableH = h - padTop - padBottom

    fun xFor(i: Int) = if (n == 1) w / 2f else i * w / (n - 1).toFloat()
    fun yFor(amount: Double) = padTop + usableH * (1f - (amount / maxAmount).toFloat())

    // Filled area under the line
    val fillPath = Path().apply {
        moveTo(xFor(0), h)
        lineTo(xFor(0), yFor(buckets[0].amount))
        for (i in 1 until n) {
            lineTo(xFor(i), yFor(buckets[i].amount))
        }
        lineTo(xFor(n - 1), h)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                DebitAmber.copy(alpha = 0.22f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = h,
        ),
    )

    // Line
    val linePath = Path().apply {
        moveTo(xFor(0), yFor(buckets[0].amount))
        for (i in 1 until n) {
            lineTo(xFor(i), yFor(buckets[i].amount))
        }
    }
    drawPath(
        path = linePath,
        color = DebitAmber,
        style = Stroke(width = 2.2f, cap = StrokeCap.Round),
    )

    // Dots at each data point
    for (i in 0 until n) {
        drawCircle(
            color = DebitAmber,
            radius = 3.5f,
            center = Offset(xFor(i), yFor(buckets[i].amount)),
        )
        drawCircle(
            color = Color(0xFF1C1E1A),
            radius = 1.8f,
            center = Offset(xFor(i), yFor(buckets[i].amount)),
        )
    }
}

// ── Category donut chart ───────────────────────────────────────────────────────

@Composable
private fun CategoryDonutCard(
    breakdown: List<CategoryBreakdown>,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        SectionHeader(title = "By category")
        if (breakdown.isEmpty()) {
            EmptyChartMessage(modifier = Modifier.padding(16.dp))
        } else {
            val total = breakdown.sumOf { it.total }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Donut
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawDonut(breakdown)
                    }
                    Text(
                        text = "${breakdown.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "cats",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceMutedDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    breakdown.take(5).forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DONUT_PALETTE[index % DONUT_PALETTE.size]),
                            )
                            Text(
                                text = "${item.emoji} ${item.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${(item.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceMutedDark,
                            )
                        }
                    }
                    if (breakdown.size > 5) {
                        Text(
                            text = "+${breakdown.size - 5} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun DrawScope.drawDonut(breakdown: List<CategoryBreakdown>) {
    val strokeWidth = size.minDimension * 0.18f
    val radius = (size.minDimension - strokeWidth) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    var startAngle = -90f
    val gapDeg = 2f

    breakdown.forEachIndexed { index, item ->
        val sweep = (item.fraction * 360f).toFloat() - gapDeg
        if (sweep > 0) {
            drawArc(
                color = DONUT_PALETTE[index % DONUT_PALETTE.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
            )
        }
        startAngle += (item.fraction * 360f).toFloat()
    }
}

// ── Debit vs credit split bar ──────────────────────────────────────────────────

@Composable
private fun DebitCreditSplitCard(
    totalSpent: Double,
    totalReceived: Double,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        SectionHeader(title = "Debit vs credit")
        val grandTotal = totalSpent + totalReceived
        if (grandTotal == 0.0) {
            EmptyChartMessage(modifier = Modifier.padding(16.dp))
        } else {
            val debitFraction = (totalSpent / grandTotal).toFloat().coerceIn(0f, 1f)
            val creditFraction = 1f - debitFraction

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Split bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(PillShape),
                ) {
                    if (debitFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(debitFraction)
                                .fillMaxSize()
                                .background(DebitAmber),
                        )
                    }
                    if (creditFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(creditFraction)
                                .fillMaxSize()
                                .background(CreditGreen),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = DebitAmber.copy(alpha = 0.8f),
                        )
                        MoneyText(
                            amount = totalSpent,
                            direction = "debit",
                            compact = true,
                            style = AmountMedium.copy(fontSize = 16.sp),
                        )
                        Text(
                            text = "${(debitFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = CreditGreen.copy(alpha = 0.8f),
                        )
                        MoneyText(
                            amount = totalReceived,
                            direction = "credit",
                            compact = true,
                            style = AmountMedium.copy(fontSize = 16.sp),
                        )
                        Text(
                            text = "${(creditFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceMutedDark,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Top counterparties ─────────────────────────────────────────────────────────

@Composable
private fun TopCounterpartiesCard(
    counterparties: List<CounterpartyTotal>,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        SectionHeader(title = "Top payees")
        if (counterparties.isEmpty()) {
            EmptyChartMessage(modifier = Modifier.padding(16.dp))
        } else {
            val maxTotal = counterparties.maxOf { it.total }.takeIf { it > 0 } ?: 1.0

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                counterparties.forEach { item ->
                    val fraction = (item.total / maxTotal).toFloat().coerceIn(0f, 1f)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            MoneyTextSmall(
                                amount = item.total,
                                direction = "debit",
                                short = true,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(PillShape)
                                .background(DebitAmber.copy(alpha = 0.15f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxSize()
                                    .background(DebitAmber),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Status strip ───────────────────────────────────────────────────────────────

@Composable
private fun StatusStripCard(
    pending: Int,
    lastRunAt: Long,
    nextRunAt: Long?,
    processNowQueued: Boolean,
    onProcessNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(PillShape)
                            .background(if (pending > 0) DebitAmber else CreditGreen.copy(alpha = 0.6f)),
                    )
                    Text(
                        text = if (pending > 0) "$pending pending" else "All processed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Last run: ${relativeTime(lastRunAt)}  ·  Next: ${nextRunText(nextRunAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMutedDark,
                )
                if (processNowQueued) {
                    Text(
                        text = "Queued",
                        style = MaterialTheme.typography.labelSmall,
                        color = CreditGreen,
                    )
                }
            }

            Button(
                onClick = onProcessNow,
                enabled = !processNowQueued,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGold.copy(alpha = 0.15f),
                    contentColor = BrandGold,
                    disabledContainerColor = BrandGold.copy(alpha = 0.06f),
                    disabledContentColor = OnSurfaceMutedDark,
                ),
                shape = PillShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (processNowQueued) "Queued" else "Process now",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Recent transactions ────────────────────────────────────────────────────────

@Composable
private fun RecentTransactionsCard(
    transactions: List<TransactionEntity>,
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "Recent")
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelMedium,
                color = BrandGold,
                modifier = Modifier
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        Column {
            transactions.forEach { txn ->
                TransactionRow(
                    transaction = txn,
                    onClick = { onTransactionClick(txn.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
) {
    val category = Category.fromId(transaction.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryGlyph(category = category, size = 36.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.counterparty ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = category?.label ?: "Other",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceMutedDark,
            )
        }

        MoneyText(
            amount = transaction.amount ?: 0.0,
            direction = transaction.direction,
            compact = true,
            style = AmountMedium.copy(fontSize = 14.sp),
        )
    }
}

// ── Empty states ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No transactions yet",
            style = HeroSerif.copy(fontSize = 28.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Process your SMS messages to see spending insights here.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceMutedDark,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyChartMessage(modifier: Modifier = Modifier) {
    Text(
        text = "No data for this period",
        style = MaterialTheme.typography.bodySmall,
        color = OnSurfaceMutedDark,
        modifier = modifier,
    )
}
