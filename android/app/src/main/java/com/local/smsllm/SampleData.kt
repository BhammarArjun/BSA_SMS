package com.local.smsllm

/** Hardcoded sample Indian bank/UPI SMS messages for testing. */
object SampleData {

    const val MODEL_FILENAME = "qwen3_0_6b_mixed_int4.litertlm"

    /** A few representative messages. First one is the default test. */
    val SAMPLES = listOf(
        "Dear Customer, Rs.2,499.00 has been debited from your HDFC Bank A/c XX1234 on 12-06-26 to VPA swiggy@ybl (UPI Ref 412345678901). Avl Bal: Rs.18,230.55",
        "INR 55,000.00 credited to your ICICI Bank Account XX7788 on 01-Jun-26 by NEFT from ACME PAYROLL PVT LTD. Available balance INR 91,204.10.",
        "Your OTP for the transaction is 884213. Do not share it with anyone. - SBI",
        "Get 40% OFF on your next order! Use code SAVE40. Order now on FreshMart. T&C apply."
    )
}
