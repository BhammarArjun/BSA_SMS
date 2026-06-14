package com.local.smsllm.ui.transactions

import com.local.smsllm.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the transactions list filter + date-grouping logic. */
class TransactionsListLogicTest {

    // Fixed reference "now": 2026-06-14 12:00:00 IST-ish (epoch millis, UTC-based).
    private val now = 1_781_524_800_000L // arbitrary but fixed
    private val oneDay = 86_400_000L

    private fun txn(
        id: Long,
        createdAt: Long,
        direction: String? = "debit",
        amount: Double? = 100.0,
        counterparty: String? = "Swiggy",
        category: String? = "food",
        confidence: Double = 0.9,
        isTransaction: Boolean = true,
        includedInAnalytics: Boolean = true,
    ) = TransactionEntity(
        id = id,
        smsId = id,
        isTransaction = isTransaction,
        direction = direction,
        amount = amount,
        currency = "INR",
        dateText = null,
        dateEpoch = null,
        counterparty = counterparty,
        category = category,
        confidence = confidence,
        rawModelOutput = "{}",
        modelId = "qwen3_0_6b",
        backend = "CPU",
        includedInAnalytics = includedInAnalytics,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun allItems(sections: List<DateSection>) = sections.flatMap { it.items }

    @Test
    fun `search matches counterparty case-insensitively`() {
        val list = listOf(
            txn(1, now, counterparty = "Zomato"),
            txn(2, now, counterparty = "Swiggy"),
        )
        val out = filterAndGroup(list, TransactionsFilter(query = "zoMA"), now)
        val items = allItems(out)
        assertEquals(1, items.size)
        assertEquals(1L, items.first().id)
    }

    @Test
    fun `category filter keeps only matching ids`() {
        val list = listOf(
            txn(1, now, category = "food"),
            txn(2, now, category = "transport"),
            txn(3, now, category = null),
        )
        val out = filterAndGroup(list, TransactionsFilter(categoryIds = setOf("food")), now)
        assertEquals(listOf(1L), allItems(out).map { it.id })
    }

    @Test
    fun `direction filter keeps only that direction`() {
        val list = listOf(
            txn(1, now, direction = "debit"),
            txn(2, now, direction = "credit"),
        )
        val out = filterAndGroup(list, TransactionsFilter(direction = "credit"), now)
        assertEquals(listOf(2L), allItems(out).map { it.id })
    }

    @Test
    fun `min confidence excludes low-confidence rows`() {
        val list = listOf(
            txn(1, now, confidence = 0.4),
            txn(2, now, confidence = 0.95),
        )
        val out = filterAndGroup(list, TransactionsFilter(minConfidence = 0.5), now)
        assertEquals(listOf(2L), allItems(out).map { it.id })
    }

    @Test
    fun `groups into Today Yesterday and dated sections`() {
        val list = listOf(
            txn(1, now),                       // today
            txn(2, now - oneDay),              // yesterday
            txn(3, now - (5 * oneDay)),        // older -> "d MMM"
        )
        val out = filterAndGroup(list, TransactionsFilter(), now)
        val labels = out.map { it.label }
        assertEquals("Today", labels[0])
        assertEquals("Yesterday", labels[1])
        // third section is a formatted day label, not Today/Yesterday
        assertTrue(labels[2] != "Today" && labels[2] != "Yesterday")
        assertEquals(3, allItems(out).size)
    }

    @Test
    fun `sections are most-recent-first and items sorted desc within`() {
        val list = listOf(
            txn(1, now - 1000),
            txn(2, now),
            txn(3, now - oneDay),
        )
        val out = filterAndGroup(list, TransactionsFilter(), now)
        // First section "Today" should have id 2 before id 1 (newer first)
        assertEquals("Today", out.first().label)
        assertEquals(listOf(2L, 1L), out.first().items.map { it.id })
    }

    @Test
    fun `empty filter returns all rows including excluded ones`() {
        val list = listOf(
            txn(1, now, includedInAnalytics = true, isTransaction = true),
            txn(2, now, includedInAnalytics = false, isTransaction = false),
        )
        val out = filterAndGroup(list, TransactionsFilter(), now)
        assertEquals(2, allItems(out).size)
    }
}
