package com.local.smsllm.llm

import android.content.Context
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.SamplerConfig
import com.local.smsllm.domain.ExtractionResult
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.domain.ModelSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [LlmService] backed by the LiteRT-LM Kotlin SDK (litertlm 0.13.1).
 *
 * One [Engine] is kept alive for the lifetime of the service; each [extract] call
 * creates a fresh [com.google.ai.edge.litertlm.Conversation] so SMS messages are
 * fully independent (no cross-contamination from KV-cache).
 *
 * Thread safety: [ensureLoaded] uses a [Mutex] to guarantee that at most one coroutine
 * builds the [Engine] even when multiple callers race; a cheap volatile null-check before
 * lock acquisition avoids contention once the engine is ready. [close] sets the field to
 * null under [@Volatile] before closing, so concurrent [extract] callers see null and fail
 * fast rather than using a closed engine.
 *
 * Note — constrained decoding:
 *   [ConversationConfig] in litertlm 0.13.1 does NOT expose an enableConstrainedDecoding
 *   parameter. The experimental API surface ([ExperimentalFlags]) does expose
 *   [com.google.ai.edge.litertlm.ExperimentalFlags.enableConversationConstrainedDecoding],
 *   but it is a global flag gated behind @ExperimentalApi and applies to all sessions,
 *   not per-conversation. We do not set it here — the risk of side-effects on unrelated
 *   sessions is too high for a Singleton service, and [ExtractionParser] already handles
 *   malformed output safely.
 */
@Singleton
class LiteRtLmService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val backendSelector: BackendSelector,
) : LlmService {

    private val spec: ModelSpec = ModelRegistry.QWEN3_0_6B

    @Volatile private var engine: Engine? = null
    @Volatile private var backendName: String? = null
    private val loadMutex = Mutex()

    override suspend fun ensureLoaded(pref: BackendChoice) {
        if (engine != null) return // fast-path: no lock needed once loaded
        withContext(Dispatchers.Default) {
            loadMutex.withLock {
                if (engine != null) return@withLock // re-check after acquiring lock
                val modelPath = modelManager.resolveModelPath(spec)
                val (newEngine, name) = backendSelector.open(
                    modelPath = modelPath,
                    cacheDir = context.cacheDir.absolutePath,
                    pref = pref,
                )
                engine = newEngine
                backendName = name
            }
        }
    }

    override suspend fun extract(sms: String): ExtractionResult = withContext(Dispatchers.Default) {
        val e = engine ?: error("Model not loaded — call ensureLoaded() first")
        val convConfig = ConversationConfig(
            systemInstruction = Contents.of(PromptBuilder.systemInstruction(spec)),
            samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1),
        )
        val rawText = e.createConversation(convConfig).use { conversation ->
            val response = conversation.sendMessage(sms)
            response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
                .trim()
        }
        ExtractionParser.parse(rawText)
    }

    override fun loadedBackend(): String? = backendName

    override fun isLoaded(): Boolean = engine != null

    override fun close() {
        val e = engine
        engine = null
        backendName = null
        e?.close()
    }
}
