package com.local.smsllm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTest {

    @Test
    fun `fromId returns matching entry for known id`() {
        assertEquals(Category.FOOD, Category.fromId("food"))
    }

    @Test
    fun `fromId returns null for unknown id`() {
        assertNull(Category.fromId("nope"))
    }

    @Test
    fun `fromId returns null for null input`() {
        assertNull(Category.fromId(null))
    }

    @Test
    fun `entries has exactly 20 categories`() {
        assertEquals(20, Category.entries.size)
    }

    @Test
    fun `all ids are unique`() {
        val ids = Category.entries.map { it.id }
        assertEquals("Ids must be unique", ids.distinct().size, ids.size)
    }

    @Test
    fun `all expected ids are present`() {
        val expectedIds = listOf(
            "food", "groceries", "shopping", "transport", "fuel",
            "bills_utilities", "recharge", "rent", "emi_loan", "investment",
            "insurance", "health", "education", "entertainment", "travel",
            "transfer", "income_salary", "refund_cashback", "cash_atm", "other"
        )
        val actualIds = Category.entries.map { it.id }
        for (id in expectedIds) {
            assertTrue("Missing id: $id", actualIds.contains(id))
        }
    }

    @Test
    fun `fromId round-trips all entries`() {
        for (cat in Category.entries) {
            assertEquals(cat, Category.fromId(cat.id))
        }
    }

    @Test
    fun `entries are in spec order`() {
        val expectedIds = listOf(
            "food", "groceries", "shopping", "transport", "fuel",
            "bills_utilities", "recharge", "rent", "emi_loan", "investment",
            "insurance", "health", "education", "entertainment", "travel",
            "transfer", "income_salary", "refund_cashback", "cash_atm", "other"
        )
        assertEquals(expectedIds, Category.entries.map { it.id })
    }
}
