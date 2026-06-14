package com.local.smsllm.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** User-facing preference for which hardware backend to use. */
enum class BackendChoice { AUTO, CPU, GPU, NPU }

/**
 * Tries each hardware backend in priority order and returns the first [Engine] that
 * initialises successfully, paired with the backend name string.
 *
 * AUTO order: NPU → GPU → CPU (best-effort hardware acceleration).
 * Other choices: only that backend is attempted, and an exception is thrown on failure.
 */
@Singleton
class BackendSelector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Returns an initialized [Engine] and the backend name that succeeded, or throws
     * [IllegalStateException] if every candidate failed.
     *
     * Must be called from a coroutine; dispatches on [Dispatchers.Default].
     */
    suspend fun open(
        modelPath: String,
        cacheDir: String,
        pref: BackendChoice,
    ): Pair<Engine, String> = withContext(Dispatchers.Default) {
        val candidates = when (pref) {
            BackendChoice.AUTO -> listOf(BackendChoice.NPU, BackendChoice.GPU, BackendChoice.CPU)
            else -> listOf(pref)
        }

        val tried = mutableListOf<String>()
        var lastError: Throwable? = null

        for (candidate in candidates) {
            val name = candidate.name
            tried += name
            val backend = when (candidate) {
                BackendChoice.NPU -> Backend.NPU(
                    nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
                )
                BackendChoice.GPU -> Backend.GPU()
                BackendChoice.CPU, BackendChoice.AUTO -> Backend.CPU()
            }
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = cacheDir,
            )
            val engine = Engine(config)
            try {
                engine.initialize()
                return@withContext Pair(engine, name)
            } catch (t: Throwable) {
                runCatching { engine.close() }
                lastError = t
            }
        }

        throw IllegalStateException(
            "All backends failed (tried: ${tried.joinToString()}). Last error: ${lastError?.message}",
            lastError,
        )
    }
}
