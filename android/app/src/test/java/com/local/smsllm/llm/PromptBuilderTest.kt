package com.local.smsllm.llm

import com.local.smsllm.domain.Category
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.domain.ModelSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `SYSTEM_INSTRUCTION contains food category id`() {
        assertTrue(PromptBuilder.SYSTEM_INSTRUCTION.contains("food"))
    }

    @Test
    fun `SYSTEM_INSTRUCTION contains refund_cashback category id`() {
        assertTrue(PromptBuilder.SYSTEM_INSTRUCTION.contains("refund_cashback"))
    }

    @Test
    fun `SYSTEM_INSTRUCTION contains all 20 category ids`() {
        for (cat in Category.entries) {
            assertTrue(
                "SYSTEM_INSTRUCTION missing category id: ${cat.id}",
                PromptBuilder.SYSTEM_INSTRUCTION.contains(cat.id)
            )
        }
    }

    @Test
    fun `systemInstruction appends no_think for QWEN3_0_6B`() {
        val instruction = PromptBuilder.systemInstruction(ModelRegistry.QWEN3_0_6B)
        assertTrue("Expected /no_think suffix", instruction.endsWith("/no_think"))
    }

    @Test
    fun `systemInstruction does NOT append no_think when needsNoThink=false`() {
        val spec = ModelSpec(
            modelId = "test_model",
            filename = "test.litertlm",
            url = "https://example.com/test.litertlm",
            expectedBytes = 100L,
            needsNoThink = false
        )
        val instruction = PromptBuilder.systemInstruction(spec)
        assertFalse("Should not end with /no_think", instruction.endsWith("/no_think"))
        assertFalse("Should not contain /no_think", instruction.contains("/no_think"))
    }

    @Test
    fun `SYSTEM_INSTRUCTION contains is_transaction schema key`() {
        assertTrue(PromptBuilder.SYSTEM_INSTRUCTION.contains("is_transaction"))
    }

    @Test
    fun `SYSTEM_INSTRUCTION contains direction schema key`() {
        assertTrue(PromptBuilder.SYSTEM_INSTRUCTION.contains("direction"))
    }

    @Test
    fun `SYSTEM_INSTRUCTION contains confidence schema key`() {
        assertTrue(PromptBuilder.SYSTEM_INSTRUCTION.contains("confidence"))
    }
}
