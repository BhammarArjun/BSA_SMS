package com.local.smsllm.work

import com.local.smsllm.repo.clampInterval
import org.junit.Assert.assertEquals
import org.junit.Test

class ClampIntervalTest {

    @Test
    fun `clampInterval returns minimum 15 for values below 15`() {
        assertEquals(15, clampInterval(0))
        assertEquals(15, clampInterval(1))
        assertEquals(15, clampInterval(14))
    }

    @Test
    fun `clampInterval returns 15 for exactly 15`() {
        assertEquals(15, clampInterval(15))
    }

    @Test
    fun `clampInterval returns the value unchanged for values above 15`() {
        assertEquals(30, clampInterval(30))
        assertEquals(60, clampInterval(60))
        assertEquals(1440, clampInterval(1440))
    }

    @Test
    fun `clampInterval handles negative values by returning 15`() {
        assertEquals(15, clampInterval(-1))
        assertEquals(15, clampInterval(Int.MIN_VALUE))
    }
}
