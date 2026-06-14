package com.local.smsllm.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAssemblerTest {

    @Test
    fun `single part single sender produces one assembled message`() {
        val parts = listOf(Triple("HDFC", "Rs.500 debited", 1_000L))
        val result = assembleMessages(parts)
        assertEquals(1, result.size)
        assertEquals("HDFC", result[0].sender)
        assertEquals("Rs.500 debited", result[0].body)
        assertEquals(1_000L, result[0].receivedAt)
    }

    @Test
    fun `two parts same sender concatenates into one body`() {
        val parts = listOf(
            Triple("BANK", "Part one ", 2_000L),
            Triple("BANK", "Part two", 2_001L),
        )
        val result = assembleMessages(parts)
        assertEquals(1, result.size)
        assertEquals("BANK", result[0].sender)
        assertEquals("Part one Part two", result[0].body)
        // timestamp taken from first part
        assertEquals(2_000L, result[0].receivedAt)
    }

    @Test
    fun `parts from two distinct senders produce two assembled messages`() {
        val parts = listOf(
            Triple("HDFC", "Rs.200 debited", 3_000L),
            Triple("ICICI", "INR 100 credited", 3_100L),
        )
        val result = assembleMessages(parts)
        assertEquals(2, result.size)
        val senders = result.map { it.sender }
        assertEquals(listOf("HDFC", "ICICI"), senders)
        assertEquals("Rs.200 debited", result[0].body)
        assertEquals("INR 100 credited", result[1].body)
    }

    @Test
    fun `empty parts list returns empty result`() {
        val result = assembleMessages(emptyList())
        assertEquals(0, result.size)
    }

    @Test
    fun `three parts same sender concatenated in order`() {
        val parts = listOf(
            Triple("SBI", "A", 5_000L),
            Triple("SBI", "B", 5_001L),
            Triple("SBI", "C", 5_002L),
        )
        val result = assembleMessages(parts)
        assertEquals(1, result.size)
        assertEquals("ABC", result[0].body)
        assertEquals(5_000L, result[0].receivedAt)
    }
}
