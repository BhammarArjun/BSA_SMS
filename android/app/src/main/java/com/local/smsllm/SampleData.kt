package com.local.smsllm

/** Hardcoded prompt + sample Indian bank/UPI SMS for the spike. */
object SampleData {

    const val MODEL_FILENAME = "qwen3_0_6b_mixed_int4.litertlm"

    /** Strict extraction instruction. Asks for one line of minified JSON.
     *  `/no_think` disables Qwen3's chain-of-thought (otherwise it burns the token
     *  budget reasoning before emitting JSON). */
    val SYSTEM_INSTRUCTION = """
        You are a strict information extractor for Indian bank and UPI transaction SMS.
        Given ONE SMS message, output ONLY a single-line minified JSON object, nothing else.
        Keys (use exactly these):
        {"is_transaction":bool,"direction":"debit"|"credit"|null,"amount":number|null,
         "currency":string|null,"date":string|null,"counterparty":string|null,
         "category":string|null,"confidence":number}
        Rules:
        - If the SMS says money was debited, credited, spent, withdrawn, paid, or received
          with an amount on an account/card/UPI, then is_transaction MUST be true.
        - OTP, promotional/offer, and balance-only messages are NOT transactions:
          set is_transaction=false and every other field null.
        - direction is "debit" when money leaves the account (debited/spent/withdrawn/paid),
          "credit" when money arrives (credited/received).
        - amount is a plain number (no commas, no currency symbol). currency is like "INR".
        - counterparty is the merchant/person/VPA if present, else null.
        - category is a short guess like "food","shopping","transfer","bills","salary","atm", else null.
        - Do not wrap the JSON in markdown. Do not add explanations.

        Example:
        SMS: Rs.350 debited from A/c XX12 to zomato@upi on 03-01-26. Avl Bal Rs.900
        Output JSON: {"is_transaction":true,"direction":"debit","amount":350,"currency":"INR","date":"03-01-26","counterparty":"zomato@upi","category":"food","confidence":0.97}
        /no_think
    """.trimIndent()

    /** A few representative messages. First one is the default test. */
    val SAMPLES = listOf(
        "Dear Customer, Rs.2,499.00 has been debited from your HDFC Bank A/c XX1234 on 12-06-26 to VPA swiggy@ybl (UPI Ref 412345678901). Avl Bal: Rs.18,230.55",
        "INR 55,000.00 credited to your ICICI Bank Account XX7788 on 01-Jun-26 by NEFT from ACME PAYROLL PVT LTD. Available balance INR 91,204.10.",
        "Your OTP for the transaction is 884213. Do not share it with anyone. - SBI",
        "Get 40% OFF on your next order! Use code SAVE40. Order now on FreshMart. T&C apply."
    )
}
