package com.local.smsllm.ui.settings

import com.local.smsllm.data.TransactionEntity

/**
 * Pure (no-Android) CSV builder for [TransactionEntity] records.
 *
 * Follows RFC 4180:
 *  - Fields containing a comma, double-quote, or newline are enclosed in double-quotes.
 *  - A literal double-quote inside a quoted field is escaped by doubling it ("").
 *  - Null fields are written as empty strings.
 *
 * Security: no raw SMS bodies are present in TransactionEntity; the fields written
 * here (amount, counterparty, category…) are the extracted metadata, not raw message
 * content. This function must never be called with data that would expose PII beyond
 * what the user already sees in the UI.
 */
fun transactionsToCsv(txns: List<TransactionEntity>): String {
    val header = listOf(
        "id", "smsId", "isTransaction", "direction", "amount", "currency",
        "dateText", "dateEpoch", "counterparty", "category", "confidence",
        "modelId", "backend", "userEdited", "includedInAnalytics",
        "createdAt", "updatedAt",
    )

    val sb = StringBuilder()
    sb.appendCsvRow(header)

    for (t in txns) {
        val row = listOf(
            t.id.toString(),
            t.smsId.toString(),
            t.isTransaction.toString(),
            t.direction.orEmpty(),
            t.amount?.let { "%.4f".format(it) }.orEmpty(),
            t.currency.orEmpty(),
            t.dateText.orEmpty(),
            t.dateEpoch?.toString().orEmpty(),
            t.counterparty.orEmpty(),
            t.category.orEmpty(),
            "%.6f".format(t.confidence),
            t.modelId,
            t.backend,
            t.userEdited.toString(),
            t.includedInAnalytics.toString(),
            t.createdAt.toString(),
            t.updatedAt.toString(),
        )
        sb.appendCsvRow(row)
    }

    return sb.toString()
}

/** Appends one RFC-4180 row (CRLF terminated) to the StringBuilder. */
private fun StringBuilder.appendCsvRow(fields: List<String>) {
    fields.forEachIndexed { index, field ->
        if (index > 0) append(',')
        append(field.csvEscape())
    }
    append("\r\n")
}

/**
 * Wraps the field in double-quotes and escapes interior double-quotes by doubling them,
 * but only when necessary (comma, double-quote, CR, or LF present).
 */
private fun String.csvEscape(): String {
    val needsQuoting = contains(',') || contains('"') || contains('\n') || contains('\r')
    return if (needsQuoting) {
        "\"${replace("\"", "\"\"")}\""
    } else {
        this
    }
}
