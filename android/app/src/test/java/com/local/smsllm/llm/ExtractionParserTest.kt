package com.local.smsllm.llm

import com.local.smsllm.domain.Category
import com.local.smsllm.domain.TxnDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionParserTest {

    // --- Example 1: debit/350/food from prompt examples ---
    @Test
    fun `parses example 1 debit zomato to food`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":350,"currency":"INR","date":"03-01-26","counterparty":"zomato@upi","category":"food","confidence":0.97}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.DEBIT, result.direction)
        assertEquals(350.0, result.amount)
        assertEquals("INR", result.currency)
        assertEquals("03-01-26", result.dateText)
        assertEquals("zomato@upi", result.counterparty)
        assertEquals(Category.FOOD, result.category)
        assertEquals(0.97, result.confidence, 0.001)
        assertEquals(json, result.raw)
    }

    // --- Example 2: credit/55000/income_salary ---
    @Test
    fun `parses example 2 credit NEFT salary`() {
        val json = """{"is_transaction":true,"direction":"credit","amount":55000,"currency":"INR","date":"01-Jun-26","counterparty":"ACME PAYROLL","category":"income_salary","confidence":0.95}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.CREDIT, result.direction)
        assertEquals(55000.0, result.amount)
        assertEquals("income_salary", result.category?.id)
        assertEquals(0.95, result.confidence, 0.001)
    }

    // --- Example 3: OTP / non-transaction ---
    @Test
    fun `parses example 3 OTP as non-transaction with nulls`() {
        val json = """{"is_transaction":false,"direction":null,"amount":null,"currency":null,"date":null,"counterparty":null,"category":null,"confidence":0.99}"""
        val result = ExtractionParser.parse(json)
        assertFalse(result.isTransaction)
        assertNull(result.direction)
        assertNull(result.amount)
        assertNull(result.currency)
        assertNull(result.dateText)
        assertNull(result.counterparty)
        assertNull(result.category)
        assertEquals(0.99, result.confidence, 0.001)
    }

    // --- Malformed: Gemma-style doubled-quote corruption ---
    @Test
    fun `repairs Gemma doubled-quote corruption debit comma amount`() {
        val malformed = """{"is_transaction":true,"direction":"debit,""amount":2499,"currency":"INR","category":"shopping","confidence":0.8}"""
        val result = ExtractionParser.parse(malformed)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.DEBIT, result.direction)
        assertEquals(2499.0, result.amount)
        assertEquals(Category.SHOPPING, result.category)
    }

    // --- Think block stripping ---
    @Test
    fun `strips think block before parsing`() {
        val input = "<think>\n\n</think>\n{\"is_transaction\":true,\"direction\":\"credit\",\"amount\":100,\"confidence\":0.5}"
        val result = ExtractionParser.parse(input)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.CREDIT, result.direction)
        assertEquals(100.0, result.amount)
        assertEquals(0.5, result.confidence, 0.001)
        assertEquals(input, result.raw)
    }

    // --- Amount as string ---
    @Test
    fun `parses amount when given as string with commas`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":"2,499.00","currency":"INR","confidence":0.9}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(2499.0, result.amount)
    }

    // --- Markdown fenced ---
    @Test
    fun `parses JSON inside markdown code fence`() {
        val input = "```json\n{\"is_transaction\":true,\"direction\":\"debit\",\"amount\":500,\"currency\":\"INR\",\"confidence\":0.85}\n```"
        val result = ExtractionParser.parse(input)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.DEBIT, result.direction)
        assertEquals(500.0, result.amount)
    }

    // --- Garbage input ---
    @Test
    fun `returns safe default for garbage input`() {
        val garbage = "hello world no json"
        val result = ExtractionParser.parse(garbage)
        assertFalse(result.isTransaction)
        assertEquals(0.0, result.confidence, 0.001)
        assertEquals(garbage, result.raw)
    }

    // --- Trailing comma ---
    @Test
    fun `parses JSON with trailing comma before closing brace`() {
        val json = """{"is_transaction":false,"confidence":0.99,}"""
        val result = ExtractionParser.parse(json)
        assertFalse(result.isTransaction)
        assertEquals(0.99, result.confidence, 0.001)
    }

    // --- Unknown category ---
    @Test
    fun `unknown category in JSON maps to null category but isTransaction stays true`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":5,"category":"foobar","confidence":0.4}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertNull(result.category)
        assertEquals(5.0, result.amount)
    }

    // --- Confidence clamping ---
    @Test
    fun `confidence out of range 1point7 clamps to 1point0`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":100,"currency":"INR","confidence":1.7}"""
        val result = ExtractionParser.parse(json)
        assertEquals(1.0, result.confidence, 0.001)
    }

    @Test
    fun `confidence negative clamps to 0point0`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":100,"currency":"INR","confidence":-0.5}"""
        val result = ExtractionParser.parse(json)
        assertEquals(0.0, result.confidence, 0.001)
    }

    // --- Clean valid JSON passes through repair unchanged ---
    @Test
    fun `clean valid JSON parses correctly without corruption`() {
        val json = """{"is_transaction":true,"direction":"credit","amount":10000,"currency":"INR","date":"01-Jan-26","counterparty":"ACME","category":"income_salary","confidence":0.98}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(TxnDirection.CREDIT, result.direction)
        assertEquals(10000.0, result.amount)
        assertEquals(Category.INCOME_SALARY, result.category)
        assertEquals(0.98, result.confidence, 0.001)
    }

    // --- Non-transaction nulls out fields ---
    @Test
    fun `when isTransaction false all other fields are nulled out`() {
        val json = """{"is_transaction":false,"direction":"debit","amount":500,"currency":"INR","date":"01-Jan-26","counterparty":"someone","category":"food","confidence":0.9}"""
        val result = ExtractionParser.parse(json)
        assertFalse(result.isTransaction)
        assertNull(result.direction)
        assertNull(result.amount)
        assertNull(result.currency)
        assertNull(result.dateText)
        assertNull(result.counterparty)
        assertNull(result.category)
    }

    // --- Think block with content ---
    @Test
    fun `strips think block with content`() {
        val input = "<think>Let me analyze this SMS carefully...</think>\n{\"is_transaction\":true,\"direction\":\"debit\",\"amount\":200,\"confidence\":0.8}"
        val result = ExtractionParser.parse(input)
        assertTrue(result.isTransaction)
        assertEquals(200.0, result.amount)
    }

    // --- raw always equals original input ---
    @Test
    fun `raw field always equals original model output`() {
        val original = "  <think></think> {\"is_transaction\":false,\"confidence\":0.5}  "
        val result = ExtractionParser.parse(original)
        assertEquals(original, result.raw)
    }

    // --- C1: brace extractor is string-aware (brace inside quoted value) ---
    @Test
    fun `C1 brace inside string value does not truncate extraction`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":5,"counterparty":"A}B","confidence":0.9}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals("A}B", result.counterparty)
        assertEquals(5.0, result.amount)
    }

    // --- C1: nested-object sanity – extractor returns the full outer object ---
    @Test
    fun `C1 nested object is returned in full by extractor`() {
        val json = """{"a":{"b":1},"is_transaction":false,"confidence":0.5}"""
        val result = ExtractionParser.parse(json)
        assertFalse(result.isTransaction)
        assertEquals(0.5, result.confidence, 0.001)
    }

    // --- I1: Rs.-prefixed amount parses correctly ---
    @Test
    fun `I1 Rs dot prefixed amount parses correctly`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":"Rs.2,499.00","currency":"INR","confidence":0.9}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(2499.0, result.amount)
    }

    // --- m3: amount zero is preserved, not treated as null ---
    @Test
    fun `m3 amount zero is preserved`() {
        val json = """{"is_transaction":true,"direction":"credit","amount":0,"confidence":0.8}"""
        val result = ExtractionParser.parse(json)
        assertTrue(result.isTransaction)
        assertEquals(0.0, result.amount)
    }

    // --- m2: confidence given as string parses correctly ---
    @Test
    fun `m2 confidence as string parses to double`() {
        val json = """{"is_transaction":true,"direction":"debit","amount":100,"confidence":"0.9"}"""
        val result = ExtractionParser.parse(json)
        assertEquals(0.9, result.confidence, 0.001)
    }
}
