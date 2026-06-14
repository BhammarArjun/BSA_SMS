package com.local.smsllm.llm

import com.local.smsllm.domain.Category
import com.local.smsllm.domain.ModelSpec

/**
 * Builds the system instruction sent to the LLM before each SMS extraction.
 *
 * The instruction is stable and model-agnostic; the only model-specific tweak is
 * appending "/no_think" for models (like Qwen3) that require it to suppress chain-of-thought.
 */
object PromptBuilder {

    private val CATEGORY_IDS = Category.entries.joinToString(", ") { it.id }

    val SYSTEM_INSTRUCTION = """
        You extract structured data from ONE Indian bank/UPI transaction SMS.
        Output ONLY one line of minified JSON. No markdown, no prose, no extra text.

        Schema (use exactly these keys, in this order):
        {"is_transaction":<bool>,"direction":<"debit"|"credit"|null>,"amount":<number|null>,"currency":<string|null>,"date":<string|null>,"counterparty":<string|null>,"category":<string|null>,"confidence":<number 0..1>}

        Rules:
        1. is_transaction = true ONLY if the SMS reports actual money moving on a bank account, card, or wallet (debited, credited, spent, withdrawn, paid, sent, received, purchase of <amount>).
        2. is_transaction = false for: OTP codes, promotional/offer messages, EMI/bill DUE reminders (no money moved yet), balance-only alerts, FAILED/DECLINED transactions, and delivery/info messages. When false, set every other field to null and confidence = your certainty it is NOT a transaction.
        3. direction: "debit" when money leaves (debited/spent/withdrawn/paid/sent/purchase); "credit" when money arrives (credited/received/refund/salary).
        4. amount: number only, no commas or symbols (e.g. "Rs.2,499.00" -> 2499). currency is usually "INR".
        5. date: copy the date exactly as printed; if absent, null.
        6. counterparty: merchant, person, or VPA/UPI handle if present; else null.
        7. category: choose EXACTLY ONE id from this list, or null if unsure:
           $CATEGORY_IDS
        8. confidence: 0..1 overall certainty.

        Examples:
        SMS: Rs.350 debited from A/c XX12 to zomato@upi on 03-01-26. Avl Bal Rs.900
        JSON: {"is_transaction":true,"direction":"debit","amount":350,"currency":"INR","date":"03-01-26","counterparty":"zomato@upi","category":"food","confidence":0.97}
        SMS: INR 55000 credited to A/c XX77 by NEFT from ACME PAYROLL on 01-Jun-26.
        JSON: {"is_transaction":true,"direction":"credit","amount":55000,"currency":"INR","date":"01-Jun-26","counterparty":"ACME PAYROLL","category":"income_salary","confidence":0.95}
        SMS: Your OTP is 884213. Do not share. -SBI
        JSON: {"is_transaction":false,"direction":null,"amount":null,"currency":null,"date":null,"counterparty":null,"category":null,"confidence":0.99}
    """.trimIndent()

    /**
     * Returns the system instruction for the given [spec].
     * Appends "/no_think" when [ModelSpec.needsNoThink] is true.
     */
    fun systemInstruction(spec: ModelSpec): String =
        if (spec.needsNoThink) "$SYSTEM_INSTRUCTION\n/no_think" else SYSTEM_INSTRUCTION
}
