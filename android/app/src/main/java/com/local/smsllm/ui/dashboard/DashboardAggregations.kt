package com.local.smsllm.ui.dashboard

import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.Category
import java.util.Calendar

// ── Period definition ─────────────────────────────────────────────────────────

enum class Period { THIS_MONTH, LAST_MONTH, ALL }

/** Closed epoch-millis range [start, end). Null means unbounded. */
data class EpochRange(val start: Long?, val end: Long?)

fun periodRange(period: Period, nowMs: Long = System.currentTimeMillis()): EpochRange {
    if (period == Period.ALL) return EpochRange(null, null)

    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }

    return when (period) {
        Period.THIS_MONTH -> {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            EpochRange(start, null) // open end = now
        }
        Period.LAST_MONTH -> {
            // End = first moment of current month
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val end = cal.timeInMillis

            // Start = first moment of previous month
            cal.add(Calendar.MONTH, -1)
            val start = cal.timeInMillis

            EpochRange(start, end)
        }
        Period.ALL -> EpochRange(null, null)
    }
}

// ── Filter helpers ────────────────────────────────────────────────────────────

/**
 * Filters the list to the given period range and confidence threshold.
 * Uses createdAt as the reliable timestamp (dateEpoch is often null).
 */
fun List<TransactionEntity>.filterPeriod(
    range: EpochRange,
    confidenceThreshold: Double,
): List<TransactionEntity> = filter { txn ->
    val aboveThreshold = confidenceThreshold <= 0.0 || txn.confidence >= confidenceThreshold
    val inRange = (range.start == null || txn.createdAt >= range.start) &&
        (range.end == null || txn.createdAt < range.end)
    aboveThreshold && inRange
}

// ── Aggregated output types ───────────────────────────────────────────────────

data class SpendBucket(val label: String, val amount: Double)

data class CategoryBreakdown(
    val category: Category?,
    val label: String,
    val emoji: String,
    val total: Double,
    val count: Int,
    val fraction: Double,
)

data class CounterpartyTotal(val name: String, val total: Double)

data class DashboardData(
    val totalSpent: Double,          // sum of debit amounts
    val totalReceived: Double,       // sum of credit amounts
    val net: Double,                 // received - spent
    val spendBuckets: List<SpendBucket>,      // spend over time
    val categoryBreakdown: List<CategoryBreakdown>,
    val topCounterparties: List<CounterpartyTotal>,
    val recentTransactions: List<TransactionEntity>,
    val txnCount: Int,
)

val EmptyDashboardData = DashboardData(
    totalSpent = 0.0,
    totalReceived = 0.0,
    net = 0.0,
    spendBuckets = emptyList(),
    categoryBreakdown = emptyList(),
    topCounterparties = emptyList(),
    recentTransactions = emptyList(),
    txnCount = 0,
)

// ── Main aggregation entry point ──────────────────────────────────────────────

/**
 * Pure aggregation function — no Android dependencies, fully unit-testable.
 *
 * @param txns Already filtered to includedInAnalytics=1 (from observeIncluded()).
 * @param period Selected period for time bucketing.
 * @param confidenceThreshold Minimum confidence score; 0.0 means no filter.
 * @param nowMs Current time in epoch millis (injectable for testing).
 */
fun aggregate(
    txns: List<TransactionEntity>,
    period: Period,
    confidenceThreshold: Double,
    nowMs: Long = System.currentTimeMillis(),
): DashboardData {
    val range = periodRange(period, nowMs)
    val filtered = txns.filterPeriod(range, confidenceThreshold)

    if (filtered.isEmpty()) return EmptyDashboardData

    // ── Summary totals ──────────────────────────────────────────────────────
    val totalSpent = filtered
        .filter { it.direction?.lowercase() == "debit" }
        .sumOf { it.amount ?: 0.0 }

    val totalReceived = filtered
        .filter { it.direction?.lowercase() == "credit" }
        .sumOf { it.amount ?: 0.0 }

    val net = totalReceived - totalSpent

    // ── Spend over time (debit only, bucketed by day or week) ──────────────
    val spendBuckets = buildSpendBuckets(filtered, period, range)

    // ── Category breakdown (debit only, sorted desc by total) ──────────────
    val categoryBreakdown = buildCategoryBreakdown(filtered)

    // ── Top counterparties (debit, top 5 by absolute amount) ───────────────
    val topCounterparties = filtered
        .filter { it.direction?.lowercase() == "debit" && (it.amount ?: 0.0) > 0.0 }
        .groupBy { it.counterparty?.trim()?.ifBlank { null } ?: "Unknown" }
        .map { (name, list) -> CounterpartyTotal(name, list.sumOf { it.amount ?: 0.0 }) }
        .sortedByDescending { it.total }
        .take(5)

    // ── Recent transactions (latest 5) ──────────────────────────────────────
    val recentTransactions = filtered
        .sortedByDescending { it.createdAt }
        .take(5)

    return DashboardData(
        totalSpent = totalSpent,
        totalReceived = totalReceived,
        net = net,
        spendBuckets = spendBuckets,
        categoryBreakdown = categoryBreakdown,
        topCounterparties = topCounterparties,
        recentTransactions = recentTransactions,
        txnCount = filtered.size,
    )
}

// ── Bucketing helpers ─────────────────────────────────────────────────────────

/**
 * Builds spend (debit) buckets over time.
 * Uses day buckets for THIS_MONTH/LAST_MONTH, week buckets for ALL.
 */
internal fun buildSpendBuckets(
    txns: List<TransactionEntity>,
    period: Period,
    range: EpochRange,
): List<SpendBucket> {
    val debits = txns.filter { it.direction?.lowercase() == "debit" && (it.amount ?: 0.0) > 0.0 }
    if (debits.isEmpty()) return emptyList()

    return when (period) {
        Period.ALL -> buildWeekBuckets(debits)
        else -> buildDayBuckets(debits, range)
    }
}

private fun buildDayBuckets(
    debits: List<TransactionEntity>,
    range: EpochRange,
): List<SpendBucket> {
    // Group by "day of month" derived from createdAt
    val grouped = mutableMapOf<Int, Double>()
    debits.forEach { txn ->
        val cal = Calendar.getInstance().apply { timeInMillis = txn.createdAt }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        grouped[day] = (grouped[day] ?: 0.0) + (txn.amount ?: 0.0)
    }
    return grouped.entries
        .sortedBy { it.key }
        .map { (day, amount) -> SpendBucket(day.toString(), amount) }
}

private fun buildWeekBuckets(
    debits: List<TransactionEntity>,
): List<SpendBucket> {
    // Group by ISO year-week "YYwWW"
    val grouped = mutableMapOf<String, Double>()
    val weekLabels = mutableMapOf<String, String>() // key → display label
    debits.forEach { txn ->
        val cal = Calendar.getInstance().apply { timeInMillis = txn.createdAt }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        val key = "$year-$week"
        // Label as "Wk N" for compactness
        weekLabels.getOrPut(key) { "W$week" }
        grouped[key] = (grouped[key] ?: 0.0) + (txn.amount ?: 0.0)
    }
    return grouped.entries
        .sortedBy { it.key }
        .map { (key, amount) -> SpendBucket(weekLabels[key] ?: key, amount) }
}

// ── Category breakdown ────────────────────────────────────────────────────────

internal fun buildCategoryBreakdown(
    txns: List<TransactionEntity>,
): List<CategoryBreakdown> {
    val debits = txns.filter { it.direction?.lowercase() == "debit" && (it.amount ?: 0.0) > 0.0 }
    val totalDebit = debits.sumOf { it.amount ?: 0.0 }.takeIf { it > 0.0 } ?: return emptyList()

    return debits
        .groupBy { Category.fromId(it.category) }
        .map { (cat, list) ->
            val total = list.sumOf { it.amount ?: 0.0 }
            CategoryBreakdown(
                category = cat,
                label = cat?.label ?: "Other",
                emoji = cat?.emoji ?: "❓",
                total = total,
                count = list.size,
                fraction = total / totalDebit,
            )
        }
        .sortedByDescending { it.total }
}
