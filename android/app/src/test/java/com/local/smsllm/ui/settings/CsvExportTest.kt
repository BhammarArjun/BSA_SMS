package com.local.smsllm.ui.settings

import com.local.smsllm.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeTxn(
        id: Long = 1L,
        smsId: Long = 10L,
        isTransaction: Boolean = true,
        direction: String? = "debit",
        amount: Double? = 250.0,
        currency: String? = "INR",
        dateText: String? = "01-Jun-26",
        dateEpoch: Long? = 1_748_736_000L,
        counterparty: String? = "Amazon Pay",
        category: String? = "shopping",
        confidence: Double = 0.95,
        modelId: String = "qwen3_0_6b",
        backend: String = "CPU",
        userEdited: Boolean = false,
        includedInAnalytics: Boolean = true,
        createdAt: Long = 1_748_736_100L,
        updatedAt: Long = 1_748_736_100L,
    ) = TransactionEntity(
        id = id,
        smsId = smsId,
        isTransaction = isTransaction,
        direction = direction,
        amount = amount,
        currency = currency,
        dateText = dateText,
        dateEpoch = dateEpoch,
        counterparty = counterparty,
        category = category,
        confidence = confidence,
        rawModelOutput = "",
        modelId = modelId,
        backend = backend,
        userEdited = userEdited,
        includedInAnalytics = includedInAnalytics,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun parseCsvRows(csv: String): List<List<String>> =
        csv.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                // naive CSV split for testing — assumes no escaped quotes in test data columns
                // except where we explicitly test them
                line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                    .map { it.trim('"').replace("\"\"", "\"") }
            }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `header row is present and has correct column names`() {
        val csv = transactionsToCsv(emptyList())
        val rows = parseCsvRows(csv)
        assertEquals(1, rows.size)
        val header = rows[0]
        assertEquals("id", header[0])
        assertEquals("smsId", header[1])
        assertEquals("isTransaction", header[2])
        assertEquals("direction", header[3])
        assertEquals("amount", header[4])
        assertEquals("counterparty", header[8])
        assertEquals("confidence", header[10])
    }

    @Test
    fun `empty list produces header only`() {
        val csv = transactionsToCsv(emptyList())
        // Should have exactly one CRLF-terminated row — the header
        val rows = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(1, rows.size)
    }

    @Test
    fun `counterparty containing a comma is quoted`() {
        val txn = makeTxn(counterparty = "Swiggy, Instamart")
        val csv = transactionsToCsv(listOf(txn))
        // The counterparty field must appear quoted in the raw CSV string
        assertTrue(
            "Expected quoted counterparty in CSV: $csv",
            csv.contains("\"Swiggy, Instamart\""),
        )
        // And parsing gives back the unquoted value
        val rows = parseCsvRows(csv)
        val dataRow = rows[1]
        assertEquals("Swiggy, Instamart", dataRow[8])
    }

    @Test
    fun `field containing a double-quote is escaped by doubling`() {
        val txn = makeTxn(counterparty = "Say \"Hello\" Bank")
        val csv = transactionsToCsv(listOf(txn))
        // Escaped form — the outer quotes plus the doubled interior quotes
        assertTrue(
            "Expected escaped double-quotes in CSV: $csv",
            csv.contains("\"Say \"\"Hello\"\" Bank\""),
        )
        // Parsing gives back the original string
        val rows = parseCsvRows(csv)
        val dataRow = rows[1]
        assertEquals("Say \"Hello\" Bank", dataRow[8])
    }

    @Test
    fun `amount and confidence are formatted as decimals`() {
        val txn = makeTxn(amount = 1234.5, confidence = 0.875)
        val csv = transactionsToCsv(listOf(txn))
        val rows = parseCsvRows(csv)
        val dataRow = rows[1]
        // amount column index = 4
        assertEquals("1234.5000", dataRow[4])
        // confidence column index = 10
        assertEquals("0.875000", dataRow[10])
    }

    @Test
    fun `null optional fields are written as empty strings`() {
        val txn = makeTxn(
            direction = null,
            amount = null,
            currency = null,
            dateText = null,
            dateEpoch = null,
            counterparty = null,
            category = null,
        )
        val csv = transactionsToCsv(listOf(txn))
        val rows = parseCsvRows(csv)
        val dataRow = rows[1]
        assertEquals("", dataRow[3])  // direction
        assertEquals("", dataRow[4])  // amount
        assertEquals("", dataRow[5])  // currency
        assertEquals("", dataRow[6])  // dateText
        assertEquals("", dataRow[7])  // dateEpoch
        assertEquals("", dataRow[8])  // counterparty
        assertEquals("", dataRow[9])  // category
    }

    @Test
    fun `multiple rows produce correct row count`() {
        val txns = listOf(
            makeTxn(id = 1L, smsId = 10L),
            makeTxn(id = 2L, smsId = 11L),
            makeTxn(id = 3L, smsId = 12L),
        )
        val csv = transactionsToCsv(txns)
        val rows = csv.split("\r\n").filter { it.isNotEmpty() }
        assertEquals(4, rows.size) // 1 header + 3 data rows
    }
}
