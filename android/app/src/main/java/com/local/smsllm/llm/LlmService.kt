package com.local.smsllm.llm

import com.local.smsllm.domain.ExtractionResult

/**
 * Contract for the on-device LLM extraction service.
 *
 * Implementations load a model onto a hardware backend and run SMS extraction.
 * All suspend functions are safe to call from any coroutine context — they
 * dispatch onto [kotlinx.coroutines.Dispatchers.Default] internally.
 */
interface LlmService {
    /**
     * Ensures the model is loaded on the preferred backend.
     * No-ops if the model is already loaded; re-loads if [pref] changed.
     * Throws on failure (e.g. model file missing, all backends rejected).
     */
    suspend fun ensureLoaded(pref: BackendChoice)

    /**
     * Extracts structured transaction data from [sms].
     * [ensureLoaded] must have been called and succeeded before calling this.
     */
    suspend fun extract(sms: String): ExtractionResult

    /** Human-readable name of the currently loaded backend ("CPU"/"GPU"/"NPU"), or null. */
    fun loadedBackend(): String?

    /** True when a model is loaded and ready to accept [extract] calls. */
    fun isLoaded(): Boolean

    /** Releases the native Engine and its resources. Safe to call multiple times. */
    fun close()
}
