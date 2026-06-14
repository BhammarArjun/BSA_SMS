package com.local.smsllm.domain

/** Whether money left (DEBIT) or arrived (CREDIT) in the account. */
enum class TxnDirection {
    DEBIT,
    CREDIT;

    companion object {
        /**
         * Maps "debit"/"credit" (case-insensitive) to the corresponding enum entry.
         * Returns null for any other input, including null itself.
         */
        fun fromString(s: String?): TxnDirection? = when (s?.lowercase()) {
            "debit" -> DEBIT
            "credit" -> CREDIT
            else -> null
        }
    }
}
