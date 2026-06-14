package com.local.smsllm.ui.dashboard

import com.local.smsllm.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Pure JVM tests for [aggregate], [periodRange], and related helpers.
 * No Android deps — TransactionEntity is a plain Kotlin data class.
 */
class DashboardAggregationsTest {

    // ── Fixed time anchor ──────────────────────────────────────────────────────
    // 2024-06-15 12:00:00 UTC
    private val nowMs: Long = run {
        Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // First moment of June 2024 in the local timezone
    private val juneStartMs: Long = run {
        Calendar.getInstance().apply {
            set(2024, Calendar.JUNE, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // First moment of May 2024
    private val mayStartMs: Long = run {
        Calendar.getInstance().apply {
            set(2024, Calendar.MAY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun txn(
        id: Long,
        direction: String,
        amount: Double,
        createdAt: Long,
        category: String? = "food",
        counterparty: String? = "Zomato",
        confidence: Double = 0.95,
    ) = TransactionEntity(
        id = id,
        smsId = id,
        isTransaction = true,
        direction = direction,
        amount = amount,
        currency = "INR",
        dateText = null,
        dateEpoch = null,
        counterparty = counterparty,
        category = category,
        confidence = confidence,
        rawModelOutput = "{}",
        modelId = "test",
        backend = "test",
        userEdited = false,
        includedInAnalytics = true,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun midJune(dayOffset: Int = 0): Long = juneStartMs + dayOffset * 86_400_000L
    private fun midMay(dayOffset: Int = 0): Long = mayStartMs + dayOffset * 86_400_000L

    // ── Test 1: net = received − debits ────────────────────────────────────────

    @Test
    fun `net equals received minus spent`() {
        val txns = listOf(
            txn(1, "credit", 10_000.0, midJune(1)),
            txn(2, "debit",  3_500.0,  midJune(2)),
            txn(3, "debit",  1_000.0,  midJune(3)),
        )
        val data = aggregate(txns, Period.THIS_MONTH, 0.0, nowMs)
        assertEquals(10_000.0, data.totalReceived, 0.01)
        assertEquals(4_500.0,  data.totalSpent,    0.01)
        assertEquals(5_500.0,  data.net,           0.01)
    }

    // ── Test 2: debit/credit summation ─────────────────────────────────────────

    @Test
    fun `debit and credit amounts are summed correctly`() {
        val txns = listOf(
            txn(1, "debit",  200.0, midJune(1)),
            txn(2, "debit",  300.0, midJune(2)),
            txn(3, "credit", 500.0, midJune(3)),
            txn(4, "credit", 250.0, midJune(4)),
        )
        val data = aggregate(txns, Period.ALL, 0.0, nowMs)
        assertEquals(500.0, data.totalSpent,    0.01)
        assertEquals(750.0, data.totalReceived, 0.01)
        assertEquals(250.0, data.net,           0.01)
    }

    // ── Test 3: category grouping totals and fractions sum ≈ 1 ─────────────────

    @Test
    fun `category fractions sum to 1 and totals group correctly`() {
        val txns = listOf(
            txn(1, "debit", 400.0, midJune(1), category = "food"),
            txn(2, "debit", 400.0, midJune(2), category = "food"),
            txn(3, "debit", 200.0, midJune(3), category = "transport"),
        )
        val data = aggregate(txns, Period.THIS_MONTH, 0.0, nowMs)
        val breakdown = data.categoryBreakdown

        // Two categories
        assertEquals(2, breakdown.size)

        // Sorted descending by total — food first
        assertEquals("Food & Dining", breakdown[0].label)
        assertEquals(800.0, breakdown[0].total, 0.01)
        assertEquals(200.0, breakdown[1].total, 0.01)

        // Fractions sum to 1 (within floating point tolerance)
        val fractionSum = breakdown.sumOf { it.fraction }
        assertEquals(1.0, fractionSum, 0.001)
    }

    // ── Test 4: top counterparties sorted desc with null/blank → "Unknown" ─────

    @Test
    fun `top counterparties are sorted desc and null name becomes Unknown`() {
        val txns = listOf(
            txn(1, "debit", 100.0,  midJune(1), counterparty = "Swiggy"),
            txn(2, "debit", 500.0,  midJune(2), counterparty = "Amazon"),
            txn(3, "debit", 300.0,  midJune(3), counterparty = null),
            txn(4, "debit", 200.0,  midJune(4), counterparty = "   "),  // blank
            txn(5, "debit", 150.0,  midJune(5), counterparty = "Uber"),
            txn(6, "debit", 50.0,   midJune(6), counterparty = "Netflix"),
        )
        val data = aggregate(txns, Period.ALL, 0.0, nowMs)
        val top = data.topCounterparties

        // At most 5 entries
        assertTrue(top.size <= 5)

        // First is Amazon (500), second is Unknown (300 + 200 = 500 — same; order may vary)
        // At minimum: Amazon must be in top, Netflix (50) may be excluded
        assertTrue(top.any { it.name == "Amazon" })
        assertTrue(top.any { it.name == "Unknown" })
        assertTrue(top.none { it.name == "   " })
        // No Netflix if the list is trimmed to 5 (300+200 unknown > 150 uber > 100 swiggy > 50 netflix)
        val names = top.map { it.name }
        assertTrue("Netflix should be excluded or last if 6 unique entries found",
            !names.contains("Netflix") || top.size == 5)

        // Sorted descending
        for (i in 0 until top.size - 1) {
            assertTrue(top[i].total >= top[i + 1].total)
        }
    }

    // ── Test 5: THIS_MONTH includes in-range, excludes last-month txns ─────────

    @Test
    fun `THIS_MONTH period filters by createdAt`() {
        val txns = listOf(
            txn(1, "debit", 100.0, midJune(1)),    // in this month
            txn(2, "debit", 200.0, midMay(10)),     // last month — excluded
            txn(3, "credit", 50.0, midJune(5)),     // in this month
        )
        val data = aggregate(txns, Period.THIS_MONTH, 0.0, nowMs)

        assertEquals(2, data.txnCount)
        assertEquals(100.0, data.totalSpent,    0.01)
        assertEquals(50.0,  data.totalReceived, 0.01)
    }

    // ── Test 6: LAST_MONTH includes only previous-month txns ───────────────────

    @Test
    fun `LAST_MONTH period excludes current month and earlier months`() {
        val txns = listOf(
            txn(1, "debit", 500.0, midMay(5)),      // May — included
            txn(2, "debit", 100.0, midJune(2)),     // June — excluded
            txn(3, "debit", 200.0,                  // April — excluded
                mayStartMs - 5 * 86_400_000L),
        )
        val data = aggregate(txns, Period.LAST_MONTH, 0.0, nowMs)

        assertEquals(1, data.txnCount)
        assertEquals(500.0, data.totalSpent, 0.01)
    }

    // ── Test 7: confidenceThreshold filters low-confidence transactions ─────────

    @Test
    fun `confidenceThreshold filters out low-confidence txns`() {
        val txns = listOf(
            txn(1, "debit", 1000.0, midJune(1), confidence = 0.90),
            txn(2, "debit",  500.0, midJune(2), confidence = 0.50),  // below threshold
            txn(3, "credit", 300.0, midJune(3), confidence = 0.80),
        )
        val data = aggregate(txns, Period.ALL, 0.70, nowMs)

        // Only txn 1 (0.90) and txn 3 (0.80) pass the 0.70 threshold
        assertEquals(2, data.txnCount)
        assertEquals(1000.0, data.totalSpent,    0.01)
        assertEquals(300.0,  data.totalReceived, 0.01)
    }

    // ── Test 8: empty input → EmptyDashboardData ───────────────────────────────

    @Test
    fun `empty transaction list returns EmptyDashboardData`() {
        val data = aggregate(emptyList(), Period.THIS_MONTH, 0.0, nowMs)

        assertEquals(0.0, data.totalSpent,    0.0)
        assertEquals(0.0, data.totalReceived, 0.0)
        assertEquals(0.0, data.net,           0.0)
        assertEquals(0,   data.txnCount)
        assertTrue(data.spendBuckets.isEmpty())
        assertTrue(data.categoryBreakdown.isEmpty())
        assertTrue(data.topCounterparties.isEmpty())
        assertTrue(data.recentTransactions.isEmpty())
    }

    // ── Test 9: recentTransactions are capped at 5, sorted by latest first ─────

    @Test
    fun `recent transactions returns latest 5 sorted desc`() {
        val txns = (1..8).map { i ->
            txn(i.toLong(), "debit", i * 100.0, midJune(i))
        }
        val data = aggregate(txns, Period.ALL, 0.0, nowMs)

        assertEquals(5, data.recentTransactions.size)
        // Latest first — day 8 should be first
        assertTrue(data.recentTransactions[0].createdAt >= data.recentTransactions[1].createdAt)
    }
}
