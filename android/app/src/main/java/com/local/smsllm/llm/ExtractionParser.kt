package com.local.smsllm.llm

import com.local.smsllm.domain.Category
import com.local.smsllm.domain.ExtractionResult
import com.local.smsllm.domain.TxnDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull

/**
 * Parses and repairs the raw text emitted by the on-device LLM into a structured [ExtractionResult].
 *
 * The pipeline:
 *  1. Strip `<think>…</think>` blocks and markdown code fences.
 *  2. Extract the first balanced `{…}` JSON object substring.
 *  3. Repair common small-model glitches (doubled quotes, trailing commas, smart-quotes).
 *  4. Decode with a lenient [Json] instance into an internal DTO.
 *  5. Coerce / validate each field into domain types.
 *  6. On total failure return a safe default with `isTransaction=false`.
 */
object ExtractionParser {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // Matches <think>…</think> including multi-line and empty variants
    private val THINK_BLOCK = Regex("""<think>[\s\S]*?</think>""", RegexOption.DOT_MATCHES_ALL)

    // Matches opening ``` optionally followed by "json" and closing ```
    private val CODE_FENCE = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.DOT_MATCHES_ALL)

    // Smart-quote characters to replace with standard ASCII quotes
    private val SMART_QUOTES = Regex("""[""'']""")

    /** Safe default returned when no JSON can be decoded from [modelOutput]. */
    private fun safeDefault(modelOutput: String) = ExtractionResult(
        isTransaction = false,
        direction = null,
        amount = null,
        currency = null,
        dateText = null,
        counterparty = null,
        category = null,
        confidence = 0.0,
        raw = modelOutput,
    )

    /**
     * Parses [modelOutput] into an [ExtractionResult].
     * Never throws; returns a safe default on unrecoverable errors.
     */
    fun parse(modelOutput: String): ExtractionResult {
        // Step 1: strip think blocks and code fences
        var text = THINK_BLOCK.replace(modelOutput, "")
        text = CODE_FENCE.replace(text) { it.groupValues[1] }

        // Step 2: extract first balanced { … } substring
        val jsonString = extractFirstJsonObject(text) ?: return safeDefault(modelOutput)

        // Step 3: repair common small-model glitches
        val repaired = repair(jsonString)

        // Step 4: decode
        val dto = try {
            lenientJson.decodeFromString(ExtractionDto.serializer(), repaired)
        } catch (e: Exception) {
            return safeDefault(modelOutput)
        }

        // Step 5: coerce fields into domain types
        val isTransaction = dto.is_transaction?.let {
            when (it) {
                is JsonPrimitive -> it.booleanOrNull ?: false
                else -> false
            }
        } ?: false

        val direction = dto.direction?.let {
            when (it) {
                is JsonPrimitive -> TxnDirection.fromString(it.content.trim().lowercase())
                else -> null
            }
        }

        val amount = dto.amount?.let {
            when (it) {
                is JsonPrimitive -> {
                    val content = it.content.trim()
                    if (content == "null" || content.isEmpty()) null
                    else parseAmount(content)
                }
                else -> null
            }
        }

        val currency = dto.currency?.let {
            when (it) {
                is JsonPrimitive -> it.content.takeIf { c -> c != "null" && c.isNotEmpty() }
                else -> null
            }
        }

        val dateText = dto.date?.let {
            when (it) {
                is JsonPrimitive -> it.content.takeIf { c -> c != "null" && c.isNotEmpty() }
                else -> null
            }
        }

        val counterparty = dto.counterparty?.let {
            when (it) {
                is JsonPrimitive -> it.content.takeIf { c -> c != "null" && c.isNotEmpty() }
                else -> null
            }
        }

        val category = dto.category?.let {
            when (it) {
                is JsonPrimitive -> {
                    val id = it.content.takeIf { c -> c != "null" && c.isNotEmpty() }
                    Category.fromId(id)
                }
                else -> null
            }
        }

        val rawConfidence = dto.confidence?.let {
            when (it) {
                // Some small models emit confidence as a quoted string (e.g. "confidence":"0.9").
                // Fall back to content.toDoubleOrNull() when doubleOrNull returns null so that
                // both numeric and string representations are accepted.
                is JsonPrimitive -> it.doubleOrNull ?: it.content.toDoubleOrNull()
                else -> null
            }
        } ?: 0.0
        val confidence = rawConfidence.coerceIn(0.0, 1.0)

        // Step 5c: when isTransaction==false, null out all transactional fields
        return if (isTransaction) {
            ExtractionResult(
                isTransaction = true,
                direction = direction,
                amount = amount,
                currency = currency,
                dateText = dateText,
                counterparty = counterparty,
                category = category,
                confidence = confidence,
                raw = modelOutput,
            )
        } else {
            ExtractionResult(
                isTransaction = false,
                direction = null,
                amount = null,
                currency = null,
                dateText = null,
                counterparty = null,
                category = null,
                confidence = confidence,
                raw = modelOutput,
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Finds the first balanced `{ … }` substring in [text], or null if none.
     *
     * Tracks string context so that `{` / `}` characters inside quoted string values
     * are not counted toward brace depth. Respects `\` escape sequences (e.g. `\"`)
     * so an escaped quote inside a string does not prematurely end the string context.
     */
    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0; var inString = false; var i = start
        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (ch == '\\') i++ else if (ch == '"') inString = false
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
            i++
        }
        return null
    }

    /**
     * Repairs common small-model JSON glitches.
     *
     * Known failure modes handled:
     *  - Smart/curly quotes → ASCII double-quotes
     *  - Trailing comma before `}` or `]`
     *  - Gemma int4: `"direction":"debit,""amount":…` → `"direction":"debit","amount":…`
     *    The model omits the closing `"` of a string value and the separator comma, fusing
     *    the value tail with the next key as: `<value-chars>,""<key>"`. The fix targets
     *    only this structural pattern — `,""` preceded by a word character (end of a value
     *    token) and followed by a word character (start of a key) — to avoid corrupting a
     *    legitimate string value that happens to contain the literal substring `,""`
     *    (e.g. `"note":"see ,\"\" below"`).
     */
    private fun repair(json: String): String {
        var s = json

        // Replace smart/curly quotes with straight double-quotes
        s = SMART_QUOTES.replace(s, "\"")

        // Gemma int4 glitch: collapse `<word-char>,""<word-char>` → `<word-char>","<word-char>`
        // Anchoring on \w on both sides ensures we only fix the structural glitch and not a
        // value that genuinely contains the substring `,""`).
        s = Regex("""(\w),""(\w)""").replace(s, """$1","$2""")

        // Remove trailing commas before } or ]
        s = Regex(""",\s*([}\]])""").replace(s, "$1")

        return s
    }

    /** Strips commas, currency symbols, and spaces, then parses as Double. */
    private fun parseAmount(raw: String): Double? {
        val cleaned = raw.replace(",", "")
            .replace(Regex("""[₹$€£¥]"""), "")
            .replace("INR", "", ignoreCase = true)
            // Strip "Rs" together with an optional trailing dot (e.g. "Rs." or "Rs").
            // Using a regex so both "Rs2499" and "Rs.2499" are handled uniformly.
            .replace(Regex("""(?i)rs\.?"""), "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    // ── Internal DTO ──────────────────────────────────────────────────────────

    /**
     * Lenient internal DTO that accepts any JSON value type for each field.
     * Using [JsonElement] so we can handle amount as either number or string.
     */
    @Serializable
    private data class ExtractionDto(
        val is_transaction: JsonElement? = null,
        val direction: JsonElement? = null,
        val amount: JsonElement? = null,
        val currency: JsonElement? = null,
        val date: JsonElement? = null,
        val counterparty: JsonElement? = null,
        val category: JsonElement? = null,
        val confidence: JsonElement? = null,
    )
}
