package com.local.smsllm.domain

/**
 * Structured result produced by parsing the LLM's JSON output for one SMS.
 *
 * @param isTransaction  True when the SMS records actual money movement.
 * @param direction      DEBIT or CREDIT; null when [isTransaction] is false.
 * @param amount         Numeric amount with no currency symbols or commas; null when not applicable.
 * @param currency       ISO code or detected currency string (usually "INR"); null when not applicable.
 * @param dateText       Date string exactly as printed in the SMS; null when absent.
 * @param counterparty   Merchant, person, or VPA/UPI handle; null when not identified.
 * @param category       Expense/income category; null when unrecognised or not applicable.
 * @param confidence     Model's stated certainty, clamped to [0.0, 1.0].
 * @param raw            Original unmodified model output string.
 */
data class ExtractionResult(
    val isTransaction: Boolean,
    val direction: TxnDirection?,
    val amount: Double?,
    val currency: String?,
    val dateText: String?,
    val counterparty: String?,
    val category: Category?,
    val confidence: Double,
    val raw: String,
)
