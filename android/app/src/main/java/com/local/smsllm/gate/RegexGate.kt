package com.local.smsllm.gate

/**
 * Lightweight pre-filter that decides whether an SMS is worth sending to the LLM.
 *
 * Design intent: HIGH RECALL over precision. False positives are acceptable here because
 * the LLM will filter them out. False negatives (real transactions we miss) are permanent.
 *
 * An SMS passes if it contains BOTH:
 *  1. A recognisable monetary amount (Rs / INR / ₹ followed by digits).
 *  2. At least one financial keyword (debited, credited, UPI, NEFT, etc.).
 */
object RegexGate {

    private val AMOUNT = Regex("""(?i)(?:rs\.?|inr|₹)\s?\d[\d,]*(?:\.\d{1,2})?""")

    private val KEYWORDS = Regex(
        """(?i)\b(debited|credited|debit|credit|spent|withdrawn|withdrawal|paid|payment|sent|received|txn|transaction|purchase|a/c|upi|imps|neft|rtgs)\b"""
    )

    /**
     * Returns true if [body] contains both an amount pattern and a financial keyword.
     * The [sender] parameter is reserved for future allow-list/block-list logic.
     */
    fun passes(sender: String, body: String): Boolean =
        AMOUNT.containsMatchIn(body) && KEYWORDS.containsMatchIn(body)
}
