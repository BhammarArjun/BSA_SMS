package com.local.smsllm.ui.transactions

import com.local.smsllm.data.TransactionEntity
import java.util.Calendar

// ── Filter parameters ─────────────────────────────────────────────────────────

data class TransactionsFilter(
    val query: String = "",
    val categoryIds: Set<String> = emptySet(),   // empty = all
    val direction: String? = null,               // null = all, "debit", "credit"
    val minConfidence: Double = 0.0,
    val fromEpoch: Long? = null,
    val toEpoch: Long? = null,
)

// ── Output structures ─────────────────────────────────────────────────────────

data class DateSection(
    val label: String,
    val items: List<TransactionEntity>,
)

// ── Pure logic — no Android deps ──────────────────────────────────────────────

/**
 * Applies [filter] to [transactions] and groups the result into dated sections.
 *
 * @param transactions Raw list from observeAll() (all rows, including excluded ones).
 * @param filter       Active filter state.
 * @param nowMs        Current time in millis (injected so the logic is unit-testable).
 * @return List of [DateSection] sorted most-recent first.
 */
fun filterAndGroup(
    transactions: List<TransactionEntity>,
    filter: TransactionsFilter,
    nowMs: Long,
): List<DateSection> {
    val filtered = transactions.filter { txn -> matches(txn, filter) }

    // Sort descending by createdAt
    val sorted = filtered.sortedByDescending { it.createdAt }

    // Compute midnight boundaries for "Today" and "Yesterday"
    val todayStart = dayStart(nowMs)
    val yesterdayStart = todayStart - 86_400_000L

    // Group into sections keyed by calendar day string
    val groups = LinkedHashMap<String, MutableList<TransactionEntity>>()
    for (txn in sorted) {
        val label = when {
            txn.createdAt >= todayStart -> "Today"
            txn.createdAt >= yesterdayStart -> "Yesterday"
            else -> formatDayLabel(txn.createdAt)
        }
        groups.getOrPut(label) { mutableListOf() }.add(txn)
    }

    return groups.map { (label, items) -> DateSection(label, items) }
}

/** Returns true when the transaction matches all active filter criteria. */
fun matches(txn: TransactionEntity, filter: TransactionsFilter): Boolean {
    // Search (counterparty, case-insensitive)
    if (filter.query.isNotBlank()) {
        val q = filter.query.trim().lowercase()
        val cp = txn.counterparty?.lowercase() ?: ""
        if (!cp.contains(q)) return false
    }

    // Category
    if (filter.categoryIds.isNotEmpty()) {
        if (txn.category == null || txn.category !in filter.categoryIds) return false
    }

    // Direction
    if (filter.direction != null) {
        if (txn.direction?.lowercase() != filter.direction.lowercase()) return false
    }

    // Confidence
    if (txn.confidence < filter.minConfidence) return false

    // Date range
    if (filter.fromEpoch != null && txn.createdAt < filter.fromEpoch) return false
    if (filter.toEpoch != null && txn.createdAt > filter.toEpoch) return false

    return true
}

// ── Date helpers ──────────────────────────────────────────────────────────────

/** Returns the epoch-ms of midnight (00:00:00.000) of the day containing [epochMs]. */
fun dayStart(epochMs: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Formats [epochMs] as "d MMM" (e.g. "12 Jun"). */
fun formatDayLabel(epochMs: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = epochMs
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = MONTH_ABBREVS[cal.get(Calendar.MONTH)]
    return "$day $month"
}

private val MONTH_ABBREVS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
