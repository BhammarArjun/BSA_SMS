package com.local.smsllm.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSpecTest {

    @Test
    fun `byId returns QWEN3_0_6B with needsNoThink true`() {
        val spec = ModelRegistry.byId("qwen3_0_6b")
        assertTrue("Expected needsNoThink=true", spec?.needsNoThink == true)
    }

    @Test
    fun `byId returns null for missing id`() {
        assertNull(ModelRegistry.byId("missing"))
    }

    @Test
    fun `ALL list contains QWEN3_0_6B`() {
        assertTrue(ModelRegistry.ALL.contains(ModelRegistry.QWEN3_0_6B))
    }

    @Test
    fun `QWEN3_0_6B has correct modelId`() {
        assertEquals("qwen3_0_6b", ModelRegistry.QWEN3_0_6B.modelId)
    }

    @Test
    fun `QWEN3_0_6B has correct filename`() {
        assertEquals("qwen3_0_6b_mixed_int4.litertlm", ModelRegistry.QWEN3_0_6B.filename)
    }

    @Test
    fun `QWEN3_0_6B has correct expectedBytes`() {
        assertEquals(497_664_000L, ModelRegistry.QWEN3_0_6B.expectedBytes)
    }

    @Test
    fun `QWEN3_0_6B has correct url`() {
        assertEquals(
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            ModelRegistry.QWEN3_0_6B.url
        )
    }
}
