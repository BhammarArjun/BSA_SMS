package com.local.smsllm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of one extraction call, with timing for the spike's benchmark display. */
data class ExtractResult(
    val text: String,
    val genMs: Long,
    val approxTokens: Int,
) {
    /** Rough tok/s: ~4 chars per token. Good enough to compare backends/devices. */
    val tokensPerSec: Double
        get() = if (genMs > 0) approxTokens * 1000.0 / genMs else 0.0
}

/**
 * Thin wrapper around the LiteRT-LM Kotlin SDK.
 * One [Engine] is kept loaded; each extraction uses a fresh conversation so SMS are independent.
 */
class LlmEngine {

    private var engine: Engine? = null

    @Volatile var loadMs: Long = 0L
        private set

    @Volatile var loadedBackend: String = "none"
        private set

    val isLoaded: Boolean get() = engine != null

    /** Loads the model on the given backend. Blocking — call off the main thread. */
    suspend fun load(modelPath: String, cacheDir: String, useGpu: Boolean) = withContext(Dispatchers.Default) {
        close()
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir,
        )
        val t0 = System.currentTimeMillis()
        val e = Engine(config)
        e.initialize()
        loadMs = System.currentTimeMillis() - t0
        loadedBackend = if (useGpu) "GPU" else "CPU"
        engine = e
    }

    /** Runs one extraction. Blocking — call off the main thread. */
    suspend fun extract(sms: String): ExtractResult = withContext(Dispatchers.Default) {
        val e = engine ?: error("Model not loaded")
        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(SampleData.SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1),
        )
        e.createConversation(convConfig).use { conversation ->
            val t0 = System.currentTimeMillis()
            val response = conversation.sendMessage(sms)
            val genMs = System.currentTimeMillis() - t0
            val text = response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
            ExtractResult(text = text, genMs = genMs, approxTokens = (text.length / 4).coerceAtLeast(1))
        }
    }

    fun close() {
        engine?.close()
        engine = null
        loadedBackend = "none"
    }
}
