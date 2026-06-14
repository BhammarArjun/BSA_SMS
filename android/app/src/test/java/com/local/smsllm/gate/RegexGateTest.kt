package com.local.smsllm.gate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexGateTest {

    @Test
    fun `HDFC debit SMS passes gate`() {
        val body = "Rs.2,499.00 has been debited from your HDFC Bank A/c XX1234 on 03-Jan-26 to VPA swiggy@ybl. Avl bal Rs.12,000.50"
        assertTrue(RegexGate.passes("HDFC-Bank", body))
    }

    @Test
    fun `ICICI credit NEFT SMS passes gate`() {
        val body = "INR 55,000.00 credited to your ICICI Bank Account XX7788 by NEFT on 01-Jun-26."
        assertTrue(RegexGate.passes("ICICIB", body))
    }

    @Test
    fun `OTP SMS fails gate - no amount`() {
        val body = "Your OTP for the transaction is 884213. Do not share."
        assertFalse(RegexGate.passes("SBI", body))
    }

    @Test
    fun `promo SMS fails gate`() {
        val body = "Get 40% OFF on your next order! Use code SAVE40"
        assertFalse(RegexGate.passes("PROMO", body))
    }

    @Test
    fun `UPI debit SMS passes gate`() {
        val body = "Rs.350 debited from A/c XX12 to zomato@upi on 03-01-26. Avl Bal Rs.900"
        assertTrue(RegexGate.passes("SBIUPI", body))
    }

    @Test
    fun `EMI reminder with amount passes gate - permissive`() {
        val body = "EMI of Rs.5000 due on 05-Jan for your loan A/c. Please ensure sufficient balance."
        // Gate is permissive; LLM decides it's not an actual transaction
        // This body has amount Rs.5000 - but does it have a keyword?
        // "due" is not in keywords, "loan" not in keywords, "A/c" IS in keywords
        assertTrue(RegexGate.passes("HDFC", body))
    }

    @Test
    fun `empty body fails gate`() {
        assertFalse(RegexGate.passes("BANK", ""))
    }

    @Test
    fun `rupee symbol with amount and spent keyword passes gate`() {
        val body = "₹1200 spent at BigBasket"
        assertTrue(RegexGate.passes("AXIS", body))
    }

    @Test
    fun `INR amount with purchase keyword passes gate`() {
        val body = "INR 500 purchase at Amazon"
        assertTrue(RegexGate.passes("HDFC", body))
    }

    @Test
    fun `amount only no keyword fails gate`() {
        val body = "Your balance is Rs.5000"
        assertFalse(RegexGate.passes("BANK", body))
    }
}
