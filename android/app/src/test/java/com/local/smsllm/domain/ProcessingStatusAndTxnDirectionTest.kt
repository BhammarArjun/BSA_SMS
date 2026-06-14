package com.local.smsllm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessingStatusTest {

    @Test
    fun `valueOf round-trips all ProcessingStatus values`() {
        for (status in ProcessingStatus.entries) {
            assertEquals(status, ProcessingStatus.valueOf(status.name))
        }
    }
}

class TxnDirectionTest {

    @Test
    fun `fromString returns DEBIT for uppercase DEBIT`() {
        assertEquals(TxnDirection.DEBIT, TxnDirection.fromString("DEBIT"))
    }

    @Test
    fun `fromString returns CREDIT for lowercase credit`() {
        assertEquals(TxnDirection.CREDIT, TxnDirection.fromString("credit"))
    }

    @Test
    fun `fromString returns DEBIT case insensitive mixed`() {
        assertEquals(TxnDirection.DEBIT, TxnDirection.fromString("Debit"))
    }

    @Test
    fun `fromString returns CREDIT case insensitive mixed`() {
        assertEquals(TxnDirection.CREDIT, TxnDirection.fromString("Credit"))
    }

    @Test
    fun `fromString returns null for unknown string`() {
        assertNull(TxnDirection.fromString("x"))
    }

    @Test
    fun `fromString returns null for null input`() {
        assertNull(TxnDirection.fromString(null))
    }

    @Test
    fun `valueOf round-trips all TxnDirection values`() {
        for (dir in TxnDirection.entries) {
            assertEquals(dir, TxnDirection.valueOf(dir.name))
        }
    }
}
