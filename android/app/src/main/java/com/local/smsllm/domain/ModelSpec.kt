package com.local.smsllm.domain

/**
 * Describes a downloadable LiteRT-LM model file and its runtime requirements.
 *
 * @param modelId     Stable identifier used in the registry and preferences.
 * @param filename    Local filename used when saving the model to disk.
 * @param url         Remote URL from which the model is downloaded.
 * @param expectedBytes Expected download size in bytes; used to validate integrity.
 * @param needsNoThink When true, append "/no_think" to the system instruction so
 *                    the model skips its chain-of-thought reasoning block.
 * @param npuVariants Alternative filenames for NPU-accelerated variants (future use).
 */
data class ModelSpec(
    val modelId: String,
    val filename: String,
    val url: String,
    val expectedBytes: Long,
    val needsNoThink: Boolean,
    val npuVariants: List<String> = emptyList(),
)

/** Central registry of supported on-device models. */
object ModelRegistry {

    val QWEN3_0_6B = ModelSpec(
        modelId = "qwen3_0_6b",
        filename = "qwen3_0_6b_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
        expectedBytes = 497_664_000L,
        needsNoThink = true,
    )

    val ALL: List<ModelSpec> = listOf(QWEN3_0_6B)

    /** Returns the [ModelSpec] with the given [id], or null if not registered. */
    fun byId(id: String): ModelSpec? = ALL.firstOrNull { it.modelId == id }
}
