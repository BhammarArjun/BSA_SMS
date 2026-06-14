package com.local.smsllm.domain

/** Lifecycle state of a raw SMS as it moves through the pipeline. */
enum class ProcessingStatus {
    /** Newly received, not yet inspected by the gate. */
    NEW,
    /** Rejected by the regex gate — not a financial SMS. */
    GATE_REJECTED,
    /** Passed the gate; queued for LLM extraction. */
    PENDING,
    /** LLM extraction completed successfully. */
    PROCESSED,
    /** Confirmed to be a non-transaction SMS (e.g. OTP, promo). */
    NON_TXN,
    /** Extraction result questionable; needs a second pass. */
    NEEDS_REVERIFY,
    /** An unexpected error occurred during processing. */
    ERROR,
}
